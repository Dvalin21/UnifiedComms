package com.unifiedcomms.sync

import com.unifiedcomms.data.model.UnifiedContact

/**
 * Minimal vCard 3.0 serializer covering the fields UnifiedContact carries and
 * that VCardParser reads back. Deliberately omits PHOTO/binary, group-attributed
 * TEL/EMAIL types, and structured ADR sub-fields beyond a single line — those are
 * not in the current model. Extend when the model needs them (ponytail: don't emit
 * what we can't parse back).
 */
object VCardSerializer {

    fun toVCard(contact: UnifiedContact, uid: String): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCARD")
        sb.appendLine("VERSION:3.0")
        sb.appendLine("PRODID:-//UnifiedComms//EN")
        sb.appendLine("UID:${uid.escape()}")
        sb.appendLine("FN:${contact.displayName.escape()}")
        val n = buildString {
            append(contact.lastName?.escape().orEmpty())
            append(';')
            append(contact.firstName?.escape().orEmpty())
            append(";;;")
        }
        sb.appendLine("N:$n")
        contact.emails.forEach { sb.appendLine("EMAIL;TYPE=INTERNET:${it.escape()}") }
        contact.phoneNumbers.forEach { sb.appendLine("TEL:${it.escape()}") }
        contact.organization?.let { sb.appendLine("ORG:${it.escape()}") }
        contact.title?.let { sb.appendLine("TITLE:${it.escape()}") }
        contact.addresses.forEach { sb.appendLine("ADR;TYPE=HOME:;;${it.escape()};;;;") }
        contact.websites.forEach { sb.appendLine("URL:${it.escape()}") }
        contact.notes?.let { sb.appendLine("NOTE:${it.escape()}") }
        sb.appendLine("END:VCARD")
        return sb.toString()
    }

    // ponytail: vCard 3.0 escaping — backslash, newline, comma, semicolon.
    private fun String.escape(): String = this
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
}
