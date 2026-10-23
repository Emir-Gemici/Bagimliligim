package com.bagimliligim.app.domain

/** Expired, future-dated or unavailable offers must never masquerade as current prices. */
object ComparisonRotation {
    const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
    fun fresh(checkedAt: Long, now: Long): Boolean = now - checkedAt in 0..MAX_AGE_MILLIS
    fun next(ids: List<String>, recentlyShown: List<String>): String? {
        if (ids.isEmpty()) return null
        val unseen = ids.filterNot { it in recentlyShown }
        return unseen.randomOrNull() ?: ids.filterNot { it == recentlyShown.lastOrNull() }.randomOrNull() ?: ids.first()
    }
}
