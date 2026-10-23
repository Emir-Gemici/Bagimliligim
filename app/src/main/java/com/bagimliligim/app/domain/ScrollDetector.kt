package com.bagimliligim.app.domain

import kotlin.math.abs

data class ScrollSample(
    val platform: AppPlatform,
    val sourceKey: String,
    val className: String,
    val feedContext: Boolean,
    val blockedContext: Boolean,
    val viewportHeight: Int,
    val screenHeight: Int,
    val fromIndex: Int,
    val toIndex: Int,
    val deltaX: Int,
    val deltaY: Int,
    val eventTime: Long,
    val scrollY: Int? = null,
)

/** Estimates page transitions, never reading text, video, account IDs, or message content. */
class ScrollDetector(private val debounceMillis: Long = 450) {
    private data class Position(val index: Int, val time: Long, val scrollY: Int?, val pending: Long = 0)
    private val positions = mutableMapOf<String, Position>()
    private var lastAccepted: Long? = null

    fun reset() { positions.clear(); lastAccepted = null }

    fun accepts(sample: ScrollSample): Boolean {
        if (!sample.feedContext || sample.blockedContext || sample.viewportHeight <= 0 ||
            sample.screenHeight <= 0 || sample.viewportHeight < sample.screenHeight * 0.65f) return false
        if (abs(sample.deltaX.toLong()) > abs(sample.deltaY.toLong()) && sample.deltaX != 0) return false
        val pager = sample.className.contains("ViewPager", ignoreCase = true)
        val recycler = sample.className.contains("RecyclerView", ignoreCase = true)
        val singleVisible = sample.fromIndex >= 0 && sample.toIndex >= 0 &&
            abs(sample.toIndex.toLong() - sample.fromIndex.toLong()) <= 1
        // YouTube also emits ScrollView/custom ViewGroup events from its Shorts container.
        // This fallback is only available after positively identifying the Shorts screen.
        val youtubeContainer = sample.platform == AppPlatform.YOUTUBE && sample.feedContext
        if (!pager && !recycler && !singleVisible && !youtubeContainer) return false
        val key = "${sample.platform}:${sample.sourceKey}"
        val previous = positions[key]
        if (previous != null && sample.eventTime <= previous.time) return false
        if (positions.size > 32) positions.clear()
        val absoluteDelta = if (previous?.scrollY != null && sample.scrollY != null)
            sample.scrollY.toLong() - previous.scrollY else 0L
        val delta = if (sample.deltaY != 0) sample.deltaY.toLong() else absoluteDelta
        val continuous = previous != null && sample.eventTime - previous.time <= 900 &&
            (previous.pending == 0L || delta == 0L || (previous.pending > 0) == (delta > 0))
        val pending = (if (continuous) previous!!.pending else 0L) + delta
        positions[key] = Position(sample.fromIndex, sample.eventTime, sample.scrollY, pending)

        // fromIndex/toIndex describe the visible range, not previous/next pages.
        // Compare consecutive events to reject an initial layout or repeated snap callbacks.
        val indexChange = if (sample.fromIndex >= 0 && previous != null && previous.index >= 0)
            abs(sample.fromIndex.toLong() - previous.index) else 0L
        if (indexChange > 1) {
            positions[key] = Position(sample.fromIndex, sample.eventTime, sample.scrollY)
            return false
        }
        val movedOnePage = indexChange == 1L ||
            ((pager || recycler || youtubeContainer) && abs(pending) >= sample.viewportHeight * 0.65f)
        if (!movedOnePage) return false
        // Consume rejected duplicate callbacks too, so they cannot leak into the next gesture.
        positions[key] = Position(sample.fromIndex, sample.eventTime, sample.scrollY)
        if (lastAccepted?.let { sample.eventTime - it < debounceMillis } == true) return false
        lastAccepted = sample.eventTime
        return true
    }
}
