package com.bagimliligim.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollDetectorTest {
    @Test fun `recorded YouTube events reproduce ten verified page transitions`() {
        val detector = ScrollDetector()
        val rows = requireNotNull(javaClass.getResourceAsStream("/youtube-20.10.41.events"))
            .bufferedReader().use { it.readLines() }.filterNot { it.startsWith('#') || it.isBlank() }
        var accepted = 0
        rows.forEach { line ->
            val f = line.split('|')
            val sample = ScrollSample(AppPlatform.YOUTUBE, "shorts", f[0], true, false,
                f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt(), f[6].toInt(),
                f[7].toLong(), f[8].toIntOrNull())
            val result = detector.accepts(sample)
            org.junit.Assert.assertEquals("Recorded event at ${f[7]}", f[9].toBooleanStrict(), result)
            if (result) accepted++
        }
        org.junit.Assert.assertEquals(10, accepted)
    }
    @Test fun `youtube custom scroll container accumulates small vertical callbacks`() {
        val detector = ScrollDetector()
        val start = sample(-1, platform = AppPlatform.YOUTUBE).copy(className = "android.view.ViewGroup", deltaY = 400)
        assertFalse(detector.accepts(start))
        assertFalse(detector.accepts(start.copy(eventTime = 1100)))
        assertTrue(detector.accepts(start.copy(eventTime = 1200)))
        assertFalse(detector.accepts(start.copy(eventTime = 1250, deltaY = 50)))
    }
    @Test fun `youtube constant index uses absolute scroll position when delta omitted`() {
        val detector = ScrollDetector()
        val start = sample(0, platform = AppPlatform.YOUTUBE).copy(className = "android.widget.ScrollView", scrollY = 0)
        assertFalse(detector.accepts(start))
        assertTrue(detector.accepts(start.copy(eventTime = 2000, scrollY = 1600)))
        assertFalse(detector.accepts(start.copy(eventTime = 2100, scrollY = 1600)))
    }
    @Test fun `separate small gestures and direction reversals do not become a page`() {
        val detector = ScrollDetector()
        val start = sample(-1, platform = AppPlatform.YOUTUBE).copy(deltaY = 700)
        assertFalse(detector.accepts(start))
        assertFalse(detector.accepts(start.copy(eventTime = 3000)))
        assertFalse(detector.accepts(start.copy(eventTime = 3100, deltaY = -700)))
    }
    @Test fun `shorts context requires player and does not accept home shelves`() {
        assertTrue(FeedContext.feed("com.google.android.youtube:id/reel_watch_fragment_root", AppPlatform.YOUTUBE))
        assertFalse(FeedContext.feed("com.google.android.youtube:id/shorts_shelf", AppPlatform.YOUTUBE))
        assertTrue(FeedContext.excluded("com.google.android.youtube:id/engagement_panel"))
    }
    private fun sample(index: Int, time: Long = 1000, platform: AppPlatform = AppPlatform.INSTAGRAM) = ScrollSample(
        platform, "feed", "androidx.viewpager2.widget.ViewPager2", true, false,
        1600, 2000, index, index, 0, 0, time)

    @Test fun `first layout establishes baseline and next full page counts`() {
        val detector = ScrollDetector()
        assertFalse(detector.accepts(sample(0)))
        assertTrue(detector.accepts(sample(1, 1500)))
        assertFalse(detector.accepts(sample(1, 2100)))
        assertTrue(detector.accepts(sample(0, 2600)))
    }
    @Test fun `debounce rejects rapid callbacks and recovers at 450 milliseconds`() {
        val detector = ScrollDetector()
        detector.accepts(sample(0))
        assertTrue(detector.accepts(sample(1, 1500)))
        assertFalse(detector.accepts(sample(2, 1700)))
        assertTrue(detector.accepts(sample(3, 1950)))
    }
    @Test fun `comments messages regular feeds horizontal swipes and short lists are rejected`() {
        val invalid = listOf(
            sample(0).copy(blockedContext = true), sample(0).copy(feedContext = false),
            sample(0).copy(deltaX = 900, deltaY = 12), sample(0).copy(viewportHeight = 500),
        )
        invalid.forEach { initial ->
            val detector = ScrollDetector()
            assertFalse(detector.accepts(initial))
            assertFalse(detector.accepts(initial.copy(fromIndex = 1, toIndex = 1, eventTime = 2000)))
        }
    }
    @Test fun `visible range is not mistaken for a swipe`() {
        val detector = ScrollDetector()
        assertFalse(detector.accepts(sample(3).copy(toIndex = 4)))
        assertFalse(detector.accepts(sample(3, 2000).copy(toIndex = 4)))
    }
    @Test fun `multiple item jump is rejected`() {
        val detector = ScrollDetector()
        detector.accepts(sample(0))
        assertFalse(detector.accepts(sample(8, 2000)))
    }
    @Test fun `indexless recycler needs vertical full page delta`() {
        val detector = ScrollDetector()
        val initial = sample(-1).copy(className = "RecyclerView", deltaY = 100)
        assertFalse(detector.accepts(initial))
        assertTrue(detector.accepts(initial.copy(deltaY = 1300, eventTime = 2000)))
        assertFalse(detector.accepts(initial.copy(deltaY = 1300, eventTime = 2100)))
    }
    @Test fun `switching apps resets position and debounce`() {
        val detector = ScrollDetector()
        detector.accepts(sample(0))
        detector.accepts(sample(1, 1500))
        detector.reset()
        assertFalse(detector.accepts(sample(9, 1600)))
        assertTrue(detector.accepts(sample(10, 2100)))
    }
    @Test fun `stale event cannot roll back baseline`() {
        val detector = ScrollDetector()
        detector.accepts(sample(0, 2000))
        assertFalse(detector.accepts(sample(1, 1000)))
        assertTrue(detector.accepts(sample(1, 2500)))
    }
    @Test fun `only exact target package names map to a platform`() {
        assertTrue(AppPlatform.fromPackage("com.ss.android.ugc.trill") == AppPlatform.TIKTOK)
        assertTrue(AppPlatform.fromPackage("com.zhiliaoapp.musically") == AppPlatform.TIKTOK)
        assertTrue(AppPlatform.fromPackage("com.instagram.android.fake") == null)
        assertTrue(AppPlatform.fromPackage(null) == null)
    }
}
