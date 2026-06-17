package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.EmailDao
import com.unifiedcomms.data.model.Email
import kotlinx.coroutines.flow.Flow

class EmailRepositoryImpl(private val dao: EmailDao) : EmailRepository {
    override suspend fun insert(email: Email): Long = dao.insert(email)

    override suspend fun insertAll(emails: List<Email>): List<Long> = dao.insertAll(emails)

    override suspend fun update(email: Email): Int = dao.update(email)

    override suspend fun updateAll(emails: List<Email>): Int = dao.updateAll(emails)

    override suspend fun delete(email: Email): Int = dao.delete(email)

    override suspend fun deleteById(id: String): Int = dao.deleteById(id)

    override suspend fun getById(id: String): Email? = dao.getById(id)

    override suspend fun getByUid(accountId: String, uid: String, folder: String): Email? =
        dao.getByUid(accountId, uid, folder)

    override suspend fun getByMessageId(messageId: String): Email? = dao.getByMessageId(messageId)

    override fun getByThreadId(threadId: String): Flow<List<Email>> = dao.getByThreadId(threadId)

    override fun getByAccountAndFolder(accountId: String, folder: String, limit: Int, offset: Int): Flow<List<Email>> =
        dao.getByAccountAndFolder(accountId, folder, limit, offset)

    override fun getUnreadByAccountAndFolder(accountId: String, folder: String): Flow<List<Email>> =
        dao.getUnreadByAccountAndFolder(accountId, folder)

    override suspend fun getUnreadCount(accountId: String, folder: String): Int =
        dao.getUnreadCount(accountId, folder)

    override fun getFlagged(accountId: String): Flow<List<Email>> = dao.getFlagged(accountId)

    override fun getDrafts(accountId: String): Flow<List<Email>> = dao.getDrafts(accountId)

    override fun getSent(accountId: String, limit: Int): Flow<List<Email>> = dao.getSent(accountId, limit)

    override fun getUnifiedInbox(accountIds: List<String>, folders: List<String>, limit: Int): Flow<List<Email>> =
        dao.getUnifiedInbox(accountIds, folders, limit)

    override fun getUnifiedUnread(accountIds: List<String>, limit: Int): Flow<List<Email>> =
        dao.getUnifiedUnread(accountIds, limit)

    override fun searchEmails(query: String, accountIds: List<String>, limit: Int): Flow<List<Email>> =
        dao.searchEmails("%$query%", accountIds, limit)

    override fun getWithAttachments(accountId: String, limit: Int): Flow<List<Email>> =
        dao.getWithAttachments(accountId, limit)

    override fun getSince(accountId: String, since: Long): Flow<List<Email>> =
        dao.getSince(accountId, since)

    override suspend fun getCount(accountId: String, folder: String): Int = dao.getCount(accountId, folder)

    override suspend fun getTotalCount(accountId: String): Long = dao.getTotalCount(accountId)

    override suspend fun markAsRead(emailIds: List<String>) = dao.markAsRead(emailIds)

    override suspend fun markAsUnread(emailIds: List<String>) = dao.markAsUnread(emailIds)

    override suspend fun flag(emailIds: List<String>, flagged: Boolean) = dao.flag(emailIds, flagged)

    override suspend fun moveToFolder(emailIds: List<String>, newFolder: String) = dao.moveToFolder(emailIds, newFolder)

    override suspend fun deletePermanently(emailIds: List<String>) = dao.deletePermanently(emailIds)

    override suspend fun cleanupOldTrashAndSpam(accountId: String, olderThan: Long): Int =
        dao.cleanupOldTrashAndSpam(accountId, olderThan)
}