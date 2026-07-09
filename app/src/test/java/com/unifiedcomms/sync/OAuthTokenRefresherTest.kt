package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.AuthType
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 16: verifies the OAuth2 refresh HTTP path end-to-end against a mock token endpoint.
 * The live Google/Outlook round-trip is NOT testable here (needs real client creds +
 * interactive browser consent + network egress). This proves the refresher's plumbing:
 * POST refresh_token -> parse access_token/expires_in -> persist updated AuthConfig.
 */
class OAuthTokenRefresherTest {

    private class FakeCrypto : CryptoManager {
        override fun encrypt(data: ByteArray) = data
        override fun decrypt(encrypted: ByteArray) = encrypted
        override fun encryptAuthConfig(config: AuthConfig) = config
        override fun decryptAuthConfig(config: AuthConfig) = config
    }

    private class FakeAccountRepo : AccountRepository {
        val stored = AtomicReference<Account?>(null)
        override suspend fun update(account: Account): Int { stored.set(account); return 1 }
        override suspend fun insert(account: Account): Long { stored.set(account); return 1 }
        override suspend fun getById(id: String) = stored.get()
        override suspend fun delete(accountId: String): Int = 0
        override suspend fun setDefault(accountId: String) {}
        override suspend fun getDefault(): Account? = null
        override suspend fun getByEmailAndType(email: String, type: com.unifiedcomms.data.model.AccountType) = null
        override fun getAllActive() = kotlinx.coroutines.flow.flowOf(emptyList<Account>())
        override fun getByType(type: com.unifiedcomms.data.model.AccountType) = kotlinx.coroutines.flow.flowOf(emptyList<Account>())
        override fun getEmailSyncAccounts() = kotlinx.coroutines.flow.flowOf(emptyList<Account>())
        override fun getCalendarSyncAccounts() = kotlinx.coroutines.flow.flowOf(emptyList<Account>())
        override fun getTaskSyncAccounts() = kotlinx.coroutines.flow.flowOf(emptyList<Account>())
    }

    @Test
    fun refreshesExpiredTokenAndPersists() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"access_token":"NEW_ACCESS_123","expires_in":3600}""")
        )
        server.start()

        val repo = FakeAccountRepo()
        val account = Account(
            id = "acct-oauth-1",
            name = "Test",
            email = "oauth@example.com",
            accountType = com.unifiedcomms.data.model.AccountType.GENERIC_IMAP_SMTP,
            serverConfig = ServerConfig(oauthTokenUrl = server.url("/token").toString(), oauthClientId = "fake-client"),
            authConfig = AuthConfig.OAuth2(
                accessToken = "OLD_ACCESS",
                refreshToken = "REFRESH_999",
                // already expired -> needsRefresh true
                expiry = kotlinx.datetime.Instant.fromEpochMilliseconds(0)
            ),
            syncConfig = com.unifiedcomms.data.model.SyncConfig.Defaults(),
            uiConfig = com.unifiedcomms.data.model.UIConfig.Defaults()
        )
        repo.stored.set(account)

        val refresher = OAuthTokenRefresher(repo, FakeCrypto())
        val result = refresher.ensureFreshToken(account)

        // 1) access token updated
        assertEquals("NEW_ACCESS_123", result.authConfig.oauthAccessToken)
        // 2) expiry recomputed from expires_in
        assertNotNull(result.authConfig.oauthTokenExpiry)
        assertTrue(result.authConfig.oauthTokenExpiry!!.toEpochMilliseconds() > 0)
        // 3) persisted via repo.update
        val persisted = repo.stored.get()
        assertNotNull(persisted)
        assertEquals("NEW_ACCESS_123", persisted!!.authConfig.oauthAccessToken)

        // 4) correct POST body hit the server
        val req = server.takeRequest()
        val body = req.body.readUtf8()
        assertEquals("refresh_token", body.substringAfter("grant_type=").substringBefore("&"))
        assertTrue("body must carry refresh_token", body.contains("refresh_token=REFRESH_999"))
        assertTrue("body must carry client_id", body.contains("client_id=fake-client"))

        server.shutdown()
    }

    @Test
    fun skipsRefreshWhenTokenStillValid() = runBlocking {
        val server = MockWebServer()
        server.start()
        val repo = FakeAccountRepo()
        val future = kotlinx.datetime.Instant.fromEpochMilliseconds(
            kotlinx.datetime.Clock.System.now().toEpochMilliseconds() + 10 * 60 * 1000
        )
        val account = Account(
            id = "acct-oauth-2",
            name = "Test",
            email = "oauth2@example.com",
            accountType = com.unifiedcomms.data.model.AccountType.GENERIC_IMAP_SMTP,
            serverConfig = ServerConfig(oauthTokenUrl = server.url("/token").toString()),
            authConfig = AuthConfig.OAuth2(accessToken = "STILL_GOOD", refreshToken = "R", expiry = future),
            syncConfig = com.unifiedcomms.data.model.SyncConfig.Defaults(),
            uiConfig = com.unifiedcomms.data.model.UIConfig.Defaults()
        )
        repo.stored.set(account)

        val refresher = OAuthTokenRefresher(repo, FakeCrypto())
        val result = refresher.ensureFreshToken(account)

        assertEquals("STILL_GOOD", result.authConfig.oauthAccessToken)
        // server must not have been hit
        assertEquals(0, server.requestCount)
        server.shutdown()
    }
}
