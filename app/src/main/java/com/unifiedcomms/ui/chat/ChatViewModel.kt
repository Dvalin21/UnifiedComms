package com.unifiedcomms.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedcomms.data.db.dao.MessageDao
import com.unifiedcomms.data.e2ee.ChatSyncManager
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.util.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatConversation(
    val peerPhone: String,
    val peerName: String,
    val lastMessage: String,
    val lastTs: Long,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
)

data class ChatMessage(
    val id: String,
    val peerPhone: String,
    val senderId: String,
    val content: String,
    val sentAt: Long,
    val status: MessageStatus = MessageStatus.SENT,
)

class ChatViewModel(
    private val messageDao: MessageDao,
    private val chatSyncManager: ChatSyncManager? = null,
) : ViewModel() {

    private val currentUserId: String
        get() = PreferencesManager.getInstance().getString("current_user_id", "current_user")

    val conversations: StateFlow<List<ChatConversation>> = messageDao.getAllMessages()
        .map { messages -> buildConversations(messages) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun messagesForPeer(peerPhone: String): StateFlow<List<ChatMessage>> {
        return flow {
            val messages = messageDao.getDirectMessages(currentUserId, peerPhone, 200)
            emit(messages)
        }
            .map { messages ->
                messages.map { msg ->
                    ChatMessage(
                        id = msg.id,
                        peerPhone = if (msg.senderId == currentUserId) peerPhone else msg.senderId,
                        senderId = msg.senderId,
                        content = msg.content,
                        sentAt = msg.sentAt.toEpochMilliseconds(),
                        status = msg.status,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun sendMessage(peerPhone: String, plaintext: String) {
        if (chatSyncManager == null) return
        viewModelScope.launch {
            chatSyncManager.sendMessage(peerPhone, plaintext, System.currentTimeMillis())
        }
    }

    fun refresh() {
        chatSyncManager?.let { sync ->
            if (!sync.isRunning()) sync.start()
        }
    }

    private fun buildConversations(messages: List<Message>): List<ChatConversation> {
        val grouped = messages
            .filter { it.senderId != currentUserId && it.recipientId == currentUserId ||
                     it.senderId == currentUserId && it.recipientId != currentUserId }
            .groupBy { msg ->
                if (msg.senderId == currentUserId) msg.recipientId else msg.senderId
            }

        return grouped.map { (peerPhone, msgs) ->
            val sorted = msgs.sortedByDescending { it.sentAt }
            val lastMsg = sorted.firstOrNull()
            val unread = msgs.count { it.senderId != currentUserId && it.status != MessageStatus.READ }

            ChatConversation(
                peerPhone = peerPhone,
                peerName = extractPeerName(peerPhone, msgs),
                lastMessage = lastMsg?.content?.take(80) ?: "",
                lastTs = lastMsg?.sentAt?.toEpochMilliseconds() ?: 0L,
                unreadCount = unread,
            )
        }.sortedByDescending { it.lastTs }
    }

    private fun extractPeerName(peerPhone: String, messages: List<Message>): String {
        return peerPhone.take(20)
    }
}
