package com.unifiedcomms.sync

import com.unifiedcomms.data.model.AccountType
import javax.mail.AuthenticationFailedException
import javax.net.ssl.SSLHandshakeException

// ponytail: JavaMail surfaces every connect/auth failure as a MessagingException
// subtype, but they mean radically different things. The user-facing path was
// blindly prefixing "Could not connect (email):" to the raw message — a lie when
// the server actually answered and rejected auth, or is in lockout and stalling
// the handshake to a timeout. Classify so the UI stops blaming the network.
//
// [accountType] only adds provider-specific advice. The load-bearing case is
// mailcow: enabling 2FA makes the main mailbox password REJECTED for IMAP/SMTP
// and only an app password works (mailcow 2025-03+). Without that hint the user
// retypes the password that literally cannot work.
internal fun classifyImapError(e: Throwable, accountType: AccountType? = null): String {
    val base = when {
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
    return if (e is AuthenticationFailedException && accountType == AccountType.MAILCOW) {
        "$base. If 2FA is enabled on this mailcow server, IMAP/SMTP need an app password " +
            "from the mailcow UI (App passwords tab) — the account password will not work."
    } else {
        base
    }
}
