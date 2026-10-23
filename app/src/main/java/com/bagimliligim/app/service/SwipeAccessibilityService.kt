package com.bagimliligim.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.bagimliligim.app.appContainer
import com.bagimliligim.app.BuildConfig
import com.bagimliligim.app.domain.AppPlatform
import com.bagimliligim.app.domain.FeedContext
import com.bagimliligim.app.domain.ScrollDetector
import com.bagimliligim.app.domain.ScrollSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SwipeAccessibilityService : AccessibilityService() {
    internal companion object {
        // Test instrumentation can observe metadata; release R8 removes this debug-only path.
        var sampleObserver: ((ScrollSample, Boolean) -> Unit)? = null
    }
    private val detector = ScrollDetector()
    private var currentPackage: String? = null
    private var receiverRegistered = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var consentJob: Job? = null
    private var trackingAllowed = false
    private var windowFeed = false
    private var windowFeedCheckedAt = 0L
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            detector.reset()
            appContainer.tracker.flush()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Disable event delivery until the stored affirmative consent has been loaded.
        applyConsent(false)
        consentJob?.cancel()
        consentJob = serviceScope.launch {
            appContainer.preferences.map { it?.accessibilityConsent == true }.distinctUntilChanged().collect(::applyConsent)
        }
        appContainer.serviceConnected.value = true
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!trackingAllowed || appContainer.preferences.value?.accessibilityConsent != true) return
        val packageName = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (currentPackage != packageName) {
                appContainer.tracker.flush()
                detector.reset()
                currentPackage = packageName
            }
            windowFeed = false
            windowFeedCheckedAt = 0
            return
        }
        val selectedPage = event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED && !selectedPage) return
        val platform = AppPlatform.fromPackage(packageName) ?: return
        if (selectedPage && platform != AppPlatform.YOUTUBE) return
        if (!power.isInteractive) return
        if (currentPackage != packageName) {
            appContainer.tracker.flush()
            detector.reset()
            currentPackage = packageName
            windowFeed = false
            windowFeedCheckedAt = 0
        }
        val source = event.source ?: return
        try {
            if (source.packageName?.toString() != packageName || !source.isVisibleToUser) return
            val context = sourceContext(source, platform)
            val className = source.className?.toString() ?: event.className?.toString().orEmpty()
            if (selectedPage && !className.contains("ViewPager", true) && !className.contains("RecyclerView", true)) return
            val blocked = context.ids.any(FeedContext::excluded)
            if (blocked) { detector.reset(); return }
            if (platform == AppPlatform.YOUTUBE && event.eventTime - windowFeedCheckedAt >= 750) {
                windowFeed = visibleShortsContainer(packageName)
                windowFeedCheckedAt = event.eventTime
            }
            val feed = context.ids.any { FeedContext.feed(it, platform) } ||
                (platform == AppPlatform.YOUTUBE && windowFeed) ||
                (platform == AppPlatform.TIKTOK && className.contains("ViewPager", true))
            val sourceKey = if (platform == AppPlatform.YOUTUBE) "${event.windowId}:shorts"
                else "${event.windowId}:${context.pagerId ?: source.viewIdResourceName}:$className"
            val sample = ScrollSample(platform, sourceKey,
                className, feed, blocked, context.height, resources.displayMetrics.heightPixels,
                if (selectedPage) event.currentItemIndex else event.fromIndex,
                if (selectedPage) event.currentItemIndex else event.toIndex,
                if (Build.VERSION.SDK_INT >= 28) event.scrollDeltaX else 0,
                if (Build.VERSION.SDK_INT >= 28) event.scrollDeltaY else 0,
                event.eventTime, event.scrollY.takeIf { it >= 0 })
            val accepted = detector.accepts(sample)
            if (BuildConfig.DEBUG) sampleObserver?.invoke(sample, accepted)
            if (accepted) appContainer.tracker.record(platform, System.currentTimeMillis())
        } catch (_: IllegalStateException) {
            detector.reset()
        } catch (_: SecurityException) {
            detector.reset()
        } finally { release(source) }
    }

    private fun applyConsent(allowed: Boolean) {
        trackingAllowed = allowed
        serviceInfo = serviceInfo.apply {
            // Framework timeouts coalesce Shorts scroll events into its invisible underlay.
            // Deliver metadata immediately; ScrollDetector owns the 450 ms count debounce.
            notificationTimeout = 0
            eventTypes = if (allowed) AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_SELECTED else 0
        }
        if (!allowed) {
            detector.reset()
            currentPackage = null
            appContainer.tracker.flush()
        }
    }

    private data class SourceContext(val ids: List<String>, val height: Int, val pagerId: String?)

    private fun sourceContext(source: AccessibilityNodeInfo, platform: AppPlatform): SourceContext {
        val ids = mutableListOf<String>()
        source.viewIdResourceName?.let(ids::add)
        var height = Rect().also(source::getBoundsInScreen).height()
        var pagerId: String? = null
        var node = source.parent
        var depth = 0
        while (node != null && depth++ < 16) {
            val current = node
            current.viewIdResourceName?.let(ids::add)
            val id = current.viewIdResourceName.orEmpty()
            val type = current.className?.toString().orEmpty()
            if (pagerId == null && (type.contains("ViewPager", true) || FeedContext.feed(id, platform))) {
                height = maxOf(height, Rect().also(current::getBoundsInScreen).height())
                pagerId = id
            }
            node = current.parent
            release(current)
        }
        node?.let(::release)
        return SourceContext(ids, height, pagerId)
    }

    private fun visibleShortsContainer(expectedPackage: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var examined = 0
        var feed = false
        var blocked = false
        try {
            while (queue.isNotEmpty() && examined++ < 120) {
                val node = queue.removeFirst()
                try {
                    if (node.packageName?.toString() != expectedPackage || !node.isVisibleToUser) continue
                    val id = node.viewIdResourceName.orEmpty()
                    // A Shorts comment button is not an open comments panel.
                    blocked = blocked || listOf("comments_recycler", "comment_recycler", "engagement_panel", "live_chat_recycler", "bottom_sheet")
                        .any(id::contains)
                    feed = feed || (FeedContext.feed(id, AppPlatform.YOUTUBE) &&
                        Rect().also(node::getBoundsInScreen).height() >= resources.displayMetrics.heightPixels * .6f)
                    for (index in 0 until minOf(node.childCount, 40)) {
                        if (queue.size >= 120) break
                        node.getChild(index)?.let(queue::addLast)
                    }
                } finally { release(node) }
            }
        } finally { while (queue.isNotEmpty()) release(queue.removeFirst()) }
        return feed && !blocked
    }

    @Suppress("DEPRECATION")
    private fun release(node: AccessibilityNodeInfo) { if (Build.VERSION.SDK_INT < 33) node.recycle() }

    override fun onInterrupt() { detector.reset(); appContainer.tracker.flush() }
    override fun onUnbind(intent: Intent?): Boolean {
        consentJob?.cancel()
        trackingAllowed = false
        appContainer.serviceConnected.value = false
        appContainer.tracker.flush()
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        serviceScope.cancel()
        trackingAllowed = false
        appContainer.serviceConnected.value = false
        appContainer.tracker.flush()
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        super.onDestroy()
    }
}
