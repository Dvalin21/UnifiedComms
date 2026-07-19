package com.unifiedcomms.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.Conversation
import com.unifiedcomms.data.model.UnifiedContact
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.data.model.MessageType
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<Message>): List<Long>

    @Update
    suspend fun update(message: Message): Int

    @Update
    suspend fun updateAll(messages: List<Message>): Int

    @Delete
    suspend fun delete(message: Message): Int

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): Message?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sentAt ASC LIMIT :limit OFFSET :offset")
    fun getByConversation(conversationId: String, limit: Int, offset: Int): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sentAt DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: String): Message?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND status IN (:statuses) ORDER BY sentAt ASC")
    fun getPendingMessages(conversationId: String, statuses: List<MessageStatus>): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE senderId = :senderId AND recipientId = :recipientId ORDER BY sentAt DESC LIMIT :limit")
    suspend fun getDirectMessages(senderId: String, recipientId: String, limit: Int): List<Message>

    @Query("SELECT * FROM messages WHERE content LIKE :query ORDER BY sentAt DESC LIMIT :limit")
    fun searchMessages(query: String, limit: Int): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE messageType = :type AND conversationId = :conversationId ORDER BY sentAt DESC")
    fun getByType(conversationId: String, type: MessageType): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE needsSync = 1")
    suspend fun getNeedingSync(): List<Message>

    @Query("SELECT * FROM messages WHERE isLocalOnly = 1")
    suspend fun getLocalOnly(): List<Message>

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: MessageStatus): Int

    @Transaction
    suspend fun markDelivered(messageIds: List<String>) {
        messageIds.forEach { id ->
            updateStatus(id, MessageStatus.DELIVERED)
        }
    }

    @Transaction
    suspend fun markRead(messageIds: List<String>) {
        messageIds.forEach { id ->
            val msg = getById(id)
            if (msg != null) {
                update(msg.copy(status = MessageStatus.READ, readAt = Clock.System.now()))
            }
        }
    }

    @Transaction
    suspend fun markConversationRead(conversationId: String, currentUserId: String) {
        val messages = getByConversation(conversationId, 1000, 0).first()
        val toMark = messages.filter { it.recipientId == currentUserId && it.status != MessageStatus.READ }
        for (msg in toMark) {
            markRead(listOf(msg.id))
        }
    }

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND sentAt < :olderThan")
    suspend fun cleanupOldMessages(conversationId: String, olderThan: Long): Int
}

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: Conversation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<Conversation>): List<Long>

    @Update
    suspend fun update(conversation: Conversation): Int

    @Delete
    suspend fun delete(conversation: Conversation): Int

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): Conversation?

    @Query("SELECT * FROM conversations WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Conversation>

    // ponytail: participantIds is a JSON-array STRING column (StringListConverter), so
    // `:userId IN (participantIds)` compares the whole blob and never matches a real id.
    // Match by JSON containment instead. Format is ["id1","id2"], so LIKE '%"userId"%' is exact.
    @Query("SELECT * FROM conversations WHERE participantIds LIKE '%' || :userId || '%' ORDER BY isPinned DESC, lastActivityAt DESC")
    fun getAllForUser(userId: String): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE participantIds LIKE '%' || :userId || '%' AND isArchived = 0 ORDER BY isPinned DESC, lastActivityAt DESC")
    fun getActiveForUser(userId: String): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE participantIds LIKE '%' || :userId || '%' AND isArchived = 1 ORDER BY lastActivityAt DESC")
    fun getArchivedForUser(userId: String): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE participantIds LIKE '%' || :userId || '%' AND isPinned = 1 ORDER BY lastActivityAt DESC")
    fun getPinnedForUser(userId: String): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE (participantIds = :asc OR participantIds = :desc) AND type = :type")
    suspend fun findDirectConversation(asc: List<String>, desc: List<String>, type: com.unifiedcomms.data.model.ConversationType): Conversation?

    @Query("SELECT * FROM conversations WHERE unreadCount > 0 AND participantIds LIKE '%' || :userId || '%' ORDER BY lastActivityAt DESC")
    suspend fun getWithUnread(userId: String): List<Conversation>

    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM conversations WHERE participantIds LIKE '%' || :userId || '%'")
    suspend fun getTotalUnreadCount(userId: String): Int

    @Transaction
    suspend fun updateLastMessage(conversationId: String, message: Message, currentUserId: String) {
        getById(conversationId)?.let { conv ->
            val isIncoming = message.recipientId == currentUserId
            val newUnread = if (isIncoming && message.status != MessageStatus.READ) conv.unreadCount + 1 else conv.unreadCount
            update(conv.copy(
                lastMessageId = message.id,
                lastMessagePreview = message.content.take(100),
                lastActivityAt = message.sentAt,
                unreadCount = newUnread,
                updatedAt = Clock.System.now()
            ))
        }
    }

    @Transaction
    suspend fun markConversationRead(conversationId: String, currentUserId: String) {
        getById(conversationId)?.let { conv ->
            update(conv.copy(unreadCount = 0, updatedAt = Clock.System.now()))
        }
    }

    @Transaction
    suspend fun togglePin(conversationId: String) {
        getById(conversationId)?.let { conv ->
            update(conv.copy(isPinned = !conv.isPinned, updatedAt = Clock.System.now()))
        }
    }

    @Transaction
    suspend fun toggleArchive(conversationId: String) {
        getById(conversationId)?.let { conv ->
            update(conv.copy(isArchived = !conv.isArchived, updatedAt = Clock.System.now()))
        }
    }

    @Transaction
    suspend fun toggleMute(conversationId: String, muteUntil: Instant? = null) {
        getById(conversationId)?.let { conv ->
            update(conv.copy(isMuted = !conv.isMuted, muteUntil = muteUntil, updatedAt = Clock.System.now()))
        }
    }
}

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: UnifiedContact): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<UnifiedContact>): List<Long>

    @Update
    suspend fun update(contact: UnifiedContact): Int

    @Delete
    suspend fun delete(contact: UnifiedContact): Int

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: String): UnifiedContact?

    @Query("SELECT * FROM contacts WHERE unifiedCommsId = :id")
    suspend fun getByUnifiedCommsId(id: String): UnifiedContact?

    @Query("SELECT * FROM contacts WHERE EXISTS (SELECT 1 FROM json_each(contacts.emails) WHERE value = :email)")
    suspend fun getByEmail(email: String): UnifiedContact?

    @Query("SELECT * FROM contacts WHERE EXISTS (SELECT 1 FROM json_each(contacts.phoneNumbers) WHERE value = :phone)")
    suspend fun getByPhone(phone: String): UnifiedContact?

    @Query("SELECT * FROM contacts WHERE accountId = :accountId ORDER BY displayName ASC")
    fun getByAccount(accountId: String): Flow<List<UnifiedContact>>

    @Query("SELECT * FROM contacts WHERE source = :source AND accountId = :accountId ORDER BY displayName ASC")
    fun getBySourceAndAccount(source: com.unifiedcomms.data.model.ContactSource, accountId: String): Flow<List<UnifiedContact>>

    @Query("SELECT * FROM contacts WHERE unifiedCommsId IS NOT NULL ORDER BY displayName ASC")
    fun getUnifiedCommsContacts(): Flow<List<UnifiedContact>>

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 ORDER BY displayName ASC")
    fun getFavorites(): Flow<List<UnifiedContact>>

    @Query("SELECT * FROM contacts WHERE (displayName LIKE :query OR EXISTS (SELECT 1 FROM json_each(contacts.emails) WHERE value = :query) OR EXISTS (SELECT 1 FROM json_each(contacts.phoneNumbers) WHERE value = :query)) LIMIT :limit")
    fun search(query: String, limit: Int): Flow<List<UnifiedContact>>

    @Query("SELECT * FROM contacts WHERE needsSync = 1")
    suspend fun getNeedingSync(): List<UnifiedContact>

    @Query("SELECT * FROM contacts WHERE accountId = :accountId AND sourceId = :sourceId LIMIT 1")
    suspend fun getBySourceId(accountId: String, sourceId: String): UnifiedContact?

    @Query("SELECT * FROM contacts WHERE accountId = :accountId AND source = :source")
    suspend fun getAllByAccountAndSource(accountId: String, source: com.unifiedcomms.data.model.ContactSource): List<UnifiedContact>
}