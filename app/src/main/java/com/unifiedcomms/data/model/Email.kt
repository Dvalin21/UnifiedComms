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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "emails",
    indices = [
        Index(value = ["accountId", "folder"]),
        Index(value = ["accountId", "receivedAt"]),
        Index(value = ["threadId"]),
        Index(value = ["isRead"]),
        Index(value = ["isFlagged"]),
        Index(value = ["subject", "sender", "body_text"])
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
data class Email(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val accountId: String,
    val folder: String,
    val uid: String,
    val messageId: String,
    val threadId: String,
    val inReplyTo: String? = null,
    val references: List<String> = emptyList(),
    val sender: EmailAddress,
    val recipients: EmailRecipients,
    val subject: String,
    val bodyText: String? = null,
    val bodyHtml: String? = null,
    val preview: String = "",
    @TypeConverters(DateTimeConverter::class) val sentAt: Instant,
    @TypeConverters(DateTimeConverter::class) val receivedAt: Instant = Clock.System.now(),
    @TypeConverters(DateTimeConverter::class) val fetchedAt: Instant = Clock.System.now(),
    @TypeConverters(StringListConverter::class) val labels: List<String> = emptyList(),
    val systemLabels: SystemLabels = SystemLabels(),
    val flags: EmailFlags = EmailFlags(),
    val attachments: List<Attachment> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val priority: EmailPriority = EmailPriority.NORMAL,
    val sensitivity: EmailSensitivity = EmailSensitivity.NORMAL,
    val isEncrypted: Boolean = false,
    val isSigned: Boolean = false,
    val encryptionKeyId: String? = null,
    val sizeBytes: Long = 0,
    val mimeType: String = "text/plain"
) {
    fun isUnread(): Boolean = !flags.isRead
    fun isStarred(): Boolean = flags.isFlagged
    fun hasAttachments(): Boolean = attachments.isNotEmpty()
    fun getSnippet(maxLen: Int = 100): String = preview.take(maxLen)
}

@Serializable
data class EmailAddress(
    val name: String? = null,
    val email: String
) {
    override fun toString(): String = name?.let { "\"$it\" <$email>" } ?: email
    fun getInitials(): String = name?.split(' ')?.joinToString("") { it.first().uppercase() }?.takeIf { it.isNotEmpty() }
        ?: email.take(2).uppercase()
}

@Serializable
data class EmailRecipients(
    val to: List<EmailAddress> = emptyList(),
    val cc: List<EmailAddress> = emptyList(),
    val bcc: List<EmailAddress> = emptyList(),
    val replyTo: List<EmailAddress> = emptyList()
) {
    val all: List<EmailAddress> get() = to + cc + bcc + replyTo
}

@Serializable
data class SystemLabels(
    val inbox: Boolean = false,
    val sent: Boolean = false,
    val draft: Boolean = false,
    val trash: Boolean = false,
    val spam: Boolean = false,
    val starred: Boolean = false,
    val important: Boolean = false,
    val unread: Boolean = false,
    val categorized: Map<String, Boolean> = emptyMap()
) {
    fun primaryLabel(): String {
        return when {
            inbox -> "INBOX"
            sent -> "SENT"
            draft -> "DRAFTS"
            trash -> "TRASH"
            spam -> "SPAM"
            starred -> "STARRED"
            important -> "IMPORTANT"
            else -> "OTHER"
        }
    }
}

@Serializable
data class EmailFlags(
    val isRead: Boolean = false,
    val isFlagged: Boolean = false,
    val isAnswered: Boolean = false,
    val isForwarded: Boolean = false,
    val isDeleted: Boolean = false,
    val isDraft: Boolean = false,
    val isRecent: Boolean = false,
    val isJunk: Boolean = false,
    val isNotJunk: Boolean = false,
    val customFlags: Set<String> = emptySet()
)

@Serializable
enum class EmailPriority {
    LOW, NORMAL, HIGH
}

@Serializable
enum class EmailSensitivity {
    NORMAL, PERSONAL, PRIVATE, CONFIDENTIAL
}

@Serializable
data class Attachment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentId: String? = null,
    val contentLocation: String? = null,
    val isInline: Boolean = false,
    val thumbnail: ByteArray? = null,
    val localPath: String? = null,
    val remoteUrl: String? = null,
    val isDownloaded: Boolean = false,
    @TypeConverters(DateTimeConverter::class) val downloadedAt: Instant? = null
) {
    fun isImage(): Boolean = mimeType.startsWith("image/")
    fun isVideo(): Boolean = mimeType.startsWith("video/")
    fun isAudio(): Boolean = mimeType.startsWith("audio/")
    fun isPdf(): Boolean = mimeType == "application/pdf"
    fun getExtension(): String = fileName.substringAfterLast(".", "").lowercase()
}