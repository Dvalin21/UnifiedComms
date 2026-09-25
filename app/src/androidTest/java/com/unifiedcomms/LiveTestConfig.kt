package com.unifiedcomms

import androidx.test.platform.app.InstrumentationRegistry

/**
 * ponytail: single source of truth for live-server endpoints in instrumentation
 * tests. No personal address or host is hardcoded anywhere — every value arrives
 * at run time, defaulting to RFC 2606 reserved names so a run without arguments
 * fails on the server instead of silently touching a real mailbox.
 *
 *   adb shell am instrument -w -r \
 *     -e user you@example.com \
 *     -e domain mail.example.com \
 *     -e imapHost imap.mail.example.com \
 *     -e smtpHost smtp.mail.example.com \
 *     -e davUrl https://mail.example.com/dav/ \
 *     -e password '<app-password>' \
 *     -e class com.unifiedcomms.LiveAccountTest
 *
 * `domain` defaults to `example.com`; the host helpers derive from it, so passing
 * `-e domain` alone is enough for most tests.
 */
object LiveTestConfig {
    private fun arg(name: String, fallback: String): String =
        InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() } ?: fallback

    val user: String get() = arg("user", "testbox@example.com")
    val domain: String get() = arg("domain", "example.com")
    val imapHost: String get() = arg("imapHost", "imap.$domain")
    val smtpHost: String get() = arg("smtpHost", "smtp.$domain")
    val davUrl: String get() = arg("davUrl", "https://email.$domain/SOGo/dav/")

    fun password(): String =
        InstrumentationRegistry.getArguments().getString("password")
            ?: error("Supply the live password via: -e password '...'")
}
