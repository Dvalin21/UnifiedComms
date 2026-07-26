package com.unifiedcomms

import android.app.Application
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.EmailSyncEngineImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test

class EmailSyncDebugTest {
    @Test
    fun syncSavedPersonalAccount(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val emailRepo = EmailRepositoryImpl(db.emailDao())
        val engine = EmailSyncEngineImpl(emailRepo, accountRepo, crypto, this)

        val accounts = accountRepo.getAllActive().first()
        Log.e("SYNCDBG", "accounts found=${accounts.size}")
        val acc = accounts.firstOrNull { it.accountType == com.unifiedcomms.data.model.AccountType.MAILCOW }
            ?: accounts.firstOrNull()
        if (acc == null) { Log.e("SYNCDBG", "NO ACCOUNT"); return@runBlocking }
        try {
            val res = engine.syncAccount(acc)
            Log.e("SYNCDBG", "RESULT synced=${res.itemsSynced} err='${res.errorMessage}'")
            val msgs = emailRepo.getByAccountAndFolder(acc.id, "INBOX", 100, 0).first()
            val withBody = msgs.count { !(it.bodyText.isNullOrBlank()) }
            Log.e("SYNCDBG", "INBOX rows=${msgs.size} withBody=$withBody")
        } catch (e: Exception) {
            Log.e("SYNCDBG", "EXCEPTION ${e.javaClass.name} '${e.message}'")
            e.printStackTrace()
        }
    }
}
