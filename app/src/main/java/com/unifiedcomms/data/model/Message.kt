package com.unifiedcomms.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.unifiedcomms.data.db.converters.DateTimeConverter
import com.unifiedcomms.data.db.converters.StringListConverter
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

// ponytail: this file used to also carry the `Message` chat entity, its
// MessageType/MessageStatus enums and three share-message models. The chat
// feature was removed (commit 3ab6450) but they stayed behind with zero
// consumers, and because they shared a file with UnifiedContact and
// CalendarInviteMessage the whole file looked load-bearing. Only the dead
// types are gone; the live ones below are untouched.

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
    // Exact CardDAV href returned by the server; enables update/delete against the
    // correct resource even when the contact lives in a non-first address book.
    val serverHref: String? = null,
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
    // RFC 7986 COLOR is carried from the invite MIME; null keeps old JSON rows valid.
    val color: EventColor? = null,
    val attendees: List<EventAttendee>,
    val responseRequested: Boolean = true,
    val sequence: Int = 0,
    val method: InviteMethod = InviteMethod.REQUEST
) {
    enum class InviteMethod {
        REQUEST,        // New invite
        REPLY,          // Response to invite
        CANCEL,         // Event cancelled
        COUNTER,        // Counter-proposal
        REFRESH         // Update
    }
}

const val CURRENT_USER = "current_user"

fun getCurrentUserId(): String {
    // ponytail: no auth/session layer yet. Read a stored id if present (the integration
    // point for real auth); otherwise fall back to the stable placeholder.
    return runCatching { com.unifiedcomms.util.PreferencesManager.getInstance().getString("current_user_id", "") }
        .getOrNull()?.ifBlank { CURRENT_USER } ?: CURRENT_USER
}
