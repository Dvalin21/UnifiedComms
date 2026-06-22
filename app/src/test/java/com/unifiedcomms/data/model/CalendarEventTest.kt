package com.unifiedcomms.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarEventTest {

    @Test
    fun `isAllDay returns true when start and end are all day`() {
        val event = CalendarEvent(
            id = "1",
            accountId = "a",
            calendarId = "c",
            uid = "u",
            title = "All Day",
            startAt = EventDateTime(date = LocalDate(2026, 6, 22), isAllDay = true),
            endAt = EventDateTime(date = LocalDate(2026, 6, 23), isAllDay = true)
        )
        assertEquals(true, event.isAllDay())
    }

    @Test
    fun `isAllDay returns false when start is not all day`() {
        val event = CalendarEvent(
            id = "1",
            accountId = "a",
            calendarId = "c",
            uid = "u",
            title = "Timed",
            startAt = EventDateTime(dateTime = LocalDateTime(2026, 6, 22, 9, 0)),
            endAt = EventDateTime(date = LocalDate(2026, 6, 23), isAllDay = true)
        )
        assertEquals(false, event.isAllDay())
    }

    @Test
    fun `getAttendeeStatus returns needs action for missing attendee`() {
        val event = CalendarEvent(
            id = "1",
            accountId = "a",
            calendarId = "c",
            uid = "u",
            title = "Meeting",
            startAt = EventDateTime(),
            endAt = EventDateTime(),
            attendees = listOf(EventAttendee(email = "alice@example.com"))
        )
        assertEquals(AttendeeStatus.NEEDS_ACTION, event.getAttendeeStatus("bob@example.com"))
    }

    @Test
    fun `getOrganizerEmail fallback`() {
        val event = CalendarEvent(
            id = "1",
            accountId = "a",
            calendarId = "c",
            uid = "u",
            title = "Solo",
            startAt = EventDateTime(),
            endAt = EventDateTime()
        )
        assertEquals("", event.getOrganizerEmail())
    }
}
