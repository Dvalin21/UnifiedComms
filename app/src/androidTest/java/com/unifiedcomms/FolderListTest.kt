package com.unifiedcomms

import android.app.Application
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import com.unifiedcomms.sync.EmailSyncEngineImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class FolderListTest {
    @Test
    fun logFolderList(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val emailRepo = com.unifiedcomms.data.repository.EmailRepositoryImpl(db.emailDao())
        val engine = EmailSyncEngineImpl(emailRepo, accountRepo, crypto, this)
        val acc = accountRepo.getAllActive().first().firstOrNull()
            ?: run { Log.e("FOLD", "NO ACCOUNT"); return@runBlocking }
        val folders = engine.listFolders(acc)
        Log.e("FOLD", "FOLDER_COUNT=${folders.size}")
        folders.forEachIndexed { i, f -> Log.e("FOLD", "  [$i] '$f'") }
        // ponytail: chat folder no longer hidden — if a "Chat" folder exists it now
        // lists as a normal mail folder (old AltMarkMove logic removed).
        Log.e("FOLD", "CHAT_LISTED=${folders.any { it.equals("Chat", true) }}")
    }
}
