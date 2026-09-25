package com.unifiedcomms.sync

import com.unifiedcomms.data.model.AccountType
import javax.mail.AuthenticationFailedException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertFalse
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
                "Couldn't connect to host, port imap.example.com, 993; timeout 60000"
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

    @Test
    fun mailcowAuthFailure_namesTheAppPasswordRequirement() {
        val m = classifyImapError(
            AuthenticationFailedException("[AUTHENTICATIONFAILED] Authentication failed."),
            AccountType.MAILCOW
        )
        assertTrue("expected the app-password hint, got: $m", m.contains("app password"))
    }

    @Test
    fun mailcowHint_isOnlyAddedForAuthFailuresOnMailcow() {
        assertFalse(
            "a timeout is not a credential problem",
            classifyImapError(javax.mail.MessagingException("timeout 60000"), AccountType.MAILCOW)
                .contains("app password")
        )
        assertFalse(
            "other providers get the plain message",
            classifyImapError(AuthenticationFailedException("nope"), AccountType.GENERIC_IMAP_SMTP)
                .contains("app password")
        )
    }
}
