package com.unifiedcomms.util

/**
 * Declarative source of truth for known-provider service endpoints.
 *
 * Replaces the scattered per-domain probe/override logic in Autodiscover
 * (mailcowEmailOverride, selfHostedEmailFallback, knownDavOverrides,
 * sogoDavProbe, ServerConfig.resolveSogoHost) that caused repeated
 * "wrong host / wrong port" regressions (mail.<domain> vs email.<domain> vs
 * apex, imap./smtp., SOGo URL, 993/587 vs 465/143).
 *
 * One table, consulted by Autodiscover.discover() before any generic
 * SRV / .well-known fallback. (LINUS #1 data structures first; #9 no
 * broken windows — no runtime host guessing / TLS probing.)
 *
 * Per-install mailcow SOGo web FQDN is encoded explicitly here
 * (e.g. houseofmanns.com -> email.<domain>) instead of a live probe.
 */
data class ProviderProfile(
    val imapHost: String? = null,
    val imapPort: Int = 993,
    val imapSsl: Boolean = true,
    val smtpHost: String? = null,
    val smtpPort: Int = 587,
    val smtpStartTls: Boolean = true,
    val caldavUrl: String? = null,
    val carddavUrl: String? = null
)

object ProviderProfiles {
    private val BY_DOMAIN: Map<String, ProviderProfile> = mapOf(
        "gmail.com" to ProviderProfile(
            imapHost = "imap.gmail.com", smtpHost = "smtp.gmail.com",
            caldavUrl = "https://apidata.googleusercontent.com/caldav/v2/",
            carddavUrl = "https://www.googleapis.com/carddav/v1/"
        ),
        "googlemail.com" to ProviderProfile(
            imapHost = "imap.gmail.com", smtpHost = "smtp.gmail.com",
            caldavUrl = "https://apidata.googleusercontent.com/caldav/v2/",
            carddavUrl = "https://www.googleapis.com/carddav/v1/"
        ),
        "outlook.com" to ProviderProfile(
            imapHost = "outlook.office365.com", smtpHost = "smtp-mail.outlook.com",
            caldavUrl = "https://outlook.office365.com/ews/exchange.asmx",
            carddavUrl = "https://outlook.office365.com/ews/exchange.asmx"
        ),
        "hotmail.com" to ProviderProfile(
            imapHost = "outlook.office365.com", smtpHost = "smtp-mail.outlook.com",
            caldavUrl = "https://outlook.office365.com/ews/exchange.asmx",
            carddavUrl = "https://outlook.office365.com/ews/exchange.asmx"
        ),
        "live.com" to ProviderProfile(
            imapHost = "outlook.office365.com", smtpHost = "smtp-mail.outlook.com",
            caldavUrl = "https://outlook.office365.com/ews/exchange.asmx",
            carddavUrl = "https://outlook.office365.com/ews/exchange.asmx"
        ),
        "office365.com" to ProviderProfile(
            imapHost = "outlook.office365.com", smtpHost = "smtp-mail.outlook.com",
            caldavUrl = "https://outlook.office365.com/ews/exchange.asmx",
            carddavUrl = "https://outlook.office365.com/ews/exchange.asmx"
        ),
        "icloud.com" to ProviderProfile(
            caldavUrl = "https://caldav.icloud.com/",
            carddavUrl = "https://contacts.icloud.com/"
        ),
        "fastmail.com" to ProviderProfile(
            caldavUrl = "https://www.fastmail.com/dav/calendars/",
            carddavUrl = "https://www.fastmail.com/dav/contacts/"
        ),
        "zoho.com" to ProviderProfile(
            caldavUrl = "https://calendar.zoho.com/caldav/",
            carddavUrl = "https://contacts.zoho.com/carddav/"
        ),
        "yahoo.com" to ProviderProfile(
            imapHost = "imap.mail.yahoo.com", smtpHost = "smtp.mail.yahoo.com",
            caldavUrl = "https://caldav.calendar.yahoo.com/dav/",
            carddavUrl = "https://carddav.address.yahoo.com/dav/"
        ),
        // mailcow install: SOGo web FQDN is per-install. Encoded explicitly
        // (no TLS probe). Email hosts are imap./smtp.<domain> per the account-add
        // mandate (NOT the misconfigured autoconfig XML's mail.*).
        "houseofmanns.com" to ProviderProfile(
            imapHost = "imap.houseofmanns.com", smtpHost = "smtp.houseofmanns.com",
            caldavUrl = "https://email.houseofmanns.com/SOGo/dav/",
            carddavUrl = "https://email.houseofmanns.com/SOGo/dav/"
        )
    )

    /** Exact-domain lookup. Returns null for unknown / generic domains. */
    fun forDomain(domain: String): ProviderProfile? = BY_DOMAIN[domain]
}
