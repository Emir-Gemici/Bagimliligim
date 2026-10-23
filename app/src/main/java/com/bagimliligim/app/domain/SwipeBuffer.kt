package com.bagimliligim.app.domain

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** All operations share a lock; a snapshot never clears a concurrently arriving swipe. */
class SwipeBuffer {
    private val counts = ConcurrentHashMap<AppPlatform, Int>()
    private val buckets = linkedMapOf<Key, SwipeBatch>()
    private data class Key(val platform: AppPlatform, val date: String, val hour: Int, val length: Float)

    @Synchronized
    fun add(platform: AppPlatform, timestamp: Long, lengthCm: Float, zone: ZoneId) {
        require(lengthCm.isFinite() && lengthCm > 0)
        val local = Instant.ofEpochMilli(timestamp).atZone(zone)
        val key = Key(platform, local.toLocalDate().toString(), local.hour, lengthCm)
        counts.merge(platform, 1, Int::plus)
        val old = buckets[key]
        buckets[key] = if (old == null) SwipeBatch(UUID.randomUUID().toString(), platform, 1,
            lengthCm / 100f, timestamp, key.date, key.hour)
        else old.copy(swipeCount = old.swipeCount + 1, distanceMeters = old.distanceMeters + lengthCm / 100f)
    }

    @Synchronized fun size(): Int = counts.values.sum()
    @Synchronized fun totals(date: String): Totals {
        val rows = buckets.values.filter { it.dateString == date }
        return Totals(rows.sumOf { it.swipeCount }, rows.sumOf { it.distanceMeters.toDouble() }.toFloat())
    }
    @Synchronized fun drain(): List<SwipeBatch> = buckets.values.toList().also { buckets.clear(); counts.clear() }
}
