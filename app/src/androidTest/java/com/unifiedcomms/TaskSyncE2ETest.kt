package com.unifiedcomms

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskStatus
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.TaskRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.TaskSyncEngineImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Phase 16: E2E verification of the task (VTODO) sync engine against a local mock
 * CalDAV task server (taskdav_mock.py) at http://127.0.0.1:8089 via
 * `adb reverse tcp:8089 tcp:8089`. Exercises the exact app path:
 * testConnection -> createTask (PUT) -> syncAccount (download) -> deleteTask (DELETE).
 *
 * This is the Task counterpart of ContactSyncE2ETest. It proves the CalDAVClient
 * getETagList per-<response> fix (Phase 11) also holds for task lists, and that
 * VTODO PUT/GET/DELETE round-trips through the real engine.
 */
class TaskSyncE2ETest : kotlinx.coroutines.CoroutineScope {
    override val coroutineContext = kotlinx.coroutines.Dispatchers.IO

    companion object {
        private const val MOCK_PORT = 8089
        private const val CALDAV = "http://127.0.0.1:$MOCK_PORT/tasks/"
    }

    private fun freshAccount(): Account {
        val uid = UUID.randomUUID().toString().take(8)
        return Account(
            id = "taskdav-$uid",
            name = "Mock Tasks",
            email = "tester@local",
            accountType = AccountType.GENERIC_CALDAV_CARDDAV,
            serverConfig = ServerConfig(caldavUrl = CALDAV),
            authConfig = AuthConfig.AppPassword("tester", "secret"),
            syncConfig = SyncConfig.Defaults(),
            uiConfig = UIConfig.Defaults()
        )
    }

    @Test
    fun fullTaskSyncRoundTrip(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val taskRepo = TaskRepositoryImpl(db.taskDao(), db.taskListDao())
        val engine = TaskSyncEngineImpl(taskRepo, accountRepo, crypto, this)

        val account = freshAccount()
        accountRepo.insert(account)
        val stored = accountRepo.getById(account.id) ?: account

        // 1) testConnection probes the server
        val conn = engine.testConnection(stored)
        assertTrue("testConnection should succeed against mock: ${conn.errorMessage}", conn.success)

        // 2) createTask PUTs a VTODO (seed)
        val seedUid = "${account.id}-seed"
        val seed = Task(
            uid = seedUid,
            title = "Seed Task",
            description = "from test",
            status = TaskStatus.NEEDS_ACTION,
            accountId = account.id,
            listId = ""
        )
        val seedRes = engine.createTask(stored, seed)
        assertTrue("createTask (seed) should succeed: ${seedRes.errorMessage}", seedRes.success)

        // 3) syncAccount downloads -> Room must contain seed (proves GET + getETagList)
        val syncResult = engine.syncAccount(stored)
        assertTrue("syncAccount should succeed: ${syncResult.errorMessage}", syncResult.success)
        assertTrue("downloaded seed should be present in Room", taskRepo.getByUid(seedUid, account.id) != null)

        // 4) createTask PUTs a second VTODO (new)
        val newUid = "${account.id}-new"
        val newTask = Task(
            uid = newUid,
            title = "Created Task",
            description = "created",
            status = TaskStatus.NEEDS_ACTION,
            accountId = account.id,
            listId = ""
        )
        val created = engine.createTask(stored, newTask)
        assertTrue("createTask (new) should succeed: ${created.errorMessage}", created.success)

        // 5) re-sync must surface new (proves PUT landed on the server)
        engine.syncAccount(stored)
        assertTrue("created new should be downloadable after PUT (server round-trip)", taskRepo.getByUid(newUid, account.id) != null)

        // 6) deleteTask removes the VTODO from the server
        val delResult = engine.deleteTask(stored, "${account.id}/placeholder-list", newUid)
        assertTrue("deleteTask should succeed: ${delResult.errorMessage}", delResult.success)

        // 7) fetchTask must return null now (proves DELETE landed on the server)
        engine.syncAccount(stored)
        val fetched = engine.fetchTask(stored, CALDAV, newUid)
        assertTrue("deleted new should no longer be fetchable from server", fetched == null)

        accountRepo.delete(account.id)
    }
}
