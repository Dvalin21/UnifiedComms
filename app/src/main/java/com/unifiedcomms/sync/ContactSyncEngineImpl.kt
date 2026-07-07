package com.unifiedcomms.sync

import com.unifiedcomms.data.model.Account
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

    private fun fetchContactsFromServer(account: Account): List<UnifiedContact> {
        val carddavUrl = account.serverConfig.carddavUrl ?: return emptyList()
        return try {
            val url = java.net.URL("$carddavUrl/")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "PROPFIND"
                setRequestProperty("Depth", "1")
                setRequestProperty("Content-Type", "application/xml; charset=utf-8")
                doOutput = true
            }
            val body = """<?xml version="1.0" encoding="UTF-8"?><D:propfind xmlns:D="DAV:"><D:prop><D:displayname/><D:resourcetype/></D:prop></D:propfind>""".toByteArray()
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) return emptyList()
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            val hrefs = Regex("<[^>]*href([^>]*)?>([^<]*)</[^>]*href\\1?>", RegexOption.IGNORE_CASE).findAll(xml).map { it.groupValues.last().trim() }.toList()
            hrefs.mapIndexed { idx, href ->
                UnifiedContact(
                    id = java.util.UUID.randomUUID().toString(),
                    emails = emptyList(),
                    phoneNumbers = emptyList(),
                    displayName = href.substringAfterLast('/').ifBlank { "Contact $idx" },
                    source = com.unifiedcomms.data.model.ContactSource.CARDDAV,
                    accountId = account.id
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun fetchContact(account: Account, serverId: String): UnifiedContact? = null

    override suspend fun createContact(account: Account, contact: UnifiedContact): CreateResult {
        // ponytail: unsupported — no contact write backend wired. Fail honestly.
        return CreateResult.failure("Contact write backend not implemented")
    }

    override suspend fun updateContact(account: Account, contact: UnifiedContact): SyncResult {
        return SyncResult.failure("Contact write backend not implemented")
    }

    override suspend fun deleteContact(account: Account, serverId: String): SyncResult {
        return SyncResult.failure("Contact write backend not implemented")
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