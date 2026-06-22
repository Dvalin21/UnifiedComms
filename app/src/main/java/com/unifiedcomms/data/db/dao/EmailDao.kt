package com.unifiedcomms.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import com.unifiedcomms.data.model.Email
import com.unifiedcomms.data.model.EmailFlags
import com.unifiedcomms.data.model.SystemLabels

@Dao
interface EmailDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(email: Email): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(emails: List<Email>): List<Long>

    @Update
    suspend fun update(email: Email): Int

    @Update
    suspend fun updateAll(emails: List<Email>): Int

    @Delete
    suspend fun delete(email: Email): Int

    @Query("DELETE FROM emails WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM emails WHERE accountId = :accountId AND folder = :folder")
    suspend fun deleteByAccountAndFolder(accountId: String, folder: String): Int

    @Query("SELECT * FROM emails WHERE id = :id")
    suspend fun getById(id: String): Email?

    @Query("SELECT * FROM emails WHERE accountId = :accountId AND uid = :uid AND folder = :folder")
    suspend fun getByUid(accountId: String, uid: String, folder: String): Email?

    @Query("SELECT * FROM emails WHERE messageId = :messageId")
    suspend fun getByMessageId(messageId: String): Email?

    @Query("SELECT * FROM emails WHERE threadId = :threadId ORDER BY receivedAt DESC")
    fun getByThreadId(threadId: String): Flow<List<Email>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId AND folder = :folder ORDER BY receivedAt DESC LIMIT :limit OFFSET :offset")
    fun getByAccountAndFolder(accountId: String, folder: String, limit: Int, offset: Int): Flow<List<Email>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId AND folder = :folder ORDER BY receivedAt DESC")
    fun getUnreadByAccountAndFolder(accountId: String, folder: String): Flow<List<Email>>

    @Query("SELECT COUNT(*) FROM emails WHERE accountId = :accountId AND folder = :folder")
    suspend fun getUnreadCount(accountId: String, folder: String): Int

    @Query("SELECT * FROM emails WHERE accountId = :accountId ORDER BY receivedAt DESC")
    fun getFlagged(accountId: String): Flow<List<Email>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId ORDER BY updatedAt DESC")
    fun getDrafts(accountId: String): Flow<List<Email>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId AND sentAt IS NOT NULL ORDER BY sentAt DESC LIMIT :limit")
    fun getSent(accountId: String, limit: Int): Flow<List<Email>>

    @Query("SELECT * FROM emails WHERE accountId IN (:accountIds) AND folder IN (:folders) ORDER BY receivedAt DESC LIMIT :limit")
    fun getUnifiedInbox(accountIds: List<String>, folders: List<String>, limit: Int): Flow<List<Email>>

    @Query("SELECT * FROM emails WHERE accountId IN (:accountIds) AND folder IN ('INBOX', 'Inbox', 'inbox') ORDER BY receivedAt DESC LIMIT :limit")
    fun getUnifiedUnread(accountIds: List<String>, limit: Int): Flow<List<Email>>

    @Query("SELECT * FROM emails WHERE (subject LIKE :query OR sender LIKE :query OR bodyText LIKE :query) AND accountId IN (:accountIds) ORDER BY receivedAt DESC LIMIT :limit")
    fun searchEmails(query: String, accountIds: List<String>, limit: Int): Flow<List<Email>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId ORDER BY receivedAt DESC LIMIT :limit")
    fun getWithAttachments(accountId: String, limit: Int): Flow<List<Email>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId AND receivedAt > :since ORDER BY receivedAt DESC")
    fun getSince(accountId: String, since: Long): Flow<List<Email>>

    @Query("SELECT COUNT(*) FROM emails WHERE accountId = :accountId AND folder = :folder")
    suspend fun getCount(accountId: String, folder: String): Int

    @Query("SELECT COUNT(*) FROM emails WHERE accountId = :accountId")
    suspend fun getTotalCount(accountId: String): Long

    @Transaction
    suspend fun markAsRead(emailIds: List<String>) {
        emailIds.forEach { id ->
            getById(id)?.let { email ->
                update(email.copy(flags = email.flags.copy(isRead = true)))
            }
        }
    }

    @Transaction
    suspend fun markAsUnread(emailIds: List<String>) {
        emailIds.forEach { id ->
            getById(id)?.let { email ->
                update(email.copy(flags = email.flags.copy(isRead = false)))
            }
        }
    }

    @Transaction
    suspend fun flag(emailIds: List<String>, flagged: Boolean) {
        emailIds.forEach { id ->
            getById(id)?.let { email ->
                update(email.copy(flags = email.flags.copy(isFlagged = flagged)))
            }
        }
    }

    @Transaction
    suspend fun moveToFolder(emailIds: List<String>, newFolder: String) {
        emailIds.forEach { id ->
            getById(id)?.let { email ->
                val newLabels = email.systemLabels.copy(
                    inbox = newFolder == "INBOX",
                    sent = newFolder == "Sent",
                    draft = newFolder == "Drafts",
                    trash = newFolder == "Trash",
                    spam = newFolder == "Spam"
                )
                update(email.copy(folder = newFolder, systemLabels = newLabels, labels = newLabels.categorized.keys.toList()))
            }
        }
    }

    @Transaction
    suspend fun deletePermanently(emailIds: List<String>) {
        emailIds.forEach { deleteById(it) }
    }

    @Query("DELETE FROM emails WHERE accountId = :accountId AND receivedAt < :olderThan AND folder IN ('Trash', 'Spam')")
    suspend fun cleanupOldTrashAndSpam(accountId: String, olderThan: Long): Int

    @RawQuery(observedEntities = [Email::class])
    fun rawQuery(query: SupportSQLiteQuery): Flow<List<Email>>
}