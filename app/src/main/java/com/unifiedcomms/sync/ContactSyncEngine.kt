package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.UnifiedContact
import kotlinx.coroutines.flow.Flow

interface ContactSyncEngine {
    suspend fun syncAccount(account: Account): SyncResult
    suspend fun fetchContact(account: Account, serverId: String): UnifiedContact?
    suspend fun createContact(account: Account, contact: UnifiedContact): CreateResult
    suspend fun updateContact(account: Account, contact: UnifiedContact): SyncResult
    suspend fun deleteContact(account: Account, serverId: String): SyncResult
    fun observeSyncProgress(accountId: String): Flow<SyncProgress>
    suspend fun testConnection(account: Account): ConnectionTestResult
}