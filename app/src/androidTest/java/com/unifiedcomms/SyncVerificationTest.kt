package com.unifiedcomms

import android.app.Application
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.AuthType
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.ContactRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.CalendarSyncEngineImpl
import com.unifiedcomms.sync.ContactSyncEngineImpl
import com.unifiedcomms.sync.EmailSyncEngineImpl
import com.unifiedcomms.sync.SyncManager
import com.unifiedcomms.util.Autodiscover
import com.unifiedcomms.sync.TaskSyncEngineImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SyncVerificationTest {

    companion object {
        private const val TAG = "SyncVerify"
    }

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun getApp(): Application {
        return InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
    }

    private fun readPasswordFile(): String {
        return try {
            val file = java.io.File(getApp().filesDir, "uc_main_pw")
            if (file.exists()) file.readText().trim() else ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read password", e)
            ""
        }
    }

    @Test
    fun testSyncVerification(): Unit = runBlocking {
        Log.i(TAG, "=== SyncVerificationTest starting ===")

        val app = getApp() as UnifiedCommsApplication
        val db = app.database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val emailRepo = EmailRepositoryImpl(db.emailDao())
        val calRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())
        val taskRepo = TaskRepositoryImpl(db.taskDao(), db.taskListDao())
        val contactRepo = ContactRepositoryImpl(db.contactDao())

        val requestedEmail = InstrumentationRegistry.getArguments().getString("user")
        val existing = db.accountDao().getAll().first()
            .filter { it.isActive && !it.id.startsWith("live-dav-") }
            .filter { requestedEmail == null || it.email.equals(requestedEmail, ignoreCase = true) }
            .onEach {
                Log.i(TAG, "Existing account candidate: id=${it.id}, name=${it.name}, type=${it.accountType}, default=${it.isDefault}")
            }
            .firstOrNull()
        val accountEmail = existing?.email ?: requestedEmail
            ?: error("Supply -e user or log in an account first")
        val password = if (existing == null) readPasswordFile() else ""
        if (existing == null) Assert.assertTrue("Password file empty or unreadable", password.isNotBlank())

        val discovered = if (existing == null) Autodiscover.discover(accountEmail) else null
        val serverConfig = existing?.serverConfig ?: com.unifiedcomms.data.model.ServerConfig(
            imapHost = discovered?.imapHost ?: error("IMAP host was not discovered"),
            imapPort = discovered.imapPort,
            imapUseSsl = discovered.imapSsl,
            smtpHost = discovered.smtpHost.ifBlank { error("SMTP host was not discovered") },
            smtpPort = discovered.smtpPort,
            smtpUseStartTls = discovered.smtpStartTls,
            caldavUrl = discovered.caldavUrl,
            carddavUrl = discovered.carddavUrl,
            acceptAllCerts = false
        )

        val authConfig = AuthConfig(
            type = AuthType.PASSWORD,
            username = accountEmail,
            passwordEncrypted = password
        )

        val account = existing ?: Account(
            name = "Mailcow Live Test",
            email = accountEmail,
            accountType = AccountType.GENERIC_IMAP_SMTP,
            serverConfig = serverConfig,
            authConfig = authConfig,
            syncConfig = SyncConfig(
                syncEmail = true,
                syncCalendar = true,
                syncTasks = true,
                syncContacts = true,
                syncIntervalMinutes = 15,
                foldersToSync = listOf("INBOX")
            ),
            uiConfig = UIConfig.Defaults(),
            isActive = true,
            isDefault = true
        ).also { accountRepo.insert(it) }

        Log.i(TAG, "Using account: ${account.email}, id=${account.id}, created=${existing == null}")

        val stored = accountRepo.getById(account.id) ?: error("Account was not persisted")
        Log.i(TAG, "Stored: ${stored.email}")

        val emailSync = EmailSyncEngineImpl(emailRepo, accountRepo, crypto, testScope)
        val calendarSync = CalendarSyncEngineImpl(calRepo, accountRepo, crypto, testScope)
        val taskSync = TaskSyncEngineImpl(taskRepo, accountRepo, crypto, testScope)
        val contactSync = ContactSyncEngineImpl(contactRepo, accountRepo, crypto, testScope)
        val syncManager = SyncManager(
            emailSync, calendarSync, taskSync, contactSync,
            accountRepo, app, crypto
        )

        Log.i(TAG, "Starting full sync (timeout ~90s)...")
        val result = syncManager.performFullSync(stored)

        Log.i(TAG, "Sync result: success=${result.success}, items=${result.itemsSynced}, error=${result.errorMessage}")

        val emailCount = emailRepo.getCount(account.id, "INBOX")
        val events = calRepo.getAllEventsForAccount(account.id).first()
        val tasks = taskRepo.getAllUnified(listOf(account.id)).first()
        val contacts = contactRepo.getByAccount(account.id).first()

        Log.i(TAG, "DB: emails($emailCount) events(${events.size}) tasks(${tasks.size}) contacts(${contacts.size})")

        if (emailCount > 0) {
            val sample = emailRepo.getByAccountAndFolder(account.id, "INBOX", 10, 0).first()
            if (sample.isNotEmpty()) {
                Log.i(TAG, "Sample email: subject=\"${sample.first().subject}\" ($emailCount total)")
                sample.forEach { e ->
                    Log.i(TAG, "  [${e.folder}] ${e.flags.isRead} ${e.subject}")
                }
            }
        }
        if (events.isNotEmpty()) {
            Log.i(TAG, "Sample events:")
            events.take(5).forEach { e -> Log.i(TAG, "  ${e.title} [${e.startAt}]") }
        }
        if (tasks.isNotEmpty()) {
            Log.i(TAG, "Sample tasks:")
            tasks.take(5).forEach { e -> Log.i(TAG, "  ${e.title} [due: ${e.dueAt}]") }
        }
        if (contacts.isNotEmpty()) {
            Log.i(TAG, "Sample contacts:")
            contacts.take(5).forEach { c -> Log.i(TAG, "  ${c.displayName} emails=${c.emails}") }
        }

        val syncWorked = result.success && (emailCount > 0 || events.isNotEmpty() || tasks.isNotEmpty() || contacts.isNotEmpty())
        Assert.assertTrue(
            "Sync failed or no data. success=${result.success}, error=${result.errorMessage}, " +
            "emails=$emailCount, events=${events.size}, tasks=${tasks.size}, contacts=${contacts.size}",
            syncWorked
        )

        if (existing == null) accountRepo.delete(account.id)
        Log.i(TAG, "=== SyncVerificationTest PASSED ===")
    }
}
