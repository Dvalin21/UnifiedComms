package com.unifiedcomms.sync

import javax.mail.AuthenticationFailedException
import javax.net.ssl.SSLHandshakeException

// ponytail: JavaMail surfaces every connect/auth failure as a MessagingException
// subtype, but they mean radically different things. The user-facing path was
// blindly prefixing "Could not connect (email):" to the raw message — a lie when
// the server actually answered and rejected auth, or is in lockout and stalling
// the handshake to a timeout. Classify so the UI stops blaming the network.
internal fun classifyImapError(e: Throwable): String {
    return when {
        e is AuthenticationFailedException ->
            "Authentication failed — wrong password or account locked out: ${e.message}"
        e is javax.mail.MessagingException &&
            e.message?.contains("timeout", ignoreCase = true) == true ->
            "Connection timed out — server slow or account temporarily locked after failed " +
                "logins: ${e.message}"
        e is SSLHandshakeException || e is java.security.cert.CertificateException ->
            "TLS/certificate error — check the host matches the server certificate or enable " +
                "'accept all certs': ${e.message}"
        else -> e.message ?: e::class.java.simpleName
    }
}
