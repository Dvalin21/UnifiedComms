package com.unifiedcomms.sync

import com.unifiedcomms.data.model.AttendeeStatus
import com.unifiedcomms.data.model.EventColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ICalParserInviteTest {
    @Test
    fun parsesOrganizerAndRsvpAttendees() {
        val ical = """
            BEGIN:VCALENDAR
            VERSION:2.0
            METHOD:REQUEST
            BEGIN:VEVENT
            UID:invite-1
            DTSTAMP:20260924T120000Z
            DTSTART:20260925T090000Z
            DTEND:20260925T100000Z
            SUMMARY:Planning
            ORGANIZER;CN=Testbox:mailto:testbox@example.com
            ATTENDEE;CN=Attendee;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:attendee@example.com
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = ICalParser.parse(ical, "account", "/calendar/", "/calendar/invite-1.ics").events.single()

        assertEquals("testbox@example.com", event.organizer?.email)
        assertEquals("Testbox", event.organizer?.name)
        assertEquals("attendee@example.com", event.attendees.single().email)
        assertEquals("Attendee", event.attendees.single().name)
        assertEquals(AttendeeStatus.NEEDS_ACTION, event.attendees.single().status)
        assertTrue(event.attendees.single().rsvp)
    }

    @Test
    fun parserDistinguishesEventColorFromCollectionFallback() {
        val ical = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:color-test
            DTSTART:20260925T090000Z
            DTEND:20260925T100000Z
            SUMMARY:Color test
            COLOR:tomato
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val explicit = ICalParser.parse(ical, "account", "/calendar/", "/calendar/color-test.ics").events.single()
        assertTrue(explicit.color.isExplicit)
        assertEquals("tomato", explicit.color.background)

        val noEventColor = ical.replace("COLOR:tomato\n", "")
        val fallback = ICalParser.parse(
            noEventColor,
            "account",
            "/calendar/",
            "/calendar/color-test.ics",
            defaultColor = "#AAAAAAFF"
        ).events.single()
        assertFalse(fallback.color.isExplicit)
        assertEquals("#AAAAAAFF", fallback.color.background)
    }

    @Test
    fun collectionColorDoesNotOverwriteKnownExplicitEventColor() {
        val explicit = EventColor("#E57373", "#FFFFFF", isExplicit = true)
        val parsed = EventColor.Default().copy(isExplicit = false)

        val resolved = resolveCalendarEventColor(
            parsedColor = parsed,
            collectionColor = "#AAAAAAFF",
            collectionPath = "/calendar/",
            existingColor = explicit
        )

        assertTrue(resolved.isExplicit)
        assertEquals("#E57373", resolved.background)
    }

    @Test
    fun inviteMapperCarriesColorFromMimeToAcceptedEvent() {
        val ical = """
            BEGIN:VCALENDAR
            VERSION:2.0
            METHOD:REQUEST
            BEGIN:VEVENT
            UID:invite-color
            DTSTART:20260925T090000Z
            DTEND:20260925T100000Z
            SUMMARY:Colored invite
            COLOR:tomato
            ORGANIZER:mailto:organizer@example.com
            ATTENDEE:mailto:attendee@example.com
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val parsed = ICalParser.parse(ical, "account", "/calendar/", "/calendar/invite-color.ics").events.single()
        val invite = InviteMapper.toInviteMessage(parsed)
        assertEquals("tomato", invite.color?.background)

        val accepted = InviteMapper.toCalendarEvent(invite, "account", "/calendar/")
        assertEquals("tomato", accepted.color.background)
        assertTrue(accepted.color.isExplicit)
    }

    @Test
    fun inviteColorOnlyReplacesCollectionFallback() {
        val parsed = ICalParser.parse(
            """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:existing
                DTSTART:20260925T090000Z
                DTEND:20260925T100000Z
                SUMMARY:Existing
                END:VEVENT
                END:VCALENDAR
            """.trimIndent(),
            "account",
            "/calendar/",
            "/calendar/existing.ics",
            defaultColor = "#AAAAAAFF"
        ).events.single()
        val inviteColor = EventColor("tomato", "#FFFFFF", isExplicit = true)

        val merged = InviteMapper.applyInviteColor(parsed, inviteColor)
        assertTrue(merged.color.isExplicit)
        assertEquals("tomato", merged.color.background)
        assertEquals(parsed.color.calendarId, merged.color.calendarId)

        val userColor = EventColor("#E57373", "#FFFFFF", isExplicit = true)
        val edited = merged.copy(color = userColor)
        assertEquals(edited, InviteMapper.applyInviteColor(edited, inviteColor))
    }

    @Test
    fun cancelledEventSetsCancellationFlag() {
        val ical = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:cancelled-event
            DTSTART:20260925T090000Z
            DTEND:20260925T100000Z
            SUMMARY:Cancelled
            STATUS:CANCELLED
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = ICalParser.parse(ical, "account", "/calendar/", "/calendar/cancelled.ics").events.single()
        assertTrue(event.isCancelled)
    }
}
