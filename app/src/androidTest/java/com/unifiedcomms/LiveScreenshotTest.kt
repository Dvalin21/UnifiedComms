package com.unifiedcomms

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.unifiedcomms.ui.main.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Captures the LIVE app state (user's own synced accounts) for visual review.
 * Does NOT seed or delete any account — just launches and navigates.
 */
class LiveScreenshotTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        val f = File("/sdcard/Download", name)
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(f)
        android.util.Log.e("LIVEXSHOT", "shot $name exists=${f.exists()} size=${f.length()}")
    }

    @Test
    fun captureLive(): Unit {
        composeTestRule.waitForIdle()
        Thread.sleep(3000)

        // Go to Calendar tab
        composeTestRule.onNodeWithText("Calendar", ignoreCase = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(3000)
        shot("live_calendar_month.png")

        // Day view
        composeTestRule.onNodeWithText("Day", ignoreCase = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(2500)
        shot("live_calendar_day.png")

        // Week view
        composeTestRule.onNodeWithText("Week", ignoreCase = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(2500)
        shot("live_calendar_week.png")

        // Tasks (already confirmed good, capture for completeness)
        composeTestRule.onNodeWithText("Tasks", ignoreCase = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(2000)
        shot("live_tasks.png")
    }
}
