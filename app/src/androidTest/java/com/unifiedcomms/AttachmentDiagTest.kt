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

class AttachmentDiagTest {
    @Test
    fun exhaustiveAttachmentScan(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val acc = accountRepo.getAllActive().first().firstOrNull()
            ?: run { Log.e("DIAG", "NO ACCOUNT"); return@runBlocking }

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

        val folders = store.defaultFolder.list("*")
        Log.e("DIAG", "FOLDERS=${folders.size}")

        for (f in folders) {
            runCatching {
                if (!f.exists()) return@runCatching
                f.open(Folder.READ_ONLY)
                val uidFolder = f as? UIDFolder
                for (m in f.messages) {
                    val from = runCatching { m.getHeader("From")?.firstOrNull().orEmpty() }.getOrDefault("")
                    val subj = runCatching { m.getHeader("Subject")?.firstOrNull().orEmpty() }.getOrDefault("")
                    val uid = runCatching { uidFolder?.getUID(m) }.getOrNull()
                    val parsed = runCatching { MimeMessage(null, m.getInputStream()) }.getOrElse { m as MimeMessage }
                    val hits = mutableListOf<String>()
                    walk(parsed, hits)
                    if (hits.isNotEmpty()) {
                        Log.e("DIAG", "ATT [$uid] folder='${f.fullName}' from='$from' subj='$subj'")
                        hits.forEach { Log.e("DIAG", "   $it") }
                    }
                }
                f.close(false)
            }.onFailure { Log.e("DIAG", "folder ${f.fullName} failed: ${it.message}") }
        }
        store.close()
        Log.e("DIAG", "EXHAUSTIVE DONE")
    }

    // Recurse every part type, including message/rfc822. Flag any part that is a
    // real attachment: explicit disposition, a filename, or a binary content-type.
    private fun walk(part: Part, hits: MutableList<String>, depth: Int = 0) {
        val pad = "  ".repeat(depth)
        try {
            val ct = runCatching { part.contentType }.getOrNull().orEmpty()
            val disp = runCatching { part.disposition }.getOrNull().orEmpty()
            val fname = runCatching { part.fileName }.getOrNull().orEmpty()
            val lower = ct.lowercase()
            val isBinary = lower.startsWith("application/") ||
                    (lower.startsWith("image/") && !lower.contains("cid")) ||
                    lower.startsWith("audio/") || lower.startsWith("video/")
            val isAtt = Part.ATTACHMENT == disp || fname.isNotBlank() || isBinary
            if (isAtt) {
                hits.add("$pad ct='$ct' disp='$disp' file='$fname'")
            }
            val c = part.content
            when {
                part.isMimeType("multipart/*") -> {
                    val mp = when (c) {
                        is MimeMultipart -> c
                        is java.io.InputStream -> runCatching {
                            val ds = object : javax.activation.DataSource {
                                override fun getInputStream(): java.io.InputStream = c as java.io.InputStream
                                override fun getOutputStream() = java.io.ByteArrayOutputStream()
                                override fun getContentType() = ct
                                override fun getName() = "multipart"
                            }
                            MimeMultipart(ds)
                        }.getOrNull()
                        else -> null
                    }
                    if (mp != null) for (i in 0 until mp.count) walk(mp.getBodyPart(i), hits, depth + 1)
                }
                part.isMimeType("message/rfc822") -> {
                    val nested = runCatching { (c as? MimeMessage) ?: MimeMessage(null, (c as? java.io.InputStream) ?: return) }.getOrNull()
                        ?: runCatching { part.content as? MimeMessage }.getOrNull()
                    nested?.let { walk(it, hits, depth + 1) }
                }
            }
        } catch (e: Exception) {
            Log.e("DIAG", "$pad WALK-EX ${e.javaClass.name}: ${e.message}")
        }
    }
}
