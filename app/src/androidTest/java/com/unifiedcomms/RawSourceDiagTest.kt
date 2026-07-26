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
import javax.mail.internet.MimeMessage
import java.util.Properties
import org.junit.Test

class RawSourceDiagTest {
    @Test
    fun dumpRawSource(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val acc = accountRepo.getAllActive().first().firstOrNull()
            ?: run { Log.e("RAW", "NO ACCOUNT"); return@runBlocking }

        val config = acc.serverConfig
        val auth = crypto.decryptAuthConfig(acc.authConfig)
        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", config.imapHost)
            put("mail.imap.port", config.imapPort.toString())
            put("mail.imap.ssl.enable", "true")
            put("mail.imap.auth", "true")
        }
        val session = Session.getInstance(props, null)
        val store = session.getStore("imap") as com.sun.mail.imap.IMAPStore
        store.connect(config.imapHost, auth.username, auth.passwordEncrypted)
        val inbox: Folder = store.getFolder("INBOX")
        inbox.open(Folder.READ_ONLY)
        val uidFolder = inbox as UIDFolder
        val target = inbox.messages.firstOrNull { m ->
            runCatching { uidFolder.getUID(m) == 470L }.getOrDefault(false)
        } ?: run { Log.e("RAW", "uid 470 not found"); inbox.close(false); store.close(); return@runBlocking }

        // Dump the RAW RFC822 source bytes to a String and scan for the PDF.
        val raw = runCatching { target.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
            ?: runCatching { String(target.inputStream.readBytes(), Charsets.UTF_8) }.getOrNull().orEmpty()
        val hasPdfName = raw.contains("2025_TaxReturn", true) || raw.contains("TaxReturn", true)
        val hasPdfCt = raw.contains("application/pdf", true)
        val hasMixed = raw.contains("multipart/mixed", true)
        Log.e("RAW", "LEN=${raw.length} hasPdfName=$hasPdfName hasPdfCt=$hasPdfCt hasMixed=$hasMixed")
        // log the first structure lines: every Content-Type and Content-Disposition
        raw.lineSequence().filter { it.contains("Content-Type", true) || it.contains("Content-Disposition", true) || it.contains("filename", true) }
            .take(40).forEach { Log.e("RAW", "  $it") }
        inbox.close(false)
        store.close()
        Log.e("RAW", "DONE")
    }
}
