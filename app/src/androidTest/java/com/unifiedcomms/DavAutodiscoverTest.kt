package com.unifiedcomms

import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.util.Autodiscover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the RFC 6764 SRV-based DAV autodiscovery resolves real providers that
 * publish SRV records (Fastmail), not just the hardcoded overrides (Gmail).
 */
class DavAutodiscoverTest {

    @Test
    fun fastmailSrvDavResolves(): Unit = runBlocking {
        val d = withContext(Dispatchers.IO) { Autodiscover.discover("someone@fastmail.com") }
        assertNotNull("Fastmail autodiscover returned null", d)
        // Fastmail publishes SRV _caldavs/_carddavs -> fastmail.com/dav paths
        assertTrue("Fastmail CalDAV URL missing: ${d?.caldavUrl}", d?.caldavUrl?.contains("fastmail") == true)
        assertTrue("Fastmail CardDAV URL missing: ${d?.carddavUrl}", d?.carddavUrl?.contains("fastmail") == true)
    }

    @Test
    fun gmailDavOverrideResolves(): Unit = runBlocking {
        val d = withContext(Dispatchers.IO) { Autodiscover.discover("someone@gmail.com") }
        assertNotNull(d)
        assertTrue("Gmail CalDAV should be googleapis/apidata: ${d?.caldavUrl}", d?.caldavUrl?.contains("google") == true)
        assertTrue("Gmail CardDAV should be googleapis: ${d?.carddavUrl}", d?.carddavUrl?.contains("google") == true)
    }
}
