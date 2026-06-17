package com.unifiedcomms.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.unifiedcomms.data.db.converters.DateTimeConverter
import com.unifiedcomms.data.db.converters.StringListConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDateTime as JLocalDateTime
import java.time.LocalDate as JLocalDate
import java.time.LocalTime as JLocalTime

@Serializable
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["accountId", "listId"]),
        Index(value = ["accountId", "dueAt"]),
        Index(value = ["accountId", "status"]),
        Index(value = ["uid"]),
        Index(value = ["title", "description"])
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
data class Task(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val accountId: String,
    val listId: String,
    val uid: String,
    val title: String,
    val description: String? = null,
    val status: TaskStatus = TaskStatus.NEEDS_ACTION,
    val priority: TaskPriority = TaskPriority.NONE,
    val dueAt: TaskDateTime? = null,
    val startAt: TaskDateTime? = null,
    val completedAt: TaskDateTime? = null,
    val recurrenceRule: RecurrenceRule? = null,
    val recurrenceExceptions: List<RecurrenceException> = emptyList(),
    val assignee: TaskAssignee? = null,
    val attachments: List<TaskAttachment> = emptyList(),
    val categories: List<String> = emptyList(),
    val position: Int = 0,
    val percentComplete: Int = 0,
    val estimatedDurationMinutes: Int? = null,
    val actualDurationMinutes: Int? = null,
    val reminderMinutesBefore: Int? = null,
    val location: String? = null,
    val geoLocation: GeoLocation? = null,
    val relatedEmails: List<String> = emptyList(), // Email message IDs
    val relatedEvents: List<String> = emptyList(), // Calendar event UIDs
    val parentTaskId: String? = null, // For subtasks
    val hasSubtasks: Boolean = false,
    val subtaskCount: Int = 0,
    val completedSubtaskCount: Int = 0,
    @TypeConverters(DateTimeConverter::class) val createdAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val updatedAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val lastSyncedAt: Instant = Clock.System.now(),
    val etag: String? = null,
    val isLocalOnly: Boolean = false,
    val needsSync: Boolean = false
) {
    fun isOverdue(): Boolean = dueAt?.let { it.toInstant() < Clock.System.now() } ?: false
    fun isDueToday(): Boolean = dueAt?.let {
        val instant = it.toInstant()
        val javaInstant = java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
        val zoneId = ZoneId.systemDefault()
        javaInstant.atZone(zoneId).toLocalDate() == java.time.Instant.ofEpochMilli(Clock.System.now().toEpochMilliseconds()).atZone(ZoneId.systemDefault()).toLocalDate()
    } ?: false
    fun isCompleted(): Boolean = status == TaskStatus.COMPLETED
    fun getProgressText(): String {
        if (hasSubtasks) return "$completedSubtaskCount/$subtaskCount"
        return when (status) {
            TaskStatus.COMPLETED -> "100%"
            TaskStatus.IN_PROCESS -> "${percentComplete}%"
            else -> "0%"
        }
    }
}

@Serializable
data class TaskDateTime(
    val dateTime: LocalDateTime? = null,
    val date: LocalDate? = null,
    val timeZone: String = TimeZone.currentSystemDefault().id,
    val hasTime: Boolean = true
) {
    fun toInstant(tz: TimeZone = TimeZone.currentSystemDefault()): Instant {
        val zoneId = ZoneId.of(timeZone)
        return when {
            dateTime != null -> Instant.fromEpochMilliseconds(JLocalDateTime.parse(dateTime.toString()).atZone(ZoneId.of(timeZone)).toInstant().toEpochMilli())
            date != null -> Instant.fromEpochMilliseconds(JLocalDateTime.of(JLocalDate.parse(date.toString()), JLocalTime.MIDNIGHT).atZone(zoneId).toInstant().toEpochMilli())
            else -> Clock.System.now()
        }
    }

    companion object {
        fun fromInstant(instant: Instant, tz: TimeZone = TimeZone.currentSystemDefault(), hasTime: Boolean = true): TaskDateTime {
            val zoneId = ZoneId.of(tz.id)
            val javaInstant = java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
            val zoned = javaInstant.atZone(ZoneId.of(tz.id))
            return if (hasTime) {
                TaskDateTime(dateTime = LocalDateTime.parse(zoned.toLocalDateTime().toString()), timeZone = tz.id, hasTime = true)
            } else {
                TaskDateTime(date = LocalDate.parse(zoned.toLocalDate().toString()), timeZone = tz.id, hasTime = false)
            }
        }
    }
}

@Serializable
enum class TaskStatus {
    NEEDS_ACTION,
    IN_PROCESS,
    COMPLETED,
    CANCELLED,
    DEFERRED,
    WAITING
}

@Serializable
enum class TaskPriority(val priority: Int) {
    NONE(0), LOW(1), MEDIUM(5), HIGH(9), URGENT(10)
}

@Serializable
data class TaskAssignee(
    val email: String,
    val name: String? = null,
    val status: AttendeeStatus = AttendeeStatus.NEEDS_ACTION,
    @TypeConverters(DateTimeConverter::class) val assignedAt: Instant = Clock.System.now()
)

@Serializable
data class TaskAttachment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val url: String? = null,
    val localPath: String? = null
)

@Serializable
@Entity(tableName = "task_lists")
data class TaskList(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val accountId: String,
    val serverId: String,
    val title: String,
    val description: String? = null,
    val color: EventColor = EventColor.Default(),
    val isDefault: Boolean = false,
    val isReadOnly: Boolean = false,
    val sortOrder: Int = 0,
    val taskCount: Int = 0,
    val completedCount: Int = 0,
    @TypeConverters(DateTimeConverter::class) val lastSyncedAt: Instant? = null,
    val etag: String? = null
)