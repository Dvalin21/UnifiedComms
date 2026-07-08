package com.unifiedcomms

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.ContactSource
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.model.UnifiedContact
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.ContactRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.ContactSyncEngineImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * E2E verification of the contact sync engine (Phase 8) against a local mock
 * CardDAV server (carddav_mock.py) reachable from the emulator at
 * http://127.0.0.1:PORT (published via `adb reverse tcp:PORT tcp:PORT`). Exercises the exact code path the app uses:
 * testConnection -> syncAccount (download) -> createContact (PUT) -> deleteContact (DELETE).
 *
 * All assertions go through the server round-trip (no host filesystem access):
 * a contact is PUT via createContact, then a re-sync must surface it in Room;
 * a deleteContact must make it disappear after re-sync.
 */
class ContactSyncE2ETest : kotlinx.coroutines.CoroutineScope {
    override val coroutineContext = kotlinx.coroutines.Dispatchers.IO

    companion object {
        // The mock (carddav_mock.py) runs on the host; `adb reverse tcp:8088 tcp:8088`
        // exposes it to the emulator at 127.0.0.1:8088.
        private const val MOCK_PORT = 8088
        private const val CARDDAV = "http://127.0.0.1:$MOCK_PORT/addressbook/"
    }

    private fun freshAccount(): Account {
        val uid = UUID.randomUUID().toString().take(8)
        return Account(
            id = "carddav-$uid",
            name = "Mock CardDAV",
            email = "tester@local",
            accountType = AccountType.GENERIC_CALDAV_CARDDAV,
            serverConfig = ServerConfig(carddavUrl = CARDDAV),
            authConfig = AuthConfig.AppPassword("tester", "secret"),
            syncConfig = SyncConfig.Defaults(),
            uiConfig = UIConfig.Defaults()
        )
    }

    private suspend fun roomHas(contactRepo: ContactRepositoryImpl, sourceId: String): Boolean =
        contactRepo.search(sourceId, 50).first().any { it.sourceId == sourceId }

    @Test
    fun fullContactSyncRoundTrip(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val contactRepo = ContactRepositoryImpl(db.contactDao())
        val engine = ContactSyncEngineImpl(contactRepo, accountRepo, crypto, this)

        val account = freshAccount()
        accountRepo.insert(account)

        // Re-fetch the persisted (encrypted) account — mirrors SyncManager.performFullSync,
        // which must use the DB record (ciphertext) rather than the in-memory plaintext object.
        val stored = accountRepo.getById(account.id) ?: account

        // 1) testConnection probes the server for real
        val conn = engine.testConnection(stored)
        assertTrue("testConnection should succeed against mock: ${conn.errorMessage}", conn.success)

        // 2) createContact PUTs a vCard to the server (seed-1)
        val seed = UnifiedContact(
            id = UUID.randomUUID().toString(),
            displayName = "Seed Contact",
            firstName = "Seed",
            lastName = "Contact",
            emails = listOf("seed@example.com"),
            phoneNumbers = listOf("+15550100"),
            source = ContactSource.CARDDAV,
            accountId = account.id,
            sourceId = "seed-1"
        )
        val seedRes = engine.createContact(stored, seed)
        assertTrue("createContact (seed) should succeed: ${seedRes.errorMessage}", seedRes.success)

        // 3) syncAccount downloads from the server -> Room must contain seed-1 (proves GET)
        val syncResult = engine.syncAccount(stored)
        assertTrue("syncAccount should succeed: ${syncResult.errorMessage}", syncResult.success)
        assertTrue("downloaded seed-1 should be present in Room", roomHas(contactRepo, "seed-1"))

        // 4) createContact PUTs a second vCard (new-1)
        val newContact = UnifiedContact(
            id = UUID.randomUUID().toString(),
            displayName = "Created Contact",
            firstName = "Created",
            lastName = "Contact",
            emails = listOf("created@example.com"),
            phoneNumbers = listOf("+15550200"),
            source = ContactSource.CARDDAV,
            accountId = account.id,
            sourceId = "new-1"
        )
        val created = engine.createContact(stored, newContact)
        assertTrue("createContact should succeed: ${created.errorMessage}", created.success)

        // 5) re-sync must surface new-1 (proves the PUT actually landed on the server)
        engine.syncAccount(stored)
        assertTrue("created new-1 should be downloadable after PUT (server round-trip)", roomHas(contactRepo, "new-1"))

        // 6) deleteContact removes the vCard from the server
        val delResult = engine.deleteContact(stored, "new-1")
        assertTrue("deleteContact should succeed: ${delResult.errorMessage}", delResult.success)

        // 7) fetchContact must return null now (proves DELETE landed on the server)
        engine.syncAccount(stored)
        val fetched = engine.fetchContact(stored, "new-1")
        assertTrue("deleted new-1 should no longer be fetchable from server", fetched == null)

        // cleanup
        accountRepo.delete(account.id)
    }
}
