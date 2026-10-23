package com.bagimliligim.app.service

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bagimliligim.app.appContainer
import com.bagimliligim.app.domain.AppPlatform
import com.bagimliligim.app.presentation.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ServiceLifecycleTest {
    @Test fun realServiceBindingFlushesOnWindowSwitchAndScreenOff() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val container = context.appContainer
        val previousServices = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val previousEnabled = Settings.Secure.getString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        val previousPreferences = container.settings.preferences.first()
        val component = ComponentName(context, SwipeAccessibilityService::class.java).flattenToString()
        val manager = context.getSystemService(AccessibilityManager::class.java)
        fun subscribedEvents(): Int? = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .firstOrNull { ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name).flattenToString() == component }
            ?.eventTypes
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        suspend fun awaitCondition(condition: suspend () -> Boolean) = withTimeout(10_000) {
            while (!condition()) delay(50)
        }
        try {
            container.settings.setWarnings(false)
            container.settings.completeOnboarding(false)
            awaitCondition { container.preferences.value?.accessibilityConsent == false }
            automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
            try {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    (previousServices.orEmpty().split(':').filter { it.isNotBlank() } + component).distinct().joinToString(":"))
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            } finally { automation.dropShellPermissionIdentity() }
            withTimeout(10_000) { container.serviceConnected.first { it } }
            awaitCondition { subscribedEvents() == 0 }
            container.settings.completeOnboarding(true)
            awaitCondition {
                subscribedEvents() == (AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_SELECTED)
            }
            shell("input keyevent KEYCODE_WAKEUP")
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                instrumentation.waitForIdleSync()
                delay(1_000)
                container.tracker.flushAndAwait()
                val day = LocalDate.now()
                val before = container.repository.totals(day).swipes
                repeat(3) { container.tracker.record(AppPlatform.TIKTOK, System.currentTimeMillis()) }
                delay(100)
                assertEquals("Partial batches stay in memory", before, container.repository.totals(day).swipes)
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                awaitCondition { container.repository.totals(day).swipes == before + 3 }
                delay(500)
                repeat(2) { container.tracker.record(AppPlatform.YOUTUBE, System.currentTimeMillis()) }
                delay(100)
                assertEquals(before + 3, container.repository.totals(day).swipes)
                shell("input keyevent KEYCODE_SLEEP")
                awaitCondition { container.repository.totals(day).swipes == before + 5 }
                container.settings.completeOnboarding(false)
                awaitCondition { subscribedEvents() == 0 && container.preferences.value?.accessibilityConsent == false }
                container.tracker.record(AppPlatform.INSTAGRAM, System.currentTimeMillis())
                container.tracker.flushAndAwait()
                assertEquals("Revoked consent stops both event subscriptions and counting", before + 5, container.repository.totals(day).swipes)
            }
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
            try {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, previousServices)
                Settings.Secure.putString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, previousEnabled)
            } finally { automation.dropShellPermissionIdentity() }
            container.settings.setWarnings(previousPreferences.warningsEnabled)
            container.settings.completeOnboarding(previousPreferences.accessibilityConsent)
        }
    }
}
