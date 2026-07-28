package com.unifiedcomms.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.unifiedcomms.data.db.converters.DateTimeConverter
import com.unifiedcomms.data.db.converters.StringListConverter
import com.unifiedcomms.data.db.converters.MapConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId", "sentAt"]),
        Index(value = ["senderId"]),
        Index(value = ["recipientId"]),
        Index(value = ["status"]),
        Index(value = ["messageType"]),
        Index(value = ["content"])
    ]
)
data class Message(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val conversationId: String,
    val senderId: String,       // Our user ID
    val recipientId: String,    // Other user's ID
    val content: String,
    val messageType: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.PENDING,
    val replyToId: String? = null,
    val forwardFromId: String? = null,
    @TypeConverters(MapConverter::class) val metadata: Map<String, String> = emptyMap(),
    val isEncrypted: Boolean = true,
    val encryptionKeyId: String? = null,
    @TypeConverters(DateTimeConverter::class) val sentAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val deliveredAt: Instant? = null,
    @TypeConverters(DateTimeConverter::class) val readAt: Instant? = null,
    @TypeConverters(DateTimeConverter::class) val createdAt: Instant = Clock.System.now(),
    val isLocalOnly: Boolean = false,
    val needsSync: Boolean = false
) {
    fun isOutgoing(): Boolean = senderId == getCurrentUserId()
    fun isIncoming(): Boolean = !isOutgoing()
}

@Serializable
enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    LOCATION,
    CONTACT,
    CALENDAR_INVITE,
    CALENDAR_RESPONSE,
    EMAIL_SHARE,
    TASK_SHARE,
    SYSTEM,
    ENCRYPTED_KEY_EXCHANGE
}

@Serializable
enum class MessageStatus {
    PENDING,      // Queued locally
    SENDING,      // In transit
    SENT,         // Delivered to server
    DELIVERED,    // Delivered to recipient device
    READ,         // Read by recipient
    FAILED,       // Failed to send
    EXPIRED       // Expired (e.g., invite expired)
}

// ── Shared, non-chat model types (contacts / calendar invites / shares) ──

@Serializable
@Entity(tableName = "contacts")
data class UnifiedContact(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val unifiedCommsId: String? = null, // If they have the app
    @TypeConverters(StringListConverter::class) val emails: List<String> = emptyList(),
    @TypeConverters(StringListConverter::class) val phoneNumbers: List<String> = emptyList(),
    val displayName: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val avatarLocalPath: String? = null,
    val organization: String? = null,
    val title: String? = null,
    val department: String? = null,
    @TypeConverters(StringListConverter::class) val addresses: List<String> = emptyList(),
    @TypeConverters(StringListConverter::class) val websites: List<String> = emptyList(),
    val notes: String? = null,
    @TypeConverters(StringListConverter::class) val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false,
    val source: ContactSource = ContactSource.LOCAL,
    val sourceId: String? = null,
    val accountId: String? = null, // Which account this contact came from
    @TypeConverters(DateTimeConverter::class) val createdAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val updatedAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val lastSyncedAt: Instant? = null,
    val isLocalOnly: Boolean = false,
    val needsSync: Boolean = false
) {
    fun hasUnifiedComms(): Boolean = unifiedCommsId != null
    fun getInitials(): String {
        val first = firstName?.firstOrNull()?.uppercase() ?: ""
        val last = lastName?.firstOrNull()?.uppercase() ?: ""
        val initials = first + last
        return if (initials.isNotEmpty()) initials else displayName.take(2).uppercase()
    }
}

@Serializable
enum class ContactSource {
    LOCAL,
    GOOGLE,
    EXCHANGE,
    ICLOUD,
    CARDDAV,
    UNIFIED_COMMS, // Other UnifiedComms users
    IMPORTED
}

@Serializable
data class CalendarInviteMessage(
    val eventUid: String,
    val eventTitle: String,
    val eventDescription: String? = null,
    val organizerEmail: String,
    val organizerName: String? = null,
    val startAt: Instant,
    val endAt: Instant,
    val timezone: String,
    val location: String? = null,
    val recurrenceRule: RecurrenceRule? = null,
    val attendees: List<EventAttendee>,
    val responseRequested: Boolean = true,
    val sequence: Int = 0,
    val method: InviteMethod = InviteMethod.REQUEST
) {
    enum class InviteMethod {
        REQUEST,      // New invite
        REPLY,        // Response to invite
        CANCEL,       // Event cancelled
        COUNTER,      // Counter-proposal
        REFRESH       // Update
    }
}

@Serializable
data class CalendarResponseMessage(
    val eventUid: String,
    val attendeeEmail: String,
    val attendeeName: String? = null,
    val status: AttendeeStatus,
    val comment: String? = null,
    @TypeConverters(DateTimeConverter::class) val respondedAt: Instant = Clock.System.now()
)

@Serializable
data class EmailShareMessage(
    val emailId: String,
    val accountId: String,
    val subject: String,
    val sender: EmailAddress,
    val preview: String,
    val hasAttachments: Boolean
)

@Serializable
data class TaskShareMessage(
    val taskId: String,
    val accountId: String,
    val title: String,
    val description: String? = null,
    val dueAt: Instant? = null,
    val priority: TaskPriority
)

const val CURRENT_USER = "current_user"

fun getCurrentUserId(): String {
    // ponytail: no auth/session layer yet. Read a stored id if present (the integration
    // point for real auth); otherwise fall back to the stable placeholder so messaging
    // rows stay consistent across the app (#8).
    return runCatching { com.unifiedcomms.util.PreferencesManager.getInstance().getString("current_user_id", "") }
        .getOrNull()?.ifBlank { CURRENT_USER } ?: CURRENT_USER
}
