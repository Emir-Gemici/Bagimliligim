package com.bagimliligim.app.presentation

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bagimliligim.app.presentation.components.GoogleSignInCard
import com.bagimliligim.app.presentation.theme.SwipeMeterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleConsentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitConsentIsRequiredAndBusySignInCannotBeRepeated() {
        val busy = mutableStateOf(false)
        var attempts = 0
        compose.setContent {
            SwipeMeterTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                        GoogleSignInCard(configured = true, busy = busy.value) { attempts++; busy.value = true }
                    }
                }
            }
        }
        compose.onNodeWithTag("google-sign-in").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("google-consent").performScrollTo().performClick()
        compose.onNodeWithTag("google-sign-in").performScrollTo().assertIsEnabled()
        compose.waitForIdle()
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/v2-google-sign-in.png")).use { it.readBytes() }
        compose.onNodeWithTag("google-sign-in").performClick().assertIsNotEnabled()
        compose.onNodeWithTag("google-consent").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, attempts); busy.value = false }
        compose.onNodeWithTag("google-sign-in").assertIsEnabled()
    }
}
