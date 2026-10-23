package com.bagimliligim.app.domain

import java.util.Locale

/** Resource identifiers only. Never examines accessibility text or descriptions. */
object FeedContext {
    fun excluded(id: String): Boolean = listOf("comment", "direct", "inbox", "message", "chat",
        "search", "bottom_sheet", "engagement_panel", "replies").any(id.lowercase(Locale.ROOT)::contains)

    fun feed(id: String, platform: AppPlatform): Boolean {
        val name = id.lowercase(Locale.ROOT).substringAfter(":id/")
        return when (platform) {
            AppPlatform.INSTAGRAM -> listOf("clips_viewer", "clips_video", "reels_viewer", "clips_swipe", "clips_pager")
            AppPlatform.YOUTUBE -> listOf("reel_watch", "reel_player", "shorts_player", "shorts_container",
                "reel_recycler", "reel_pager", "shorts_pager", "reel_watch_fragment", "shorts_video")
            AppPlatform.TIKTOK -> listOf("vertical_view_pager", "feed_view_pager", "recycler_view_pager", "video_viewpager")
        }.any(name::contains)
    }
}
