package com.unifiedcomms.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.unifiedcomms.data.db.converters.DateTimeConverter
import com.unifiedcomms.data.db.converters.StringListConverter
import com.unifiedcomms.data.db.converters.EventColorConverter
import com.unifiedcomms.data.db.converters.EventDateTimeConverter
import com.unifiedcomms.data.db.converters.EventAttendeeConverter
import com.unifiedcomms.data.db.converters.AttendeeListConverter
import com.unifiedcomms.data.db.converters.RecurrenceRuleConverter
import com.unifiedcomms.data.db.converters.RecurrenceExceptionListConverter
import com.unifiedcomms.data.db.converters.InstantListConverter
import com.unifiedcomms.data.db.converters.EventReminderListConverter
import com.unifiedcomms.data.db.converters.EventAttachmentListConverter
import com.unifiedcomms.data.db.converters.ConferenceDataConverter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDateTime as JLocalDateTime
import java.time.LocalDate as JLocalDate
import java.time.LocalTime as JLocalTime

@Serializable
@Entity(
    tableName = "calendar_events",
    indices = [
        Index(value = ["accountId", "calendarId"]),
        Index(value = ["accountId", "startAt"]),
        Index(value = ["accountId", "endAt"]),
        Index(value = ["uid"]),
        Index(value = ["recurrenceId"]),
        Index(value = ["title", "description", "location"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(InstantListConverter::class)
data class CalendarEvent(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val accountId: String,
    val calendarId: String,
    val uid: String,
    val recurrenceId: String? = null,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val geoLocation: GeoLocation? = null,
    @TypeConverters(EventDateTimeConverter::class) val startAt: EventDateTime,
    @TypeConverters(EventDateTimeConverter::class) val endAt: EventDateTime,
    val timezone: String = TimeZone.currentSystemDefault().id,
    @TypeConverters(EventColorConverter::class) val color: EventColor = EventColor.Default(),
    @TypeConverters(EventAttendeeConverter::class) val organizer: EventAttendee? = null,
    @TypeConverters(AttendeeListConverter::class) val attendees: List<EventAttendee> = emptyList(),
    @TypeConverters(RecurrenceRuleConverter::class) val recurrenceRule: RecurrenceRule? = null,
    @TypeConverters(RecurrenceExceptionListConverter::class) val recurrenceExceptions: List<RecurrenceException> = emptyList(),
    @TypeConverters(InstantListConverter::class) val recurrenceInstances: List<Instant> = emptyList(),
    val status: EventStatus = EventStatus.CONFIRMED,
    val transparency: EventTransparency = EventTransparency.OPAQUE,
    val visibility: EventVisibility = EventVisibility.DEFAULT,
    val priority: EventPriority = EventPriority.NORMAL,
    @TypeConverters(EventReminderListConverter::class) val reminders: List<EventReminder> = listOf(EventReminder.Default()),
    val categories: List<String> = emptyList(),
    @TypeConverters(EventAttachmentListConverter::class) val attachments: List<EventAttachment> = emptyList(),
    @TypeConverters(ConferenceDataConverter::class) val conferenceData: ConferenceData? = null,
    val sequence: Int = 0,
    val iCalUid: String? = null,
    val etag: String? = null,
    @TypeConverters(DateTimeConverter::class) val createdAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val updatedAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val lastSyncedAt: Instant = Clock.System.now(),
    val isLocalOnly: Boolean = false,
    val needsSync: Boolean = false,
    val isCancelled: Boolean = false
) {
    fun isAllDay(): Boolean = startAt.isAllDay && endAt.isAllDay
    fun isRecurring(): Boolean = recurrenceRule != null
    fun isInstance(): Boolean = recurrenceId != null
    fun isMaster(): Boolean = recurrenceId == null && recurrenceRule != null
    fun getDurationMinutes(): Long = (endAt.toInstant(TimeZone.of(startAt.timeZone)) - startAt.toInstant(TimeZone.of(startAt.timeZone))).inWholeMinutes
    fun getAttendeeStatus(email: String): AttendeeStatus = attendees.find { it.email == email }?.status ?: AttendeeStatus.NEEDS_ACTION
    fun hasAttendee(email: String): Boolean = attendees.any { it.email == email }
    fun getOrganizerEmail(): String = organizer?.email ?: ""
    fun getColorInt(): Int = color.toColorInt()
}

@Serializable
data class EventDateTime(
    val dateTime: LocalDateTime? = null,
    val date: LocalDate? = null,
    val timeZone: String = TimeZone.currentSystemDefault().id,
    val isAllDay: Boolean = false
) {
    @Suppress("UNUSED_PARAMETER")
    fun toInstant(_tz: TimeZone = TimeZone.currentSystemDefault()): Instant {
        val zoneId = ZoneId.of(timeZone)
        val d = date!!
        return when {
            isAllDay -> Instant.fromEpochMilliseconds(JLocalDateTime.of(JLocalDate.of(d.year, d.monthNumber, d.dayOfMonth), JLocalTime.MIDNIGHT).atZone(zoneId).toInstant().toEpochMilli())
            dateTime != null -> Instant.fromEpochMilliseconds(JLocalDateTime.parse(dateTime.toString()).atZone(zoneId).toInstant().toEpochMilli())
            else -> Clock.System.now()
        }
    }

    companion object {
        fun fromInstant(instant: Instant, tz: TimeZone = TimeZone.currentSystemDefault(), allDay: Boolean = false): EventDateTime {
            val zoneId = ZoneId.of(tz.id)
            val javaInstant = java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
            val zoned = javaInstant.atZone(zoneId)
            return if (allDay) {
                EventDateTime(date = LocalDate.parse(zoned.toLocalDate().toString()), isAllDay = true, timeZone = tz.id)
            } else {
                EventDateTime(dateTime = LocalDateTime.parse(zoned.toLocalDateTime().toString()), timeZone = tz.id)
            }
        }
    }
}

@Serializable
data class EventColor(
    val background: String, // Hex color
    val foreground: String, // Hex color
    val calendarId: String? = null // Original calendar color reference
) {
    fun toColorInt(): Int = android.graphics.Color.parseColor(background)
    fun toForegroundInt(): Int = android.graphics.Color.parseColor(foreground)

    companion object {
        fun Default(): EventColor = EventColor("#2196F3", "#FFFFFF")
        fun fromInt(background: Int, foreground: Int = -1): EventColor =
            EventColor(String.format("#%06X", (0xFFFFFF and background)), String.format("#%06X", (0xFFFFFF and foreground)))
        @Suppress("UNUSED_PARAMETER")
        fun generate(_accountColor: Int, index: Int): EventColor {
            val colors = listOf(
                Pair("#E57373", "#FFFFFF"), // Red
                Pair("#F06292", "#FFFFFF"), // Pink
                Pair("#BA68C8", "#FFFFFF"), // Purple
                Pair("#9575CD", "#FFFFFF"), // Deep Purple
                Pair("#7986CB", "#FFFFFF"), // Indigo
                Pair("#64B5F6", "#FFFFFF"), // Blue
                Pair("#4FC3F7", "#000000"), // Light Blue
                Pair("#4DD0E1", "#000000"), // Cyan
                Pair("#4DB6AC", "#FFFFFF"), // Teal
                Pair("#81C784", "#FFFFFF"), // Green
                Pair("#AED581", "#000000"), // Light Green
                Pair("#DCE775", "#000000"), // Lime
                Pair("#FFF176", "#000000"), // Yellow
                Pair("#FFD54F", "#000000"), // Amber
                Pair("#FFB74D", "#000000"), // Orange
                Pair("#FF8A65", "#FFFFFF"), // Deep Orange
                Pair("#A1887F", "#FFFFFF"), // Brown
                Pair("#90A4AE", "#FFFFFF"), // Grey
                Pair("#78909C", "#FFFFFF")  // Blue Grey
            )
            return EventColor(colors[index % colors.size].first, colors[index % colors.size].second)
        }
    }
}

@Serializable
data class EventAttendee(
    val email: String,
    val name: String? = null,
    val status: AttendeeStatus = AttendeeStatus.NEEDS_ACTION,
    val role: AttendeeRole = AttendeeRole.REQ_PARTICIPANT,
    val rsvp: Boolean = true,
    val delegatedFrom: String? = null,
    val delegatedTo: String? = null,
    @TypeConverters(DateTimeConverter::class) val respondedAt: Instant? = null,
    val comment: String? = null
) {
    fun isOrganizer(): Boolean = role == AttendeeRole.ORGANIZER
    fun isRequired(): Boolean = role == AttendeeRole.REQ_PARTICIPANT
    fun isOptional(): Boolean = role == AttendeeRole.OPT_PARTICIPANT
    fun isResource(): Boolean = role == AttendeeRole.NON_PARTICIPANT
}

@Serializable
enum class AttendeeStatus {
    NEEDS_ACTION,     // No response yet
    ACCEPTED,         // Yes
    DECLINED,         // No
    TENTATIVE,        // Maybe
    DELEGATED,        // Delegated to someone else
    COMPLETED,        // Event completed
    IN_PROCESS        // Being processed
}

@Serializable
enum class AttendeeRole {
    REQ_PARTICIPANT,  // Required
    OPT_PARTICIPANT,  // Optional
    NON_PARTICIPANT,  // Resource (room, equipment)
    CHAIR,            // Chair/Co-organizer
    ORGANIZER         // Organizer
}

@Serializable
data class RecurrenceRule(
    val freq: RecurrenceFrequency,
    val interval: Int = 1,
    val count: Int? = null,
    val until: Instant? = null,
    val bySecond: List<Int> = emptyList(),
    val byMinute: List<Int> = emptyList(),
    val byHour: List<Int> = emptyList(),
    val byDay: List<RecurrenceDay> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byYearDay: List<Int> = emptyList(),
    val byWeekNo: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val bySetPos: List<Int> = emptyList(),
    val wkst: RecurrenceDay? = null
) {
    fun toRfc5545(): String {
        val sb = StringBuilder("FREQ=$freq")
        if (interval > 1) sb.append(";INTERVAL=$interval")
        count?.let { sb.append(";COUNT=$it") }
        until?.let { sb.append(";UNTIL=${it.toString().replace("-", "").replace(":", "").replace(".", "")}Z") }
        if (bySecond.isNotEmpty()) sb.append(";BYSECOND=${bySecond.joinToString(",")}")
        if (byMinute.isNotEmpty()) sb.append(";BYMINUTE=${byMinute.joinToString(",")}")
        if (byHour.isNotEmpty()) sb.append(";BYHOUR=${byHour.joinToString(",")}")
        if (byDay.isNotEmpty()) sb.append(";BYDAY=${byDay.joinToString(",")}")
        if (byMonthDay.isNotEmpty()) sb.append(";BYMONTHDAY=${byMonthDay.joinToString(",")}")
        if (byYearDay.isNotEmpty()) sb.append(";BYYEARDAY=${byYearDay.joinToString(",")}")
        if (byWeekNo.isNotEmpty()) sb.append(";BYWEEKNO=${byWeekNo.joinToString(",")}")
        if (byMonth.isNotEmpty()) sb.append(";BYMONTH=${byMonth.joinToString(",")}")
        if (bySetPos.isNotEmpty()) sb.append(";BYSETPOS=${bySetPos.joinToString(",")}")
        wkst?.let { sb.append(";WKST=$it") }
        return sb.toString()
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun parse(_rrule: String): RecurrenceRule? {
            // Simplified parser - in production use ical4j
            return null
        }
    }
}

@Serializable
enum class RecurrenceFrequency {
    SECONDLY, MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY
}

@Serializable
data class RecurrenceDay(
    val day: DayOfWeek,
    val weekNumber: Int? = null // e.g., +1 for first, -1 for last
) {
    override fun toString(): String = weekNumber?.let { "$it$day" } ?: day.name.take(2).uppercase()
}

enum class DayOfWeek { SU, MO, TU, WE, TH, FR, SA }

@Serializable
data class RecurrenceException(
    val originalDate: Instant,
    val exceptionEvent: CalendarEvent? = null,
    val isDeleted: Boolean = false
)

@Serializable
enum class EventStatus {
    TENTATIVE,
    CONFIRMED,
    CANCELLED
}

@Serializable
enum class EventTransparency {
    TRANSPARENT,  // Free
    OPAQUE        // Busy
}

@Serializable
enum class EventVisibility {
    DEFAULT,
    PUBLIC,
    PRIVATE,
    CONFIDENTIAL
}

@Serializable
enum class EventPriority(val priority: Int) {
    LOW(0), NORMAL(5), HIGH(9)
}

@Serializable
data class EventReminder(
    val method: ReminderMethod,
    val minutesBefore: Int,
    val isCustom: Boolean = false
) {
    companion object {
        fun Default(): EventReminder = EventReminder(ReminderMethod.NOTIFICATION, 60) // 1 hour default
        fun AtTimeOfEvent(): EventReminder = EventReminder(ReminderMethod.NOTIFICATION, 0)
        fun Custom(minutes: Int): EventReminder = EventReminder(ReminderMethod.NOTIFICATION, minutes, true)
        fun Email(minutes: Int): EventReminder = EventReminder(ReminderMethod.EMAIL, minutes)
        fun Popup(minutes: Int): EventReminder = EventReminder(ReminderMethod.POPUP, minutes)
    }
}

@Serializable
enum class ReminderMethod {
    NOTIFICATION,
    EMAIL,
    POPUP,
    SMS,
    WEBHOOK
}

@Serializable
data class EventAttachment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val url: String? = null,
    val localPath: String? = null
)

@Serializable
data class ConferenceData(
    val conferenceId: String,
    val conferenceUri: String,
    val conferenceType: ConferenceType,
    val password: String? = null,
    val phoneNumbers: List<PhoneNumber> = emptyList(),
    val notes: String? = null
) {
    enum class ConferenceType {
        GOOGLE_MEET,
        TEAMS,
        ZOOM,
        WEBEX,
        JITSI,
        CUSTOM
    }
}

@Serializable
data class PhoneNumber(
    val number: String,
    val label: String? = null,
    val countryCode: String? = null
)

@Serializable
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null
)

@Serializable
@Entity(tableName = "calendars")
data class Calendar(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val accountId: String,
    val serverId: String,
    val name: String,
    val description: String? = null,
    val color: EventColor = EventColor.Default(),
    val isPrimary: Boolean = false,
    val isReadOnly: Boolean = false,
    val isSelected: Boolean = true,
    val syncEnabled: Boolean = true,
    val canEdit: Boolean = true,
    val owner: String? = null,
    val accessRole: CalendarAccessRole = CalendarAccessRole.OWNER,
    val timeZone: String = TimeZone.currentSystemDefault().id,
    @TypeConverters(StringListConverter::class) val supportedComponents: List<String> = listOf("VEVENT", "VTODO"),
    @TypeConverters(DateTimeConverter::class) val lastSyncedAt: Instant? = null,
    val etag: String? = null
) {
    enum class CalendarAccessRole {
        NONE, FREE_BUSY_READER, READER, WRITER, OWNER
    }
}