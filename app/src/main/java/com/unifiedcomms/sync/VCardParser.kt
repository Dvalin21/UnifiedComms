package com.unifiedcomms.sync

import com.unifiedcomms.data.model.ContactSource
import com.unifiedcomms.data.model.UnifiedContact

/**
 * Minimal RFC 6350 (vCard 4.0) / RFC 2426 (vCard 3.0) parser.
 * Covers the fields UnifiedContact carries. Unknown properties are ignored.
 * Line folding (continuation starting with space/tab) is unfolded.
 */
object VCardParser {

    fun parse(vcard: String, accountId: String, source: ContactSource, sourceId: String): UnifiedContact {
        val lines = unfold(vcard).map { it.trimEnd('\r') }
        var uid = sourceId
        var fn: String? = null
        val nParts = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val phones = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        val websites = mutableListOf<String>()
        var org: String? = null
        var title: String? = null
        var note: String? = null

        for (raw in lines) {
            if (!raw.contains(':')) continue
            val colon = raw.indexOf(':')
            val nameAndParams = raw.substring(0, colon)
            val value = raw.substring(colon + 1)
            val name = nameAndParams.substringBefore(';').uppercase()
            when (name) {
                "UID" -> if (uid.isBlank() || uid == sourceId) uid = unescape(value)
                "FN" -> fn = unescape(value)
                "N" -> nParts += value.split(';').map { unescape(it.trim()) }
                "EMAIL" -> emails += unescape(value.trim())
                "TEL" -> phones += unescape(value.trim())
                "ADR" -> {
                    val parts = value.split(';').map { unescape(it.trim()) }
                    val street = parts.getOrNull(2).orEmpty()
                    val city = parts.getOrNull(3).orEmpty()
                    val region = parts.getOrNull(4).orEmpty()
                    val code = parts.getOrNull(5).orEmpty()
                    val country = parts.getOrNull(6).orEmpty()
                    val joined = listOf(street, city, region, code, country).filter { it.isNotBlank() }.joinToString(", ")
                    if (joined.isNotBlank()) addresses += joined
                }
                "URL" -> websites += unescape(value.trim())
                "ORG" -> org = unescape(value.split(';').first().trim())
                "TITLE" -> title = unescape(value.trim())
                "NOTE" -> note = unescape(value.trim())
            }
        }

        val firstName = nParts.getOrNull(1).takeIf { !it.isNullOrBlank() }
        val lastName = nParts.getOrNull(0).takeIf { !it.isNullOrBlank() }
        val displayName = fn ?: buildDisplayName(firstName, lastName, emails, phones)

        return UnifiedContact(
            id = java.util.UUID.randomUUID().toString(),
            displayName = displayName,
            firstName = firstName,
            lastName = lastName,
            emails = emails.distinct(),
            phoneNumbers = phones.distinct(),
            organization = org,
            title = title,
            addresses = addresses.distinct(),
            websites = websites.distinct(),
            notes = note,
            source = source,
            sourceId = uid,
            accountId = accountId,
            isLocalOnly = false,
            needsSync = false
        )
    }

    private fun buildDisplayName(first: String?, last: String?, emails: List<String>, phones: List<String>): String {
        val name = listOfNotNull(first, last).joinToString(" ").trim()
        if (name.isNotBlank()) return name
        return emails.firstOrNull() ?: phones.firstOrNull() ?: "Unknown Contact"
    }

    private fun unfold(vcard: String): List<String> {
        val raw = vcard.replace("\r\n", "\n").replace('\r', '\n').lineSequence()
        val out = mutableListOf<String>()
        for (line in raw) {
            if (line.isEmpty()) continue
            if (line.startsWith(" ") || line.startsWith("\t")) {
                // continuation of previous line
                if (out.isNotEmpty()) out[out.lastIndex] += line.substring(1)
            } else {
                out += line
            }
        }
        return out
    }

    // ponytail: only \, \\ \; \n need unescaping for our property set.
    private fun unescape(s: String): String = s
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\n", "\n")
        .replace("\\\\", "\\")
}
