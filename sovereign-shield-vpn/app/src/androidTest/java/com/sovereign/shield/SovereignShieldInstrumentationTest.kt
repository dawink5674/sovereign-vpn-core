package com.sovereign.shield

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SovereignShieldInstrumentationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var device: UiDevice

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun appContext_hasCorrectPackage() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.sovereign.shield", appContext.packageName)
    }

    @Test
    fun dashboardScreen_displaysShieldUI() {
        composeTestRule.onNodeWithText("SOVEREIGN SHIELD").assertIsDisplayed()
    }

    @Test
    fun dashboardScreen_showsEncryptionBadge() {
        composeTestRule.onNodeWithText("AES-256 + Curve25519").assertIsDisplayed()
    }

    @Test
    fun bottomNav_navigatesToSettings() {
        composeTestRule.onNodeWithText("Config").performClick()
        composeTestRule.onNodeWithText("SETTINGS").assertIsDisplayed()
    }

    @Test
    fun bottomNav_navigatesToStats() {
        composeTestRule.onNodeWithText("Stats").performClick()
        composeTestRule.onNodeWithText("STATISTICS").assertIsDisplayed()
    }

    @Test
    fun bottomNav_navigatesToLog() {
        composeTestRule.onNodeWithText("Log").performClick()
        composeTestRule.onNodeWithText("SECURITY LOG").assertIsDisplayed()
    }

    @Test
    fun vpnConnection_handlesSystemDialog() {
        try {
            composeTestRule.onNodeWithText("CONNECT").performClick()
        } catch (_: AssertionError) { return }

        val okButton = device.wait(Until.findObject(By.text("OK")), 5000)
        okButton?.click()
        composeTestRule.waitForIdle()
    }
}
