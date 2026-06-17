package com.unifiedcomms.messaging

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.Conversation
import com.unifiedcomms.data.model.CalendarInviteMessage
import com.unifiedcomms.data.model.CalendarResponseMessage
import com.unifiedcomms.data.model.EmailShareMessage
import com.unifiedcomms.data.model.TaskShareMessage
import com.unifiedcomms.data.repository.MessagingRepository
import com.unifiedcomms.push.PushManager
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import javax.inject.Inject

class MessagingService @Inject constructor(
    private val repository: MessagingRepository,
    private val pushManager: PushManager,
    private val crypto: CryptoManager,
    private val scope: CoroutineScope
) : Service() {

    private val json = Json { ignoreUnknownKeys = true }
    private var currentUserId: String? = null

    override fun onCreate() {
        super.onCreate()
        // Initialize current user ID from preferences
        currentUserId = getSharedPreferences("unifiedcomms", MODE_PRIVATE)
            .getString("user_id", null)
    }

    override fun onBind(intent: Intent): IBinder {
        return MessagingBinder()
    }

    inner class MessagingBinder : IMessagingService.Stub() {
        @Throws(RemoteException::class)
        override fun sendMessage(messageJson: String): String {
            val result = CompletableDeferred<String>()
            scope.launch(Dispatchers.IO) {
                try {
                    val message = json.decodeFromString<Message>(messageJson)
                    val savedId = repository.insertMessage(message).await()
                    // Send push notification to recipient
                    sendPushForMessage(message)
                    result.complete(json.encodeToString(message.copy(id = savedId.toString())))
                } catch (e: Exception) {
                    result.completeExceptionally(e)
                }
            }
            return result.await()
        }

        @Throws(RemoteException::class)
        override fun getConversations(userId: String): String {
            // This would be a flow in real implementation
            return "[]"
        }

        @Throws(RemoteException::class)
        override fun markAsRead(messageIdsJson: String): Boolean {
            val ids = json.decodeFromString<List<String>>(messageIdsJson)
            scope.launch { repository.markMessagesRead(ids) }
            return true
        }
    }

    private suspend fun sendPushForMessage(message: Message) {
        val conversation = repository.getConversationById(message.conversationId).await()
        val recipient = conversation?.participantIds?.firstOrNull { it != currentUserId }
        recipient?.let { userId ->
            val pushTitle = when (message.messageType) {
                com.unifiedcomms.data.model.MessageType.CALENDAR_INVITE -> "New Calendar Invite"
                com.unifiedcomms.data.model.MessageType.CALENDAR_RESPONSE -> "Calendar Response"
                com.unifiedcomms.data.model.MessageType.EMAIL_SHARE -> "Shared Email"
                com.unifiedcomms.data.model.MessageType.TASK_SHARE -> "Shared Task"
                else -> "New Message"
            }
            pushManager.sendPush(userId, com.unifiedcomms.push.PushPayload(
                title = pushTitle,
                body = message.content.take(100),
                data = mapOf(
                    "message_id" to message.id,
                    "conversation_id" to message.conversationId,
                    "sender_id" to message.senderId,
                    "type" to message.messageType.name
                )
            ))
        }
    }

    // High-level messaging functions
    suspend fun sendTextMessage(conversationId: String, recipientId: String, text: String): Message {
        val message = Message(
            conversationId = conversationId,
            senderId = currentUserId!!,
            recipientId = recipientId,
            content = text,
            messageType = com.unifiedcomms.data.model.MessageType.TEXT
        )
        val id = repository.insertMessage(message).await()
        sendPushForMessage(message.copy(id = id.toString()))
        return message.copy(id = id.toString())
    }

    suspend fun sendCalendarInvite(conversationId: String, recipientId: String, invite: CalendarInviteMessage): Message {
        val content = json.encodeToString(invite)
        val message = Message(
            conversationId = conversationId,
            senderId = currentUserId!!,
            recipientId = recipientId,
            content = content,
            messageType = com.unifiedcomms.data.model.MessageType.CALENDAR_INVITE
        )
        val id = repository.insertMessage(message).await()
        sendPushForMessage(message.copy(id = id.toString()))
        return message.copy(id = id.toString())
    }

    suspend fun sendCalendarResponse(conversationId: String, recipientId: String, response: CalendarResponseMessage): Message {
        val content = json.encodeToString(response)
        val message = Message(
            conversationId = conversationId,
            senderId = currentUserId!!,
            recipientId = recipientId,
            content = content,
            messageType = com.unifiedcomms.data.model.MessageType.CALENDAR_RESPONSE
        )
        val id = repository.insertMessage(message).await()
        sendPushForMessage(message.copy(id = id.toString()))
        return message.copy(id = id.toString())
    }

    suspend fun sendEmailShare(conversationId: String, recipientId: String, emailShare: EmailShareMessage): Message {
        val content = json.encodeToString(emailShare)
        val message = Message(
            conversationId = conversationId,
            senderId = currentUserId!!,
            recipientId = recipientId,
            content = content,
            messageType = com.unifiedcomms.data.model.MessageType.EMAIL_SHARE
        )
        val id = repository.insertMessage(message).await()
        sendPushForMessage(message.copy(id = id.toString()))
        return message.copy(id = id.toString())
    }

    suspend fun sendTaskShare(conversationId: String, recipientId: String, taskShare: TaskShareMessage): Message {
        val content = json.encodeToString(taskShare)
        val message = Message(
            conversationId = conversationId,
            senderId = currentUserId!!,
            recipientId = recipientId,
            content = content,
            messageType = com.unifiedcomms.data.model.MessageType.TASK_SHARE
        )
        val id = repository.insertMessage(message).await()
        sendPushForMessage(message.copy(id = id.toString()))
        return message.copy(id = id.toString())
    }

    suspend fun getOrCreateConversation(participantIds: List<String>): Conversation {
        val allParticipants = (currentUserId?.let { listOf(it) } ?: emptyList()) + participantIds
        return repository.findDirectConversation(
            allParticipants,
            com.unifiedcomms.data.repository.ConversationType.DIRECT
        ).await() ?: createConversation(allParticipants)
    }

    private suspend fun createConversation(participantIds: List<String>): Conversation {
        val conversation = Conversation(
            participantIds = participantIds,
            participantNames = participantIds.associateWith { it }, // Would fetch from contacts
            type = com.unifiedcomms.data.model.ConversationType.DIRECT
        )
        val id = repository.insertConversation(conversation).await()
        return conversation.copy(id = id.toString())
    }

    // AIDL interface
    interface IMessagingService : android.os.IInterface {
        fun sendMessage(messageJson: String): String
        fun getConversations(userId: String): String
        fun markAsRead(messageIdsJson: String): Boolean

        companion object {
            fun asInterface(binder: IBinder): IMessagingService? {
                // Implementation would use AIDL
                return null
            }
        }
    }
}