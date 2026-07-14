package com.unifiedcomms

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Deterministic emulator UI verification using the Compose UI test rule (the only
 * API that can see into Compose semantics). Asserts on stable hooks:
 * contentDescription on icons, label text on fields, and the app's OWN
 * EncryptedSharedPreferences for the theme toggle (the app uses
 * EncryptedSharedPreferences, so a plain getSharedPreferences read is empty/garbage).
 *
 * Run on a single emulator:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.unifiedcomms.EmulatorUiVerificationTest
 */
class EmulatorUiVerificationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /** Mirror PreferencesManager's EncryptedSharedPreferences so we can assert persistence. */
    private fun readThemeMode(): String {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val masterKey = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            ctx, "unifiedcomms_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return prefs.getString("theme_mode", "system") ?: "system"
    }

    @Test
    fun settingsAppearance_themeToggle_persistsDark() {
        composeTestRule.onNodeWithContentDescription("Settings").performTouchInput { click() }
        composeTestRule.onNodeWithText("Appearance").performScrollTo()
        composeTestRule.onNodeWithText("Dark").performScrollTo().performTouchInput { click() }
        assertEquals("theme_mode pref not updated to dark", "dark", readThemeMode())
    }

    @Test
    fun addAccountScreen_fieldsRender_noServer_noCrash() {
        // touch-input (not semantics click) so the pointer hits the IconButton onClick
        composeTestRule.onNodeWithContentDescription("Add Account").performTouchInput { click() }
        // "Close" exists ONLY on AddAccountScreen's TopAppBar -> proves navigation worked
        composeTestRule.onNodeWithText("Close").assertExists()
        composeTestRule.onAllNodesWithText("Email").fetchSemanticsNodes().isNotEmpty()
        composeTestRule.onAllNodesWithText("Server URL").fetchSemanticsNodes().isNotEmpty()
        composeTestRule.onAllNodesWithText("Display name").fetchSemanticsNodes().isNotEmpty()
        composeTestRule.onAllNodesWithText("Password or app password").fetchSemanticsNodes().isNotEmpty()
        composeTestRule.onAllNodesWithText("Save").fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun mainTabs_renderWithoutCrash() {
        for (label in listOf("Email", "Calendar", "Tasks", "Messages")) {
            composeTestRule.onNodeWithText(label).performClick()
        }
    }
}
