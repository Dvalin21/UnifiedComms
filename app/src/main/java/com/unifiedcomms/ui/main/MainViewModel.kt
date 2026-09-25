package com.unifiedcomms.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedcomms.UnifiedCommsApplication
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.Calendar
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.Email
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
import com.unifiedcomms.reminder.ReminderScheduler
import com.unifiedcomms.sync.BackgroundSyncScheduler
import com.unifiedcomms.util.PreferencesManager
import com.unifiedcomms.data.model.CalendarInviteMessage
import com.unifiedcomms.data.model.AttendeeStatus
import com.unifiedcomms.data.model.UnifiedContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val accountMutationMutex = Mutex()
    private val crypto = com.unifiedcomms.security.CryptoManagerImpl(app)
    private val emailSyncEngine = EmailSyncEngineImpl(emailRepo, accountRepo, crypto, viewModelScope, calendarRepo)
    private val calendarSyncEngine = CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, viewModelScope)
    private val taskSyncEngine = TaskSyncEngineImpl(taskRepo, accountRepo, crypto, viewModelScope)
    private val syncManager: SyncManager = SyncManager(
        emailSyncEngine,
        calendarSyncEngine,
        taskSyncEngine,
        ContactSyncEngineImpl(contactRepo, accountRepo, crypto, viewModelScope),
        accountRepo,
        app,
        crypto
    )

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts

    private val _pendingTab = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    val pendingTab: kotlinx.coroutines.flow.StateFlow<Int?> = _pendingTab

    fun requestTab(tab: Int) { _pendingTab.value = tab }
    fun clearPendingTab() { _pendingTab.value = null }

    private val _calendarSyncError = MutableStateFlow<String?>(null)
    val calendarSyncError: StateFlow<String?> = _calendarSyncError

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

    suspend fun removeAccount(accountId: String): Boolean = accountMutationMutex.withLock {
        val removed = accountRepo.delete(accountId) > 0
        if (removed) {
            ReminderScheduler(app, calendarRepo, accountRepo).cancelAccountReminders(accountId)
            ensureDefaultAccount()
        }
        removed
    }

    private suspend fun ensureDefaultAccount() {
        if (accountRepo.getDefault() != null) return
        accountRepo.getAllActive().first().firstOrNull()?.let { accountRepo.setDefault(it.id) }
    }

    suspend fun setDefaultAccount(accountId: String): Boolean = accountMutationMutex.withLock {
        val account = accountRepo.getById(accountId) ?: return@withLock false
        if (!account.isActive) return@withLock false
        accountRepo.setDefault(accountId)
        accountRepo.getById(accountId)?.isDefault == true
    }

    suspend fun updateAccount(account: Account): Account? = accountMutationMutex.withLock {
        val normalized = if (account.isActive) account else account.copy(isDefault = false)
        if (accountRepo.update(normalized) > 0) {
            if (!normalized.isActive) {
                ReminderScheduler(app, calendarRepo, accountRepo).cancelAccountReminders(normalized.id)
                ensureDefaultAccount()
            }
            normalized
        } else null
    }

    suspend fun syncAllAccounts() {
        _isSyncing.value = true
        _syncProgress.value = 0
        try {
            val accounts = getActiveAccounts()
            if (accounts.isEmpty()) return
            var completed = 0

            for (account in accounts) {
                val result = syncManager.performFullSync(account)
                if (result.success) ReminderScheduler(app, calendarRepo, accountRepo).scheduleReminders(account.id)
                completed++
                _syncProgress.value = (completed * 100 / accounts.size)
            }
        } finally {
            _isSyncing.value = false
            _syncProgress.value = 100
        }
    }

    suspend fun syncAccount(account: Account): SyncResult {
        _isSyncing.value = true
        return try {
            val result = syncManager.performFullSync(account)
            if (result.success) ReminderScheduler(app, calendarRepo, accountRepo).scheduleReminders(account.id)
            result
        } finally {
            _isSyncing.value = false
        }
    }

    // ponytail: launch on viewModelScope so the post-add sync survives the
    // AddAccountScreen being dismissed. Calling the suspend syncAccount from the
    // composable's scope cancelled mid-flight on navigation -> empty inbox.
    fun syncAccountAsync(account: Account) {
        viewModelScope.launch {
            val result = syncManager.performFullSync(account)
            if (result.success) ReminderScheduler(app, calendarRepo, accountRepo).scheduleReminders(account.id)
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
                    val result = try {
                        syncManager.syncCalendar(account)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SyncResult.failure(e.message ?: "Calendar sync failed")
                    }
                    if (!result.success) {
                        _calendarSyncError.value = result.errorMessage ?: "Calendar sync failed"
                    } else {
                        _calendarSyncError.value = null
                        ReminderScheduler(app, calendarRepo, accountRepo).scheduleReminders(account.id)
                    }
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

    suspend fun moveEmails(emailIds: List<String>, fromFolder: String, toFolder: String): SyncResult {
        if (emailIds.isEmpty()) return SyncResult.failure("No messages selected")
        val messages = emailIds.map { emailRepo.getById(it) ?: return SyncResult.failure("Email not found") }
        val first = messages.first()
        if (messages.any { it.accountId != first.accountId }) return SyncResult.failure("Messages belong to different accounts")
        if (messages.any { !it.folder.equals(fromFolder, ignoreCase = true) }) return SyncResult.failure("Selected message is not in the source folder")
        val account = accountRepo.getById(first.accountId) ?: return SyncResult.failure("Account not found")
        val uids = messages.map { it.imapUid?.takeIf(String::isNotBlank) ?: return SyncResult.failure("Message is missing its IMAP UID") }
        val result = syncManager.moveEmail(account, uids, fromFolder, toFolder)
        if (result.success && result.itemsSynced == uids.size) emailRepo.moveToFolder(emailIds, toFolder)
        return result
    }

    suspend fun deleteEmails(emailIds: List<String>, folder: String): SyncResult {
        if (emailIds.isEmpty()) return SyncResult.failure("No messages selected")
        val messages = emailIds.map { emailRepo.getById(it) ?: return SyncResult.failure("Email not found") }
        val first = messages.first()
        if (messages.any { it.accountId != first.accountId }) return SyncResult.failure("Messages belong to different accounts")
        if (messages.any { !it.folder.equals(folder, ignoreCase = true) }) return SyncResult.failure("Selected message is not in the requested folder")
        val account = accountRepo.getById(first.accountId) ?: return SyncResult.failure("Account not found")
        val uids = messages.map { it.imapUid?.takeIf(String::isNotBlank) ?: return SyncResult.failure("Message is missing its IMAP UID") }
        val result = syncManager.deleteEmail(account, folder, uids)
        if (result.success && result.itemsSynced == uids.size) emailRepo.deletePermanently(emailIds)
        return result
    }

    suspend fun setEmailFlags(email: Email, flags: com.unifiedcomms.data.model.EmailFlags): SyncResult {
        val account = accountRepo.getById(email.accountId) ?: return SyncResult.failure("Account not found")
        val uid = email.imapUid
        if (uid.isNullOrBlank()) {
            emailRepo.update(email.copy(flags = flags, needsSync = false))
            return SyncResult.success()
        }
        val result = emailSyncEngine.setFlags(account, email.folder, uid, flags)
        emailRepo.update(email.copy(flags = flags, needsSync = !result.success))
        return result
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
                ?: event.uid.takeIf { it.isNotBlank() }?.let { calendarRepo.getEventByUid(it, account.id) }
            val resolvedCalendarId = when {
                isServerPath(event.calendarId) -> event.calendarId
                else -> calendarRepo.getCalendarById(event.calendarId)?.serverId
            }
            val canPush = account.syncConfig.syncCalendar &&
                !account.serverConfig.caldavUrl.isNullOrBlank() && isServerPath(resolvedCalendarId ?: "")

            val candidate = if (existing == null) {
                event.copy(
                    calendarId = resolvedCalendarId ?: "local",
                    needsSync = true
                )
            } else {
                event.copy(
                    id = existing.id,
                    calendarId = resolvedCalendarId ?: existing.calendarId,
                    serverHref = event.serverHref ?: existing.serverHref,
                    etag = event.etag ?: existing.etag,
                    recurrenceRule = event.recurrenceRule ?: existing.recurrenceRule,
                    recurrenceExceptions = event.recurrenceExceptions.ifEmpty { existing.recurrenceExceptions },
                    attendees = event.attendees.ifEmpty { existing.attendees },
                    organizer = event.organizer ?: existing.organizer,
                    reminders = event.reminders.ifEmpty { existing.reminders },
                    attachments = event.attachments.ifEmpty { existing.attachments },
                    categories = event.categories.ifEmpty { existing.categories },
                    needsSync = true
                )
            }
            val inviteCandidate = candidate.copy(
                organizer = candidate.organizer ?: com.unifiedcomms.data.model.EventAttendee(
                    email = account.email,
                    name = account.name,
                    status = com.unifiedcomms.data.model.AttendeeStatus.ACCEPTED,
                    role = com.unifiedcomms.data.model.AttendeeRole.ORGANIZER,
                    rsvp = false
                ),
                attendees = candidate.attendees
                    .filterNot { it.email.equals(account.email, ignoreCase = true) }
                    .distinctBy { it.email.lowercase() }
            )
            val shouldSendInvite = inviteCandidate.attendees.isNotEmpty() &&
                (existing == null || existing.attendees.map { it.email.lowercase() }.toSet() != inviteCandidate.attendees.map { it.email.lowercase() }.toSet())

            val operationResult = if (!canPush) {
                persistPendingEvent(inviteCandidate, existing == null)
                SyncResult.success()
            } else {
                val result = if (existing == null || inviteCandidate.isLocalOnly) {
                    calendarSyncEngine.createEvent(account, inviteCandidate).asSyncResult()
                } else {
                    calendarSyncEngine.updateEvent(account, inviteCandidate)
                }
                if (result.success) {
                    result
                } else {
                    persistPendingEvent(inviteCandidate, existing == null)
                    result
                }
            }
            val inviteResult = if (shouldSendInvite) {
                emailSyncEngine.sendCalendarInvite(account, inviteCandidate)
            } else {
                null
            }
            ReminderScheduler(app, calendarRepo, accountRepo).cancelReminders(inviteCandidate.id)
            ReminderScheduler(app, calendarRepo, accountRepo).scheduleReminders(account.id)
            if (inviteResult?.success == false) {
                SyncResult.failure("Event saved locally; invite email failed: ${inviteResult.errorMessage ?: "unknown SMTP error"}")
            } else {
                operationResult
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            SyncResult.failure("Event saved locally; pending sync: ${error.message ?: error::class.simpleName}")
        }
    }

    /** Persist a task locally, then push VTODO when a task-list path is known. */
    suspend fun saveTask(task: Task): SyncResult {
        val account = getAccountById(task.accountId) ?: accountRepo.getById(task.accountId)
            ?: return SyncResult.failure("Account not found")
        return runCatching {
            val existing = taskRepo.getById(task.id)
                ?: task.uid.takeIf { it.isNotBlank() }?.let { taskRepo.getByUid(it, account.id) }
            val listPath = resolveTaskListPath(account, task.listId)
            val requiresRemote = account.syncConfig.syncTasks &&
                !account.serverConfig.caldavUrl.isNullOrBlank()
            if (requiresRemote && task.listId.isNotBlank() && listPath == null &&
                !task.listId.equals("local", ignoreCase = true)
            ) {
                return@runCatching SyncResult.failure("Unknown task list: ${task.listId}")
            }

            val candidate = if (existing == null) {
                task.withDueAt(task.dueAt).copy(listId = listPath ?: task.listId, needsSync = true)
            } else {
                task.withDueAt(task.dueAt).copy(
                    id = existing.id,
                    uid = existing.uid,
                    listId = listPath ?: existing.listId,
                    serverHref = task.serverHref ?: existing.serverHref,
                    etag = task.etag ?: existing.etag,
                    isLocalOnly = task.isLocalOnly || existing.isLocalOnly,
                    needsSync = true
                )
            }
            val canPush = requiresRemote && isServerPath(candidate.listId)
            if (!canPush) {
                persistPendingTask(candidate, existing == null)
                SyncResult.success()
            } else if (existing == null || candidate.isLocalOnly) {
                val created = taskSyncEngine.createTask(account, candidate)
                val result = created.asSyncResult()
                if (result.success) {
                    val stored = candidate.copy(
                        uid = created.uid ?: candidate.uid,
                        serverHref = com.unifiedcomms.sync.VTaskSerializer.hrefFor(candidate),
                        etag = created.etag,
                        isLocalOnly = false,
                        needsSync = false
                    )
                    taskRepo.insert(stored)
                    result
                } else {
                    persistPendingTask(candidate, existing == null)
                    // The row is durable locally; background sync will retry the
                    // remote write instead of making the editor create duplicates.
                    SyncResult.success()
                }
            } else {
                val result = taskSyncEngine.updateTask(account, candidate)
                if (result.success) {
                    result
                } else {
                    persistPendingTask(candidate, false)
                    // Keep the local edit and let the next sync retry the server write.
                    SyncResult.success()
                }
            }
        }.getOrElse {
            SyncResult.failure("Task save failed: ${it.message ?: it::class.simpleName}")
        }
    }

    suspend fun setTaskCompleted(task: Task, completed: Boolean): SyncResult {
        taskRepo.markCompleted(task.id, completed)
        val current = taskRepo.getById(task.id) ?: task.copy(
            status = if (completed) com.unifiedcomms.data.model.TaskStatus.COMPLETED
            else com.unifiedcomms.data.model.TaskStatus.NEEDS_ACTION,
            completedAt = if (completed) com.unifiedcomms.data.model.TaskDateTime.fromInstant(Clock.System.now()) else null,
            percentComplete = if (completed) 100 else 0,
            needsSync = true
        )
        return saveTask(current)
    }

    suspend fun deleteTask(task: Task): SyncResult {
        val account = getAccountById(task.accountId) ?: accountRepo.getById(task.accountId)
            ?: return SyncResult.failure("Account not found")
        return taskSyncEngine.deleteTask(account, task.listId, task.uid)
    }

    suspend fun deleteEvent(event: CalendarEvent): SyncResult {
        val account = getAccountById(event.accountId) ?: accountRepo.getById(event.accountId)
            ?: return SyncResult.failure("Account not found")
        val result = calendarSyncEngine.deleteEvent(account, event.calendarId, event.uid)
        if (result.success) ReminderScheduler(app, calendarRepo, accountRepo).cancelReminders(event.id)
        return result
    }

    private suspend fun persistPendingEvent(event: CalendarEvent, isNew: Boolean) {
        val pending = if (isNew) {
            event.copy(isLocalOnly = true, needsSync = true, etag = null)
        } else {
            event.copy(needsSync = true)
        }
        if (isNew) calendarRepo.insertEvent(pending) else calendarRepo.updateEvent(pending)
    }

    private suspend fun persistPendingTask(task: Task, isNew: Boolean) {
        val pending = if (isNew) {
            task.copy(isLocalOnly = true, needsSync = true, etag = null)
        } else {
            task.copy(needsSync = true)
        }
        if (isNew) taskRepo.insert(pending) else taskRepo.update(pending)
    }

    private suspend fun resolveTaskListPath(account: Account, requested: String): String? {
        if (isServerPath(requested)) return requested
        taskRepo.getListById(requested)?.serverId?.let { if (isServerPath(it)) return it }
        taskRepo.getListByServerId(account.id, requested)?.serverId?.let { if (isServerPath(it)) return it }
        if (!account.syncConfig.syncTasks || account.serverConfig.caldavUrl.isNullOrBlank()) return null
        val lists = runCatching { taskSyncEngine.getTaskLists(account) }.getOrDefault(emptyList())
        val name = requested.trim()
        lists.firstOrNull { it.title.equals(name, ignoreCase = true) || it.id == requested }?.let {
            return it.serverId
        }
        // "local" is the only implicit list; never silently route an unknown
        // user-entered name to the first discovered collection.
        if (name.isBlank() || name.equals("local", ignoreCase = true)) {
            lists.firstOrNull()?.serverId?.let { return it }
        }
        return null
    }

    private fun isServerPath(value: String): Boolean =
        value.startsWith("http://", true) || value.startsWith("https://", true) || value.contains('/')

    private fun com.unifiedcomms.sync.CreateResult.asSyncResult(): SyncResult =
        if (success) SyncResult.success() else SyncResult.failure(errorMessage ?: "Remote write failed")

    fun scheduleCalendarReminders() {
        ReminderScheduler(app, calendarRepo, accountRepo).scheduleReminders()
    }

    fun cancelCalendarReminders() {
        ReminderScheduler(app, calendarRepo, accountRepo).cancelAll()
    }

    fun getAccountColor(accountId: String): com.unifiedcomms.ui.theme.AccountColor {
        return com.unifiedcomms.ui.theme.AccountColors.getColorForAccount(accountId)
    }

    fun clearAllData() {
        BackgroundSyncScheduler.cancel(app)
        ReminderScheduler(app, calendarRepo, accountRepo).cancelAll()
        viewModelScope.launch(Dispatchers.IO) {
            UnifiedCommsDatabase.getInstance(app).clearAllTables()
            val prefs = PreferencesManager.getInstance()
            BackgroundSyncScheduler.schedule(
                app,
                prefs.getSyncIntervalMinutes(15).toLong(),
                autoSync = prefs.getBoolean("auto_sync", true),
                wifiOnly = prefs.getBoolean("sync_wifi_only", false),
                replaceShort = true
            )
        }
    }

    // Repository accessors for screens
    val accountRepository: AccountRepository = accountRepo
    val emailRepository: EmailRepository = emailRepo
    val calendarRepository: CalendarRepository = calendarRepo
    val taskRepository: TaskRepository = taskRepo
    val contactRepository: ContactRepository = contactRepo
    val contactsFlow: Flow<List<UnifiedContact>> = combine(
        contactRepo.getUnifiedCommsContacts(),
        allAccounts
    ) { contacts, accounts ->
        val activeIds = accounts.filter { it.isActive }.mapTo(mutableSetOf()) { it.id }
        contacts.filter { it.accountId == null || it.accountId in activeIds }
    }
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

    /** Explicit folder navigation is a user request, so sync non-INBOX folders on demand. */
    fun syncFolder(accountId: String, folder: String) {
        viewModelScope.launch {
            val account = accountRepo.getById(accountId) ?: return@launch
            if (!account.syncConfig.syncEmail) return@launch
            emailSyncEngine.syncFolder(account, folder)
        }
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
        val attendeeEmails = invite.attendees.map { it.email }
        val account = _accounts.value.firstOrNull { candidate ->
            attendeeEmails.any { it.equals(candidate.email, ignoreCase = true) }
        } ?: getDefaultAccount() ?: return null
        val accountId = account.id
        val calendars: List<com.unifiedcomms.data.model.Calendar> =
            calendarRepo.getCalendarsByAccount(accountId).first()
        val calendar = calendars.firstOrNull() ?: return null
        val event = InviteMapper.toCalendarEvent(invite, account.id, calendar.serverId)
        val id = calendarRepo.insertEvent(event)
        return if (id > 0) event.copy(id = id.toString()) else null
    }

    /** Add the invite to the calendar without changing RSVP status. */
    suspend fun addInviteToCalendar(invite: CalendarInviteMessage): Boolean {
        return runCatching { insertInviteEvent(invite) != null }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                false
            }
    }

    /**
     * Accept/Decline the invite: ensure the event exists in the calendar, then
     * delegate to CalendarSyncEngineImpl, which stamps the attendee status,
     * updates local state, pushes to CalDAV, and sends the iTIP REPLY.
     */
    suspend fun respondToInvite(invite: CalendarInviteMessage, status: AttendeeStatus): Boolean {
        return runCatching {
            val account = _accounts.value.firstOrNull { candidate ->
                invite.attendees.any { it.email.equals(candidate.email, ignoreCase = true) }
            } ?: getDefaultAccount() ?: return@runCatching false
            val sync = CalendarSyncEngineImpl(calendarRepo, accountRepo, crypto, viewModelScope)
            val existing = calendarRepo.getEventByUid(invite.eventUid, account.id)
            val event = existing?.let { current ->
                InviteMapper.applyInviteColor(current, invite.color).also { merged ->
                    if (merged != current) calendarRepo.updateEvent(merged)
                }
            } ?: insertInviteEvent(invite) ?: return@runCatching false
            val result = sync.respondToInvite(account, event.uid, status, null)
            if (status == AttendeeStatus.DECLINED) {
                ReminderScheduler(app, calendarRepo, accountRepo).cancelReminders(event.id)
            }
            result.success
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            false
        }
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