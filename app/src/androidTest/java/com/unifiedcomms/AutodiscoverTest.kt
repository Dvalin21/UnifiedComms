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
 * Proves the autodiscover backend actually resolves real provider configs over the
 * network (Thunderbird autoconfig / domain .well-known). If this fails, the
 * AddAccountScreen "auto-detect settings" path is lying to the user.
 */
class AutodiscoverTest {

    @Test
    fun gmailAutodiscoverResolves(): Unit = runBlocking {
        val d = withContext(Dispatchers.IO) { Autodiscover.discover("someone@gmail.com") }
        assertNotNull("Gmail autodiscover returned null (no config found)", d)
        assertTrue("Expected imap.gmail.com, got ${d?.imapHost}", d?.imapHost?.contains("gmail.com") == true)
        assertTrue("Expected smtp.gmail.com, got ${d?.smtpHost}", d?.smtpHost?.contains("gmail.com") == true)
        // DAV autodiscovery (RFC 6764 / provider override) must resolve real CalDAV/CardDAV URLs.
        assertTrue("Gmail CalDAV URL missing: ${d?.caldavUrl}", d?.caldavUrl?.contains("google") == true)
        assertTrue("Gmail CardDAV URL missing: ${d?.carddavUrl}", d?.carddavUrl?.contains("google") == true)
    }

    @Test
    fun outlookAutodiscoverResolves(): Unit = runBlocking {
        val d = withContext(Dispatchers.IO) { Autodiscover.discover("someone@outlook.com") }
        assertNotNull("Outlook autodiscover returned null (no config found)", d)
        assertTrue("Expected an outlook/office365 IMAP host, got ${d?.imapHost}",
            d?.imapHost?.contains("outlook") == true || d?.imapHost?.contains("office365") == true)
    }

    @Test
    fun bogusDomainReturnsNull(): Unit = runBlocking {
        val d = withContext(Dispatchers.IO) { Autodiscover.discover("someone@this-domain-does-not-exist-xyz123.com") }
        assertTrue("Bogus domain should return null so UI can expand Advanced", d == null)
    }
}
