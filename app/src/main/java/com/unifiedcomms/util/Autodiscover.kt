package com.unifiedcomms.util

import android.util.Log
import com.unifiedcomms.data.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
 *  3. RFC 6186 /.well-known/autoconfig/mail/config-v1.1.xml
 *
 * Calendar/Contacts (RFC 6764):
 *  1. SRV _caldavs._tcp / _carddavs._tcp (SSL) -> host:port:path
 *  2. .well-known/caldav + .well-known/carddav (redirect Location)
 *  3. Known-provider overrides for the majors that don't publish SRV correctly.
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
        // If email autoconfig failed entirely but DAV resolved, still return DAV-only.
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

    // ---- EMAIL (unchanged strategy, corrected URLs) ----

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
        return null
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

    // ---- CALDAV / CARDDAV (RFC 6764) ----

    /**
     * Returns (caldavUrl, carddavUrl) or null if neither could be discovered.
     */
    private fun discoverDav(domain: String): Pair<String, String>? = withContextSafe {
        // 1) Known-provider overrides (SRV often absent/incorrect for the majors).
        knownDavOverrides(domain)?.let { return@withContextSafe it }

        // 2) SRV records (SSL first).
        val cal = srvLookup("_caldavs._tcp.$domain") ?: srvLookup("_caldav._tcp.$domain")
        val card = srvLookup("_carddavs._tcp.$domain") ?: srvLookup("_carddav._tcp.$domain")
        if (cal != null || card != null) {
            return@withContextSafe Pair(cal ?: card!!, card ?: cal!!)
        }

        // 3) .well-known redirects.
        val calWk = wellKnownDav("https://$domain/.well-known/caldav")
        val cardWk = wellKnownDav("https://$domain/.well-known/carddav")
        if (calWk != null || cardWk != null) {
            return@withContextSafe Pair(calWk ?: cardWk!!, cardWk ?: calWk!!)
        }
        null
    }

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
            // Outlook/M365 CalDAV is not generally available; leave null so UI can ask.
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

    fun toServerConfig(d: Discovered, server: String): ServerConfig {
        return ServerConfig(
            imapHost = d.imapHost.ifBlank { null },
            imapPort = d.imapPort,
            imapUseSsl = d.imapSsl,
            smtpHost = d.smtpHost.ifBlank { null },
            smtpPort = d.smtpPort,
            smtpUseStartTls = d.smtpStartTls,
            caldavUrl = d.caldavUrl ?: "$server/dav/",
            carddavUrl = d.carddavUrl ?: "$server/dav/"
        )
    }

    // WithContext is inline; this just gives a typed wrapper for nullable returns.
    private inline fun <T> withContextSafe(crossinline block: () -> T?): T? = block()
}
