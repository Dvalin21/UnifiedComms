package com.unifiedcomms

import android.app.Application
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.mail.Folder
import javax.mail.Session
import javax.mail.UIDFolder
import java.util.Properties
import org.junit.Test

class BoundaryDiagTest {
    @Test
    fun dumpBoundaries(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val acc = accountRepo.getAllActive().first().firstOrNull() ?: run { Log.e("BND", "NO ACC"); return@runBlocking }
        val config = acc.serverConfig
        val auth = crypto.decryptAuthConfig(acc.authConfig)
        val props = Properties().apply {
            put("mail.store.protocol", "imap"); put("mail.imap.host", config.imapHost)
            put("mail.imap.port", config.imapPort.toString()); put("mail.imap.ssl.enable", "true"); put("mail.imap.auth", "true")
        }
        val session = Session.getInstance(props, null)
        val store = session.getStore("imap") as com.sun.mail.imap.IMAPStore
        store.connect(config.imapHost, auth.username, auth.passwordEncrypted)
        val inbox = store.getFolder("INBOX"); inbox.open(Folder.READ_ONLY)
        val uidFolder = inbox as UIDFolder
        val target = inbox.messages.firstOrNull { runCatching { uidFolder.getUID(it) == 470L }.getOrDefault(false) }
            ?: run { Log.e("BND", "no 470"); inbox.close(false); store.close(); return@runBlocking }
        val raw = String(target.inputStream.readBytes(), Charsets.UTF_8)
        // Print every boundary marker and Content-Type/Disposition line with line number.
        raw.lineSequence().withIndex().forEach { (i, line) ->
            val t = line.trim()
            if (t.startsWith("--") || t.equals("--", ignoreCase = true) ||
                line.contains("Content-Type", true) || line.contains("Content-Disposition", true) ||
                line.contains("boundary=", true) || line.contains("filename=", true) || line.contains("name=", true)) {
                Log.e("BND", "[${i+1}] $line")
            }
        }
        inbox.close(false); store.close(); Log.e("BND", "DONE")
    }
}
