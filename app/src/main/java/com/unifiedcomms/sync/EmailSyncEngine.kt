package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Email
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface EmailSyncEngine {
    val syncProgress: StateFlow<Map<String, SyncProgress>>
    suspend fun syncAccount(account: Account): SyncResult
    suspend fun syncFolder(account: Account, folder: String): SyncResult
    suspend fun fetchMessage(account: Account, folder: String, uid: String): Email?
    suspend fun sendEmail(account: Account, email: Email): SendResult
    suspend fun moveToFolder(account: Account, uids: List<String>, fromFolder: String, toFolder: String): SyncResult
    suspend fun deleteMessages(account: Account, folder: String, uids: List<String>): SyncResult
    fun observeSyncProgress(accountId: String): Flow<SyncProgress>
    suspend fun testConnection(account: Account): ConnectionTestResult
}