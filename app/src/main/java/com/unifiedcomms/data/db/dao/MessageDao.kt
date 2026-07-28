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

    // ponytail: Message.conversationId now keys the search index only — searchMessages
    // is the real consumer. kept as a simple lookup helper.
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sentAt DESC LIMIT 1")
    suspend fun getLastForConversation(conversationId: String): Message?

    @Query("SELECT * FROM messages WHERE (senderId = :senderId AND recipientId = :recipientId) OR (senderId = :recipientId AND recipientId = :senderId) ORDER BY sentAt DESC LIMIT :limit")
    suspend fun getDirectMessages(senderId: String, recipientId: String, limit: Int): List<Message>

    @Query("SELECT * FROM messages WHERE content LIKE :query ORDER BY sentAt DESC LIMIT :limit")
    fun searchMessages(query: String, limit: Int): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE messageType = :type ORDER BY sentAt DESC")
    fun getByType(type: MessageType): Flow<List<Message>>

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

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND sentAt < :olderThan")
    suspend fun cleanupOldMessages(conversationId: String, olderThan: Long): Int
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

    @Transaction
    suspend fun mergeContacts(primaryId: String, secondaryIds: List<String>) {
        val primary = getById(primaryId) ?: return
        secondaryIds.forEach { secondaryId ->
            val secondary = getById(secondaryId) ?: return@forEach
            val merged = primary.copy(
                emails = (primary.emails + secondary.emails).distinct(),
                phoneNumbers = (primary.phoneNumbers + secondary.phoneNumbers).distinct(),
                addresses = (primary.addresses + secondary.addresses).distinct(),
                websites = (primary.websites + secondary.websites).distinct(),
                notes = "${primary.notes ?: ""}\n\n-- Merged from ${secondary.displayName} --\n${secondary.notes ?: ""}",
                tags = (primary.tags + secondary.tags).distinct()
            )
            update(merged)
            deleteById(secondaryId)
        }
    }
}
