package com.unifiedcomms.data.db

import com.unifiedcomms.data.db.converters.ServerConfigConverter
import com.unifiedcomms.data.db.converters.SyncConfigConverter
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: the Room converters used `Json { ignoreUnknownKeys = true }`, whose
 * default is encodeDefaults=false, so every field equal to its declared default
 * was silently dropped from the stored JSON. A mailcow ServerConfig therefore
 * persisted as {"imapHost":..,"smtpHost":..} with imapPort/smtpPort/imapUseSsl/
 * smtpUseStartTls all missing. Decoding masked it by falling back to the same
 * defaults, so the loss only surfaced when something read the raw JSON.
 *
 * A persisted config must round-trip every field. These tests assert on the
 * stored string, not just the decoded object, because the defect was invisible
 * to a decode-only assertion.
 */
class ServerConfigConverterTest {

    private val converter = ServerConfigConverter()

    @Test
    fun mailcowDefaultsRoundTripEveryField() {
        val stored = converter.fromConfig(ServerConfig.MailcowDefaults("example.com", "a@example.com"))

        assertTrue("imapPort must be persisted, was: $stored", stored!!.contains("\"imapPort\""))
        assertTrue("smtpPort must be persisted, was: $stored", stored.contains("\"smtpPort\""))
        assertTrue("imapUseSsl must be persisted, was: $stored", stored.contains("\"imapUseSsl\""))
        assertTrue("smtpUseStartTls must be persisted, was: $stored", stored.contains("\"smtpUseStartTls\""))

        val back = converter.toConfig(stored)
        assertEquals(993, back.imapPort)
        assertEquals(587, back.smtpPort)
        assertEquals(true, back.imapUseSsl)
        assertEquals(true, back.smtpUseStartTls)
    }

    @Test
    fun nonDefaultPortSurvives() {
        val stored = converter.fromConfig(
            ServerConfig.MailcowDefaults("example.com").copy(smtpPort = 2525)
        )

        val back = converter.toConfig(stored)
        assertEquals("a non-default port must not be overwritten by the default", 2525, back.smtpPort)
    }

    @Test
    fun syncConfigDefaultsRoundTripEveryField() {
        val stored = SyncConfigConverter().fromConfig(SyncConfig.Defaults())

        assertTrue("syncCalendar must be persisted, was: $stored", stored!!.contains("\"syncCalendar\""))
        assertTrue("syncTasks must be persisted, was: $stored", stored.contains("\"syncTasks\""))
        assertTrue("syncContacts must be persisted, was: $stored", stored.contains("\"syncContacts\""))

        val back = SyncConfigConverter().toConfig(stored)
        assertEquals(true, back.syncCalendar)
        assertEquals(true, back.syncTasks)
        assertEquals(true, back.syncContacts)
    }

    @Test
    fun aDisabledFlagIsNotLost() {
        // The dangerous direction: a user turns syncCalendar OFF, which is a
        // non-default value, so it must be written — and must stay false.
        val stored = SyncConfigConverter().fromConfig(
            SyncConfig.Defaults().copy(syncCalendar = false)
        )

        val back = SyncConfigConverter().toConfig(stored)
        assertEquals(false, back.syncCalendar)
    }
}
