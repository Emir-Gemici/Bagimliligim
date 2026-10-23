package com.bagimliligim.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bagimliligim.app.appContainer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun consentAndAllScreensRenderWithOptionalOnlineServicesOffByDefault() {
        val context = compose.activity
        runBlocking { context.appContainer.settings.completeOnboarding(false) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Bugünkü bütçen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Bugünkü bütçen").assertIsDisplayed()
        capture("v2-dashboard.png")
        compose.onNodeWithText("Takibi aç").performScrollTo().performClick()
        compose.onNodeWithText("Kabul et ve erişilebilirlik ayarlarını aç").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Şimdilik takip olmadan devam et").performScrollTo().performClick()
        compose.onNodeWithText("İstatistikler").performClick()
        compose.onNodeWithText("Bugünün ritmi").assertIsDisplayed()
        compose.onNodeWithText("Haftalık").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Son 7 gün").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Aylık").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Son 30 gün").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Yıllık").performClick()
        compose.onNodeWithText("Sigara").performClick()
        compose.onNodeWithText("+  Sigara kaydet").assertIsDisplayed()
        capture("v2-smoking.png")
        compose.onNodeWithText("Çevrem").performClick()
        compose.onNodeWithText("Birlikte, biraz daha iyi.").assertIsDisplayed()
        compose.onNodeWithText("Ayarlar").performClick()
        compose.onNodeWithText("Günlük Kaydırma Bütçesi").assertIsDisplayed()
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(Manifest.permission.INTERNET in permissions)
        assertFalse(runBlocking { context.appContainer.settings.preferences.first() }.accessibilityConsent)
        assertFalse(runBlocking { context.appContainer.settings.preferences.first() }.onlinePrices)
    }

    @Test fun cigaretteEntryCanBeSavedAndUndoneFromTheScreen() {
        val context = compose.activity
        context.getSharedPreferences("smoking_selection", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val dao = context.appContainer.smoking.dao
        val today = java.time.LocalDate.now().toString()
        val before = runBlocking { dao.logs(today, today).first() }.map { it.id }.toSet()
        runBlocking { context.appContainer.settings.completeOnboarding(false) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Bugünkü bütçen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sigara").performClick()
        compose.onNodeWithText("+  Sigara kaydet").performClick()
        compose.onNodeWithText("Marka veya seri ara").performTextInput("Winston Dark Blue")
        val brand = com.bagimliligim.app.data.BrandCatalog.brands.first { it.name == "Winston Dark Blue" }
        compose.onNodeWithTag("brand-option-${brand.id}").performClick()
        compose.onNodeWithTag("pack-price").performTextInput("120")
        compose.onNodeWithText("Kaydı ekle").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Kendin için bir kayıt.").fetchSemanticsNodes().isEmpty() }
        val saved = runBlocking { dao.logs(today, today).first() }.single { it.id !in before }
        org.junit.Assert.assertEquals(1, saved.count)
        org.junit.Assert.assertEquals(6_000_000L, saved.costMicros)
        compose.onNodeWithTag("smoking-list").performScrollToNode(hasTestTag("smoking-log-${saved.id}"))
        compose.onNodeWithTag("smoking-log-${saved.id}").assertIsDisplayed()
        capture("v2-smoking-record.png")
        compose.onNodeWithTag("undo-${saved.id}").performClick()
        compose.waitUntil(5_000) { runBlocking { dao.log(saved.id) } == null }
    }

    private fun capture(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        require(name.matches(Regex("[a-z0-9-]+\\.png")))
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/$name")
        ).use { it.readBytes() }
    }
}
