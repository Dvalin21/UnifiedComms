package com.unifiedcomms.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.unifiedcomms.data.db.converters.DateTimeConverter
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
    ],
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
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
    val attachments: List<MessageAttachment> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
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

@Serializable
data class MessageAttachment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: AttachmentType,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val thumbnail: String? = null,
    val url: String? = null,
    val localPath: String? = null,
    val durationSeconds: Int? = null, // For audio/video
    val dimensions: Dimensions? = null // For images/video
) {
    enum class AttachmentType { IMAGE, VIDEO, AUDIO, FILE, CONTACT, LOCATION }
    data class Dimensions(val width: Int, val height: Int)
}

@Serializable
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["participantIds"]),
        Index(value = ["lastActivityAt"]),
        Index(value = ["isArchived"]),
        Index(value = ["isPinned"])
    ]
)
data class Conversation(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val participantIds: List<String>, // Includes our user ID + others
    val participantNames: Map<String, String>, // userId -> display name
    val participantAvatars: Map<String, String> = emptyMap(),
    val type: ConversationType = ConversationType.DIRECT,
    val title: String? = null, // For groups
    val description: String? = null,
    val lastMessageId: String? = null,
    val lastMessagePreview: String? = null,
    @TypeConverters(DateTimeConverter::class) val lastActivityAt: Instant = Clock.System.now(),
    val unreadCount: Int = 0,
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val muteUntil: Instant? = null,
    val settings: ConversationSettings = ConversationSettings(),
    @TypeConverters(DateTimeConverter::class) val createdAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val updatedAt: Instant = Clock.System.now()
) {
    fun getOtherParticipantIds(currentUserId: String): List<String> = participantIds.filter { it != currentUserId }
    fun getOtherParticipantNames(currentUserId: String): List<String> = getOtherParticipantIds(currentUserId).map { participantNames[it] ?: it }
    fun getDisplayName(currentUserId: String): String = when {
        type == ConversationType.GROUP -> title ?: getOtherParticipantNames(currentUserId).joinToString(", ")
        getOtherParticipantIds(currentUserId).isNotEmpty() -> participantNames[getOtherParticipantIds(currentUserId)[0]] ?: getOtherParticipantIds(currentUserId)[0]
        else -> "Unknown"
    }
}

@Serializable
enum class ConversationType {
    DIRECT,     // 1:1
    GROUP,      // Multiple participants
    BROADCAST   // One to many (read-only for recipients)
}

@Serializable
data class ConversationSettings(
    val disappearingMessages: Boolean = false,
    val disappearingTimerSeconds: Int = 86400, // 24 hours default
    val notifyForMessages: Boolean = true,
    val notifyForMentions: Boolean = true,
    val customNotificationSound: String? = null,
    val backgroundColor: String? = null,
    val customEmoji: Map<String, String> = emptyMap()
)

@Serializable
@Entity(tableName = "contacts")
data class UnifiedContact(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val unifiedCommsId: String? = null, // If they have the app
    val emails: List<String> = emptyList(),
    val phoneNumbers: List<String> = emptyList(),
    val displayName: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    val avatarLocalPath: String? = null,
    val organization: String? = null,
    val title: String? = null,
    val department: String? = null,
    val addresses: List<String> = emptyList(),
    val websites: List<String> = emptyList(),
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false,
    val source: ContactSource = ContactSource.LOCAL,
    val sourceId: String? = null,
    val accountId: String? = null, // Which account this contact came from
    @TypeConverters(DateTimeConverter::class) val createdAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val updatedAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val lastSyncedAt: Instant? = null
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

private fun getCurrentUserId(): String {
    // This will be replaced by actual user ID from preferences
    return "current_user"
}