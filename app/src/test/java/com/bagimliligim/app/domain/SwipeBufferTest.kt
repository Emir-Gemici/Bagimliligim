package com.bagimliligim.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SwipeBufferTest {
    private val utc = ZoneId.of("UTC")
    private val beforeMidnight = Instant.parse("2026-10-15T23:59:59Z").toEpochMilli()
    @Test fun `midnight hour platform and calibration boundaries survive batching`() {
        val buffer = SwipeBuffer()
        buffer.add(AppPlatform.INSTAGRAM, beforeMidnight, 12f, utc)
        buffer.add(AppPlatform.INSTAGRAM, beforeMidnight + 1000, 12f, utc)
        buffer.add(AppPlatform.INSTAGRAM, beforeMidnight + 2000, 10f, utc)
        buffer.add(AppPlatform.TIKTOK, beforeMidnight + 2000, 10f, utc)
        val rows = buffer.drain()
        assertEquals(4, rows.size)
        assertEquals("2026-10-15", rows[0].dateString)
        assertEquals(23, rows[0].hourOfDay)
        assertEquals("2026-10-16", rows[1].dateString)
        assertEquals(0, rows[1].hourOfDay)
        assertEquals(.44, rows.sumOf { it.distanceMeters.toDouble() }, .00001)
        assertEquals(0, buffer.size())
        assertTrue(buffer.drain().isEmpty())
    }
    @Test fun `snapshot cannot clear newly arriving swipes`() {
        val buffer = SwipeBuffer()
        repeat(10) { buffer.add(AppPlatform.YOUTUBE, beforeMidnight, 12f, utc) }
        val snapshot = buffer.drain()
        buffer.add(AppPlatform.YOUTUBE, beforeMidnight, 12f, utc)
        assertEquals(10, snapshot.sumOf { it.swipeCount })
        assertEquals(1, buffer.size())
        assertTrue(snapshot.first().batchId != buffer.drain().first().batchId)
    }
    @Test fun `concurrent drains preserve every swipe`() {
        val buffer = SwipeBuffer()
        val pool = Executors.newFixedThreadPool(4)
        val collected = java.util.Collections.synchronizedList(mutableListOf<SwipeBatch>())
        repeat(3) { pool.submit { repeat(500) { buffer.add(AppPlatform.TIKTOK, beforeMidnight, 12f, utc) } } }
        pool.submit { repeat(200) { collected.addAll(buffer.drain()) } }
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        collected.addAll(buffer.drain())
        assertEquals(1500, collected.sumOf { it.swipeCount })
    }
    @Test fun `event timezone determines stored day`() {
        val buffer = SwipeBuffer()
        buffer.add(AppPlatform.TIKTOK, beforeMidnight, 12f, ZoneId.of("Europe/Istanbul"))
        assertEquals("2026-10-16", buffer.drain().first().dateString)
    }
}
