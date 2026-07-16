package com.unifiedcomms

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.unifiedcomms.ui.main.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Deterministic proof of the email-first autodiscover path in the real AddAccountScreen:
 * open Add Account -> type a Gmail address -> pick a manual provider (fires runDiscovery)
 * -> assert "Server settings found automatically" surfaces (live Thunderbird autoconfig).
 */
@OptIn(ExperimentalTestApi::class)
class AddAccountAutodiscoverUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun emailFirst_autodiscover_findsGmailSettings() {
        // open Add Account
        composeTestRule.onNodeWithContentDescription("Add Account").performTouchInput { click() }
        composeTestRule.onNodeWithText("Close").assertExists()

        // type a gmail address into the Email field (first editable text node)
        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("test.user@gmail.com")

        // pick a manual/IMAP provider -> triggers runDiscovery() against live autoconfig
        composeTestRule.onNodeWithText("Generic IMAP/SMTP").performScrollTo().performClick()

        // the screen must report auto-detected settings (live network resolves gmail)
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            try {
                composeTestRule.onNodeWithText("Server settings found automatically.").assertExists()
                true
            } catch (e: AssertionError) { false }
        }
        composeTestRule.onNodeWithText("Server settings found automatically.").assertExists()
    }
}
