package com.bagimliligim.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    @Test fun preferencesPersistClampAndDeduplicateWarnings() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SettingsStore(context)
        store.setQuota(5000)
        store.setSwipeLength(1f)
        store.completeOnboarding(false)
        val recreated = SettingsStore(context)
        assertEquals(1000, recreated.preferences.first().dailyQuota)
        assertEquals(3f, recreated.preferences.first().swipeLengthCm, .001f)
        assertFalse(recreated.preferences.first().accessibilityConsent)
        val date = LocalDate.of(2040, 1, 1)
        store.claimWarning(date.minusDays(1), 100)
        assertTrue(store.claimWarning(date, 80))
        assertFalse(recreated.claimWarning(date, 80))
        assertTrue(store.claimWarning(date, 100))
        assertFalse(recreated.claimWarning(date, 100))
        assertTrue(store.claimWarning(date.plusDays(1), 80))
        store.setQuota(200)
        store.setSwipeLength(12f)
    }
}
