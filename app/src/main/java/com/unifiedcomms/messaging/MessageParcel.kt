package com.unifiedcomms.messaging

import android.os.Parcel
import android.os.Parcelable
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.MessageType
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.data.model.MessageAttachment
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class MessageParcel(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val recipientId: String = "",
    val content: String = "",
    val messageType: Int = MessageType.TEXT.ordinal,
    val status: Int = MessageStatus.PENDING.ordinal,
    val replyToId: String? = null,
    val forwardFromId: String? = null,
    val attachmentsJson: String = "[]",
    val metadataJson: String = "{}",
    val isEncrypted: Boolean = true,
    val encryptionKeyId: String? = null,
    val sentAt: Long = 0,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val createdAt: Long = 0,
    val isLocalOnly: Boolean = false,
    val needsSync: Boolean = false
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        conversationId = parcel.readString() ?: "",
        senderId = parcel.readString() ?: "",
        recipientId = parcel.readString() ?: "",
        content = parcel.readString() ?: "",
        messageType = parcel.readInt(),
        status = parcel.readInt(),
        replyToId = parcel.readString(),
        forwardFromId = parcel.readString(),
        attachmentsJson = parcel.readString() ?: "[]",
        metadataJson = parcel.readString() ?: "{}",
        isEncrypted = parcel.readByte() != 0.toByte(),
        encryptionKeyId = parcel.readString(),
        sentAt = parcel.readLong(),
        deliveredAt = if (parcel.readByte() != 0.toByte()) parcel.readLong() else null,
        readAt = if (parcel.readByte() != 0.toByte()) parcel.readLong() else null,
        createdAt = parcel.readLong(),
        isLocalOnly = parcel.readByte() != 0.toByte(),
        needsSync = parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(conversationId)
        parcel.writeString(senderId)
        parcel.writeString(recipientId)
        parcel.writeString(content)
        parcel.writeInt(messageType)
        parcel.writeInt(status)
        parcel.writeString(replyToId)
        parcel.writeString(forwardFromId)
        parcel.writeString(attachmentsJson)
        parcel.writeString(metadataJson)
        parcel.writeByte(if (isEncrypted) 1 else 0)
        parcel.writeString(encryptionKeyId)
        parcel.writeLong(sentAt)
        parcel.writeByte(if (deliveredAt != null) 1 else 0)
        deliveredAt?.let { parcel.writeLong(it) }
        parcel.writeByte(if (readAt != null) 1 else 0)
        readAt?.let { parcel.writeLong(it) }
        parcel.writeLong(createdAt)
        parcel.writeByte(if (isLocalOnly) 1 else 0)
        parcel.writeByte(if (needsSync) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<MessageParcel> = object : Parcelable.Creator<MessageParcel> {
            override fun createFromParcel(parcel: Parcel): MessageParcel = MessageParcel(parcel)
            override fun newArray(size: Int): Array<MessageParcel?> = arrayOfNulls(size)
        }

        private val json = Json { ignoreUnknownKeys = true }

        fun fromMessage(message: Message): MessageParcel {
            return MessageParcel(
                id = message.id,
                conversationId = message.conversationId,
                senderId = message.senderId,
                recipientId = message.recipientId,
                content = message.content,
                messageType = message.messageType.ordinal,
                status = message.status.ordinal,
                replyToId = message.replyToId,
                forwardFromId = message.forwardFromId,
                attachmentsJson = json.encodeToString(message.attachments),
                metadataJson = json.encodeToString(message.metadata),
                isEncrypted = message.isEncrypted,
                encryptionKeyId = message.encryptionKeyId,
                sentAt = message.sentAt.epochMilliseconds,
                deliveredAt = message.deliveredAt?.epochMilliseconds,
                readAt = message.readAt?.epochMilliseconds,
                createdAt = message.createdAt.epochMilliseconds,
                isLocalOnly = message.isLocalOnly,
                needsSync = message.needsSync
            )
        }

        fun toMessage(parcel: MessageParcel): Message {
            val attachments = json.decodeFromString<List<MessageAttachment>>(parcel.attachmentsJson)
            val metadata = json.decodeFromString<Map<String, String>>(parcel.metadataJson)
            return Message(
                id = parcel.id,
                conversationId = parcel.conversationId,
                senderId = parcel.senderId,
                recipientId = parcel.recipientId,
                content = parcel.content,
                messageType = MessageType.values()[parcel.messageType],
                status = MessageStatus.values()[parcel.status],
                replyToId = parcel.replyToId,
                forwardFromId = parcel.forwardFromId,
                attachments = attachments,
                metadata = metadata,
                isEncrypted = parcel.isEncrypted,
                encryptionKeyId = parcel.encryptionKeyId,
                sentAt = Instant.fromEpochMilliseconds(parcel.sentAt),
                deliveredAt = parcel.deliveredAt?.let { Instant.fromEpochMilliseconds(it) },
                readAt = parcel.readAt?.let { Instant.fromEpochMilliseconds(it) },
                createdAt = Instant.fromEpochMilliseconds(parcel.createdAt),
                isLocalOnly = parcel.isLocalOnly,
                needsSync = parcel.needsSync
            )
        }
    }
}