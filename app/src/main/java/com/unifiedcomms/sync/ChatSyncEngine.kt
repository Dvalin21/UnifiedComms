package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ChatSyncEngine {
    suspend fun syncAccount(account: Account): SyncResult
    suspend fun syncFolder(account: Account, folder: String): SyncResult
    suspend fun sendChatMessage(account: Account, conversation: Conversation, message: Message): SendResult
    suspend fun testConnection(account: Account): ConnectionTestResult
    fun observeSyncProgress(accountId: String): Flow<SyncProgress>
}

data class ChatSyncResult(
    val conversation: Conversation,
    val message: Message,
    val action: ChatSyncAction
) {
    enum class ChatSyncAction { CREATED_CONVERSATION, NEW_MESSAGE, UPDATED_STATUS }
}
