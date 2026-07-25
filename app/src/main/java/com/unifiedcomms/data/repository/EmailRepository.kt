package com.unifiedcomms.data.repository

import com.unifiedcomms.data.model.Email
import com.unifiedcomms.data.model.EmailFlags
import com.unifiedcomms.data.model.SystemLabels
import kotlinx.coroutines.flow.Flow

interface EmailRepository {
    suspend fun insert(email: Email): Long
    suspend fun insertAll(emails: List<Email>): List<Long>
    suspend fun update(email: Email): Int
    suspend fun updateAll(emails: List<Email>): Int
    suspend fun delete(email: Email): Int
    suspend fun deleteById(id: String): Int
    suspend fun getById(id: String): Email?
    suspend fun getByUid(accountId: String, uid: String, folder: String): Email?
    suspend fun getByImapUid(accountId: String, imapUid: String, folder: String): Email?
    // ponytail: lightweight lookup for the sync update path (no bodyText) so we
    // never overflow the CursorWindow on large folders.
    suspend fun getSyncKeyByImapUid(accountId: String, imapUid: String, folder: String): com.unifiedcomms.data.db.dao.EmailSyncKey?
    suspend fun getSyncKeyByUid(accountId: String, uid: String, folder: String): com.unifiedcomms.data.db.dao.EmailSyncKey?
    // ponytail: targeted merge update that never reads the (possibly huge) row.
    suspend fun updateSyncMeta(
        id: String,
        flags: EmailFlags,
        labels: List<String>,
        systemLabels: SystemLabels,
        etag: String,
        updatedAt: Long,
        messageId: String,
        subject: String,
        bodyText: String?,
        bodyHtml: String?,
        preview: String?
    )
    suspend fun getByFolderAndUidValidity(accountId: String, folder: String, uidValidity: String): List<Email>
    // ponytail: exists-check only (no bodyText) to avoid CursorWindow overflow.
    suspend fun countByFolderAndUidValidity(accountId: String, folder: String, uidValidity: String): Int
    suspend fun getByMessageId(messageId: String): Email?
    fun getByThreadId(threadId: String): Flow<List<Email>>
    fun getByAccountAndFolder(accountId: String, folder: String, limit: Int, offset: Int): Flow<List<Email>>
    fun getUnreadByAccountAndFolder(accountId: String, folder: String): Flow<List<Email>>
    suspend fun getUnreadCount(accountId: String, folder: String): Int
    fun getFlagged(accountId: String): Flow<List<Email>>
    fun getDrafts(accountId: String): Flow<List<Email>>
    fun getSent(accountId: String, limit: Int): Flow<List<Email>>
    fun getUnifiedInbox(accountIds: List<String>, folders: List<String>, limit: Int): Flow<List<Email>>
    fun getUnifiedUnread(accountIds: List<String>, limit: Int): Flow<List<Email>>
    fun searchEmails(query: String, accountIds: List<String>, limit: Int): Flow<List<Email>>
    fun getWithAttachments(accountId: String, limit: Int): Flow<List<Email>>
    fun getSince(accountId: String, since: Long): Flow<List<Email>>
    suspend fun getCount(accountId: String, folder: String): Int
    suspend fun getTotalCount(accountId: String): Long
    suspend fun deleteByAccountAndFolder(accountId: String, folder: String): Int
    suspend fun markAsRead(emailIds: List<String>)
    suspend fun markAsUnread(emailIds: List<String>)
    suspend fun flag(emailIds: List<String>, flagged: Boolean)
    suspend fun moveToFolder(emailIds: List<String>, newFolder: String)
    suspend fun deletePermanently(emailIds: List<String>)
    suspend fun cleanupOldTrashAndSpam(accountId: String, olderThan: Long): Int
}