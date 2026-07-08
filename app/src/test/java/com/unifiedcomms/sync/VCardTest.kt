package com.unifiedcomms.sync

import com.unifiedcomms.data.model.ContactSource
import com.unifiedcomms.data.model.UnifiedContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardTest {

    private fun sampleVCard(): String = buildString {
        appendLine("BEGIN:VCARD")
        appendLine("VERSION:3.0")
        appendLine("UID:contact-uid-123")
        appendLine("FN:Jane Doe")
        appendLine("N:Doe;Jane;Middle;Dr;Jr")
        appendLine("EMAIL;TYPE=INTERNET:jane@example.com")
        appendLine("EMAIL:jane.work@corp.com")
        appendLine("TEL:+1-555-0100")
        appendLine("TEL:+1-555-0200")
        appendLine("ORG:Example Corp")
        appendLine("TITLE:Engineer")
        appendLine("URL:https://example.com")
        appendLine("ADR;TYPE=HOME:;;123 Main St;Springfield;IL;62704;USA")
        appendLine("NOTE:Note with \\, comma and \\; semi")
        appendLine("END:VCARD")
    }

    @Test
    fun `parse extracts all core fields`() {
        val c = VCardParser.parse(sampleVCard(), "acc1", ContactSource.CARDDAV, "contact-uid-123")
        assertEquals("Jane Doe", c.displayName)
        assertEquals("Jane", c.firstName)
        assertEquals("Doe", c.lastName)
        assertEquals(listOf("jane@example.com", "jane.work@corp.com"), c.emails)
        assertEquals(listOf("+1-555-0100", "+1-555-0200"), c.phoneNumbers)
        assertEquals("Example Corp", c.organization)
        assertEquals("Engineer", c.title)
        assertEquals(listOf("https://example.com"), c.websites)
        assertEquals("contact-uid-123", c.sourceId)
        assertEquals(ContactSource.CARDDAV, c.source)
        assertEquals("123 Main St, Springfield, IL, 62704, USA", c.addresses.first())
        assertEquals("Note with , comma and ; semi", c.notes)
    }

    @Test
    fun `parse handles line folding`() {
        // RFC 6350: unfold removes the CRLF + leading WSP fold marker entirely.
        // A value folded mid-token therefore joins without the space.
        val folded = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:John\r\n Smith\r\nN:Smith;John\r\nEND:VCARD\r\n"
        val c = VCardParser.parse(folded, "a", ContactSource.LOCAL, "u1")
        assertEquals("JohnSmith", c.displayName)
        assertEquals("John", c.firstName)
        assertEquals("Smith", c.lastName)
    }

    @Test
    fun `parse uses sourceId param when UID missing`() {
        val v = "BEGIN:VCARD\nVERSION:3.0\nFN:Bob\nN:Bob\nEND:VCARD\n"
        val c = VCardParser.parse(v, "a", ContactSource.LOCAL, "fallback-id")
        assertEquals("fallback-id", c.sourceId)
    }

    @Test
    fun `parse displayName fallback to email then phone`() {
        // no FN, no N -> email
        val v1 = "BEGIN:VCARD\nVERSION:3.0\nEMAIL:a@b.com\nEND:VCARD\n"
        assertEquals("a@b.com", VCardParser.parse(v1, "a", ContactSource.LOCAL, "x").displayName)
        // no FN, no N, no email -> phone
        val v2 = "BEGIN:VCARD\nVERSION:3.0\nTEL:555\nEND:VCARD\n"
        assertEquals("555", VCardParser.parse(v2, "a", ContactSource.LOCAL, "x").displayName)
    }

    @Test
    fun `serialize emits fields parser reads back`() {
        val c = UnifiedContact(
            id = "id1",
            displayName = "Jane Doe",
            firstName = "Jane",
            lastName = "Doe",
            emails = listOf("jane@example.com"),
            phoneNumbers = listOf("+1-555-0100"),
            organization = "Example Corp",
            title = "Engineer",
            addresses = listOf("123 Main St, Springfield, IL, 62704, USA"),
            websites = listOf("https://example.com"),
            notes = "a note",
            source = ContactSource.CARDDAV,
            sourceId = "uid-9"
        )
        val v = VCardSerializer.toVCard(c, "uid-9")
        assertTrue(v.contains("BEGIN:VCARD"))
        assertTrue(v.contains("UID:uid-9"))
        assertTrue(v.contains("FN:Jane Doe"))
        assertTrue(v.contains("N:Doe;Jane;;;"))
        assertTrue(v.contains("EMAIL;TYPE=INTERNET:jane@example.com"))
        assertTrue(v.contains("TEL:+1-555-0100"))
        assertTrue(v.contains("ORG:Example Corp"))
        assertTrue(v.contains("TITLE:Engineer"))
        assertTrue(v.contains("URL:https://example.com"))
        assertTrue(v.contains("NOTE:a note"))
        assertTrue(v.contains("END:VCARD"))
    }

    @Test
    fun `serialize escapes special chars`() {
        val c = UnifiedContact(
            id = "id2",
            displayName = "Doe, Jane; Dr.",
            source = ContactSource.LOCAL,
            sourceId = "uid-esc"
        )
        val v = VCardSerializer.toVCard(c, "uid-esc")
        assertTrue(v.contains("FN:Doe\\, Jane\\; Dr."))
    }

    @Test
    fun `round trip preserves core identity`() {
        val original = VCardParser.parse(sampleVCard(), "acc1", ContactSource.CARDDAV, "contact-uid-123")
        val serialized = VCardSerializer.toVCard(original, original.sourceId!!)
        val reparsed = VCardParser.parse(serialized, "acc1", ContactSource.CARDDAV, original.sourceId!!)
        assertEquals(original.displayName, reparsed.displayName)
        assertEquals(original.emails, reparsed.emails)
        assertEquals(original.phoneNumbers, reparsed.phoneNumbers)
        assertEquals(original.organization, reparsed.organization)
        assertEquals(original.title, reparsed.title)
        assertEquals(original.sourceId, reparsed.sourceId)
        assertEquals(original.websites, reparsed.websites)
    }
}
