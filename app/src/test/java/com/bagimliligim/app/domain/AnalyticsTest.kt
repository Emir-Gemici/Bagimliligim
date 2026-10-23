package com.bagimliligim.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AnalyticsTest {
    private val today = LocalDate.of(2026, 1, 3)
    @Test fun `weekly range includes seven days across year boundary`() {
        val range = Analytics.range(StatsPeriod.WEEKLY, today)
        assertEquals(LocalDate.of(2025, 12, 28), range.start)
        assertEquals(7, range.days)
        val points = Analytics.points(StatsPeriod.WEEKLY, today, listOf(BucketTotal("2026-01-01", 10, 1.2f)))
        assertEquals(7, points.size)
        assertEquals(10, points.sumOf { it.swipes })
        assertEquals(6, points.count { it.swipes == 0 })
    }
    @Test fun `hourly and annual charts fill all buckets`() {
        assertEquals(24, Analytics.points(StatsPeriod.DAILY, today, emptyList()).size)
        assertEquals(12, Analytics.points(StatsPeriod.YEARLY, today, emptyList()).size)
        assertEquals(3, Analytics.range(StatsPeriod.YEARLY, today).days)
        assertEquals(30, Analytics.range(StatsPeriod.MONTHLY, today).days)
    }
    @Test fun `leap year denominator includes february 29`() {
        assertEquals(60, Analytics.range(StatsPeriod.YEARLY, LocalDate.of(2024, 2, 29)).days)
    }
    @Test fun `quota boundaries and non divisible budgets are exact`() {
        assertNull(QuotaPolicy.threshold(159, 200))
        assertEquals(80, QuotaPolicy.threshold(160, 200))
        assertEquals(80, QuotaPolicy.threshold(199, 200))
        assertEquals(100, QuotaPolicy.threshold(200, 200))
        assertEquals(100, QuotaPolicy.threshold(210, 200))
        assertNull(QuotaPolicy.threshold(40, 51))
        assertEquals(80, QuotaPolicy.threshold(41, 51))
        assertNull(QuotaPolicy.threshold(0, 0))
    }
}
