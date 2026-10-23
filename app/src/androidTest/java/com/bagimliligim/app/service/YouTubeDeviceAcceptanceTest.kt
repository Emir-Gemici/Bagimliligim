package com.bagimliligim.app.service

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bagimliligim.app.appContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Explicit opt-in: drives the installed YouTube app, never part of offline CI acceptance. */
@RunWith(AndroidJUnit4::class)
class YouTubeDeviceAcceptanceTest {
    @Test fun twentyRealShortsTransitionsAreCounted() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("externalFeedProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val context = instrumentation.targetContext
        val container = context.appContainer
        val resolver = context.contentResolver
        val previousServices = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val previousEnabled = Settings.Secure.getString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        val previousPreferences = container.settings.preferences.first()
        val component = ComponentName(context, SwipeAccessibilityService::class.java).flattenToString()
        val events = JSONArray()
        val samples = JSONArray()
        val eventLock = Any()
        var measured = -1
        var expected = 0
        var commentCounts = -1
        var homeCounts = -1
        SwipeAccessibilityService.sampleObserver = { sample, accepted ->
            synchronized(eventLock) {
                samples.put(JSONObject().put("sample", sample.toString()).put("accepted", accepted))
            }
        }
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        suspend fun swipe(x: Int, from: Int, to: Int) {
            val downTime = SystemClock.uptimeMillis()
            fun send(action: Int, y: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x.toFloat(), y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
            send(MotionEvent.ACTION_DOWN, from.toFloat())
            for (step in 1..20) {
                delay(25)
                send(MotionEvent.ACTION_MOVE, from + (to - from) * step / 20f)
            }
            delay(25)
            send(MotionEvent.ACTION_UP, to.toFloat())
        }
        fun secure(block: () -> Unit) {
            automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
            try { block() } finally { automation.dropShellPermissionIdentity() }
        }
        fun findVisible(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
            automation.rootInActiveWindow?.let(queue::add)
            var examined = 0
            while (queue.isNotEmpty() && examined++ < 350) {
                val node = queue.removeFirst()
                if (!node.isVisibleToUser) continue
                if (predicate(node)) return node
                for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
            }
            return null
        }
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_VIEW_SELECTED
        }
        automation.setOnAccessibilityEventListener { event ->
            if (event.packageName?.toString() == "com.google.android.youtube") {
                val source = event.source
                val bounds = Rect().also { source?.getBoundsInScreen(it) }
                val entry = JSONObject().put("type", event.eventType).put("class", event.className?.toString())
                    .put("sourceClass", source?.className?.toString()).put("id", source?.viewIdResourceName)
                    .put("height", bounds.height()).put("from", event.fromIndex).put("to", event.toIndex)
                    .put("selected", event.currentItemIndex).put("y", event.scrollY).put("at", event.eventTime)
                if (android.os.Build.VERSION.SDK_INT >= 28) entry.put("dy", event.scrollDeltaY).put("dx", event.scrollDeltaX)
                synchronized(eventLock) { if (events.length() < 500) events.put(entry) }
            }
        }
        try {
            container.settings.setWarnings(false)
            container.settings.completeOnboarding(true)
            secure {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    (previousServices.orEmpty().split(':').filter(String::isNotBlank) + component).distinct().joinToString(":"))
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            }
            withTimeout(10_000) { container.serviceConnected.first { it } }
            delay(1_000)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/shorts/"))
                .setPackage("com.google.android.youtube").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val bounds = withTimeout(20_000) {
                var rect: Rect? = null
                while (rect == null) {
                    val feed = automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_recycler")
                        ?.firstOrNull { it.isVisibleToUser }
                    if (feed != null) rect = Rect().also(feed::getBoundsInScreen) else delay(250)
                }
                rect
            }
            delay(3_000)
            container.tracker.flushAndAwait()
            val before = container.repository.totals(LocalDate.now()).swipes
            val x = bounds.centerX()
            val top = bounds.top + bounds.height() / 20
            val bottom = bounds.bottom - bounds.height() / 20
            repeat(20) { index ->
                val start = synchronized(eventLock) { events.length() }
                if (index < 10) swipe(x, bottom, top)
                else swipe(x, top, bottom)
                delay(1_700)
                val distance = synchronized(eventLock) {
                    (start until events.length()).map { events.getJSONObject(it) }
                        .filter { it.optString("id").endsWith("/reel_recycler") }.sumOf { it.optInt("dy").toLong() }
                }
                expected += kotlin.math.abs(kotlin.math.round(distance.toDouble() / bounds.height()).toInt())
            }
            container.tracker.flushAndAwait()
            measured = container.repository.totals(LocalDate.now()).swipes - before
            assertTrue("$expected real page transitions produced $measured counts; inspect shorts-probe.json", measured in (expected - 1)..expected)

            val comments = findVisible { it.isClickable && it.contentDescription?.toString()?.contains("comments", true) == true }
            assertTrue("The test Short must expose a comments button", comments != null)
            comments!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            withTimeout(10_000) {
                while (findVisible { it.viewIdResourceName.orEmpty().contains("engagement_panel") } == null) delay(200)
            }
            delay(1_000)
            container.tracker.flushAndAwait()
            val beforeComments = container.repository.totals(LocalDate.now()).swipes
            repeat(10) { swipe(x, bottom, bounds.centerY()); delay(600) }
            container.tracker.flushAndAwait()
            commentCounts = container.repository.totals(LocalDate.now()).swipes - beforeComments
            assertTrue("Comment scrolling incorrectly added $commentCounts videos", commentCounts == 0)
            shell("input keyevent KEYCODE_BACK")
            delay(700)
            val home = findVisible { it.isClickable && it.contentDescription?.toString() == "Home" }
            assertTrue("YouTube Home tab must be visible", home != null)
            home!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1_000)
            container.tracker.flushAndAwait()
            val beforeHome = container.repository.totals(LocalDate.now()).swipes
            repeat(5) { swipe(x, bottom, top); delay(600) }
            container.tracker.flushAndAwait()
            homeCounts = container.repository.totals(LocalDate.now()).swipes - beforeHome
            assertTrue("Normal Home feed incorrectly added $homeCounts Shorts", homeCounts == 0)
            assertTrue("YouTube did not complete enough page transitions ($expected)", expected >= 10)
        } finally {
            automation.setOnAccessibilityEventListener(null)
            SwipeAccessibilityService.sampleObserver = null
            val report = synchronized(eventLock) { JSONObject().put("counted", measured).put("expected", expected)
                .put("commentsCounted", commentCounts).put("homeCounted", homeCounts)
                .put("consent", container.preferences.value?.accessibilityConsent).put("samples", samples).put("events", events) }
            val file = File(context.getExternalFilesDir(null), "shorts-probe.json").apply { writeText(report.toString(2)) }
            shell("cp ${file.absolutePath} /sdcard/Download/shorts-probe.json")
            secure {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, previousServices)
                Settings.Secure.putString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, previousEnabled)
            }
            container.settings.setWarnings(previousPreferences.warningsEnabled)
            container.settings.completeOnboarding(previousPreferences.accessibilityConsent)
        }
    }
}
