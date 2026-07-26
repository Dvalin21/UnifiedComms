package com.unifiedcomms

import android.app.Application
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.unifiedcomms.data.repository.AccountRepositoryImpl
import com.unifiedcomms.data.repository.CalendarRepositoryImpl
import com.unifiedcomms.data.repository.EmailRepositoryImpl
import com.unifiedcomms.security.CryptoManagerImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class VerifyCountsTest {
    @Test
    fun verifyAttachmentAndColorCounts(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val db = (app as UnifiedCommsApplication).database
        val crypto = CryptoManagerImpl(app)
        val accountRepo = AccountRepositoryImpl(db.accountDao(), crypto)
        val emailRepo = EmailRepositoryImpl(db.emailDao())
        val calRepo = CalendarRepositoryImpl(db.calendarEventDao(), db.calendarDao())

        val accounts = accountRepo.getAllActive().first()
        val acc = accounts.firstOrNull { it.accountType == com.unifiedcomms.data.model.AccountType.MAILCOW }
            ?: accounts.firstOrNull()
        if (acc == null) { Log.e("VERIFY", "NO ACCOUNT"); return@runBlocking }

        val total = emailRepo.getTotalCount(acc.id)
        val inboxRows = emailRepo.getByAccountAndFolder(acc.id, "INBOX", 100, 0).first()
        Log.e("VERIFY", "TOTAL_EMAILS=$total INBOX_ROWS=${inboxRows.size} accId=${acc.id}")

        val withAtt = emailRepo.getWithAttachments(acc.id, 1000).first()
        val attCount = withAtt.count { it.attachments.isNotEmpty() }
        Log.e("VERIFY", "ATTACH_EMAILS=$attCount totalWithAttRows=${withAtt.size}")
        withAtt.filter { it.attachments.isNotEmpty() }.take(5).forEach { e ->
            Log.e("VERIFY", "  att email subject='${e.subject}' n=${e.attachments.size} names=${e.attachments.map { it.fileName }}")
        }
        // ponytail: also dump the raw attachments column for the first few INBOX rows
        inboxRows.take(3).forEach { e ->
            Log.e("VERIFY", "  raw att col subject='${e.subject}' attachmentsSize=${e.attachments.size} json='${e.attachments}'")
        }

        val events = calRepo.getAllEventsForAccount(acc.id).first()
        val nonDefault = events.count { it.color.background != "#2196F3" }
        Log.e("VERIFY", "CAL_EVENTS=${events.size} nonDefaultColor=$nonDefault")
        events.take(10).forEach { e ->
            Log.e("VERIFY", "  cal '${e.title}' bg=${e.color.background}")
        }
    }
}
