package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.ConversationDao
import com.unifiedcomms.data.db.dao.MessageDao
import com.unifiedcomms.data.model.Conversation
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.data.model.MessageType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

class MessagingRepositoryImpl(
    private val msgDao: MessageDao,
    private val convDao: ConversationDao
) : MessagingRepository {
    // Messages
    override suspend fun insertMessage(message: Message): Long = msgDao.insert(message)

    override suspend fun insertMessages(messages: List<Message>): List<Long> = msgDao.insertAll(messages)

    override suspend fun updateMessage(message: Message): Int = msgDao.update(message)

    override suspend fun updateMessages(messages: List<Message>): Int = msgDao.updateAll(messages)

    override suspend fun deleteMessage(message: Message): Int = msgDao.delete(message)

    override suspend fun deleteMessageById(id: String): Int = msgDao.deleteById(id)

    override suspend fun getMessageById(id: String): Message? = msgDao.getById(id)

    override fun getMessagesByConversation(conversationId: String, limit: Int, offset: Int): Flow<List<Message>> =
        msgDao.getByConversation(conversationId, limit, offset)

    override suspend fun getLastMessage(conversationId: String): Message? = msgDao.getLastMessage(conversationId)

    override fun getPendingMessages(conversationId: String, statuses: List<MessageStatus>): Flow<List<Message>> =
        msgDao.getPendingMessages(conversationId, statuses)

    override suspend fun getDirectMessages(senderId: String, recipientId: String, limit: Int): List<Message> =
        msgDao.getDirectMessages(senderId, recipientId, limit)

    override fun searchMessages(query: String, conversationIds: List<String>, limit: Int): Flow<List<Message>> =
        msgDao.searchMessages("%$query%", conversationIds, limit)

    override fun getMessagesByType(conversationId: String, type: MessageType): Flow<List<Message>> =
        msgDao.getByType(conversationId, type)

    override suspend fun getMessagesNeedingSync(): List<Message> = msgDao.getNeedingSync()

    override suspend fun getLocalOnlyMessages(): List<Message> = msgDao.getLocalOnly()

    override suspend fun updateMessageStatus(id: String, status: MessageStatus): Int = msgDao.updateStatus(id, status)

    override suspend fun markMessagesDelivered(messageIds: List<String>) = msgDao.markDelivered(messageIds)

    override suspend fun markMessagesRead(messageIds: List<String>) = msgDao.markRead(messageIds)

    override suspend fun markConversationRead(conversationId: String, currentUserId: String) =
        msgDao.markConversationRead(conversationId, currentUserId)

    override suspend fun cleanupOldMessages(conversationId: String, olderThan: Long): Int =
        msgDao.cleanupOldMessages(conversationId, olderThan)

    // Conversations
    override suspend fun insertConversation(conversation: Conversation): Long = convDao.insert(conversation)

    override suspend fun insertConversations(conversations: List<Conversation>): List<Long> = convDao.insertAll(conversations)

    override suspend fun updateConversation(conversation: Conversation): Int = convDao.update(conversation)

    override suspend fun deleteConversation(conversation: Conversation): Int = convDao.delete(conversation)

    override suspend fun deleteConversationById(id: String): Int = convDao.deleteById(id)

    override suspend fun getConversationById(id: String): Conversation? = convDao.getById(id)

    override suspend fun getConversationsByIds(ids: List<String>): List<Conversation> = convDao.getByIds(ids)

    override fun getAllConversationsForUser(userId: String): Flow<List<Conversation>> = convDao.getAllForUser(userId)

    override fun getActiveConversationsForUser(userId: String): Flow<List<Conversation>> = convDao.getActiveForUser(userId)

    override fun getArchivedConversationsForUser(userId: String): Flow<List<Conversation>> = convDao.getArchivedForUser(userId)

    override fun getPinnedConversationsForUser(userId: String): Flow<List<Conversation>> = convDao.getPinnedForUser(userId)

    override suspend fun findDirectConversation(participants: List<String>, type: ConversationType): Conversation? =
        convDao.findDirectConversation(participants, com.unifiedcomms.data.model.ConversationType.valueOf(type.name))

    override suspend fun getConversationsWithUnread(userId: String): List<Conversation> = convDao.getWithUnread(userId)

    override suspend fun getTotalUnreadCount(userId: String): Int = convDao.getTotalUnreadCount(userId)

    override suspend fun updateLastMessage(conversationId: String, message: Message, currentUserId: String) =
        convDao.updateLastMessage(conversationId, message, currentUserId)

    override suspend fun markConversationRead(conversationId: String, currentUserId: String) =
        convDao.markConversationRead(conversationId, currentUserId)

    override suspend fun togglePin(conversationId: String) = convDao.togglePin(conversationId)

    override suspend fun toggleArchive(conversationId: String) = convDao.toggleArchive(conversationId)

    override suspend fun toggleMute(conversationId: String, muteUntil: Instant?) = convDao.toggleMute(conversationId, muteUntil)
}