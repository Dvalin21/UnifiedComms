package com.unifiedcomms.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Credentials
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

class CalDAVClient(
    serverUrl: String,
    private val username: String,
    private val password: String,
    private val client: OkHttpClient,
    private val bearerToken: String? = null
) {
    companion object {
        private const val TAG = "CalDAVClient"
        val ICAL_MEDIA_TYPE = "text/calendar; charset=utf-8".toMediaType()
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        val VCARD_MEDIA_TYPE = "text/vcard; charset=utf-8".toMediaType()
    }

    private val auth = if (bearerToken != null) "Bearer $bearerToken" else Credentials.basic(username, password)
    private val baseUrl: String = serverUrl.trimEnd('/')
    private val internalClient: OkHttpClient = client.newBuilder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", auth)
                .header("User-Agent", "UnifiedComms/1.0 (CalDAV)")
                .build()
            chain.proceed(req)
        }
        .build()

    data class CalendarInfo(val path: String, val displayName: String, val ctag: String = "", val supportsVTODO: Boolean = false)

    data class ETagEntry(val href: String, val etag: String)

    data class IcsResource(val href: String, val etag: String, val ical: String)

    // --- Hearthboard XmlPullParser helpers (ported so the principal/home-set
    // discovery above compiles and works on SOGo/mailcow). Adapted to use this
    // client's `baseUrl` field instead of Hearthboard's `serverUrl`. ---
    internal data class ParsedProp(
        val localName: String,
        val children: List<String>,
        val text: String? = null
    )

    internal data class ParsedResponse(
        val href: String?,
        val props: List<ParsedProp>
    )

    internal fun safeParseMultistatus(xml: String): List<ParsedResponse> {
        val p = runCatching { XmlPullParserFactory.newInstance().newPullParser() }.getOrNull()
            ?: throw IllegalStateException("XmlPullParserFactory unavailable: cannot parse CalDAV response")
        return parseMultistatus(xml, p)
    }

    internal fun parseMultistatus(xml: String, parser: XmlPullParser): List<ParsedResponse> {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        val responses = mutableListOf<ParsedResponse>()
        val stack = mutableListOf<ParsedResponse>()
        val current = mutableListOf<ParsedProp>()

        fun localName(p: XmlPullParser): String = p.name.substringAfter(':')

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        localName(parser).equals("response", ignoreCase = true) ->
                            stack.add(ParsedResponse(href = null, props = emptyList()))
                        localName(parser).equals("href", ignoreCase = true) ->
                            current.add(ParsedProp(localName(parser), emptyList(), safeText(parser)))
                        stack.isNotEmpty() ->
                            current.add(ParsedProp(localName(parser), emptyList()))
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotBlank() && current.isNotEmpty()) {
                        val last = current.last()
                        current[current.lastIndex] = last.copy(text = (last.text ?: "") + text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localName(parser).equals("response", ignoreCase = true) && stack.isNotEmpty()) {
                        val hrefProp = current.firstOrNull { it.localName.equals("href", ignoreCase = true) }
                        val built = stack.removeAt(stack.size - 1)
                            .copy(href = hrefProp?.text, props = current.toList())
                        responses.add(built)
                        current.clear()
                    }
                }
            }
        }
        return responses
    }

    private fun safeText(parser: XmlPullParser): String? =
        if (parser.next() == XmlPullParser.TEXT) parser.text?.trim() else null

    // Given a flat list of ParsedProp from a PROPFIND response, find the property
    // named propName and return the text of the next <href> element after it
    // (the common CalDAV pattern: <X:some-prop><D:href>/value/</D:href></X:some-prop>).
    private fun findPropHref(propName: String, r: ParsedResponse?): String? {
        if (r == null) return null
        val idx = r.props.indexOfFirst { it.localName.equals(propName, ignoreCase = true) }
        if (idx < 0) return null
        for (i in (idx + 1) until r.props.size) {
            if (r.props[i].localName.equals("href", ignoreCase = true)) {
                return r.props[i].text?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun buildUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.isBlank()) return baseUrl
        if (path.startsWith("/")) {
            val origin = extractOrigin(baseUrl)
            return "${origin.trimEnd('/')}$path"
        }
        val base = baseUrl.trimEnd('/')
        return "$base/$path"
    }

    private fun extractOrigin(url: String): String {
        val idx = url.indexOf('/', url.indexOf("//") + 2)
        return if (idx < 0) url else url.substring(0, idx)
    }

    suspend fun listCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:displayname/>
                <D:resourcetype/>
              </D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA_TYPE)

        val req = Request.Builder()
            .url(baseUrl)
            .method("PROPFIND", body)
            .headers(mapOf("Depth" to "1").toHeaders())
            .build()

        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val text = resp.body?.string().orEmpty()
            val hrefs = parseHrefs(text)
            // ponytail: listCalendars() was returning EVERY PROPFIND href — the
            // home-set, addressbooks and task lists — as if each were a calendar,
            // so the calendar picker offered addressbooks/task-lists too. Filter to
            // collections whose href carries "calendar", like scanForCalendars.
            hrefs.filter { it.contains("calendar", ignoreCase = true) }.mapIndexed { idx, href ->
                CalendarInfo(
                    path = href,
                    displayName = href.substringAfterLast('/').ifBlank { "Calendar ${idx + 1}" },
                    ctag = ""
                )
            }
        }
    }

    suspend fun discoverCalendars(): List<CalendarInfo> = withTimeout(20_000) {
        withContext(Dispatchers.IO) {
        // Evidence: user mandate + mailcow manual + stalwart#1796 — for mailcow/SOGo the CalDAV URL
        // is already the exact calendar collection `.../SOGo/dav/<user>/Calendar/personal/`. Using it
        // directly avoids the generic principal walk (which can mangle the path via resolve()/URI
        // joining and return nothing). Only fall back to the walk for non-mailcow / bare-base URLs.
        if (baseUrl.contains("/SOGo/dav/") && baseUrl.contains("/Calendar/")) {
            val calendars = mutableListOf<CalendarInfo>()
            scanForCalendars(baseUrl, calendars)
            if (calendars.isEmpty()) {
                // The URL itself is the calendar collection; only expose it if it
                // actually responds 2xx — a 401/403 means auth failed and must NOT
                // be reported as a working CalDAV leg (provision would lie).
                val ok = runCatching {
                    val xml = """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:"><D:prop><D:displayname/></D:prop></D:propfind>""".trimIndent()
                    propfind(baseUrl, xml, depth = "0")
                }.isSuccess
                if (ok) {
                    val name = baseUrl.trimEnd('/').substringAfterLast('/').ifBlank { "Calendar" }
                    calendars += CalendarInfo(path = baseUrl, displayName = name, ctag = "", supportsVTODO = false)
                }
            }
            return@withContext calendars
        }
        val principal = findPrincipalPath() ?: return@withContext emptyList()
        val homeSet = findCalendarHomeSet(principal)
        val target = homeSet ?: principal
        val calendars = mutableListOf<CalendarInfo>()
        scanForCalendars(target, calendars)
        if (calendars.isEmpty()) calendars += CalendarInfo(principal, "Calendar", "", true)
        calendars
        }
    }

    // --- Calendar discovery ported from Hearthboard's proven CalDAVClient
    // (XmlPullParser flat parser — robust against SOGo's response shapes; the
    // previous DOM walker mis-handled Harmony's getElementsByTagNameNS). ---
    private val COMMON_CALDAV_PATHS = listOf(
        "/SOGo/dav/",
        "/.well-known/caldav",
        "/remote.php/dav/",
        "/caldav/",
        "/dav/"
    )

    private suspend fun findPrincipalPath(): String? {
        tryFindPrincipalAt(baseUrl)?.let { return it }
        val origin = extractOrigin(baseUrl)
        for (suffix in COMMON_CALDAV_PATHS) {
            tryFindPrincipalAt("$origin$suffix")?.let { return it }
        }
        return null
    }

    private suspend fun tryFindPrincipalAt(url: String): String? {
        return try {
            val xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:">
                  <D:prop><D:current-user-principal/></D:prop>
                </D:propfind>
            """.trimIndent()
            val resp = propfind(url, xml, depth = "0")
            val r = safeParseMultistatus(resp).firstOrNull()
            findPropHref("current-user-principal", r) ?: r?.href
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun findCalendarHomeSet(principal: String): String? {
        val base = buildUrl(principal)
        return try {
            val xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                  <D:prop><C:calendar-home-set/></D:prop>
                </D:propfind>
            """.trimIndent()
            val resp = propfind(base, xml, depth = "0")
            val r = safeParseMultistatus(resp).firstOrNull()
            findPropHref("calendar-home-set", r) ?: principal
        } catch (e: Exception) {
            principal
        }
    }

    private suspend fun scanForCalendars(url: String, result: MutableList<CalendarInfo>, visited: MutableSet<String> = mutableSetOf()) {
        if (!result.none { it.path == url }) return
        val normalized = url.trimEnd('/')
        if (normalized in visited) return
        visited += normalized

        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav" xmlns:CS="http://calendarserver.org/ns/">
              <D:prop>
                <D:displayname/>
                <D:resourcetype/>
                <CS:getctag/>
                <C:supported-calendar-component-set/>
              </D:prop>
            </D:propfind>
        """.trimIndent()

        try {
            val body = propfind(normalized, xml, depth = "1")
            val doc = parseXml(body)
            val responses = byLocalName(doc.documentElement, "response")
            for (i in 0 until responses.length) {
                val resp = responses.item(i) as? Element ?: continue
                val hrefNode = byLocalName(resp, "href").item(0)
                val href = hrefNode?.textContent?.trim().orEmpty()
                val fullUrl = if (href.startsWith("http://", true) || href.startsWith("https://", true)) href else resolve(href)
                val subUrl = fullUrl.trimEnd('/')
                val isSelf = subUrl == normalized

                val types = mutableListOf<String>()
                var child = byLocalName(resp, "resourcetype").item(0) as? Element
                var node = child?.firstChild
                while (node != null) {
                    if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) types += (node.localName ?: node.nodeName.substringAfter(':')).lowercase()
                    node = node.nextSibling
                }
                if (types.contains("calendar")) {
                    val nameNode = byLocalName(resp, "displayname").item(0)
                    val name = nameNode?.textContent?.trim().orEmpty().ifBlank { href.split("/").lastOrNull { it.isNotBlank() }.orEmpty() }
                    val ctagNode = byLocalName(resp, "getctag").item(0)
                    val ctag = ctagNode?.textContent?.trim().orEmpty()
                    result += CalendarInfo(path = subUrl, displayName = name, ctag = ctag, supportsVTODO = false)
                } else if (!isSelf) {
                    scanForCalendars(subUrl, result, visited)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "calendar scan failed", e)
        }
    }

    suspend fun listCalendarItems(calendarPath: String): List<String> = withContext(Dispatchers.IO) {
        val target = if (calendarPath.startsWith("http://", true) || calendarPath.startsWith("https://", true)) calendarPath else resolve(calendarPath)
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:getetag/><D:calendar-data/></D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA_TYPE)

        val req = Request.Builder()
            .url(target)
            .method("PROPFIND", body)
            .headers(mapOf("Depth" to "1").toHeaders())
            .build()

        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val text = resp.body?.string().orEmpty()
            parseHrefs(text).filter { it.isNotBlank() }
        }
    }

    suspend fun getETagList(calendarPath: String): List<ETagEntry> = withContext(Dispatchers.IO) {
        val target = if (calendarPath.startsWith("http://", true) || calendarPath.startsWith("https://", true)) calendarPath else resolve(calendarPath)
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:getetag/></D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA_TYPE)

        val req = Request.Builder()
            .url(target)
            .method("PROPFIND", body)
            .headers(mapOf("Depth" to "1").toHeaders())
            .build()

        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return@withContext emptyList()
            try {
                val db = parseXml(text)
                etagEntriesFromMultistatus(db)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun fetchItem(@Suppress("UNUSED_PARAMETER") accountId: String, href: String): IcsResource? = withContext(Dispatchers.IO) {
        val target = when {
            href.startsWith("http://", true) || href.startsWith("https://", true) -> href
            else -> resolve(href)
        }
        internalClient.newCall(Request.Builder().url(target).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val etag = resp.header("ETag")?.trim('"') ?: ""
            val ical = resp.body?.string().orEmpty()
            IcsResource(href, etag, ical)
        }
    }

    // ponytail: task lists are CalDAV collections that advertise VTODO in their component set.
    suspend fun discoverTaskLists(): List<CalendarInfo> = withTimeout(20_000) {
        withContext(Dispatchers.IO) {
            val principal = findPrincipalPath() ?: return@withContext emptyList()
            val homeSet = findCalendarHomeSet(principal) ?: principal
            val out = mutableListOf<CalendarInfo>()
            scanForTaskLists(homeSet, out)
            out
        }
    }

    private suspend fun scanForTaskLists(url: String, result: MutableList<CalendarInfo>, visited: MutableSet<String> = mutableSetOf()) {
        val normalized = url.trimEnd('/')
        if (normalized in visited) return
        visited += normalized
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:prop>
                <D:displayname/>
                <D:resourcetype/>
                <C:supported-calendar-component-set/>
              </D:prop>
            </D:propfind>
        """.trimIndent()
        try {
            val body = propfind(normalized, xml, depth = "1")
            val db = parseXml(body)
            val responses = byLocalName(db.documentElement, "response")
            for (i in 0 until responses.length) {
                val resp = responses.item(i) as? Element ?: continue
                val href = byLocalName(resp, "href").item(0)?.textContent?.trim().orEmpty()
                val fullUrl = if (href.startsWith("http://", true) || href.startsWith("https://", true)) href else resolve(href)
                val subUrl = fullUrl.trimEnd('/')
                val isSelf = subUrl == normalized
                val types = mutableListOf<String>()
                var rt = byLocalName(resp, "resourcetype").item(0) as? Element
                var node = rt?.firstChild
                while (node != null) {
                    if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) types += (node.localName ?: node.nodeName.substringAfter(':')).lowercase()
                    node = node.nextSibling
                }
                val comps = componentSetOf(resp)
                if (types.contains("calendar") && comps.contains("VTODO")) {
                    val name = byLocalName(resp, "displayname").item(0)?.textContent?.trim().orEmpty()
                        .ifBlank { subUrl.split("/").lastOrNull { it.isNotBlank() }.orEmpty() }
                    result += CalendarInfo(path = subUrl, displayName = name, supportsVTODO = true)
                } else if (!isSelf) {
                    scanForTaskLists(subUrl, result, visited)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "task list scan failed", e)
        }
    }

    private fun componentSetOf(response: Element): Set<String> {
        val set = byLocalName(response, "supported-calendar-component-set").item(0) as? Element ?: return emptySet()
        val comps = byLocalName(set, "comp")
        val out = mutableSetOf<String>()
        for (i in 0 until comps.length) {
            val name = (comps.item(i) as? Element)?.getAttribute("name")?.trim()?.uppercase().orEmpty()
            if (name.isNotBlank()) out += name
        }
        return out
    }

    // ponytail: generic PUT used for task writes (VTODO). Returns server ETag or null on failure.
    suspend fun putResource(href: String, body: String, contentType: okhttp3.MediaType = ICAL_MEDIA_TYPE): String? = withContext(Dispatchers.IO) {
        val target = when {
            href.startsWith("http://", true) || href.startsWith("https://", true) -> href
            else -> resolve(href)
        }
        val req = Request.Builder().url(target).put(body.toRequestBody(contentType)).build()
        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "PUT $target failed: ${resp.code}")
                return@use null
            }
            resp.header("ETag")?.trim('"')?.ifBlank { null }
        }
    }

    suspend fun deleteResource(href: String): Boolean = withContext(Dispatchers.IO) {
        val target = when {
            href.startsWith("http://", true) || href.startsWith("https://", true) -> href
            else -> resolve(href)
        }
        val req = Request.Builder().url(target).delete().build()
        internalClient.newCall(req).execute().use { resp -> resp.isSuccessful || resp.code == 404 }
    }

    // ponytail: CardDAV reuses the same DAV plumbing. An addressbook is a collection
    // whose resourcetype contains "addressbook". vCard GET/PUT/DELETE use putResource/deleteResource.
    data class AddressBookInfo(val path: String, val displayName: String)

    suspend fun discoverAddressBooks(): List<AddressBookInfo> = withTimeout(20_000) {
        withContext(Dispatchers.IO) {
            val principal = findPrincipalPath() ?: return@withContext emptyList()
            val homeSet = findAddressBookHomeSet(principal) ?: principal
            val out = mutableListOf<AddressBookInfo>()
            scanForAddressBooks(homeSet, out)
            out
        }
    }

    private suspend fun findAddressBookHomeSet(principal: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:carddav">
                  <D:prop><C:addressbook-home-set/></D:prop>
                </D:propfind>
            """.trimIndent()
            val resp = propfind(principal, xml, depth = "0")
            parseHrefsFromPropfind(resp).firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun scanForAddressBooks(url: String, result: MutableList<AddressBookInfo>, visited: MutableSet<String> = mutableSetOf()) {
        val normalized = url.trimEnd('/')
        if (normalized in visited) return
        visited += normalized
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:carddav">
              <D:prop>
                <D:displayname/>
                <D:resourcetype/>
              </D:prop>
            </D:propfind>
        """.trimIndent()
        try {
            val body = propfind(normalized, xml, depth = "1")
            val db = parseXml(body)
            val responses = byLocalName(db.documentElement, "response")
            for (i in 0 until responses.length) {
                val resp = responses.item(i) as? Element ?: continue
                val href = byLocalName(resp, "href").item(0)?.textContent?.trim().orEmpty()
                val fullUrl = if (href.startsWith("http://", true) || href.startsWith("https://", true)) href else resolve(href)
                val subUrl = fullUrl.trimEnd('/')
                val isSelf = subUrl == normalized
                val types = mutableListOf<String>()
                var rt = byLocalName(resp, "resourcetype").item(0) as? Element
                var node = rt?.firstChild
                while (node != null) {
                    if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) types += (node.localName ?: node.nodeName.substringAfter(':')).lowercase()
                    node = node.nextSibling
                }
                if (types.contains("addressbook")) {
                    val name = byLocalName(resp, "displayname").item(0)?.textContent?.trim().orEmpty()
                        .ifBlank { subUrl.split("/").lastOrNull { it.isNotBlank() }.orEmpty() }
                    result += AddressBookInfo(path = subUrl, displayName = name)
                } else if (!isSelf) {
                    scanForAddressBooks(subUrl, result, visited)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "addressbook scan failed", e)
        }
    }

    // ponytail: href/etag must come from the SAME <response> element. Flattened
    // global index lists desync when one <response> (e.g. the collection) has no
    // getetag — the collection href then pairs with the item's etag. Walk per-response.
    private fun etagEntriesFromMultistatus(doc: org.w3c.dom.Document): List<ETagEntry> {
        val responses = byLocalName(doc.documentElement, "response")
        val out = mutableListOf<ETagEntry>()
        for (i in 0 until responses.length) {
            val resp = responses.item(i) as? Element ?: continue
            val href = byLocalName(resp, "href").item(0)?.textContent?.trim().orEmpty()
            val etag = byLocalName(resp, "getetag").item(0)?.textContent?.trim('"').orEmpty()
            if (href.isNotBlank() && etag.isNotBlank()) out += ETagEntry(href, etag)
        }
        return out
    }

    suspend fun listAddressBookItems(addressBookPath: String): List<ETagEntry> = withContext(Dispatchers.IO) {
        val target = if (addressBookPath.startsWith("http://", true) || addressBookPath.startsWith("https://", true)) addressBookPath else resolve(addressBookPath)
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop><D:getetag/></D:prop>
            </D:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA_TYPE)
        val req = Request.Builder().url(target).method("PROPFIND", xml).headers(mapOf("Depth" to "1").toHeaders()).build()
        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return@use emptyList()
            try {
                val db = parseXml(text)
                val all = etagEntriesFromMultistatus(db)
                // ponytail: skip the collection itself (no .vcf extension).
                all.filter { it.href.endsWith(".vcf", true) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // ponytail: GET a single vCard resource (text/vcard). Reuses fetchItem shape but tagged for clarity.
    suspend fun fetchVCard(href: String): IcsResource? = fetchItem("", href)

    // ponytail: PUT a vCard. Reuses putResource with the vCard media type.
    suspend fun putVCard(href: String, body: String): String? = putResource(href, body, VCARD_MEDIA_TYPE)

    suspend fun getCTag(calendarPath: String): String = withContext(Dispatchers.IO) {
        val target = if (calendarPath.startsWith("http://", true) || calendarPath.startsWith("https://", true)) calendarPath else resolve(calendarPath)
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:propfind xmlns:D="DAV:" xmlns:CS="http://calendarserver.org/ns/">
              <D:prop><CS:getctag/></D:prop>
            </D:propfind>
        """.trimIndent()
        try {
            val resp = propfind(target, xml, depth = "0")
            val db = parseXml(resp)
            val node = byLocalName(db.documentElement, "getctag").item(0)
            node?.textContent?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseXml(body: String): org.w3c.dom.Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(body.byteInputStream())

    // Harmony's DOM getElementsByTagNameNS("*", local) is unreliable; walk the tree
    // by local name instead (requires namespace-aware parsing, which parseXml sets).
    // Returns a NodeList so existing .length / .item(i) call sites keep working.
    private fun byLocalName(root: org.w3c.dom.Node, local: String): org.w3c.dom.NodeList {
        val out = mutableListOf<org.w3c.dom.Element>()
        fun walk(n: org.w3c.dom.Node?) {
            var c = n
            while (c != null) {
                if (c.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    val e = c as org.w3c.dom.Element
                    // Harmony's DOM may leave localName null even when namespace-aware;
                    // fall back to stripping the prefix from nodeName (e.g. "D:response").
                    val ln = e.localName ?: e.nodeName.substringAfter(':')
                    if (ln.equals(local, ignoreCase = true)) out += e
                }
                walk(c.firstChild)
                c = c.nextSibling
            }
        }
        walk(root)
        return object : org.w3c.dom.NodeList {
            override fun getLength(): Int = out.size
            override fun item(index: Int): org.w3c.dom.Node? = out.getOrNull(index)
        }
    }

    private suspend fun propfind(url: String, body: String, depth: String = "1"): String = withContext(Dispatchers.IO) {
        val target = resolve(url)
        val req = Request.Builder()
            .url(target)
            .method("PROPFIND", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", depth)
            .build()
        internalClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.message}")
            resp.body?.string().orEmpty()
        }
    }

    // ponytail: DAV servers return absolute-path or absolute-URL hrefs. URI.resolve()
    // correctly joins base + path WITHOUT doubling the path segment (naive
    // resolve(href) concatenated base+path -> doubled path).
    private fun resolve(url: String): String =
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) url
        else URI(baseUrl).resolve(url).toString()

    private fun parseHrefs(xml: String): List<String> {
        return try {
            val db = parseXml(xml)
            val nodes = byLocalName(db.documentElement, "href")
            List(nodes.length) { i -> nodes.item(i).textContent.trim() }
        } catch (t: Throwable) {
            Log.w(TAG, "parseHrefs failed", t)
            emptyList()
        }
    }

    private fun parseHrefsFromPropfind(xml: String): List<String> {
        return try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            val out = mutableListOf<String>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.equals("href", ignoreCase = true)) {
                    out += parser.nextText().trim()
                }
                event = parser.next()
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}
