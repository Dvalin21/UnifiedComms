package com.unifiedcomms.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedcomms.UnifiedCommsApplication
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.ContactRepository
import com.unifiedcomms.data.repository.ContactRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepository
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.data.repository.MessagingRepository
import com.unifiedcomms.data.repository.MessagingRepositoryImpl
import com.unifiedcomms.data.repository.TaskRepository
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import com.unifiedcomms.sync.ContactSyncEngineImpl
import com.unifiedcomms.sync.EmailSyncEngineImpl
import com.unifiedcomms.sync.SendResult
import com.unifiedcomms.sync.SyncManager
import com.unifiedcomms.sync.SyncResult
import com.unifiedcomms.sync.TaskSyncEngineImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(
    private val app: UnifiedCommsApplication = UnifiedCommsApplication.getInstance()
) : ViewModel() {

    private val accountRepo: AccountRepository = AccountRepositoryImpl(app.database.accountDao(), com.unifiedcomms.security.CryptoManagerImpl(app))
    private val emailRepo: EmailRepository = EmailRepositoryImpl(app.database.emailDao())
    private val calendarRepo: CalendarRepository = CalendarRepositoryImpl(
        app.database.calendarEventDao(),
        app.database.calendarDao()
    )
    private val taskRepo: TaskRepository = TaskRepositoryImpl(
        app.database.taskDao(),
        app.database.taskListDao()
    )
    private val messagingRepo: MessagingRepository = MessagingRepositoryImpl(
        app.database.messageDao(),
        app.database.conversationDao()
    )
    private val contactRepo: ContactRepository = ContactRepositoryImpl(app.database.contactDao())
    private val crypto = com.unifiedcomms.security.CryptoManagerImpl(app)
    private val syncManager: SyncManager = SyncManager(
        EmailSyncEngineImpl(emailRepo, accountRepo, crypto, viewModelScope),
        CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, viewModelScope),
        TaskSyncEngineImpl(taskRepo, accountRepo, crypto, viewModelScope),
        ContactSyncEngineImpl(contactRepo, accountRepo, crypto, viewModelScope),
        accountRepo,
        viewModelScope,
        app,
        crypto
    )

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts

    private val _pendingTab = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    val pendingTab: kotlinx.coroutines.flow.StateFlow<Int?> = _pendingTab

    fun requestTab(tab: Int) { _pendingTab.value = tab }
    fun clearPendingTab() { _pendingTab.value = null }

    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _syncProgress = MutableStateFlow<Int>(0)
    val syncProgress: StateFlow<Int> = _syncProgress

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            accountRepo.getAllActive().collect { accounts ->
                _accounts.value = accounts
            }
        }
    }

    fun getActiveAccounts(): List<Account> = _accounts.value.filter { it.isActive }

    fun getDefaultAccount(): Account? = _accounts.value.find { it.isDefault }

    fun getAccountById(accountId: String): Account? = _accounts.value.find { it.id == accountId }

    suspend fun addAccount(account: Account) {
        accountRepo.insert(account)
        loadAccounts()
    }

    suspend fun removeAccount(accountId: String) {
        // Delete associated data and account
        accountRepo.delete(accountId)
        loadAccounts()
    }

    suspend fun setDefaultAccount(accountId: String) {
        accountRepo.setDefault(accountId)
    }

    suspend fun updateAccount(account: Account): Account {
        accountRepo.update(account)
        loadAccounts()
        return _accounts.value.find { it.id == account.id } ?: account
    }

    suspend fun syncAllAccounts() {
        _isSyncing.value = true
        _syncProgress.value = 0
        val accounts = getActiveAccounts()
        if (accounts.isEmpty()) {
            _isSyncing.value = false
            _syncProgress.value = 100
            return
        }
        var completed = 0

        for (account in accounts) {
            syncManager.performFullSync(account)
            completed++
            _syncProgress.value = (completed * 100 / accounts.size)
        }

        _isSyncing.value = false
        _syncProgress.value = 100
    }

    suspend fun syncAccount(account: Account): SyncResult {
        _isSyncing.value = true
        return try {
            syncManager.performFullSync(account)
        } finally {
            _isSyncing.value = false
        }
    }

    /** Pre-persist gate: prove the connection over TLS before saving. See SyncManager.provision. */
    suspend fun provisionAccount(account: Account): com.unifiedcomms.sync.ProvisionResult =
        syncManager.provision(account)

    suspend fun sendMessage(conversationId: String, content: String) {
        val currentUserId = com.unifiedcomms.data.model.getCurrentUserId()
        val existing = messagingRepo.getConversationById(conversationId)
        val message = Message(
            conversationId = conversationId,
            senderId = currentUserId,
            recipientId = existing?.participantIds?.firstOrNull { it != currentUserId }.orEmpty(),
            content = content,
            sentAt = kotlinx.datetime.Clock.System.now()
        )
        messagingRepo.insertMessage(message)
        messagingRepo.updateLastMessage(conversationId, message, currentUserId)
    }

    suspend fun sendEmail(email: com.unifiedcomms.data.model.Email): SendResult {
        val account = accountRepo.getById(email.accountId) ?: return SendResult.failure("Account not found")
        val result = syncManager.sendEmail(account, email)
        if (result.success) {
            val stored = emailRepo.getByMessageId(email.messageId)
            if (stored != null) {
                emailRepo.update(stored.copy(folder = "Sent", systemLabels = stored.systemLabels.copy(sent = true), needsSync = false))
            } else {
                emailRepo.insert(email.copy(folder = "Sent", systemLabels = email.systemLabels.copy(sent = true), needsSync = false))
            }
        }
        return result
    }

    suspend fun moveEmails(emailIds: List<String>, fromFolder: String, toFolder: String) {
        val first = emailRepo.getById(emailIds.first()) ?: return
        val account = accountRepo.getById(first.accountId) ?: return
        val uids = emailIds.mapNotNull { emailRepo.getById(it)?.messageId }
        val result = syncManager.moveEmail(account, uids, fromFolder, toFolder)
        if (result.success) {
            emailRepo.moveToFolder(emailIds, toFolder)
        }
    }

    suspend fun deleteEmails(emailIds: List<String>, folder: String) {
        val first = emailRepo.getById(emailIds.first()) ?: return
        val account = accountRepo.getById(first.accountId) ?: return
        val uids = emailIds.mapNotNull { emailRepo.getById(it)?.messageId }
        val result = syncManager.deleteEmail(account, folder, uids)
        if (result.success) {
            emailRepo.deletePermanently(emailIds)
        }
    }

    suspend fun getEventById(eventId: String): CalendarEvent? = calendarRepo.getEventById(eventId)

    suspend fun getTaskById(taskId: String): Task? = taskRepo.getById(taskId)

    fun getAccountColor(accountId: String): com.unifiedcomms.ui.theme.AccountColor {
        return com.unifiedcomms.ui.theme.AccountColors.getColorForAccount(accountId)
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            UnifiedCommsDatabase.getInstance(app).clearAllTables()
        }
    }

    // Repository accessors for screens
    val accountRepository: AccountRepository = accountRepo
    val emailRepository: EmailRepository = emailRepo
    val calendarRepository: CalendarRepository = calendarRepo
    val taskRepository: TaskRepository = taskRepo
    val messagingRepository: MessagingRepository = messagingRepo
    val contactRepository: ContactRepository = contactRepo
    val syncManagerInstance: SyncManager = syncManager
}