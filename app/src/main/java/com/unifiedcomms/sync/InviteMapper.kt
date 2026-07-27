package com.unifiedcomms.sync

import com.unifiedcomms.data.model.AttendeeRole
import com.unifiedcomms.data.model.AttendeeStatus
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.CalendarInviteMessage
import com.unifiedcomms.data.model.EventAttendee
import com.unifiedcomms.data.model.EventDateTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Bridges a parsed calendar invite (embedded text/calendar) and the app's
 * CalendarEvent model. Used by EmailSyncEngineImpl (store on Email.invite)
 * and by the UI Accept/Decline/Add actions (insert into the calendar).
 *
 * ponytail: invite RSVP addresses from real-world ICS often contain typos
 * (e.g. "keith.manns@houseo fmanns.com" with a space). A malformed address
 * makes the iTIP reply bounce silently, so sanitize before use.
 */
object InviteMapper {

    private fun sanitize(email: String): String = email.trim().replace(Regex("\\s+"), "")

    fun toInviteMessage(event: CalendarEvent, method: CalendarInviteMessage.InviteMethod = CalendarInviteMessage.InviteMethod.REQUEST): CalendarInviteMessage {
        val tz = event.startAt.timeZone.ifBlank { TimeZone.currentSystemDefault().id }
        return CalendarInviteMessage(
            eventUid = event.uid,
            eventTitle = event.title,
            eventDescription = event.description,
            organizerEmail = sanitize(event.organizer?.email ?: ""),
            organizerName = event.organizer?.name,
            startAt = event.startAt.toInstant(TimeZone.of(tz)),
            endAt = event.endAt.toInstant(TimeZone.of(tz)),
            timezone = tz,
            location = event.location,
            recurrenceRule = event.recurrenceRule,
            attendees = event.attendees.map { it.copy(email = sanitize(it.email)) },
            responseRequested = true,
            sequence = event.sequence,
            method = method
        )
    }

    fun toCalendarEvent(invite: CalendarInviteMessage, accountId: String, calendarId: String): CalendarEvent {
        val tz = if (invite.timezone.isBlank()) TimeZone.currentSystemDefault().id else invite.timezone
        return CalendarEvent(
            accountId = accountId,
            calendarId = calendarId,
            uid = invite.eventUid,
            title = invite.eventTitle,
            description = invite.eventDescription,
            location = invite.location,
            startAt = EventDateTime.fromInstant(invite.startAt, TimeZone.of(tz)),
            endAt = EventDateTime.fromInstant(invite.endAt, TimeZone.of(tz)),
            timezone = tz,
            organizer = EventAttendee(
                email = sanitize(invite.organizerEmail),
                name = invite.organizerName,
                status = AttendeeStatus.ACCEPTED,
                role = AttendeeRole.ORGANIZER,
                rsvp = false
            ),
            attendees = invite.attendees.map { a ->
                a.copy(email = sanitize(a.email), status = a.status ?: AttendeeStatus.NEEDS_ACTION)
            },
            recurrenceRule = invite.recurrenceRule,
            sequence = invite.sequence,
            // ponytail: an event created from an email invite is local-first. Mark it
            // isLocalOnly so the CalDAV down-sync delete-pass (which prunes local
            // rows with no server counterpart) does not immediately delete it before
            // the server PUT succeeds. A successful push flips isLocalOnly=false.
            isLocalOnly = true,
            needsSync = true
        )
    }

    /** True when the invite has not yet been added to the user's calendar. */
    fun isPending(invite: CalendarInviteMessage): Boolean = invite.responseRequested
}
