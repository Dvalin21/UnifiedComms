package com.unifiedcomms.sync

import android.util.Log
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.EventAttendee
import com.unifiedcomms.data.model.EventDateTime
import com.unifiedcomms.data.model.EventStatus
import com.unifiedcomms.data.model.TimeZoneUtil
import com.unifiedcomms.ui.theme.ColorNormalizer
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Serialize a [CalendarEvent] to a minimal RFC5545 VEVENT.
 *
 * ponytail: only emits fields the app actually edits/reads back — UID, DTSTAMP,
 * DTSTART/DTEND (with TZID, never a floating Z), SUMMARY, DESCRIPTION, LOCATION,
 * STATUS, ORGANIZER/ATTENDEE, RRULE, and the server-side RECURRENCE-ID exception
 * uses. Reminders / attachments / conference remain display-only.
 *
 * DTSTART/DTEND carry their wall-clock zone in [EventDateTime.timeZone]; emit it as a
 * LOCAL time with a TZID (the same fix that corrected VTaskSerializer). Stamping Z
 * masquerades wall-clock as UTC and shifts the event by the zone offset.
 */
object VEventSerializer {
    private const val TAG = "VEventSerializer"

    fun toVevent(
        event: CalendarEvent,
        uid: String = event.uid.ifBlank { java.util.UUID.randomUUID().toString() }
    ): String = serialize(event, uid, method = null)

    /** RFC 5546 iTIP request payload for an outgoing calendar invitation. */
    fun toInvite(event: CalendarEvent): String = serialize(event, event.uid, method = "REQUEST")

    /** RFC 5546 iTIP reply payload for one attendee's RSVP decision. */
    fun toReply(
        event: CalendarEvent,
        attendeeEmail: String,
        status: com.unifiedcomms.data.model.AttendeeStatus,
        comment: String? = null
    ): String = serialize(event, event.uid, method = "REPLY", attendeeEmail, status, comment)

    private fun serialize(
        event: CalendarEvent,
        uid: String,
        method: String?,
        replyEmail: String? = null,
        replyStatus: com.unifiedcomms.data.model.AttendeeStatus? = null,
        comment: String? = null
    ): String {
        val sb = StringBuilder()
        // ponytail: CalDAV requires a full VCALENDAR envelope around the VEVENT.
        // A bare BEGIN:VEVENT (no VCALENDAR) is rejected by SOGo/mailcow with 404
        // on PUT. This matches what ICalParser reads back and what VTaskSerializer
        // must also emit for VTODO.
        sb.appendIcalLine("BEGIN:VCALENDAR")
        sb.appendIcalLine("VERSION:2.0")
        sb.appendIcalLine("PRODID:-//UnifiedComms//Calendar//EN")
        method?.let { sb.appendIcalLine("METHOD:$it") }
        sb.appendIcalLine("BEGIN:VEVENT")
        sb.appendIcalLine("UID:$uid")
        sb.appendIcalLine("DTSTAMP:${nowUtcStamp()}")
        sb.appendIcalLine("SEQUENCE:${event.sequence}")

        sb.appendIcalLine(formatDateTime("DTSTART", event.startAt))
        sb.appendIcalLine(formatDateTime("DTEND", event.endAt))

        if (event.title.isNotBlank()) sb.appendIcalLine("SUMMARY:${escape(event.title)}")
        event.description?.takeIf { it.isNotBlank() }?.let { sb.appendIcalLine("DESCRIPTION:${escape(it)}") }
        event.location?.takeIf { it.isNotBlank() }?.let { sb.appendIcalLine("LOCATION:${escape(it)}") }

        event.organizer?.let { organizer ->
            appendAddressProperty(sb, "ORGANIZER", organizer.email, listOfNotNull(
                organizer.name?.takeIf { it.isNotBlank() }?.let { "CN=\"${escapeParam(it)}\"" }
            ))
        }
        event.attendees.forEach { attendee ->
            appendAddressProperty(sb, "ATTENDEE", attendee.email, buildList {
                attendee.name?.takeIf { it.isNotBlank() }?.let { add("CN=\"${escapeParam(it)}\"") }
                add("ROLE=${roleFor(attendee)}")
                val status = if (attendee.email.equals(replyEmail, ignoreCase = true) && replyStatus != null) {
                    replyStatus
                } else {
                    attendee.status
                }
                add("PARTSTAT=${partstatFor(status)}")
                add("RSVP=${if (attendee.rsvp) "TRUE" else "FALSE"}")
            })
        }

        sb.appendIcalLine("STATUS:${when (event.status) {
            EventStatus.CANCELLED -> "CANCELLED"
            EventStatus.TENTATIVE -> "TENTATIVE"
            else -> "CONFIRMED"
        }}")

        event.recurrenceRule?.let { sb.appendIcalLine("RRULE:${it.toRfc5545()}") }
        comment?.takeIf { it.isNotBlank() }?.let { sb.appendIcalLine("COMMENT:${escape(it)}") }

