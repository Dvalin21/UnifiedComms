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
    suspend fun markAsRead(emailIds: List<String>)
    suspend fun markAsUnread(emailIds: List<String>)
    suspend fun flag(emailIds: List<String>, flagged: Boolean)
    suspend fun moveToFolder(emailIds: List<String>, newFolder: String)
    suspend fun deletePermanently(emailIds: List<String>)
    suspend fun cleanupOldTrashAndSpam(accountId: String, olderThan: Long): Int
}