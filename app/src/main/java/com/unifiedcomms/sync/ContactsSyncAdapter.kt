package com.unifiedcomms.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder
import android.provider.ContactsContract
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.model.ContactSource
import com.unifiedcomms.data.model.UnifiedContact
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.ContactRepository
import com.unifiedcomms.data.repository.ContactRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private const val ACCOUNT_TYPE = "com.unifiedcomms"
private const val USER_DATA_ACCOUNT_ID = "unifiedcomms.accountId"

/**
 * Mirrors our app's contacts into the system ContactsProvider so the native
 * Contacts app (and anything using ContactsContract) sees them, and uploads
 * natively-added contacts back to the CardDAV server. The sync engine here
 * remains the implementation of record; this adapter only round-trips state.
 */
class ContactsSyncAdapter(context: android.content.Context) :
    AbstractThreadedSyncAdapter(context, true) {

    override fun onPerformSync(
        account: Account,
        extras: Bundle?,
        authority: String,
        provider: ContentProviderClient?,
        syncResult: SyncResult
    ) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val am = AccountManager.get(context)
            val accountId = am.getUserData(account, USER_DATA_ACCOUNT_ID) ?: return
            val db = UnifiedCommsDatabase.getInstance(context)
            val crypto = CryptoManagerImpl(context)
            val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
            val contactRepo = ContactRepositoryImpl(db.contactDao())
            val appAccount = runBlocking { accountRepo.getById(accountId) } ?: return

            val engine = ContactSyncEngineImpl(contactRepo, accountRepo, crypto, scope)
            runBlocking {
                engine.syncAccount(appAccount)
                uploadNativeContacts(account, appAccount, contactRepo, engine)
                publishToContactsProvider(account, appAccount.id, contactRepo)
            }
        } catch (t: Throwable) {
            android.util.Log.e("ContactsSyncAdapter", "sync failed for ${account.name}", t)
        } finally {
            scope.cancel()
        }
    }

    /** Native-added rows (no SOURCE_ID) inside our account get pushed to the server. */
    private suspend fun uploadNativeContacts(
        account: Account,
        appAccount: com.unifiedcomms.data.model.Account,
        contactRepo: ContactRepository,
        engine: ContactSyncEngineImpl
    ) {
        val candidates = mutableListOf<Long>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.SOURCE_ID, ContactsContract.RawContacts.SYNC1),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE}=? AND ${ContactsContract.RawContacts.ACCOUNT_NAME}=? AND ${ContactsContract.RawContacts.DELETED}=0",
            arrayOf(ACCOUNT_TYPE, account.name),
            null
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            val srcCol = c.getColumnIndexOrThrow(ContactsContract.RawContacts.SOURCE_ID)
            val s1Col = c.getColumnIndexOrThrow(ContactsContract.RawContacts.SYNC1)
            while (c.moveToNext()) {
                val src = c.getString(srcCol)
                val uploaded = c.getString(s1Col)
                if (src.isNullOrBlank() && !uploaded.equals("UPLOADED", ignoreCase = true)) {
                    candidates += c.getLong(idCol)
                }
            }
        }
        for (rawId in candidates) {
            val contact = readRawContact(rawId) ?: continue
            val result = engine.createContact(appAccount, contact.copy(accountId = appAccount.id))
            if (result.success && !result.uid.isNullOrBlank()) {
                context.contentResolver.update(
                    ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId),
                    ContentValues().apply {
                        put(ContactsContract.RawContacts.SOURCE_ID, result.uid)
                        put(ContactsContract.RawContacts.SYNC1, "UPLOADED")
                    }, null, null
                )
            }
        }
    }

    private fun readRawContact(rawId: Long): UnifiedContact? {
        var name: String? = null; var given: String? = null; var family: String? = null
        var org: String? = null; var notes: String? = null
        val emails = mutableListOf<String>(); val phones = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3),
            "${ContactsContract.Data.RAW_CONTACT_ID}=?",
            arrayOf(rawId.toString()), null
        )?.use { c ->
            val mime = c.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            val d1 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
            val d2 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
            val d3 = c.getColumnIndexOrThrow(ContactsContract.Data.DATA3)
            while (c.moveToNext()) {
                when (c.getString(mime)) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        given = c.getString(d2); family = c.getString(d3)
                        name = c.getString(d1) ?: "${given ?: ""} ${family ?: ""}".trim()
                    }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                        c.getString(d1)?.takeIf { it.isNotBlank() }?.let { emails += it }
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        c.getString(d1)?.takeIf { it.isNotBlank() }?.let { phones += it }
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> org = c.getString(d1)
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> notes = c.getString(d1)
                }
            }
        }
        if (name.isNullOrBlank() && emails.isEmpty() && phones.isEmpty()) return null
        return UnifiedContact(
            displayName = name ?: emails.firstOrNull() ?: phones.firstOrNull() ?: "Unknown",
            firstName = given,
            lastName = family,
            emails = emails,
            phoneNumbers = phones,
            organization = org,
            notes = notes,
            source = ContactSource.LOCAL
        )
    }

    /** Mirror Room contacts into ContactsProvider under our account type. */
    private suspend fun publishToContactsProvider(account: Account, roomAccountId: String, contactRepo: ContactRepository) {
        val all = contactRepo.getUnifiedCommsContacts().first()
        val wantedSourceIds = mutableSetOf<String>()
        for (contact in all) {
            if (contact.accountId != null && contact.accountId != roomAccountId) continue
            val sid = contact.sourceId
            if (sid.isNullOrBlank()) continue
            wantedSourceIds += sid
            val existingRawId = lookupRawContactId(account, sid)
            if (existingRawId == null) createRawContact(account, contact)
            else syncDataRows(existingRawId, contact)
        }
        // Remove provider copies whose server resource is gone from Room/server.
        val idsToDelete = mutableListOf<Long>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.SOURCE_ID),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE}=? AND ${ContactsContract.RawContacts.ACCOUNT_NAME}=?",
            arrayOf(ACCOUNT_TYPE, account.name), null
        )?.use { c ->
            while (c.moveToNext()) {
                val src = c.getString(1)
                if (!src.isNullOrBlank() && src !in wantedSourceIds) idsToDelete += c.getLong(0)
            }
        }
        for (id in idsToDelete) {
            context.contentResolver.delete(
                ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, id),
                null, null
            )
        }
    }

    private fun lookupRawContactId(account: Account, sourceId: String): Long? {
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE}=? AND ${ContactsContract.RawContacts.ACCOUNT_NAME}=? AND ${ContactsContract.RawContacts.SOURCE_ID}=?",
            arrayOf(ACCOUNT_TYPE, account.name, sourceId), null
        )?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return null
    }

    private fun createRawContact(account: Account, contact: UnifiedContact) {
        val values = ContentValues().apply {
            put(ContactsContract.RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
            put(ContactsContract.RawContacts.SOURCE_ID, contact.sourceId)
        }
        val uri = context.contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, values)
        val rawId = uri?.lastPathSegment?.toLongOrNull() ?: return
        insertDataRows(rawId, contact)
    }

    private fun insertDataRows(rawId: Long, contact: UnifiedContact) {
        val cr = context.contentResolver
        fun row(mime: String, configure: ContentValues.() -> Unit) {
            cr.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                put(ContactsContract.Data.MIMETYPE, mime)
                configure()
            })
        }
        row(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE) {
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
            contact.firstName?.let { put(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, it) }
            contact.lastName?.let { put(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, it) }
        }
        contact.emails.forEach { email -> row(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE) {
            put(ContactsContract.CommonDataKinds.Email.ADDRESS, email) } }
        contact.phoneNumbers.forEach { phone -> row(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
            put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone) } }
        contact.organization?.let { org -> row(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE) {
            put(ContactsContract.CommonDataKinds.Organization.COMPANY, org) } }
        contact.notes?.let { n -> row(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE) {
            put(ContactsContract.CommonDataKinds.Note.NOTE, n) } }
    }

    private fun syncDataRows(rawId: Long, contact: UnifiedContact) {
        context.contentResolver.delete(
            ContactsContract.Data.CONTENT_URI,
            "${ContactsContract.Data.RAW_CONTACT_ID}=?",
            arrayOf(rawId.toString())
        )
        insertDataRows(rawId, contact)
    }
}

class ContactsSyncAdapterService : Service() {
    private var adapter: ContactsSyncAdapter? = null
    override fun onCreate() {
        super.onCreate()
        adapter = ContactsSyncAdapter(applicationContext)
    }
    override fun onBind(intent: Intent?): IBinder? = adapter?.syncAdapterBinder
}
