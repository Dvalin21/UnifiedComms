package com.unifiedcomms.sync

import javax.mail.AuthenticationFailedException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertTrue
import org.junit.Test

class ImapErrorClassifierTest {
    @Test
    fun authFailed_isClassifiedAsAuth() {
        val m = classifyImapError(
            AuthenticationFailedException("[AUTHENTICATIONFAILED] Authentication failed.")
        )
        assertTrue("expected auth classification, got: $m", m.contains("Authentication failed"))
    }

    @Test
    fun timeout_isClassifiedAsLockoutNotNetwork() {
        val m = classifyImapError(
            javax.mail.MessagingException(
                "Couldn't connect to host, port imap.houseofmanns.com, 993; timeout 60000"
            )
        )
        assertTrue("expected timeout/lockout classification, got: $m", m.contains("timed out"))
    }

    @Test
    fun certError_isClassifiedAsTls() {
        val m = classifyImapError(
            SSLHandshakeException("unable to find valid certification path to requested target")
        )
        assertTrue("expected TLS classification, got: $m", m.contains("TLS/certificate"))
    }
}
