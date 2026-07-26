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
import javax.mail.internet.MimeMultipart
import java.util.Properties
import org.junit.Test

class CountDiagTest {
    @Test
    fun logPartCount(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val acc = accountRepo.getAllActive().first().firstOrNull() ?: run { Log.e("CNT", "NO ACC"); return@runBlocking }
        val config = acc.serverConfig
        val auth = crypto.decryptAuthConfig(acc.authConfig)
        val props = Properties().apply {
            put("mail.store.protocol", "imap")
            put("mail.imap.host", config.imapHost); put("mail.imap.port", config.imapPort.toString())
            put("mail.imap.ssl.enable", "true"); put("mail.imap.auth", "true")
        }
        val session = Session.getInstance(props, null)
        val store = session.getStore("imap") as com.sun.mail.imap.IMAPStore
        store.connect(config.imapHost, auth.username, auth.passwordEncrypted)
        val inbox = store.getFolder("INBOX"); inbox.open(Folder.READ_ONLY)
        val uidFolder = inbox as UIDFolder
        val target = inbox.messages.firstOrNull { runCatching { uidFolder.getUID(it) == 470L }.getOrDefault(false) }
            ?: run { Log.e("CNT", "no 470"); inbox.close(false); store.close(); return@runBlocking }
        val parsed = MimeMessage(null, target.getInputStream())
        val c = parsed.content
        Log.e("CNT", "top isMultipart=${parsed.isMimeType("multipart/*")} contentType='${parsed.contentType}'")
        if (c is MimeMultipart) {
            Log.e("CNT", "MimeMultipart count=${c.count}")
            for (i in 0 until c.count) {
                val p = c.getBodyPart(i)
                Log.e("CNT", "  [$i] ct='${p.contentType}' disp='${runCatching { p.disposition }.getOrNull()}' file='${runCatching { p.fileName }.getOrNull()}'")
            }
        }
        inbox.close(false); store.close(); Log.e("CNT", "DONE")
    }
}
