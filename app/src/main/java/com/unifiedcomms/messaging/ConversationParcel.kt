package com.unifiedcomms.messaging

import android.os.Parcel
import android.os.Parcelable
import com.unifiedcomms.data.model.Conversation
import com.unifiedcomms.data.model.ConversationType
import com.unifiedcomms.data.model.ConversationSettings
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class ConversationParcel(
    val id: String = "",
    val participantIdsJson: String = "[]",
    val participantNamesJson: String = "{}",
    val participantAvatarsJson: String = "{}",
    val type: Int = ConversationType.DIRECT.ordinal,
    val title: String? = null,
    val description: String? = null,
    val lastMessageId: String? = null,
    val lastMessagePreview: String? = null,
    val lastActivityAt: Long = 0,
    val unreadCount: Int = 0,
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val muteUntil: Long? = null,
    val settingsJson: String = "{}",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        participantIdsJson = parcel.readString() ?: "[]",
        participantNamesJson = parcel.readString() ?: "{}",
        participantAvatarsJson = parcel.readString() ?: "{}",
        type = parcel.readInt(),
        title = parcel.readString(),
        description = parcel.readString(),
        lastMessageId = parcel.readString(),
        lastMessagePreview = parcel.readString(),
        lastActivityAt = parcel.readLong(),
        unreadCount = parcel.readInt(),
        isArchived = parcel.readByte() != 0.toByte(),
        isPinned = parcel.readByte() != 0.toByte(),
        isMuted = parcel.readByte() != 0.toByte(),
        muteUntil = if (parcel.readByte() != 0.toByte()) parcel.readLong() else null,
        settingsJson = parcel.readString() ?: "{}",
        createdAt = parcel.readLong(),
        updatedAt = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(participantIdsJson)
        parcel.writeString(participantNamesJson)
        parcel.writeString(participantAvatarsJson)
        parcel.writeInt(type)
        parcel.writeString(title)
        parcel.writeString(description)
        parcel.writeString(lastMessageId)
        parcel.writeString(lastMessagePreview)
        parcel.writeLong(lastActivityAt)
        parcel.writeInt(unreadCount)
        parcel.writeByte(if (isArchived) 1 else 0)
        parcel.writeByte(if (isPinned) 1 else 0)
        parcel.writeByte(if (isMuted) 1 else 0)
        parcel.writeByte(if (muteUntil != null) 1 else 0)
        muteUntil?.let { parcel.writeLong(it) }
        parcel.writeString(settingsJson)
        parcel.writeLong(createdAt)
        parcel.writeLong(updatedAt)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ConversationParcel> = object : Parcelable.Creator<ConversationParcel> {
            override fun createFromParcel(parcel: Parcel): ConversationParcel = ConversationParcel(parcel)
            override fun newArray(size: Int): Array<ConversationParcel?> = arrayOfNulls(size)
        }

        private val json = Json { ignoreUnknownKeys = true }

        fun fromConversation(conversation: Conversation): ConversationParcel {
            return ConversationParcel(
                id = conversation.id,
                participantIdsJson = json.encodeToString(conversation.participantIds),
                participantNamesJson = json.encodeToString(conversation.participantNames),
                participantAvatarsJson = json.encodeToString(conversation.participantAvatars),
                type = conversation.type.ordinal,
                title = conversation.title,
                description = conversation.description,
                lastMessageId = conversation.lastMessageId,
                lastMessagePreview = conversation.lastMessagePreview,
                lastActivityAt = conversation.lastActivityAt.toEpochMilliseconds(),
                unreadCount = conversation.unreadCount,
                isArchived = conversation.isArchived,
                isPinned = conversation.isPinned,
                isMuted = conversation.isMuted,
                muteUntil = conversation.muteUntil?.toEpochMilliseconds(),
                settingsJson = json.encodeToString(conversation.settings),
                createdAt = conversation.createdAt.toEpochMilliseconds(),
                updatedAt = conversation.updatedAt.toEpochMilliseconds()
            )
        }

        fun toConversation(parcel: ConversationParcel): Conversation {
            val participantIds = json.decodeFromString<List<String>>(parcel.participantIdsJson)
            val participantNames = json.decodeFromString<Map<String, String>>(parcel.participantNamesJson)
            val participantAvatars = json.decodeFromString<Map<String, String>>(parcel.participantAvatarsJson)
            val settings = json.decodeFromString<ConversationSettings>(parcel.settingsJson)
            return Conversation(
                id = parcel.id,
                participantIds = participantIds,
                participantNames = participantNames,
                participantAvatars = participantAvatars,
                type = ConversationType.values()[parcel.type],
                title = parcel.title,
                description = parcel.description,
                lastMessageId = parcel.lastMessageId,
                lastMessagePreview = parcel.lastMessagePreview,
                lastActivityAt = Instant.fromEpochMilliseconds(parcel.lastActivityAt),
                unreadCount = parcel.unreadCount,
                isArchived = parcel.isArchived,
                isPinned = parcel.isPinned,
                isMuted = parcel.isMuted,
                muteUntil = parcel.muteUntil?.let { Instant.fromEpochMilliseconds(it) },
                settings = settings,
                createdAt = Instant.fromEpochMilliseconds(parcel.createdAt),
                updatedAt = Instant.fromEpochMilliseconds(parcel.updatedAt)
            )
        }
    }
}