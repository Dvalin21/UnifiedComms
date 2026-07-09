package com.unifiedcomms.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 16: proves the XOAUTH2 SASL string Gmail/Outlook IMAP expect is built exactly.
 * Format (pre-base64): "user=<email>\u0001auth=Bearer <token>\u0001\u0001".
 * A malformed string means IMAP OAuth login silently fails -> account unusable.
 * Asserts the pure xoauth2Bare() (no android.util.Base64, so it runs on the JVM).
 */
class Xoauth2FormatTest {

    @Test
    fun buildsExactGmailFormat() {
        assertEquals(
            "user=user@gmail.com\u0001auth=Bearer ya29.abc123\u0001\u0001",
            EmailSyncEngineImpl.xoauth2Bare("user@gmail.com", "ya29.abc123")
        )
    }

    @Test
    fun buildsExactOutlookFormat() {
        assertEquals(
            "user=user@outlook.com\u0001auth=Bearer eyJ0.token.x\u0001\u0001",
            EmailSyncEngineImpl.xoauth2Bare("user@outlook.com", "eyJ0.token.x")
        )
    }
}
