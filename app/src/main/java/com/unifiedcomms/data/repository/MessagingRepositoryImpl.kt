package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.MessageDao
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.data.model.MessageType
import kotlinx.coroutines.flow.Flow

class MessagingRepositoryImpl(
    private val msgDao: MessageDao
) : MessagingRepository {
    override suspend fun insertMessage(message: Message): Long = msgDao.insert(message)
    override suspend fun insertMessages(messages: List<Message>): List<Long> = msgDao.insertAll(messages)
    override suspend fun updateMessage(message: Message): Int = msgDao.update(message)
    override suspend fun updateMessages(messages: List<Message>): Int = msgDao.updateAll(messages)
    override suspend fun deleteMessage(message: Message): Int = msgDao.delete(message)
    override suspend fun deleteMessageById(id: String): Int = msgDao.deleteById(id)
    override suspend fun getMessageById(id: String): Message? = msgDao.getById(id)

    // ponytail: conversationId is now a search-key only; getLastMessage is a thin lookup.
    override suspend fun getLastMessage(conversationId: String): Message? =
        msgDao.getLastForConversation(conversationId)

    override suspend fun getDirectMessages(senderId: String, recipientId: String, limit: Int): List<Message> =
        msgDao.getDirectMessages(senderId, recipientId, limit)

    override fun searchMessages(query: String, limit: Int): Flow<List<Message>> =
        msgDao.searchMessages("%$query%", limit)

    override fun getMessagesByType(type: MessageType): Flow<List<Message>> =
        msgDao.getByType(type)

    override suspend fun getMessagesNeedingSync(): List<Message> = msgDao.getNeedingSync()
    override suspend fun getLocalOnlyMessages(): List<Message> = msgDao.getLocalOnly()
    override suspend fun updateMessageStatus(id: String, status: MessageStatus): Int = msgDao.updateStatus(id, status)
    override suspend fun markMessagesDelivered(messageIds: List<String>) = msgDao.markDelivered(messageIds)
    override suspend fun markMessagesRead(messageIds: List<String>) = msgDao.markRead(messageIds)
}
