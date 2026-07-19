package com.unifiedcomms.util

import android.util.Log
import com.unifiedcomms.data.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.experimental.and

/**
 * Email + CalDAV/CardDAV autoconfig/autodiscover (RFC 6186 + RFC 6764).
 *
 * Email:
 *  1. Thunderbird central autoconfig (path-based v1.1 API)
 *  2. Per-domain autoconfig host (autoconfig.<domain>/mail/config-v1.1.xml)
 *     + RFC 6764 well-known autoconfig path
 *  3. RFC 6186 SRV records (_imaps / _submissions / _submission / _imap) —
 *     this is what actually resolves imap.<domain> / smtp.<domain> for
 *     providers that publish SRV (e.g. houseofmanns.com) instead of the
 *     Thunderbird XML. Never assume mail.<domain>.
 *
 * Calendar/Contacts (RFC 6764 + RFC 5397):
 *  1. Known-provider overrides for the majors that don't publish SRV correctly.
 *  2. SRV _caldavs/_carddavs (SSL) + _caldav/_carddav, then .well-known.
 *  3. Principal discovery: PROPFIND current-user-principal -> home-set, so we
 *     return a URL that actually hosts collections (NOT a guess like $domain/dav/).
 *
 * ponytail: no MS Exchange POX autodiscover POST, no NAPTR. Minimal + real.
 */
object Autodiscover {
    private const val TAG = "Autodiscover"
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    private val XML_MT = "application/xml; charset=utf-8".toMediaType()

    data class Discovered(
        val imapHost: String,
        val imapPort: Int,
        val imapSsl: Boolean,
        val smtpHost: String,
        val smtpPort: Int,
        val smtpStartTls: Boolean,
        val caldavUrl: String? = null,
        val carddavUrl: String? = null
    )

    suspend fun discover(email: String): Discovered? = withContext(Dispatchers.IO) {
        val domain = email.substringAfter('@').lowercase().trim()
        if (domain.isBlank() || !domain.contains('.')) return@withContext null
        val emailCfg = discoverEmail(domain)
        val dav = discoverDav(domain)
        if (emailCfg == null && dav == null) return@withContext null
        val e = emailCfg
        Discovered(
            imapHost = e?.imapHost ?: "",
            imapPort = e?.imapPort ?: 993,
            imapSsl = e?.imapSsl ?: true,
            smtpHost = e?.smtpHost ?: "",
            smtpPort = e?.smtpPort ?: 587,
            smtpStartTls = e?.smtpStartTls ?: true,
            caldavUrl = dav?.first,
            carddavUrl = dav?.second
        ).takeIf { it.imapHost.isNotBlank() || it.caldavUrl != null || it.carddavUrl != null }
    }

    // ---- EMAIL: Thunderbird autoconfig (XML) + RFC 6186 SRV fallback ----

