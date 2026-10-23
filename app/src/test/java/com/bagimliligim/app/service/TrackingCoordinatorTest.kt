package com.bagimliligim.app.service

import com.bagimliligim.app.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingCoordinatorTest {
    private class FakeRepository : SwipeRepository {
        val saved = linkedMapOf<String, SwipeBatch>()
        var fail = false
        var failAfterCommit = false
        var failEveryCommit = false
        var failReads = false
        var writes = 0
        override fun observeTotals(start: LocalDate, end: LocalDate) = flowOf(Totals())
        override fun observePlatforms(date: LocalDate) = flowOf(emptyList<PlatformTotal>())
        override fun observeBuckets(period: StatsPeriod, today: LocalDate) = flowOf(emptyList<BucketTotal>())
        override fun observeAllTime() = flowOf(Totals())
        override suspend fun totals(date: LocalDate): Totals {
            if (failReads) error("read unavailable")
            val rows = saved.values.filter { it.dateString == date.toString() }
            return Totals(rows.sumOf { it.swipeCount }, rows.sumOf { it.distanceMeters.toDouble() }.toFloat())
        }
        override suspend fun save(batches: List<SwipeBatch>) {
            writes++
            if (fail) error("disk full")
            batches.forEach { saved.putIfAbsent(it.batchId, it) }
            if (failEveryCommit) error("commit acknowledgement unavailable")
            if (failAfterCommit) { failAfterCommit = false; error("connection lost after commit") }
        }
    }
    private class FakePreferences : PreferencesRepository {
        override val preferences = MutableStateFlow(UserPreferences(accessibilityConsent = true))
        val claims = mutableSetOf<Pair<LocalDate, Int>>()
        var claimCalls = 0
        override suspend fun setQuota(value: Int) { preferences.value = preferences.value.copy(dailyQuota = value) }
        override suspend fun setSwipeLength(value: Float) { preferences.value = preferences.value.copy(swipeLengthCm = value) }
        override suspend fun setWarnings(enabled: Boolean) { preferences.value = preferences.value.copy(warningsEnabled = enabled) }
        override suspend fun setOnlinePrices(enabled: Boolean) { preferences.value = preferences.value.copy(onlinePrices = enabled) }
        override suspend fun completeOnboarding(consent: Boolean) { preferences.value = preferences.value.copy(accessibilityConsent = consent) }
        override suspend fun setAppearance(dark: Boolean, dynamic: Boolean) { preferences.value = preferences.value.copy(darkMode = dark, dynamicColor = dynamic) }
        override suspend fun claimWarning(date: LocalDate, threshold: Int): Boolean {
            claimCalls++
            return claims.add(date to threshold)
        }
    }
    private val timestamp = LocalDate.of(2026, 10, 16).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun `ten swipes create one batch and switching away flushes partial batch`() = runTest {
        val repo = FakeRepository()
        val prefs = FakePreferences()
        var widgets = 0
        val tracker = TrackingCoordinator(repo, prefs, prefs.preferences, backgroundScope, { _, _ -> }, MutableStateFlow(null)) { widgets++ }
        repeat(9) { tracker.record(AppPlatform.TIKTOK, timestamp) }
        runCurrent()
        assertEquals(0, repo.writes)
        tracker.record(AppPlatform.TIKTOK, timestamp)
        runCurrent()
        assertEquals(1, repo.writes)
        assertEquals(10, repo.saved.values.sumOf { it.swipeCount })
        repeat(3) { tracker.record(AppPlatform.YOUTUBE, timestamp) }
        tracker.flushAndAwait()
        assertEquals(13, repo.saved.values.sumOf { it.swipeCount })
        assertEquals(2, widgets)
    }
    @Test fun `failed batch is retained and retried without duplication`() = runTest {
        val repo = FakeRepository().apply { fail = true }
        val prefs = FakePreferences()
        val error = MutableStateFlow<String?>(null)
        val tracker = TrackingCoordinator(repo, prefs, prefs.preferences, backgroundScope, { _, _ -> }, error) {}
        tracker.record(AppPlatform.INSTAGRAM, timestamp)
        tracker.flushAndAwait()
        assertTrue(repo.saved.isEmpty())
        assertNotNull(error.value)
        repo.fail = false
        tracker.record(AppPlatform.INSTAGRAM, timestamp)
        tracker.flushAndAwait()
        assertEquals(2, repo.saved.values.sumOf { it.swipeCount })
        assertNull(error.value)
    }
    @Test fun `retry after an uncertain commit uses the same batch id`() = runTest {
        val repo = FakeRepository().apply { failAfterCommit = true }
        val prefs = FakePreferences()
        val tracker = TrackingCoordinator(repo, prefs, prefs.preferences, backgroundScope, { _, _ -> }, MutableStateFlow(null)) {}
        repeat(4) { tracker.record(AppPlatform.YOUTUBE, timestamp) }
        tracker.flushAndAwait()
        assertEquals(2, repo.writes)
        assertEquals(4, repo.saved.values.sumOf { it.swipeCount })
    }
    @Test fun `warning occurs exactly at each threshold even before a flush`() = runTest {
        val repo = FakeRepository()
        val prefs = FakePreferences().apply { setQuota(51) }
        val warnings = mutableListOf<Int>()
        val tracker = TrackingCoordinator(repo, prefs, prefs.preferences, backgroundScope, { level, _ -> warnings.add(level) }, MutableStateFlow(null)) {}
        repeat(40) { tracker.record(AppPlatform.TIKTOK, timestamp) }
        runCurrent()
        assertTrue(warnings.isEmpty())
        tracker.record(AppPlatform.TIKTOK, timestamp)
        runCurrent()
        assertEquals(listOf(80), warnings)
        repeat(20) { tracker.record(AppPlatform.TIKTOK, timestamp) }
        runCurrent()
        assertEquals(listOf(80, 100), warnings)
        assertEquals(2, prefs.claimCalls)
    }
    @Test fun `without affirmative consent no movement is recorded`() = runTest {
        val repo = FakeRepository()
        val prefs = FakePreferences().apply { completeOnboarding(false) }
        val tracker = TrackingCoordinator(repo, prefs, prefs.preferences, backgroundScope, { _, _ -> fail("No warning expected") }, MutableStateFlow(null)) {}
        repeat(20) { tracker.record(AppPlatform.YOUTUBE, timestamp) }
        tracker.flushAndAwait()
        assertTrue(repo.saved.isEmpty())
    }
    @Test fun `midnight flushes old day and resets daily warnings`() = runTest {
        val repo = FakeRepository()
        val prefs = FakePreferences().apply { setQuota(50) }
        val warnings = mutableListOf<Int>()
        val tracker = TrackingCoordinator(repo, prefs, prefs.preferences, backgroundScope, { level, _ -> warnings.add(level) }, MutableStateFlow(null)) {}
        repeat(41) { tracker.record(AppPlatform.YOUTUBE, timestamp) }
        repeat(40) { tracker.record(AppPlatform.YOUTUBE, timestamp + 86_400_000) }
        tracker.flushAndAwait()
        assertEquals(listOf(80, 80), warnings)
        assertEquals(2, repo.saved.values.map { it.dateString }.distinct().size)
        assertEquals(81, repo.saved.values.sumOf { it.swipeCount })
    }

    @Test fun `unreadable daily baseline never drops accepted swipes`() = runTest {
        val repo = FakeRepository().apply { failReads = true }
        val prefs = FakePreferences().apply { setQuota(50) }
        val warnings = mutableListOf<Int>()
        val tracker = TrackingCoordinator(repo, prefs, prefs.preferences, backgroundScope,
            { level, _ -> warnings.add(level) }, MutableStateFlow(null)) {}
        repeat(41) { tracker.record(AppPlatform.INSTAGRAM, timestamp) }
        tracker.flushAndAwait()
        assertEquals(41, repo.saved.values.sumOf { it.swipeCount })
        assertTrue(warnings.isEmpty())
        repo.failReads = false
        tracker.flushAndAwait()
        tracker.record(AppPlatform.INSTAGRAM, timestamp)
        runCurrent()
        assertEquals(listOf(80), warnings)
        tracker.flushAndAwait()
        assertEquals(42, repo.saved.values.sumOf { it.swipeCount })
    }

    @Test fun `a failed read at midnight preserves both calendar days`() = runTest {
        val repo = FakeRepository()
        val prefs = FakePreferences()
        val tracker = TrackingCoordinator(repo, prefs, prefs.preferences, backgroundScope,
            { _, _ -> }, MutableStateFlow(null)) {}
        tracker.record(AppPlatform.YOUTUBE, timestamp)
        runCurrent()
        repo.failReads = true
        tracker.record(AppPlatform.YOUTUBE, timestamp + 86_400_000)
        tracker.flushAndAwait()
        assertEquals(2, repo.saved.values.sumOf { it.swipeCount })
        assertEquals(2, repo.saved.values.map { it.dateString }.distinct().size)
    }

    @Test fun `returning to a day with an uncertain commit never doubles its quota total`() = runTest {
        val repo = FakeRepository().apply { failEveryCommit = true }
        val prefs = FakePreferences().apply { setQuota(50) }
        val warnings = mutableListOf<Int>()
        val tracker = TrackingCoordinator(repo, prefs, prefs.preferences, backgroundScope,
            { level, _ -> warnings.add(level) }, MutableStateFlow(null)) {}
        repeat(39) { tracker.record(AppPlatform.TIKTOK, timestamp) }
        tracker.record(AppPlatform.TIKTOK, timestamp + 86_400_000)
        tracker.record(AppPlatform.TIKTOK, timestamp)
        tracker.flushAndAwait()
        assertTrue(warnings.isEmpty())
        repo.failEveryCommit = false
        tracker.flushAndAwait()
        tracker.record(AppPlatform.TIKTOK, timestamp)
        tracker.flushAndAwait()
        assertEquals(listOf(80), warnings)
        assertEquals(42, repo.saved.values.sumOf { it.swipeCount })
    }
}
