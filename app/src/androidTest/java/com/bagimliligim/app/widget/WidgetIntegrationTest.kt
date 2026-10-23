package com.bagimliligim.app.widget

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bagimliligim.app.R
import com.bagimliligim.app.appContainer
import com.bagimliligim.app.domain.AppPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WidgetIntegrationTest {
    @Test fun systemHostReceivesAutomaticBatchesAndManualRefreshForBothSizes() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val container = context.appContainer
        val manager = AppWidgetManager.getInstance(context)
        val original = container.settings.preferences.first()
        container.settings.setQuota(200)
        container.settings.setWarnings(false)
        container.settings.completeOnboarding(true)
        withTimeout(5_000) { container.preferences.filterNotNull().first { it.accessibilityConsent && !it.warningsEnabled } }
        val host = AppWidgetHost(context, 80514)
        try {
            listOf(SwipeWidgetProvider::class.java to 280, CompactSwipeWidgetProvider::class.java to 160).forEach { (provider, width) ->
                val id = host.allocateAppWidgetId()
                val automation = instrumentation.uiAutomation
                automation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
                try {
                    assertTrue("System must bind the test widget", manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, provider)))
                } finally { automation.dropShellPermissionIdentity() }
                manager.updateAppWidgetOptions(id, Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
                })
                lateinit var view: AppWidgetHostView
                instrumentation.runOnMainSync {
                    view = host.createView(context, id, manager.getAppWidgetInfo(id))
                    host.startListening()
                }
                container.tracker.flushAndAwait()
                WidgetRenderer.updateAll(context, container)
                val before = container.repository.totals(LocalDate.now()).swipes
                suspend fun awaitCount(expected: Int) {
                    withTimeout(10_000) {
                        while (true) {
                            var text: String? = null
                            instrumentation.runOnMainSync { text = view.findViewById<TextView>(R.id.widget_count)?.text?.toString() }
                            if (text == "$expected / 200 Reels") break
                            delay(50)
                        }
                    }
                }
                awaitCount(before)
                repeat(10) { container.tracker.record(AppPlatform.INSTAGRAM, System.currentTimeMillis()) }
                awaitCount(before + 10)
                repeat(3) { container.tracker.record(AppPlatform.YOUTUBE, System.currentTimeMillis()) }
                instrumentation.runOnMainSync {
                    assertTrue("RemoteViews refresh must have an active PendingIntent", view.findViewById<TextView>(R.id.widget_refresh).performClick())
                }
                awaitCount(before + 13)
                host.deleteAppWidgetId(id)
            }
        } finally {
            instrumentation.runOnMainSync { host.stopListening() }
            host.deleteHost()
            container.settings.setQuota(original.dailyQuota)
            container.settings.setWarnings(original.warningsEnabled)
            container.settings.completeOnboarding(original.accessibilityConsent)
        }
    }
}