    private fun discoverEmail(domain: String): Discovered? {
        val urls = listOf(
            "https://autoconfig.thunderbird.net/v1.1/$domain",
            "https://autoconfig.$domain/mail/config-v1.1.xml",
            "https://$domain/.well-known/autoconfig/mail/config-v1.1.xml",
            "https://$domain/.well-known/mail/config-v1.1.xml"
        )
        for (url in urls) {
            try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "UnifiedComms")
                    .build()
                val body = http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                } ?: continue
                parseEmail(body)?.let { return it }
            } catch (e: Exception) {
                Log.d(TAG, "autoconfig miss at $url: ${e.message}")
            }
        }
        // RFC 6186 fallback: SRV records. Never assume mail.<domain> — probe
        // the published SRV targets (imaps/submissions/submission/imap) which is
        // exactly how a real provider (e.g. houseofmanns.com) advertises
        // imap.houseofmanns.com / smtp.houseofmanns.com.
        srvEmailLookup(domain)?.let { return it }
        // ponytail: self-hosted providers (mailcow, postfix/dovecot, etc.) publish neither
        // Thunderbird autoconfig XML nor IMAP/SMTP SRV records. They DO use the near-universal
        // convention imap.<domain>:993 (SSL) + smtp.<domain>:587/465. Probe both; if either
        // host resolves we return it (the engine verifies with a real LOGIN/CAPABILITY later).
        selfHostedEmailFallback(domain)?.let { return it }
        return null
    }

    /**
     * Last-resort email discovery for self-hosted mail (mailcow, etc.) that publishes no
     * autoconfig and no SRV. Tries imap.<domain>/smtp.<domain> with the standard secure ports.
     * Only returns if at least the IMAP host resolves, so we never hand back a fabricated host.
     */
    private fun selfHostedEmailFallback(domain: String): Discovered? {
        val imapHost = "imap.$domain"
        val smtpHost = "smtp.$domain"
        return try {
            // Verify the IMAP host actually resolves; if not, this provider doesn't follow
            // the convention and we must not invent a broken config.
            InetAddress.getByName(imapHost)
            Discovered(
                imapHost = imapHost,
                imapPort = 993,
                imapSsl = true,
                smtpHost = smtpHost,
                smtpPort = 587,
                smtpStartTls = true
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * RFC 6186 SRV-based email discovery. Tries the secure SRV labels first,
     * then the STARTTLS/legacy ones, honouring the user's mandate: 993/465
     * implicit TLS by default, 587 STARTTLS acceptable, 143 ONLY as a last
     * resort (opt-in by nature of being last in the priority order).
     */
    private fun srvEmailLookup(domain: String): Discovered? {
        data class Cand(val label: String, val ssl: Boolean, val port: Int, val startTls: Boolean)
        val candidates = listOf(
            Cand("_imaps._tcp.$domain", true, 993, false),
            Cand("_submissions._tcp.$domain", true, 465, false),
            Cand("_submission._tcp.$domain", false, 587, true),
            Cand("_imap._tcp.$domain", false, 143, true)
        )
        var imap: Pair<String, Pair<Int, Boolean>>? = null   // host -> (port, ssl)
        var smtp: Pair<String, Pair<Int, Boolean>>? = null
        for (c in candidates) {
            val target = srvLookupHost(c.label) ?: continue
            if (c.label.startsWith("_imap") && imap == null) imap = target to (c.port to c.ssl)
            if (c.label.startsWith("_submission") && smtp == null) smtp = target to (c.port to c.startTls)
        }
        if (imap == null && smtp == null) return null
        return Discovered(
            imapHost = imap?.first ?: "",
            imapPort = imap?.second?.first ?: 993,
            imapSsl = imap?.second?.second ?: true,
            smtpHost = smtp?.first ?: "",
            smtpPort = smtp?.second?.first ?: if (smtp?.second?.second == true) 587 else 465,
            smtpStartTls = smtp?.second?.second ?: true
        ).takeIf { it.imapHost.isNotBlank() || it.smtpHost.isNotBlank() }
    }

    /** Minimal SRV query returning just the target host (no path needed for email). */
    private fun srvLookupHost(name: String): String? {
        return try {
            val q = buildSrvQuery(name)
            val sock = DatagramSocket().apply { soTimeout = 4000 }
            sock.send(DatagramPacket(q, q.size, InetAddress.getAllByName(resolver())[0], 53))
            val buf = ByteArray(1024)
            val pkt = DatagramPacket(buf, buf.size)
            sock.receive(pkt)
            sock.close()
            parseSrvResponse(buf, pkt.length, name)?.first?.removeSuffix(".")
        } catch (e: Exception) {
            Log.d(TAG, "SRV $name failed: ${e.message}")
            null
        }
    }

    private fun parseEmail(xml: String): Discovered? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var imapHost: String? = null
            var imapPort = 993
            var imapSsl = true
            var smtpHost: String? = null
            var smtpPort = 587
            var smtpStartTls = true
            var event = parser.eventType
            var inIncoming = false
            var inOutgoing = false
            var currentType: String? = null
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        when {
                            name == "incomingserver" -> {
                                inIncoming = true
                                currentType = parser.getAttributeValue(null, "type")?.lowercase()
                            }
                            name == "outgoingserver" -> {
                                inOutgoing = true
                                currentType = parser.getAttributeValue(null, "type")?.lowercase()
                            }
                            name == "hostname" -> {
                                val v = parser.nextText().trim()
                                if (inIncoming && currentType == "imap") imapHost = v
                                if (inOutgoing && currentType == "smtp") smtpHost = v
                            }
                            name == "port" -> {
                                val v = parser.nextText().trim().toIntOrNull() ?: 0
                                if (inIncoming && currentType == "imap" && v > 0) imapPort = v
                                if (inOutgoing && currentType == "smtp" && v > 0) smtpPort = v
                            }
                            name == "sockettype" -> {
                                val v = parser.nextText().trim().lowercase()
                                val ssl = v.contains("ssl")
                                if (inIncoming && currentType == "imap") imapSsl = ssl
                                if (inOutgoing && currentType == "smtp") smtpStartTls = !ssl && v.contains("starttls")
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.lowercase()
                        if (name == "incomingserver") { inIncoming = false; currentType = null }
                        if (name == "outgoingserver") { inOutgoing = false; currentType = null }
                    }
                }
                event = parser.next()
            }
            if (imapHost != null && smtpHost != null) {
                Discovered(imapHost, imapPort, imapSsl, smtpHost, smtpPort, smtpStartTls)
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "parseEmail failed: ${e.message}")
            null
        }
    }

    // ---- CALDAV / CARDDAV (RFC 6764 + RFC 5397) ----

    /**
     * Returns (caldavUrl, carddavUrl) or null if neither could be discovered.
     */
    private fun discoverDav(domain: String): Pair<String, String>? = withContextSafe {
        // 1) Known-provider overrides (SRV often absent/incorrect for the majors).
        knownDavOverrides(domain)?.let { return@withContextSafe it }

        // 2) SRV records (SSL first), then .well-known. Either yields a base
        //    URL we must then resolve to the real principal/home-set (RFC 6764 §6).
        val calBase = srvLookup("_caldavs._tcp.$domain") ?: srvLookup("_caldav._tcp.$domain")
            ?: wellKnownDav("https://$domain/.well-known/caldav")
        val cardBase = srvLookup("_carddavs._tcp.$domain") ?: srvLookup("_carddav._tcp.$domain")
            ?: wellKnownDav("https://$domain/.well-known/carddav")
        // ponytail: mailcow (and others) answer /.well-known/caldav with a 307 redirect to the
        // real DAV path. wellKnownDav returns that Location; resolveDavHomeSet must run against
        // the REDIRECTED url, not the original base (which only returns the redirect body).

        // 3) Resolve each base to its home-set. calBase/cardBase are already the
        //    redirect-resolved URLs (wellKnownDav returns Location on a 307), so
        //    resolveDavHomeSet PROPFINDs the real DAV endpoint, not the redirect stub.
        //    If resolution fails, fall back to the base root rather than a wrong guess.
        val cal = calBase?.let { resolveDavHomeSet(it, "caldav") } ?: calBase
        val card = cardBase?.let { resolveDavHomeSet(it, "carddav") } ?: cardBase
        if (cal != null || card != null) {
            return@withContextSafe Pair(cal ?: card!!, card ?: cal!!)
        }
        null
    }

    /**
     * RFC 6764 / RFC 5397 principal discovery: from a CalDAV/CardDAV base URL,
     * PROPFIND current-user-principal, then the home-set, and return the home-set
     * URL. Returns null on any failure so the caller can fall back to the base.
     *
     * ponytail: the home-set lives INSIDE the home-set property element
     * (<C:calendar-home-set><D:href>...</D:href></C:calendar-home-set>). The old
     * code scraped a global <href> with a regex that REQUIRED a non-namespaced
     * <href> tag — but DAV XML is always namespaced (<D:href>/<d:href>), so the
     * regex never matched and principal discovery silently returned null. We now
     * walk each <response>, find the home-set property element, and read the href
     * nested inside IT (never the response's own principal href).
     */
    private fun resolveDavHomeSet(base: String, kind: String): String? {
        return try {
            val principal = principalHref(
                base,
                "<D:propfind xmlns:D=\"DAV:\"><D:prop><D:current-user-principal/></D:prop></D:propfind>"
            ) ?: return null
            val homeSetProp = if (kind == "caldav") "calendar-home-set" else "addressbook-home-set"
            val ns = if (kind == "caldav") "urn:ietf:params:xml:ns:caldav" else "urn:ietf:params:xml:ns:carddav"
            homeSetHref(
                principal,
                "<D:propfind xmlns:D=\"DAV:\" xmlns:C=\"$ns\"><D:prop><C:$homeSetProp/></D:prop></D:propfind>",
                homeSetProp
            )
        } catch (_: Exception) { null }
    }

    /** Read the principal URL: the href inside the current-user-principal property. */
    private fun principalHref(url: String, body: String): String? = propfindInnerHref(url, body, "current-user-principal")

    /**
     * Read the href nested INSIDE the named property element (e.g. calendar-home-set),
     * walking each <response> so we never confuse the response's own href with the
     * property's href. Namespace-agnostic (matches local name only).
     */
    private fun homeSetHref(url: String, body: String, propLocalName: String): String? =
        propfindInnerHref(url, body, propLocalName)

    private fun propfindInnerHref(url: String, body: String, propLocalName: String): String? {
        val text = try {
            val req = Request.Builder().url(url)
                .method("PROPFIND", body.toRequestBody(XML_MT))
                .header("Depth", "0")
                .header("User-Agent", "UnifiedComms")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
        } catch (_: Exception) { return null }

        return parsePropHref(text, propLocalName)
    }

    /**
     * Parse a DAV multistatus and return the href nested INSIDE the named property
     * element (e.g. calendar-home-set / current-user-principal). Namespace-agnostic:
     * matches local element names only, so <D:href>/<d:href> both work. Walking each
     * <response> avoids cross-contaminating the response's own href with the
     * property's href (the bug that made principal discovery silently return null).
     */
    internal fun parsePropHref(text: String, propLocalName: String): String? {
        // ponytail: DAV elements are ALWAYS namespaced (<d:response>,
        // <c:calendar-home-set>, <d:href>). Match the LOCAL name with an optional
        // ns prefix so <response>/<calendar-home-set>/<href> all resolve. Walk each
        // <response> and read the href nested INSIDE the named property element —
        // never the response's own href — so principal/home-set hrefs never cross.
        val responses = Regex("<(?:[A-Za-z0-9_]*:)?response\\b[^>]*>(.*?)</(?:[A-Za-z0-9_]*:)?response>", RegexOption.DOT_MATCHES_ALL)
            .findAll(text).map { it.groupValues[1] }.toList()
        for (resp in responses) {
            val prop = Regex("<(?:[A-Za-z0-9_]*:)?$propLocalName\\b[^>]*>(.*?)</(?:[A-Za-z0-9_]*:)?$propLocalName>", RegexOption.DOT_MATCHES_ALL)
                .find(resp)?.groupValues?.get(1) ?: continue
            val hrefMatch = Regex(
                "<(?:[A-Za-z0-9_]*:)?href\\b[^>]*>(.*?)</(?:[A-Za-z0-9_]*:)?href>",
                RegexOption.DOT_MATCHES_ALL
            ).find(prop) ?: continue
            val value = hrefMatch.groupValues[1].trim()
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun findAll(text: String, re: Regex): List<String> = re.findAll(text).map { it.groupValues[1] }.toList()
    private fun findFirst(text: String, re: Regex): String? = re.find(text)?.groupValues?.get(1)

    private fun knownDavOverrides(domain: String): Pair<String, String>? = when (domain) {
        "gmail.com" -> Pair(
            "https://apidata.googleusercontent.com/caldav/v2/",
            "https://www.googleapis.com/carddav/v1/"
        )
        "googlemail.com" -> Pair(
            "https://apidata.googleusercontent.com/caldav/v2/",
            "https://www.googleapis.com/carddav/v1/"
        )
        "outlook.com", "hotmail.com", "live.com", "office365.com" -> Pair(
            "https://outlook.office.com/owa/calendar/",
            "https://outlook.office.com/owa/contacts/"
        )
        "icloud.com" -> Pair(
            "https://caldav.icloud.com/",
            "https://contacts.icloud.com/"
        )
        "fastmail.com" -> Pair(
            "https://www.fastmail.com/dav/calendars/",
            "https://www.fastmail.com/dav/contacts/"
        )
        "zoho.com" -> Pair(
            "https://calendar.zoho.com/caldav/",
            "https://contacts.zoho.com/carddav/"
        )
        "yahoo.com" -> Pair(
            "https://caldav.calendar.yahoo.com/dav/",
            "https://carddav.address.yahoo.com/dav/"
        )
        else -> null
    }

    /**
     * Minimal RFC 1035 DNS SRV query over UDP. Android has no public SRV API, so we
     * speak DNS directly. Returns the absolute https URL derived from the SRV target.
     */
    private fun srvLookup(name: String): String? {
        return try {
            val q = buildSrvQuery(name)
            val sock = DatagramSocket().apply { soTimeout = 4000 }
            sock.send(DatagramPacket(q, q.size, InetAddress.getAllByName(resolver())[0], 53))
            val buf = ByteArray(1024)
            val pkt = DatagramPacket(buf, buf.size)
            sock.receive(pkt)
            sock.close()
            parseSrvResponse(buf, pkt.length, name)?.let { (target, port, path) ->
                val clean = target.removeSuffix(".")
                val scheme = if (name.startsWith("_caldavs") || name.startsWith("_carddavs")) "https" else "http"
                "$scheme://$clean:$port${if (path.isBlank()) "/" else path}"
            }
        } catch (e: Exception) {
            Log.d(TAG, "SRV $name failed: ${e.message}")
            null
        }
    }

    private fun resolver(): String {
        // Use the device's DNS if discoverable, else a public resolver.
        return try {
            val p = java.io.BufferedReader(java.io.FileReader("/etc/resolv.conf")).useLines { lines ->
                lines.firstOrNull { it.startsWith("nameserver") }?.substringAfter("nameserver")?.trim()
            }
            p ?: "8.8.8.8"
        } catch (_: Exception) { "8.8.8.8" }
    }

    private fun buildSrvQuery(name: String): ByteArray {
        val id = byteArrayOf(0x12, 0x34)
        val flags = byteArrayOf(0x01, 0x00) // standard query, recursion desired
        val counts = byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val qname = buildQName(name)
        val qtype = byteArrayOf(0x00, 0x21) // SRV = 33
        val qclass = byteArrayOf(0x00, 0x01) // IN
        return id + flags + counts + qname + qtype + qclass
    }

    private fun buildQName(name: String): ByteArray {
        val out = mutableListOf<Byte>()
        for (label in name.split('.')) {
            if (label.isEmpty()) continue
            out.add(label.length.toByte())
            label.toByteArray(Charsets.US_ASCII).forEach { out.add(it) }
        }
        out.add(0.toByte())
        return out.toByteArray()
    }

    private fun parseSrvResponse(buf: ByteArray, len: Int, qname: String): Triple<String, Int, String>? {
        // Skip header (12) + question section (re-parse qname length).
        var off = 12
        // parse question name to find end
        while (buf[off].toInt() != 0) off += (buf[off].toInt() and 0xFF) + 1
        off += 1 // null terminator
        off += 4 // qtype + qclass
        // answer section; we just need the first SRV (type 0x0021) in the response.
        while (off + 12 <= len) {
            // skip name (may be pointer 0xC0)
            if ((buf[off].toInt() and 0xC0) == 0xC0) off += 2 else {
                while (buf[off] != 0.toByte()) off += (buf[off].toInt() and 0xFF) + 1
                off += 1
            }
            val type = ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)
            off += 10 // type(2) + class(2) + ttl(4) + rdlength(2)
            val rdlen = ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)
            off += 2
            if (type == 0x0021) {
                // priority(2) weight(2) port(2)
                val port = ((buf[off + 4].toInt() and 0xFF) shl 8) or (buf[off + 5].toInt() and 0xFF)
                off += 6
                val (target, _) = readName(buf, off, len)
                return Triple(target, port, "")
            } else {
                off += rdlen
            }
        }
        return null
    }

    private fun readName(buf: ByteArray, start: Int, len: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var off = start
        while (off < len) {
            val lenByte = buf[off].toInt() and 0xFF
            if (lenByte == 0) { off += 1; break }
            if ((lenByte and 0xC0) == 0xC0) { off += 2; break } // pointer
            off += 1
            val label = String(buf, off, lenByte, Charsets.US_ASCII)
            labels.add(label)
            off += lenByte
        }
        return Pair(labels.joinToString("."), off)
    }

    private fun wellKnownDav(url: String): String? {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "UnifiedComms")
                .build()
            http.newCall(req).execute().use { resp ->
                resp.header("Location")?.takeIf { it.isNotBlank() }
                    ?: if (resp.isSuccessful) url else null
            }
        } catch (e: Exception) {
            Log.d(TAG, "well-known $url failed: ${e.message}")
            null
        }
    }

    // WithContext is inline; this just gives a typed wrapper for nullable returns.
    private inline fun <T> withContextSafe(crossinline block: () -> T?): T? = block()
}
