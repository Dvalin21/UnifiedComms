package com.unifiedcomms.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.unifiedcomms.data.db.converters.DateTimeConverter
import com.unifiedcomms.data.db.converters.StringListConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["accountId", "listId"]),
        Index(value = ["accountId", "dueAt"]),
        Index(value = ["accountId", "status"]),
        Index(value = ["uid"]),
        Index("idx_tasks_search", value = ["title", "description"])
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
    @TypeConverters(DateTimeConverter::class) val createdAt: Instant = Instant.now(),
    @TypeConverters(DateTimeConverter::class) val updatedAt: Instant = Instant.now(),
    @TypeConverters(DateTimeConverter::class) val lastSyncedAt: Instant = Instant.now(),
    val etag: String? = null,
    val isLocalOnly: Boolean = false,
    val needsSync: Boolean = false
) {
    fun isOverdue(): Boolean = dueAt?.let { it.toInstant() < Instant.now() } ?: false
    fun isDueToday(): Boolean = dueAt?.let { it.toInstant().toLocalDate() == Instant.now().toLocalDate() } ?: false
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
        return when {
            dateTime != null -> dateTime!!.toInstant(androidx.timeZone.ZoneOffset.of(timeZone))
            date != null -> date!!.atStartOfDay(tz).toInstant()
            else -> Instant.now()
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
enum class TaskPriority {
    NONE(0), LOW(1), MEDIUM(5), HIGH(9), URGENT(10)
    val priority: Int
}

@Serializable
data class TaskAssignee(
    val email: String,
    val name: String? = null,
    val status: AttendeeStatus = AttendeeStatus.NEEDS_ACTION,
    @TypeConverters(DateTimeConverter::class) val assignedAt: Instant = Instant.now()
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