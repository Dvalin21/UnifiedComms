package com.unifiedcomms

import android.app.Application
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.EmailSyncEngineImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ResyncAttachTest {
    @Test
    fun clearAndResyncThenCheckAttachment(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val emailRepo = EmailRepositoryImpl(db.emailDao())
        val engine = EmailSyncEngineImpl(emailRepo, accountRepo, crypto, this)
        val acc = accountRepo.getAllActive().first().firstOrNull()
            ?: run { Log.e("RESYNC", "NO ACCOUNT"); return@runBlocking }

        // Force re-extraction: drop cached INBOX rows so sync re-inserts them
        // with the FIXED boundary-aware attachment parser.
        val cleared = emailRepo.deleteByAccountAndFolder(acc.id, "INBOX")
        Log.e("RESYNC", "cleared INBOX rows=$cleared")

        val res = engine.syncAccount(acc)
        Log.e("RESYNC", "sync itemsSynced=${res.itemsSynced} err='${res.errorMessage}'")

        val withAtt = emailRepo.getWithAttachments(acc.id, 1000).first()
        val attCount = withAtt.count { it.attachments.isNotEmpty() }
        Log.e("RESYNC", "ATTACH_EMAILS=$attCount")
        withAtt.filter { it.attachments.isNotEmpty() }.forEach { e ->
            e.attachments.forEach { a ->
                Log.e("RESYNC", "  subject='${e.subject}' file='${a.fileName}' mime='${a.mimeType}' size=${a.sizeBytes}")
            }
        }
    }
}
