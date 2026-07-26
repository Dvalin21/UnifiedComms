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
import javax.mail.Part
import java.util.Properties
import org.junit.Test

class FixedExtractDiagTest {
    @Test
    fun verifyPdfExtractedFrom470(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val acc = accountRepo.getAllActive().first().firstOrNull() ?: run { Log.e("FIX", "NO ACC"); return@runBlocking }
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
            ?: run { Log.e("FIX", "no 470"); inbox.close(false); store.close(); return@runBlocking }

        // Replicate the FIXED extractAttachments (boundary-aware): build MimeMultipart
        // from the REAL outer boundary as multipart/mixed, ignoring the broken
        // multipart/alternative header.
        val raw = target.inputStream.readBytes()
        val text = String(raw, Charsets.ISO_8859_1)
        val boundary = text.lineSequence().firstNotNullOfOrNull { line ->
            val t = line.trim()
            if (t.startsWith("--") && t.length > 4) t.substring(2).trim().takeIf { it.isNotBlank() } else null
        }
        val hits = mutableListOf<String>()
        if (boundary != null) {
            val ds = object : javax.activation.DataSource {
                override fun getInputStream(): java.io.InputStream = raw.inputStream()
                override fun getOutputStream() = java.io.ByteArrayOutputStream()
                override fun getContentType() = "multipart/mixed; boundary=\"$boundary\""
                override fun getName() = "multipart"
            }
            val mp = runCatching { MimeMultipart(ds) }.getOrNull()
            if (mp != null) for (i in 0 until mp.count) walk(mp.getBodyPart(i), hits)
        }
        Log.e("FIX", "BOUNDARY='$boundary' PDF_PARTS_FOUND=${hits.size}")
        hits.forEach { Log.e("FIX", "  $it") }
        inbox.close(false); store.close(); Log.e("FIX", "DONE")
    }

    private fun walk(part: Part, hits: MutableList<String>, depth: Int = 0) {
        val pad = "  ".repeat(depth)
        try {
            val ct = runCatching { part.contentType }.getOrNull().orEmpty()
            if (part.isMimeType("multipart/*")) {
                val raw = part.inputStream.readBytes()
                val t = String(raw, Charsets.ISO_8859_1)
                val b = t.lineSequence().firstNotNullOfOrNull { line ->
                    val s = line.trim()
                    if (s.startsWith("--") && s.length > 4) s.substring(2).trim().takeIf { it.isNotBlank() } else null
                }
                val ds = object : javax.activation.DataSource {
                    override fun getInputStream(): java.io.InputStream = raw.inputStream()
                    override fun getOutputStream() = java.io.ByteArrayOutputStream()
                    override fun getContentType() = "multipart/mixed; boundary=\"$b\""
                    override fun getName() = "multipart"
                }
                val mp = runCatching { MimeMultipart(ds) }.getOrNull()
                if (mp != null) for (i in 0 until mp.count) walk(mp.getBodyPart(i), hits, depth + 1)
                return
            }
            if (part.isMimeType("message/rfc822")) {
                runCatching { part.content as? MimeMessage }.getOrNull()?.let { walk(it, hits, depth + 1) }
                return
            }
            val disp = runCatching { part.disposition }.getOrNull().orEmpty()
            val fname = runCatching { part.fileName }.getOrNull().orEmpty()
            val low = ct.lowercase()
            val isBinary = low.startsWith("application/") || low.startsWith("image/") ||
                low.startsWith("audio/") || low.startsWith("video/")
            if (Part.ATTACHMENT == disp || fname.isNotBlank() || isBinary) {
                hits.add("$pad ct='$ct' disp='$disp' file='$fname'")
            }
        } catch (e: Exception) {
            Log.e("FIX", "$pad WALK-EX ${e.message}")
        }
    }
}
