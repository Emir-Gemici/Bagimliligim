package com.bagimliligim.app.domain

import org.junit.Assert.*
import org.junit.Test

class SmokingTest {
    @Test fun `turkish decimal prices parse exactly without binary floating point`() {
        assertEquals(12050L, Money.parseKurus("120,50"))
        assertEquals(12050L, Money.parseKurus("120.50"))
        listOf("NaN", "-1", "0", "2,345", "1.000,00", "99999999999").forEach { assertNull(Money.parseKurus(it)) }
    }
    @Test fun `single cigarette entries add up to exact pack expense`() {
        assertEquals(123_450_000L, (1..20).sumOf { Money.costMicros(12345, 20, 1) })
        assertEquals(18_000_000L, Money.costMicros(12000, 20, 3))
    }
    @Test fun `missing days do not manufacture reduction or smoke free streaks`() {
        assertNull(Reduction.percent(140, 0, 7, 1))
        assertNull(Reduction.percent(0, 0, 7, 7))
        assertEquals(50, Reduction.percent(140, 70, 7, 7))
        assertEquals(-100, Reduction.percent(70, 140, 7, 7))
    }
    @Test fun `stale and future comparison prices are excluded`() {
        val now = 2_000_000_000_000L
        assertTrue(ComparisonRotation.fresh(now, now))
        assertFalse(ComparisonRotation.fresh(now + 1, now))
        assertFalse(ComparisonRotation.fresh(now - ComparisonRotation.MAX_AGE_MILLIS - 1, now))
    }
    @Test fun `rotation avoids repeating until catalog exhausted`() {
        assertEquals("third", ComparisonRotation.next(listOf("first", "second", "third"), listOf("first", "second")))
        assertEquals("first", ComparisonRotation.next(listOf("first", "second"), listOf("first", "second")))
        assertNull(ComparisonRotation.next(emptyList(), emptyList()))
    }
}
