package com.unifiedcomms

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.unifiedcomms.ui.main.MainActivity
import com.unifiedcomms.util.DemoDataSeeder
import com.unifiedcomms.util.PreferencesManager
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule

/**
 * Screenshot gallery harness.
 *
 * Seeds the real demo dataset (via the app's own DemoDataSeeder) and walks every
 * major screen, capturing the device framebuffer to /sdcard/uc_*.png for each screen.
 *
 * Why coordinate taps instead of Compose/UiAutomator semantics:
 *  - The app boots into BiometricLockScreen on a no-biometric emulator, so we force
 *    biometric_lock=false (and the theme) via the app's PreferencesManager and recreate the
 *    activity to reach the inbox.
 *  - ActivityScenario.recreate() leaves BOTH ComposeTestRule's cached activity AND
 *    UiAutomator's window binding pointing at the dead instance, so node queries
 *    can no longer locate nodes. The framebuffer (screencap) and raw coordinate taps
 *    (UiDevice.click) act on the live, recreated screen and are unaffected by that.
 *
 * Coordinates are for the 1080x2400 / 420dpi emulator (emulator-5556):
 *   top bar y=211: Settings(880) Search(1005) AddAccount(629)
 *   bottom nav y=2171: Inbox(100) Email(310) Calendar(530) Tasks(750) Messages(960)
 *
 * Run on emulator-5556:
 *   adb -s emulator-5556 shell pm clear com.unifiedcomms.debug
 *   adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
 *   adb -s emulator-5556 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
 *   adb -s emulator-5556 shell am instrument -w -r \
 *     -e class com.unifiedcomms.ScreenshotGalleryTest \
 *     com.unifiedcomms.debug.test/androidx.test.runner.AndroidJUnitRunner
 *   adb -s emulator-5556 pull /sdcard/uc_*.png docs/screenshots/
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotGalleryTest {

    @get:Rule
    val scenarioRule = ActivityScenarioRule(MainActivity::class.java)

    private val ui: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun ctx() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun tap(x: Int, y: Int) {
        ui.click(x, y)
        Thread.sleep(500)
    }

    // Force biometric_lock=false + the requested theme via the app's own PreferencesManager
    // (this updates the _themeMode StateFlow that MainActivity collects, so the recreated
    // activity actually applies the theme — writing the file directly does NOT).
    private fun unlock(mode: String) {
        com.unifiedcomms.util.PreferencesManager.initialize(ctx())
        val pm = com.unifiedcomms.util.PreferencesManager.getInstance()
        pm.putBoolean("biometric_lock", false)
        pm.putThemeMode(mode)
        scenarioRule.scenario.recreate()
        Thread.sleep(2500)
    }

    private fun seedNow() {
        ctx().getSharedPreferences("unifiedcomms_demo_seed", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("user_requested_demo", true).commit()
        runBlocking { DemoDataSeeder.seedIfUserRequested(ctx()) }
        Thread.sleep(800)
    }

    private fun shot(name: String) {
        Thread.sleep(900)
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand("screencap -p /sdcard/uc_$name.png")
        Thread.sleep(200)
    }

    // Top-bar / bottom-nav coordinates (1080x2400), derived from the live light-theme
    // inbox screenshot: top bar y=211 (Add 565, Sync 712, Settings 859, Search 1005);
    // bottom nav y=2171 (Inbox 100, Email 310, Calendar 530, Tasks 750, Messages 960).
    private fun settings() = tap(880, 211)
    private fun search() = tap(1005, 211)
    private fun addAccount() = tap(565, 212)
    private fun tabInbox() = tap(100, 2171)
    private fun tabEmail() = tap(310, 2171)
    private fun tabCalendar() = tap(530, 2171)
    private fun tabTasks() = tap(750, 2171)
    private fun tabMessages() = tap(960, 2171)

    @Test
    fun lightGallery() {
        unlock("light")
        seedNow()

        shot("01_inbox")                 // default tab, seeded card (app guaranteed foreground)

        // Capture Search now, while the app is reliably in the foreground.
        search(); Thread.sleep(500); shot("09_search")
        ui.pressBack(); Thread.sleep(500)   // SearchActivity -> inbox

        tabEmail(); shot("02_email_overview")

        // 03 Email folder (Unified Inbox): from the inbox tab, tap the account card.
        tabInbox()
        tap(540, 420)
        Thread.sleep(900)
        shot("03_email_folder")
        ui.pressBack(); Thread.sleep(500)   // return to main inbox (restores bottom nav)

        tabCalendar(); shot("04_calendar")
        tabTasks(); shot("05_tasks")
        tabMessages(); shot("06_messages")

        settings(); Thread.sleep(600); shot("07_settings")   // settings screen
        // Add Account is opened from Settings (top-bar Add button was removed to de-crowd
        // the title bar). Tap the "Add Account" TextButton at the bottom of the Accounts card.
        tap(221, 786); Thread.sleep(700); shot("08_add_account")
        ui.pressBack(); Thread.sleep(500)                    // add-account -> settings
        ui.pressBack(); Thread.sleep(500)                    // settings -> inbox
    }

    @Test
    fun darkGallery() {
        unlock("dark")
        seedNow()

        shot("10_inbox_dark")
        tabCalendar(); shot("11_calendar_dark")
        tabTasks(); shot("12_tasks_dark")

        settings(); Thread.sleep(600); shot("13_settings_dark")
    }
}
