package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Email
import kotlinx.coroutines.flow.Flow

interface EmailSyncEngine {
    suspend fun syncAccount(account: Account): SyncResult
    suspend fun syncFolder(account: Account, folder: String): SyncResult
    suspend fun fetchMessage(account: Account, folder: String, uid: String): Email?
    suspend fun sendEmail(account: Account, email: Email): SendResult
    suspend fun moveToFolder(account: Account, uids: List<String>, fromFolder: String, toFolder: String): SyncResult
    suspend fun deleteMessages(account: Account, folder: String, uids: List<String>): SyncResult
    fun observeSyncProgress(accountId: String): Flow<SyncProgress>
    suspend fun testConnection(account: Account): ConnectionTestResult
}

data class SyncResult(
    val success: Boolean,
    val itemsSynced: Int = 0,
    val itemsFailed: Int = 0,
    val errorMessage: String? = null,
    val newItems: List<String> = emptyList(),
    val updatedItems: List<String> = emptyList(),
    val deletedItems: List<String> = emptyList()
) {
    companion object {
        fun success(itemsSynced: Int = 0, newItems: List<String> = emptyList(), updatedItems: List<String> = emptyList(), deletedItems: List<String> = emptyList()): SyncResult =
            SyncResult(true, itemsSynced, 0, null, newItems, updatedItems, deletedItems)
        fun failure(error: String, itemsFailed: Int = 0): SyncResult = SyncResult(false, 0, itemsFailed, error)
    }
}

data class SendResult(
    val success: Boolean,
    val messageId: String? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun success(messageId: String): SendResult = SendResult(true, messageId)
        fun failure(error: String): SendResult = SendResult(false, errorMessage = error)
    }
}

data class SyncProgress(
    val accountId: String,
    val folder: String?,
    val stage: SyncStage,
    val current: Int,
    val total: Int,
    val bytesTransferred: Long = 0,
    val totalBytes: Long = 0
) {
    val percent: Float get() = if (total > 0) (current.toFloat() / total * 100) else 0f
}

enum class SyncStage {
    CONNECTING,
    AUTHENTICATING,
    LISTING_FOLDERS,
    FETCHING_HEADERS,
    FETCHING_BODIES,
    FETCHING_ATTACHMENTS,
    SYNCING_FLAGS,
    EXPUNGING,
    COMPLETED,
    ERROR
}

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long = 0,
    val capabilities: List<String> = emptyList(),
    val errorMessage: String? = null
)