package com.unifiedcomms.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesManagerTest {

    @Test
    fun `automatic sync honors global interval and network switches`() {
        assertFalse(automaticSyncAllowed(autoSync = false, wifiOnly = false, hasUnmeteredNetwork = true))
        assertTrue(automaticSyncAllowed(autoSync = true, wifiOnly = false, hasUnmeteredNetwork = false))
        assertTrue(automaticSyncAllowed(autoSync = true, wifiOnly = true, hasUnmeteredNetwork = true))
        assertFalse(automaticSyncAllowed(autoSync = true, wifiOnly = true, hasUnmeteredNetwork = false))
        assertFalse(automaticSyncAllowed(autoSync = true, wifiOnly = false, hasUnmeteredNetwork = true, intervalMinutes = -1))
    }
}
