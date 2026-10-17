package com.unifiedcomms.ui.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Regression for the silent-draft-discard defect: the event editor's back arrow
 * called the same callback as "save", so a half-built event was destroyed with no
 * confirmation. Driven through the real activity so it exercises the actual
 * navigation wiring, not just the composable in isolation.
 */
@OptIn(ExperimentalTestApi::class)
class DraftDiscardTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun openCreateEvent() {
        rule.onNodeWithText("Calendar").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Create event").performClick()
        rule.waitForIdle()
    }

    @Test
    fun backArrowOnDirtyEventAsksBeforeDiscarding() {
        openCreateEvent()

        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Half built event")
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Cancel").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Discard event?").assertExists()
    }

    @Test
    fun emptyEventLeavesWithoutAsking() {
        openCreateEvent()

        rule.onNodeWithContentDescription("Cancel").performClick()
        rule.waitForIdle()

        // Back on the calendar, and no discard prompt was shown.
        rule.onNodeWithText("Discard event?").assertDoesNotExist()
        rule.onNodeWithContentDescription("Create event").assertExists()
    }
}
