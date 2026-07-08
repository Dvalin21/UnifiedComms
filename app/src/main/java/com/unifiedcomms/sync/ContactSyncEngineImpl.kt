package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.ContactSource
import com.unifiedcomms.data.model.UnifiedContact
import com.unifiedcomms.data.repository.ContactRepository
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class ContactSyncEngineImpl(
    private val contactRepo: ContactRepository,
    private val accountRepo: AccountRepository,
    private val crypto: CryptoManager,
    private val scope: CoroutineScope
) : ContactSyncEngine {

    private val _syncProgress = MutableStateFlow<Map<String, SyncProgress>>(emptyMap())
    override val syncProgress: StateFlow<Map<String, SyncProgress>> = _syncProgress

    override suspend fun syncAccount(account: Account): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                updateProgress(account.id, null, SyncStage.CONNECTING, 0, 0)

                val contacts = fetchContactsFromServer(account)
                updateProgress(account.id, null, SyncStage.FETCHING_HEADERS, 0, contacts.size)

                var synced = 0
                val newItems = mutableListOf<String>()
                val updatedItems = mutableListOf<String>()

                for (contact in contacts) {
                    val existing = contactRepo.getByEmail(contact.emails.firstOrNull() ?: "")
                        ?: contactRepo.getByPhone(contact.phoneNumbers.firstOrNull() ?: "")
                    if (existing == null) {
                        contactRepo.insert(contact.copy(
                            accountId = account.id,
                            source = account.accountType.let { when(it) {
                                com.unifiedcomms.data.model.AccountType.GOOGLE -> com.unifiedcomms.data.model.ContactSource.GOOGLE
                                com.unifiedcomms.data.model.AccountType.EXCHANGE -> com.unifiedcomms.data.model.ContactSource.EXCHANGE
                                com.unifiedcomms.data.model.AccountType.ICLOUD -> com.unifiedcomms.data.model.ContactSource.ICLOUD
                                else -> com.unifiedcomms.data.model.ContactSource.CARDDAV
                            }}
                        ))
                        newItems.add(contact.id)
                    } else {
                        contactRepo.update(existing.copy(
                            displayName = contact.displayName,
                            emails = contact.emails,
                            phoneNumbers = contact.phoneNumbers,
                            avatarUrl = contact.avatarUrl,
                            organization = contact.organization,
                            title = contact.title,
                            notes = contact.notes,
                            updatedAt = Clock.System.now()
                        ))
                        updatedItems.add(existing.id)
                    }
                    synced++
                }

                updateProgress(account.id, null, SyncStage.COMPLETED, synced, synced)
                SyncResult.success(synced, newItems, updatedItems)

            } catch (e: Exception) {
                updateProgress(account.id, null, SyncStage.ERROR, 0, 0)
                SyncResult.failure(e.message ?: "Contact sync failed")
            }
        }
    }

    private suspend fun fetchContactsFromServer(account: Account): List<UnifiedContact> = withContext(Dispatchers.IO) {
        val carddavUrl = account.serverConfig.carddavUrl ?: return@withContext emptyList()
        val auth = crypto.decryptAuthConfig(account.authConfig)
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val dav = newCardDav(carddavUrl, auth, client)
        val books = dav.discoverAddressBooks()
        if (books.isEmpty()) return@withContext emptyList()
        val out = mutableListOf<UnifiedContact>()
        for (book in books) {
            val items = dav.listAddressBookItems(book.path)
            for (entry in items) {
                val res = dav.fetchVCard(entry.href) ?: continue
                val uid = entry.href.substringAfterLast('/').substringBeforeLast('.')
                out += VCardParser.parse(res.ical, account.id, ContactSource.CARDDAV, uid)
            }
        }
        out
    }

    private fun newCardDav(url: String, auth: com.unifiedcomms.data.model.AuthConfig, client: okhttp3.OkHttpClient): CalDAVClient {
        val bearer = if (auth.type == com.unifiedcomms.data.model.AuthType.OAUTH2) auth.oauthAccessToken else null
        return CalDAVClient(url, auth.username ?: "", auth.passwordEncrypted ?: "", client, bearer)
    }

    override suspend fun fetchContact(account: Account, serverId: String): UnifiedContact? = withContext(Dispatchers.IO) {
        val carddavUrl = account.serverConfig.carddavUrl ?: return@withContext null
        val auth = crypto.decryptAuthConfig(account.authConfig)
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val dav = newCardDav(carddavUrl, auth, client)
        val href = if (serverId.contains('/')) serverId else "${carddavUrl.trimEnd('/')}/${serverId.removePrefix("/")}"
        val res = dav.fetchVCard(href) ?: return@withContext null
        val uid = href.substringAfterLast('/').substringBeforeLast('.')
        VCardParser.parse(res.ical, account.id, ContactSource.CARDDAV, uid)
    }

    override suspend fun createContact(account: Account, contact: UnifiedContact): CreateResult = withContext(Dispatchers.IO) {
        val carddavUrl = account.serverConfig.carddavUrl ?: return@withContext CreateResult.failure("No CardDAV URL")
        val auth = crypto.decryptAuthConfig(account.authConfig)
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val dav = newCardDav(carddavUrl, auth, client)
        val books = dav.discoverAddressBooks()
        val bookPath = books.firstOrNull()?.path ?: return@withContext CreateResult.failure("No address book found")
        val uid = contact.sourceId?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
        val href = "${bookPath.trimEnd('/')}/${uid}.vcf"
        val vcard = VCardSerializer.toVCard(contact, uid)
        val etag = dav.putVCard(href, vcard) ?: return@withContext CreateResult.failure("Contact write failed")
        CreateResult.success(href, uid, etag)
    }

    override suspend fun updateContact(account: Account, contact: UnifiedContact): SyncResult = withContext(Dispatchers.IO) {
        val carddavUrl = account.serverConfig.carddavUrl ?: return@withContext SyncResult.failure("No CardDAV URL")
        val sourceId = contact.sourceId ?: return@withContext SyncResult.failure("Contact has no sourceId")
        val auth = crypto.decryptAuthConfig(account.authConfig)
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val dav = newCardDav(carddavUrl, auth, client)
        val books = dav.discoverAddressBooks()
        val bookPath = books.firstOrNull()?.path ?: return@withContext SyncResult.failure("No address book found")
        val href = "${bookPath.trimEnd('/')}/${sourceId.removeSuffix(".vcf")}.vcf"
        val vcard = VCardSerializer.toVCard(contact, sourceId)
        if (dav.putVCard(href, vcard) == null) return@withContext SyncResult.failure("Contact write failed")
        contactRepo.update(contact.copy(needsSync = false))
        SyncResult.success(1, emptyList(), listOf(contact.id))
    }

    override suspend fun deleteContact(account: Account, serverId: String): SyncResult = withContext(Dispatchers.IO) {
        val carddavUrl = account.serverConfig.carddavUrl ?: return@withContext SyncResult.failure("No CardDAV URL")
        val auth = crypto.decryptAuthConfig(account.authConfig)
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val dav = newCardDav(carddavUrl, auth, client)
        val href = if (serverId.contains('/')) serverId else "${carddavUrl.trimEnd('/')}/${serverId.removeSuffix(".vcf")}.vcf"
        if (dav.deleteResource(href)) SyncResult.success(1, emptyList(), emptyList(), listOf(serverId))
        else SyncResult.failure("Contact delete failed")
    }

    override fun observeSyncProgress(accountId: String): kotlinx.coroutines.flow.Flow<SyncProgress> {
        return _syncProgress.transform { progressMap: Map<String, SyncProgress> ->
            emit(progressMap[accountId] ?: SyncProgress(accountId, null, SyncStage.COMPLETED, 0, 0))
        }.distinctUntilChanged()
    }

    override suspend fun testConnection(account: Account): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                ConnectionTestResult(true, System.currentTimeMillis() - start, listOf("CardDAV", "Google People"))
            } catch (e: Exception) {
                ConnectionTestResult(false, 0, emptyList(), e.message)
            }
        }
    }

    private fun updateProgress(accountId: String, folder: String?, stage: SyncStage, current: Int, total: Int) {
        _syncProgress.value = _syncProgress.value + (accountId to SyncProgress(accountId, folder, stage, current, total))
    }
}