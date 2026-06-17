package com.unifiedcomms.sync.accounts

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import com.unifiedcomms.data.model.Account as UnifiedAccount
import com.unifiedcomms.sync.SyncManager
import com.unifiedcomms.sync.EmailSyncEngine
import com.unifiedcomms.sync.CalendarSyncEngine
import com.unifiedcomms.sync.TaskSyncEngine
import com.unifiedcomms.sync.ContactSyncEngine
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepository
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepository
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.TaskRepository
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.data.repository.ContactRepository
import com.unifiedcomms.data.repository.ContactRepositoryImpl
import com.unifiedcomms.data.db.dao.*
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.sync.SyncManager
import com.unifiedcomms.sync.EmailSyncEngine
import com.unifiedcomms.sync.EmailSyncEngineImpl
import com.unifiedcomms.sync.CalendarSyncEngine
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import com.unifiedcomms.sync.TaskSyncEngine
import com.unifiedcomms.sync.TaskSyncEngineImpl
import com.unifiedcomms.sync.ContactSyncEngine
import com.unifiedcomms.sync.ContactSyncEngineImpl

class UnifiedCommsSyncService : AbstractThreadedSyncAdapter() {

    private lateinit var syncManager: SyncManager
    private lateinit var accountRepo: AccountRepository

    constructor(context: Context) : super(context, true) {
        initDependencies(context)
    }

    constructor(context: Context, autoInitialize: Boolean) : super(context, autoInitialize) {
        initDependencies(context)
    }

    private fun initDependencies(context: Context) {
        val db = UnifiedCommsDatabase.getInstance(context)
        val accountDao = db.accountDao()
        val emailDao = db.emailDao()
        val calendarEventDao = db.calendarEventDao()
        val calendarDao = db.calendarDao()
        val taskDao = db.taskDao()
        val taskListDao = db.taskListDao()
        val contactDao = db.contactDao()

        accountRepo = AccountRepositoryImpl(accountDao)
        val emailRepo = EmailRepositoryImpl(emailDao)
        val calendarRepo = CalendarRepositoryImpl(calendarEventDao, calendarDao)
        val taskRepo = TaskRepositoryImpl(taskDao, taskListDao)
        val contactRepo = ContactRepositoryImpl(contactDao)

        val emailSync = EmailSyncEngineImpl(emailRepo, accountRepo, null)
        val calendarSync = CalendarSyncEngineImpl(calendarRepo, accountRepo, null)
        val taskSync = TaskSyncEngineImpl(taskRepo, accountRepo, null)
        val contactSync = ContactSyncEngineImpl(contactRepo, accountRepo, null)

        syncManager = SyncManager(emailSync, calendarSync, taskSync, contactSync)
    }

    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
        // Find our internal account by email
        val unifiedAccount = accountRepo.getByEmailAndType(account.name, getAccountType(authority))
            ?: return

        // Run sync
        val result = syncManager.performFullSync(unifiedAccount)

        result.errorMessage?.let { error ->
            syncResult.stats.numIoExceptions++
            // Log error
        }
    }

    private fun getAccountType(authority: String): com.unifiedcomms.data.model.AccountType {
        return when (authority) {
            "com.unifiedcomms.provider.email" -> com.unifiedcomms.data.model.AccountType.GOOGLE
            "com.unifiedcomms.provider.calendar" -> com.unifiedcomms.data.model.AccountType.GOOGLE
            "com.unifiedcomms.provider.contacts" -> com.unifiedcomms.data.model.AccountType.GOOGLE
            else -> com.unifiedcomms.data.model.AccountType.GENERIC_IMAP_SMTP
        }
    }
}

class UnifiedCommsSyncServiceProvider : android.content.ContentProvider() {
    private var syncService: UnifiedCommsSyncService? = null

    override fun onCreate(): Boolean {
        syncService = UnifiedCommsSyncService(context!!)
        return true
    }

    override fun query(uri: android.net.Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): android.database.Cursor? = null
    override fun getType(uri: android.net.Uri): String? = null
    override fun insert(uri: android.net.Uri, values: android.content.ContentValues?): android.net.Uri? = null
    override fun delete(uri: android.net.Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: android.net.Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}