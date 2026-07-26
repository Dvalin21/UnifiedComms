package com.unifiedcomms

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

/** One-shot: disable the biometric lock pref so the device isn't left locked. */
class ResetBiometricTest {
    @Test
    fun disableLock() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        com.unifiedcomms.util.PreferencesManager.initialize(ctx)
        com.unifiedcomms.util.PreferencesManager.getInstance().putBoolean("biometric_lock", false)
        Thread.sleep(500)
        android.util.Log.e("BIORESET", "biometric_lock set false")
    }
}
