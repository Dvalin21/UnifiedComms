package com.unifiedcomms.data.repository

import com.unifiedcomms.data.model.Conversation
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.data.model.MessageType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface MessagingRepository {
    suspend fun insertMessage(message: Message): Long
    suspend fun insertMessages(messages: List<Message>): List<Long>
    suspend fun updateMessage(message: Message): Int
    suspend fun updateMessages(messages: List<Message>): Int
    suspend fun deleteMessage(message: Message): Int
    suspend fun deleteMessageById(id: String): Int
    suspend fun getMessageById(id: String): Message?
    fun getMessagesByConversation(conversationId: String, limit: Int, offset: Int): Flow<List<Message>>
    suspend fun getLastMessage(conversationId: String): Message?
    fun getPendingMessages(conversationId: String, statuses: List<MessageStatus>): Flow<List<Message>>
    suspend fun getDirectMessages(senderId: String, recipientId: String, limit: Int): List<Message>
    fun searchMessages(query: String, limit: Int): Flow<List<Message>>
    fun getMessagesByType(conversationId: String, type: MessageType): Flow<List<Message>>
    suspend fun getMessagesNeedingSync(): List<Message>
    suspend fun getLocalOnlyMessages(): List<Message>
    suspend fun updateMessageStatus(id: String, status: MessageStatus): Int
    suspend fun markMessagesDelivered(messageIds: List<String>)
    suspend fun markMessagesRead(messageIds: List<String>)
    suspend fun markConversationRead(conversationId: String, currentUserId: String)

    // Conversations
    suspend fun insertConversation(conversation: Conversation): Long
    suspend fun insertConversations(conversations: List<Conversation>): List<Long>
    suspend fun updateConversation(conversation: Conversation): Int
    suspend fun deleteConversation(conversation: Conversation): Int
    suspend fun deleteConversationById(id: String): Int
    suspend fun getConversationById(id: String): Conversation?
    suspend fun getConversationsByIds(ids: List<String>): List<Conversation>
    fun getAllConversationsForUser(userId: String): Flow<List<Conversation>>
    fun getActiveConversationsForUser(userId: String): Flow<List<Conversation>>
    fun getArchivedConversationsForUser(userId: String): Flow<List<Conversation>>
    fun getPinnedConversationsForUser(userId: String): Flow<List<Conversation>>
    suspend fun findDirectConversation(participants: List<String>, type: com.unifiedcomms.data.model.ConversationType): Conversation?
    suspend fun getConversationsWithUnread(userId: String): List<Conversation>
    suspend fun getTotalUnreadCount(userId: String): Int
    suspend fun updateLastMessage(conversationId: String, message: Message, currentUserId: String)
    suspend fun togglePin(conversationId: String)
    suspend fun toggleArchive(conversationId: String)
    suspend fun toggleMute(conversationId: String, muteUntil: Instant?)
}

