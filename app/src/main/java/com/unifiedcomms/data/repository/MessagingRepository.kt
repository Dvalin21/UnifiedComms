package com.unifiedcomms.data.repository

import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.data.model.MessageType
import kotlinx.coroutines.flow.Flow

interface MessagingRepository {
    suspend fun insertMessage(message: Message): Long
    suspend fun insertMessages(messages: List<Message>): List<Long>
    suspend fun updateMessage(message: Message): Int
    suspend fun updateMessages(messages: List<Message>): Int
    suspend fun deleteMessage(message: Message): Int
    suspend fun deleteMessageById(id: String): Int
    suspend fun getMessageById(id: String): Message?
    suspend fun getLastMessage(conversationId: String): Message?
    suspend fun getDirectMessages(senderId: String, recipientId: String, limit: Int): List<Message>
    fun searchMessages(query: String, limit: Int): Flow<List<Message>>
    fun getMessagesByType(type: MessageType): Flow<List<Message>>
    suspend fun getMessagesNeedingSync(): List<Message>
    suspend fun getLocalOnlyMessages(): List<Message>
    suspend fun updateMessageStatus(id: String, status: MessageStatus): Int
    suspend fun markMessagesDelivered(messageIds: List<String>)
    suspend fun markMessagesRead(messageIds: List<String>)
}