        // ponytail: emit RECURRENCE-ID for exception instances so the server can store
        // them as overrides of the master. Without this, editing one occurrence of a
        // recurring event is silently lost on round-trip (ICalParser reads RECURRENCE-ID
        // back but the serializer never wrote it).
        event.recurrenceId?.let { rid ->
            sb.appendIcalLine("RECURRENCE-ID:${rid}")
        }

        // ponytail: emit EXDATE entries so server-side cancellations of specific occurrences
        // survive a round-trip. ICalParser reads EXDATE back into recurrenceExceptions.
        event.recurrenceExceptions.filter { it.isDeleted }.forEach { ex ->
            sb.appendIcalLine("EXDATE:${ex.originalDate.toEpochMilliseconds()}")
        }

        // Preserve the user-chosen event color so it survives a round-trip through
        // the server. RFC 7986 COLOR requires a CSS3 name; X-APPLE-COLOR carries
        // the exact normalized RGB for clients that support the Apple extension.
        if (event.color.isExplicit && event.color.background.isNotBlank()) {
            val cssName = ColorNormalizer.toCss3Name(event.color.background)
            if (cssName != null) {
                sb.appendIcalLine("COLOR:$cssName")
                val exactRgb = ColorNormalizer.normalize(event.color.background)
                if (exactRgb.isNotEmpty()) sb.appendIcalLine("X-APPLE-COLOR:$exactRgb")
            }
        }

        sb.appendIcalLine("END:VEVENT")
        sb.appendIcalLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun appendAddressProperty(
        sb: StringBuilder,
        property: String,
        email: String,
        params: List<String>
    ) {
        sb.append(property)
        params.forEach { sb.append(';').append(it) }
        sb.append(":mailto:").append(email.trim()).append("\r\n")
    }

    private fun StringBuilder.appendIcalLine(value: String) {
        append(value).append("\r\n")
    }

    private fun roleFor(attendee: EventAttendee): String = when (attendee.role) {
        com.unifiedcomms.data.model.AttendeeRole.ORGANIZER -> "CHAIR"
        com.unifiedcomms.data.model.AttendeeRole.CHAIR -> "CHAIR"
        com.unifiedcomms.data.model.AttendeeRole.OPT_PARTICIPANT -> "OPT-PARTICIPANT"
        com.unifiedcomms.data.model.AttendeeRole.NON_PARTICIPANT -> "NON-PARTICIPANT"
        else -> "REQ-PARTICIPANT"
    }

    private fun partstatFor(status: com.unifiedcomms.data.model.AttendeeStatus): String = when (status) {
        com.unifiedcomms.data.model.AttendeeStatus.ACCEPTED -> "ACCEPTED"
        com.unifiedcomms.data.model.AttendeeStatus.DECLINED -> "DECLINED"
        com.unifiedcomms.data.model.AttendeeStatus.TENTATIVE -> "TENTATIVE"
        com.unifiedcomms.data.model.AttendeeStatus.DELEGATED -> "DELEGATED"
        com.unifiedcomms.data.model.AttendeeStatus.COMPLETED -> "COMPLETED"
        com.unifiedcomms.data.model.AttendeeStatus.IN_PROCESS -> "IN-PROCESS"
        else -> "NEEDS-ACTION"
    }

    private fun escapeParam(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    /**
     * Return the exact item href when the server supplied one. A UID-derived path
     * is only a fallback for local events and rows written before serverHref existed.
     */
    fun hrefFor(event: CalendarEvent): String {
        event.serverHref?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val cal = event.calendarId.trimEnd('/')
        val uid = event.uid.ifBlank { java.util.UUID.randomUUID().toString() }
        return "$cal/$uid.ics"
    }

    private fun formatDateTime(name: String, dt: EventDateTime): String {
        val zoneId = TimeZoneUtil.normalize(dt.timeZone)?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")
        return runCatching {
            if (dt.isAllDay && dt.date != null) {
                val d = dt.date!!
                "$name;VALUE=DATE:${String.format("%04d%02d%02d", d.year, d.monthNumber, d.dayOfMonth)}"
            } else if (dt.dateTime != null) {
                val zoned = java.time.LocalDateTime.parse(dt.dateTime.toString()).atZone(zoneId)
                val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                "$name;TZID=${TimeZoneUtil.normalize(dt.timeZone)}:${zoned.format(fmt)}"
            } else {
                "$name;VALUE=DATE:${nowUtcDate()}"
            }
        }.onFailure { Log.w(TAG, "bad $name for ${dt.timeZone}", it) }
            .getOrDefault("$name;VALUE=DATE:${nowUtcDate()}")
    }

    private fun nowUtcStamp(): String =
        java.time.Instant.now().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

    private fun nowUtcDate(): String =
        java.time.Instant.now().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
}
