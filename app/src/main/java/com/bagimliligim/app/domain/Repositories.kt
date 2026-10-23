package com.bagimliligim.app.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class SwipeBatch(
    val batchId: String,
    val platform: AppPlatform,
    val swipeCount: Int,
    val distanceMeters: Float,
    val timestamp: Long,
    val dateString: String,
    val hourOfDay: Int,
)

interface SwipeRepository {
    fun observeTotals(start: LocalDate, end: LocalDate): Flow<Totals>
    fun observePlatforms(date: LocalDate): Flow<List<PlatformTotal>>
    fun observeBuckets(period: StatsPeriod, today: LocalDate): Flow<List<BucketTotal>>
    fun observeAllTime(): Flow<Totals>
    suspend fun totals(date: LocalDate): Totals
    suspend fun save(batches: List<SwipeBatch>)
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setQuota(value: Int)
    suspend fun setSwipeLength(value: Float)
    suspend fun setWarnings(enabled: Boolean)
    suspend fun completeOnboarding(consent: Boolean)
    suspend fun setAppearance(dark: Boolean, dynamic: Boolean)
    suspend fun setOnlinePrices(enabled: Boolean)
    suspend fun claimWarning(date: LocalDate, threshold: Int): Boolean
}
