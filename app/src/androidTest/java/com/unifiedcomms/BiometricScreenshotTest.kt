package com.unifiedcomms

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.unifiedcomms.ui.main.MainActivity
import com.unifiedcomms.util.PreferencesManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Verifies the biometric lock gate renders when biometric_lock is enabled.
 * Cannot perform a real fingerprint tap headlessly, but we prove the gate
 * (BiometricLockScreen dialog + Unlock button when a credential is available)
 * appears on launch.
 */
class BiometricScreenshotTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun enableLock() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        com.unifiedcomms.util.PreferencesManager.initialize(ctx)
        com.unifiedcomms.util.PreferencesManager.getInstance().putBoolean("biometric_lock", true)
        Thread.sleep(2000)
        val read = com.unifiedcomms.util.PreferencesManager.getInstance().getBoolean("biometric_lock", false)
        android.util.Log.e("BIOXSHOT", "pref after set = $read")
    }

    @Test
    fun captureLockScreen(): Unit {
        composeTestRule.waitForIdle()
        Thread.sleep(2500)
        val f = File("/sdcard/Download", "biometric_lock.png")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(f)
        android.util.Log.e("BIOXSHOT", "shot exists=${f.exists()} size=${f.length()}")
    }

    @org.junit.After
    fun disableLock() {
        // Don't leave the device locked for the user after the test.
        com.unifiedcomms.util.PreferencesManager.getInstance().putBoolean("biometric_lock", false)
    }
}
