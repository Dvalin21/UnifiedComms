package com.unifiedcomms.messaging

import android.app.Service
import android.content.Intent
import android.os.IBinder
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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class MessagingService : Service() {

    private val json = Json { ignoreUnknownKeys = true }
    private var currentUserId: String? = null
    private var repository: MessagingRepository? = null
    private var pushManager: PushManager? = null
    private var crypto: CryptoManager? = null
    private var scope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        // Initialize current user ID from preferences
        currentUserId = getSharedPreferences("unifiedcomms", MODE_PRIVATE)
            .getString("user_id", null)
        // Dependencies will be injected via manual DI
        // Initialize scope
        scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    }

    override fun onBind(intent: Intent): IBinder? {
        // No binding interface needed - not using AIDL
        return null
    }

    // Remove the LocalBinder inner class

    fun initialize(
        repository: MessagingRepository,
        pushManager: PushManager,
        crypto: CryptoManager,
        scope: CoroutineScope
    ) {
        this.repository = repository
        this.pushManager = pushManager
        this.crypto = crypto
        this.scope = scope
    }

    private suspend fun sendPushForMessage(message: Message) = withContext(Dispatchers.IO) {
        val repo = repository ?: return@withContext
        val push = pushManager ?: return@withContext
        val conversation = repo.getConversationById(message.conversationId)
        val recipient = conversation?.participantIds?.firstOrNull { it != currentUserId }
        recipient?.let { userId ->
            val pushTitle = when (message.messageType) {
                com.unifiedcomms.data.model.MessageType.CALENDAR_INVITE -> "New Calendar Invite"
                com.unifiedcomms.data.model.MessageType.CALENDAR_RESPONSE -> "Calendar Response"
                com.unifiedcomms.data.model.MessageType.EMAIL_SHARE -> "Shared Email"
                com.unifiedcomms.data.model.MessageType.TASK_SHARE -> "Shared Task"
                else -> "New Message"
            }
            push.sendPush(userId, com.unifiedcomms.push.PushPayload(
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
        val repo = repository ?: throw IllegalStateException("Repository not initialized")
        val message = Message(
            conversationId = conversationId,
            senderId = currentUserId!!,
            recipientId = recipientId,
            content = text,
            messageType = com.unifiedcomms.data.model.MessageType.TEXT
        )
        val id = repo.insertMessage(message)
        sendPushForMessage(message.copy(id = id.toString()))
        return message.copy(id = id.toString())
    }

    suspend fun sendCalendarInvite(conversationId: String, recipientId: String, invite: CalendarInviteMessage): Message {
        val repo = repository ?: throw IllegalStateException("Repository not initialized")
        val content = json.encodeToString(invite)
        val message = Message(
            conversationId = conversationId,
            senderId = currentUserId!!,
            recipientId = recipientId,
            content = content,
            messageType = com.unifiedcomms.data.model.MessageType.CALENDAR_INVITE
        )
        val id = repo.insertMessage(message)
        sendPushForMessage(message.copy(id = id.toString()))
        return message.copy(id = id.toString())
    }

    suspend fun sendCalendarResponse(conversationId: String, recipientId: String, response: CalendarResponseMessage): Message {
        val repo = repository ?: throw IllegalStateException("Repository not initialized")
        val content = json.encodeToString(response)
        val message = Message(
            conversationId = conversationId,
            senderId = currentUserId!!,
            recipientId = recipientId,
            content = content,
            messageType = com.unifiedcomms.data.model.MessageType.CALENDAR_RESPONSE
        )
        val id = repo.insertMessage(message)
        sendPushForMessage(message.copy(id = id.toString()))
        return message.copy(id = id.toString())
    }

    suspend fun sendEmailShare(conversationId: String, recipientId: String, emailShare: EmailShareMessage): Message {
        val repo = repository ?: throw IllegalStateException("Repository not initialized")
        val content = json.encodeToString(emailShare)
        val message = Message(
            conversationId = conversationId,
            senderId = currentUserId!!,
            recipientId = recipientId,
            content = content,
            messageType = com.unifiedcomms.data.model.MessageType.EMAIL_SHARE
        )
        val id = repo.insertMessage(message)
        sendPushForMessage(message.copy(id = id.toString()))
        return message.copy(id = id.toString())
    }

    suspend fun sendTaskShare(conversationId: String, recipientId: String, taskShare: TaskShareMessage): Message {
        val repo = repository ?: throw IllegalStateException("Repository not initialized")
        val content = json.encodeToString(taskShare)
        val message = Message(
            conversationId = conversationId,
            senderId = currentUserId!!,
            recipientId = recipientId,
            content = content,
            messageType = com.unifiedcomms.data.model.MessageType.TASK_SHARE
        )
        val id = repo.insertMessage(message)
        sendPushForMessage(message.copy(id = id.toString()))
        return message.copy(id = id.toString())
    }

    suspend fun getOrCreateConversation(participantIds: List<String>): Conversation {
        val repo = repository ?: throw IllegalStateException("Repository not initialized")
        val allParticipants = (currentUserId?.let { listOf(it) } ?: emptyList()) + participantIds
        return repo.findDirectConversation(
            allParticipants,
            com.unifiedcomms.data.model.ConversationType.DIRECT
        ) ?: createConversation(allParticipants)
    }

    private suspend fun createConversation(participantIds: List<String>): Conversation {
        val repo = repository ?: throw IllegalStateException("Repository not initialized")
        val conversation = Conversation(
            participantIds = participantIds,
            participantNames = participantIds.associateWith { it }, // Would fetch from contacts
            type = com.unifiedcomms.data.model.ConversationType.DIRECT
        )
        val id = repo.insertConversation(conversation)
        return conversation.copy(id = id.toString())
    }
}