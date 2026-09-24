package com.unifiedcomms.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedcomms.UnifiedCommsApplication
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Calendar
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
import com.unifiedcomms.data.repository.TaskRepository
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import com.unifiedcomms.sync.ContactSyncEngine
import com.unifiedcomms.sync.ContactSyncEngineImpl
import com.unifiedcomms.sync.EmailSyncEngineImpl
import com.unifiedcomms.sync.InviteMapper
import com.unifiedcomms.sync.SendResult
import com.unifiedcomms.sync.SyncManager
import com.unifiedcomms.sync.SyncResult
import com.unifiedcomms.sync.TaskSyncEngineImpl
import com.unifiedcomms.data.model.CalendarInviteMessage
import com.unifiedcomms.data.model.AttendeeStatus
import com.unifiedcomms.data.model.UnifiedContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class MainViewModel(
    private val app: UnifiedCommsApplication = UnifiedCommsApplication.getInstance()
) : ViewModel() {

    private val accountDao = app.database.accountDao()
    private val allAccounts: Flow<List<Account>> = accountDao.getAll()
    private val accountRepo: AccountRepository = AccountRepositoryImpl(accountDao, com.unifiedcomms.security.CryptoManagerImpl(app))
    private val emailRepo: EmailRepository = EmailRepositoryImpl(app.database.emailDao())
    private val calendarRepo: CalendarRepository = CalendarRepositoryImpl(
        app.database.calendarEventDao(),
        app.database.calendarDao()
    )
    private val taskRepo: TaskRepository = TaskRepositoryImpl(
        app.database.taskDao(),
        app.database.taskListDao()
    )
    private val contactRepo: ContactRepository = ContactRepositoryImpl(app.database.contactDao())
    private val crypto = com.unifiedcomms.security.CryptoManagerImpl(app)
    private val emailSyncEngine = EmailSyncEngineImpl(emailRepo, accountRepo, crypto, viewModelScope)
    private val calendarSyncEngine = CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, viewModelScope)
    private val taskSyncEngine = TaskSyncEngineImpl(taskRepo, accountRepo, crypto, viewModelScope)
    private val syncManager: SyncManager = SyncManager(
        emailSyncEngine,
        calendarSyncEngine,
        taskSyncEngine,
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
            allAccounts.collect { accounts ->
                _accounts.value = accounts
            }
        }
    }

    fun getActiveAccounts(): List<Account> = _accounts.value.filter { it.isActive }

    fun getDefaultAccount(): Account? = _accounts.value.find { it.isActive && it.isDefault }

    fun getAccountById(accountId: String): Account? = _accounts.value.find { it.id == accountId }

    suspend fun addAccount(account: Account) {
        val toInsert = if (getActiveAccounts().isEmpty()) {
            account.copy(isDefault = account.isActive)
        } else {
            account
        }
        accountRepo.insert(toInsert)
        if (toInsert.isDefault) accountRepo.setDefault(toInsert.id)
    }

    suspend fun removeAccount(accountId: String) {
        // Delete associated data and account
        accountRepo.delete(accountId)
    }

    suspend fun setDefaultAccount(accountId: String) {
        if (accountRepo.getById(accountId)?.isActive == true) {
            accountRepo.setDefault(accountId)
        }
    }

    suspend fun updateAccount(account: Account): Account {
        val normalized = if (account.isActive) account else account.copy(isDefault = false)
        accountRepo.update(normalized)
        return normalized
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

    // ponytail: launch on viewModelScope so the post-add sync survives the
    // AddAccountScreen being dismissed. Calling the suspend syncAccount from the
    // composable's scope cancelled mid-flight on navigation -> empty inbox.
    fun syncAccountAsync(account: Account) {
        viewModelScope.launch {
            syncManager.performFullSync(account)
        }
    }

    // Calendar-screen trigger: sync calendar for the active accounts without
    // blocking (used by CalendarScreen's LaunchedEffect so events surface on open).
    fun syncCalendarForAccounts(accountIds: List<String>) {
        if (accountIds.isEmpty()) return
        viewModelScope.launch {
            for (id in accountIds) {
                val account = accountRepository.getById(id) ?: continue
                if (account.syncConfig.syncCalendar) {
                    runCatching { syncManager.syncCalendar(account) }
                }
            }
        }
    }

    /** Pre-persist gate: prove the connection over TLS before saving. See SyncManager.provision. */
    suspend fun provisionAccount(account: Account): com.unifiedcomms.sync.ProvisionResult =
        syncManager.provision(account)

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
        val uids = emailIds.mapNotNull { emailRepo.getById(it)?.imapUid }
        val result = syncManager.moveEmail(account, uids, fromFolder, toFolder)
        if (result.success) {
            emailRepo.moveToFolder(emailIds, toFolder)
        }
    }

    suspend fun deleteEmails(emailIds: List<String>, folder: String) {
        val first = emailRepo.getById(emailIds.first()) ?: return
        val account = accountRepo.getById(first.accountId) ?: return
        val uids = emailIds.mapNotNull { emailRepo.getById(it)?.imapUid }
        val result = syncManager.deleteEmail(account, folder, uids)
        if (result.success) {
            emailRepo.deletePermanently(emailIds)
        }
    }

    suspend fun getEventById(eventId: String): CalendarEvent? = calendarRepo.getEventById(eventId)

    suspend fun getTaskById(taskId: String): Task? = taskRepo.getById(taskId)

    /**
     * Persist an event locally, then push it to CalDAV when the account has a real
     * collection path. A failed network write stays in Room with needsSync=true;
     * the next account sync retries it instead of losing the user's edit.
     */
    suspend fun saveEvent(event: CalendarEvent): SyncResult {
        val account = getAccountById(event.accountId) ?: accountRepo.getById(event.accountId)
            ?: return SyncResult.failure("Account not found")
        return runCatching {
            val existing = calendarRepo.getEventById(event.id)
            val calendarId = when {
                isServerPath(event.calendarId) -> event.calendarId
                else -> calendarRepo.getCalendarById(event.calendarId)?.serverId ?: event.calendarId
            }
            val candidate = event.copy(calendarId = calendarId, needsSync = true)
            val canPush = account.syncConfig.syncCalendar &&
                !account.serverConfig.caldavUrl.isNullOrBlank() && isServerPath(candidate.calendarId)
            if (!canPush) {
                persistPendingEvent(candidate, existing == null)
                SyncResult.success()
            } else {
                val result = if (existing == null || candidate.isLocalOnly || candidate.etag.isNullOrBlank()) {
                    calendarSyncEngine.createEvent(account, candidate).asSyncResult()
                } else {
                    calendarSyncEngine.updateEvent(account, candidate)
                }
                if (result.success) {
                    result
                } else {
                    persistPendingEvent(candidate.copy(isLocalOnly = true, etag = null), existing == null)
                    result
                }
            }
        }.getOrElse {
            SyncResult.failure("Event saved locally; pending sync: ${it.message ?: it::class.simpleName}")
        }
    }

    /** Persist a task locally, then push VTODO when a task-list path is known. */
    suspend fun saveTask(task: Task): SyncResult {
        val account = getAccountById(task.accountId) ?: accountRepo.getById(task.accountId)
            ?: return SyncResult.failure("Account not found")
        return runCatching {
            val existing = taskRepo.getById(task.id)
            val listPath = resolveTaskListPath(account, task.listId)
            val candidate = task.copy(listId = listPath ?: task.listId, needsSync = true)
            val canPush = account.syncConfig.syncTasks &&
                !account.serverConfig.caldavUrl.isNullOrBlank() && isServerPath(candidate.listId)
            if (!canPush) {
                persistPendingTask(candidate, existing == null)
                SyncResult.success()
            } else if (existing == null || candidate.isLocalOnly || candidate.etag.isNullOrBlank()) {
                val created = taskSyncEngine.createTask(account, candidate)
                val result = created.asSyncResult()
                if (result.success) {
                    taskRepo.insert(
                        candidate.copy(
                            uid = created.uid ?: candidate.uid,
                            etag = created.etag,
                            isLocalOnly = false,
                            needsSync = false
                        )
                    )
                    result
                } else {
                    persistPendingTask(candidate.copy(isLocalOnly = true, etag = null), true)
                    result
                }
            } else {
                val result = taskSyncEngine.updateTask(account, candidate)
                if (result.success) {
                    result
                } else {
                    persistPendingTask(candidate.copy(isLocalOnly = true, etag = null), false)
                    result
                }
            }
        }.getOrElse {
            SyncResult.failure("Task saved locally; pending sync: ${it.message ?: it::class.simpleName}")
        }
    }

    suspend fun setTaskCompleted(task: Task, completed: Boolean): SyncResult = saveTask(
        task.copy(
            status = if (completed) com.unifiedcomms.data.model.TaskStatus.COMPLETED
            else com.unifiedcomms.data.model.TaskStatus.NEEDS_ACTION,
            completedAt = if (completed) com.unifiedcomms.data.model.TaskDateTime.fromInstant(Clock.System.now()) else null,
            percentComplete = if (completed) 100 else 0,
            needsSync = true
        )
    )

    private suspend fun persistPendingEvent(event: CalendarEvent, isNew: Boolean) {
        val pending = event.copy(isLocalOnly = true, needsSync = true, etag = null)
        if (isNew) calendarRepo.insertEvent(pending) else calendarRepo.updateEvent(pending)
    }

    private suspend fun persistPendingTask(task: Task, isNew: Boolean) {
        val pending = task.copy(isLocalOnly = true, needsSync = true, etag = null)
        if (isNew) taskRepo.insert(pending) else taskRepo.update(pending)
    }

    private suspend fun resolveTaskListPath(account: Account, requested: String): String? {
        if (isServerPath(requested)) return requested
        taskRepo.getListById(requested)?.serverId?.let { if (isServerPath(it)) return it }
        taskRepo.getListByServerId(account.id, requested)?.serverId?.let { if (isServerPath(it)) return it }
        if (!account.syncConfig.syncTasks || account.serverConfig.caldavUrl.isNullOrBlank()) return null
        return taskSyncEngine.getTaskLists(account).firstOrNull()?.serverId
    }

    private fun isServerPath(value: String): Boolean =
        value.startsWith("http://", true) || value.startsWith("https://", true) || value.contains('/')

    private fun com.unifiedcomms.sync.CreateResult.asSyncResult(): SyncResult =
        if (success) SyncResult.success() else SyncResult.failure(errorMessage ?: "Remote write failed")

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
    val contactRepository: ContactRepository = contactRepo
    val contactsFlow: Flow<List<UnifiedContact>> = contactRepo.getUnifiedCommsContacts()
    val contactSyncEngine: ContactSyncEngine = ContactSyncEngineImpl(contactRepo, accountRepo, crypto, viewModelScope)
    val syncManagerInstance: SyncManager = syncManager

    /** Download an attachment's bytes from IMAP and return the cached local path. */
    suspend fun downloadAttachment(
        accountId: String,
        folder: String,
        uid: String,
        attachment: com.unifiedcomms.data.model.Attachment
    ): String? {
        val account = getAccountById(accountId) ?: return null
        return emailSyncEngine.fetchAttachment(account, folder, uid, attachment)
    }

    /** List the account's real mail folders (Chat folder excluded) for the drawer. */
    suspend fun loadFolders(accountId: String): List<String> {
        val account = getAccountById(accountId) ?: return emptyList()
        return runCatching { emailSyncEngine.listFolders(account) }.getOrDefault(emptyList())
    }

    /** All contacts across accounts (for the Contacts tab). */
    fun getAllContacts(): Flow<List<UnifiedContact>> = contactsFlow

    /**
     * Create a contact on its owning account (CardDAV server + local row). For a
     * LOCAL contact (no accountId) we only persist locally. The engine writes the
     * server row and returns the serverId/etag; we then hand that back to the
     * caller so the local row can be persisted with the correct sourceId.
     */
    suspend fun createContact(contact: UnifiedContact): ContactOpResult {
        val account = contact.accountId?.let { getAccountById(it) }
        return if (account != null) {
            val r = contactSyncEngine.createContact(account, contact)
            if (r.success) {
                ContactOpResult(true, r.uid, r.etag)
            } else {
                contactRepo.insert(
                    contact.copy(
                        accountId = account.id,
                        source = com.unifiedcomms.data.model.ContactSource.LOCAL,
                        isLocalOnly = true,
                        needsSync = true
                    )
                )
                ContactOpResult(true, error = "Saved locally; CardDAV sync pending: ${r.errorMessage ?: "write failed"}")
            }
        } else {
            contactRepo.insert(contact.copy(needsSync = false))
            ContactOpResult(true)
        }
    }

    suspend fun updateContact(contact: UnifiedContact): SyncResult {
        val account = contact.accountId?.let { getAccountById(it) }
        return if (account != null && contact.sourceId.isNullOrBlank()) {
            val created = contactSyncEngine.createContact(account, contact)
            if (created.success) {
                SyncResult.success()
            } else {
                contactRepo.update(contact.copy(isLocalOnly = true, needsSync = true))
                SyncResult.failure("Saved locally; CardDAV sync pending: ${created.errorMessage ?: "write failed"}")
            }
        } else if (account != null) {
            val result = contactSyncEngine.updateContact(account, contact)
            if (!result.success) contactRepo.update(contact.copy(needsSync = true))
            result
        } else {
            contactRepo.update(contact.copy(needsSync = false))
            SyncResult.success()
        }
    }

    suspend fun deleteContact(contact: UnifiedContact): SyncResult {
        val account = contact.accountId?.let { getAccountById(it) }
        val serverId = contact.sourceId
        return if (account != null && serverId != null) {
            val r = contactSyncEngine.deleteContact(account, serverId)
            if (r.success) contactRepo.deleteById(contact.id)
            r
        } else {
            contactRepo.deleteById(contact.id)
            SyncResult.success()
        }
    }

    // ── Calendar invite actions (email-embedded text/calendar) ──────────────

    /**
     * Build a CalendarEvent from the invite and insert it into the user's calendar.
     * Returns the inserted event (so callers can chain a response), or null on failure.
     */
    private suspend fun insertInviteEvent(invite: CalendarInviteMessage): CalendarEvent? {
        val attendeeEmail = invite.attendees.firstOrNull()?.email
        val accountId = attendeeEmail
            ?.let { email -> _accounts.value.firstOrNull { it.email.equals(email, ignoreCase = true) }?.id }
            ?: getDefaultAccount()?.id ?: return null
        val account = getAccountById(accountId) ?: return null
        val calendars: List<com.unifiedcomms.data.model.Calendar> =
            calendarRepo.getCalendarsByAccount(accountId).first()
        val calendar = calendars.firstOrNull() ?: return null
        val event = InviteMapper.toCalendarEvent(invite, account.id, calendar.serverId)
        val id = calendarRepo.insertEvent(event)
        return if (id > 0) event.copy(id = id.toString()) else null
    }

    /** Add the invite to the calendar without changing RSVP status. */
    suspend fun addInviteToCalendar(invite: CalendarInviteMessage): Boolean {
        return runCatching { insertInviteEvent(invite) != null }.getOrDefault(false)
    }

    /**
     * Accept/Decline the invite: ensure the event exists in the calendar, then
     * delegate to CalendarSyncEngineImpl, which stamps the attendee status,
     * updates local state, pushes to CalDAV, and sends the iTIP REPLY.
     */
    suspend fun respondToInvite(invite: CalendarInviteMessage, status: AttendeeStatus): Boolean {
        return runCatching {
            val account = getDefaultAccount() ?: return@runCatching false
            val sync = CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, viewModelScope)
            val existing = calendarRepo.getEventByUid(invite.eventUid, account.id)
            val event = existing ?: insertInviteEvent(invite) ?: return@runCatching false
            val result = sync.respondToInvite(account, event.uid, status, null)
            result.success
        }.getOrDefault(false)
    }
}

/** Result of a contact create op: carries the server-assigned uid/etag so the
 *  caller can persist the local row with the correct sourceId/etag. */
data class ContactOpResult(
    val success: Boolean,
    val uid: String? = null,
    val etag: String? = null,
    val error: String? = null
)