package com.unifiedcomms.util

import android.content.Context
import android.util.Log
import com.unifiedcomms.data.db.UnifiedCommsDatabase
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import com.unifiedcomms.data.model.AuthConfig
import com.unifiedcomms.data.model.CalendarEvent
import com.unifiedcomms.data.model.Conversation
import com.unifiedcomms.data.model.EventAttendee
import com.unifiedcomms.data.model.EventDateTime
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.ServerConfig
import com.unifiedcomms.data.model.SyncConfig
import com.unifiedcomms.data.model.Task
import com.unifiedcomms.data.model.TaskList
import com.unifiedcomms.data.model.TaskPriority
import com.unifiedcomms.data.model.TaskStatus
import com.unifiedcomms.data.model.UIConfig
import com.unifiedcomms.data.model.UnifiedContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import java.util.UUID

object DemoDataSeeder {
    private const val PREFS_NAME = "unifiedcomms_demo_seed"
    private const val KEY_SEEDED = "demo_seeded"
    private const val KEY_USER_REQUESTED_DEMO = "user_requested_demo"
    fun seedIfNeeded(context: Context, scope: CoroutineScope) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        // Do not seed demo data on fresh install anymore.
        // Demo data should only appear if the user explicitly opts in via Help > Load demo data.
        return
    }

    suspend fun seedIfUserRequested(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_USER_REQUESTED_DEMO, false)) return
        try {
            seed(context)
            prefs.edit().putBoolean(KEY_SEEDED, true).commit()
        } catch (t: Throwable) {
            Log.e("DemoDataSeeder", "seed failed", t)
        }
    }

    private suspend fun seed(context: Context) {
        val db = UnifiedCommsDatabase.getInstance(context)
        // Idempotent: never create a second demo account if one already exists.
        if (db.accountDao().getCount() > 0) return
        val accountId = UUID.randomUUID().toString()
        val account = Account(
            id = accountId,
            name = "Demo User",
            email = "demo@example.com",
            accountType = AccountType.GENERIC_IMAP_SMTP,
            serverConfig = ServerConfig(
                imapHost = "imap.example.test",
                imapPort = 993,
                imapUseSsl = true,
                smtpHost = "smtp.example.test",
                smtpPort = 587,
                smtpUseStartTls = true
            ),
            authConfig = AuthConfig.Password("demo@example.com", "demo"),
            syncConfig = SyncConfig.Defaults(),
            uiConfig = UIConfig.Defaults(),
            isActive = true,
            isDefault = true
        )
        db.accountDao().insert(account)

        val a1 = UUID.randomUUID().toString()
        val a2 = UUID.randomUUID().toString()
        db.contactDao().insert(UnifiedContact(id = UUID.randomUUID().toString(), displayName = "Alice Example", emails = listOf("alice@example.test"), organization = "Acme", accountId = accountId))
        db.contactDao().insert(UnifiedContact(id = UUID.randomUUID().toString(), displayName = "Bob Example", emails = listOf("bob@example.test"), organization = "Acme", accountId = accountId))

        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
        db.calendarEventDao().insertAll(
            listOf(
                makeEvent(accountId, "event-1", "Team sync", "${today}T10:00:00", "${today}T10:30:00"),
                makeEvent(accountId, "event-2", "Release review", "${today}T14:00:00", "${today}T15:00:00")
            )
        )

        val taskListId = UUID.randomUUID().toString()
        db.taskListDao().insert(TaskList(id = taskListId, accountId = accountId, serverId = "local", title = "Demo", isDefault = true))
        val nextDay = today.plusDays(1)
        db.taskDao().insertAll(
            listOf(
                Task(accountId = accountId, listId = taskListId, uid = "task-1", title = "Review security findings", status = TaskStatus.NEEDS_ACTION, priority = TaskPriority.HIGH, dueAt = taskDateTime(today, 17, 0)),
                Task(accountId = accountId, listId = taskListId, uid = "task-2", title = "Wire encryption screen", status = TaskStatus.IN_PROCESS, priority = TaskPriority.MEDIUM, dueAt = taskDateTime(nextDay, 12, 0))
            )
        )

        // Demo emails so the Unified Inbox / folder views render real content (not blank).
        val now = kotlinx.datetime.Clock.System.now()
        val localUser = com.unifiedcomms.data.model.EmailAddress("Demo User", "demo@example.com")
        fun mail(uid: String, fromName: String, fromEmail: String, subject: String, body: String, folder: String, read: Boolean, minsAgo: Long) =
            com.unifiedcomms.data.model.Email(
                accountId = accountId,
                folder = folder,
                uid = uid,
                messageId = "<$uid@unifiedcomms.local>",
                threadId = uid,
                sender = com.unifiedcomms.data.model.EmailAddress(fromName, fromEmail),
                recipients = com.unifiedcomms.data.model.EmailRecipients(to = listOf(localUser)),
                subject = subject,
                bodyText = body,
                preview = body.take(80),
                sentAt = kotlinx.datetime.Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - minsAgo * 60_000),
                receivedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - minsAgo * 60_000),
                flags = com.unifiedcomms.data.model.EmailFlags(isRead = read)
            )
        db.emailDao().insertAll(
            listOf(
                mail("demo-mail-1", "Alice Example", "alice@example.test", "Quarterly roadmap", "Hi! Attached the draft roadmap for next quarter. Can you review the security section before Thursday?", "INBOX", false, 42),
                mail("demo-mail-2", "Bob Example", "bob@example.test", "Lunch this week?", "Want to grab lunch Wednesday? There's a new place near the office.", "INBOX", false, 18),
                mail("demo-mail-3", "Demo User", "demo@example.com", "Re: Demo account active", "Thanks — calendar and tasks are showing up correctly on their tabs now.", "Sent", true, 120)
            )
        )

        val localUserId = com.unifiedcomms.data.model.getCurrentUserId()
        var conversationId = java.util.UUID.randomUUID().toString()
        db.conversationDao().insert(Conversation(id = conversationId, participantIds = listOf(localUserId, a1, a2), participantNames = mapOf(localUserId to "Demo User", a1 to "Alice Example", a2 to "Bob Example"), title = "Demo group"))
        db.messageDao().insert(Message(conversationId = conversationId, senderId = a1, recipientId = localUserId, content = "Welcome to UnifiedComms demo."))
        db.messageDao().insert(Message(conversationId = conversationId, senderId = localUserId, recipientId = a1, content = "Demo account active. Calendar and tasks are visible on their tabs."))
    }

    private fun makeEvent(accountId: String, uid: String, title: String, start: String, end: String) = CalendarEvent(accountId = accountId, calendarId = "local", uid = uid, title = title, startAt = EventDateTime(LocalDateTime.parse(start)), endAt = EventDateTime(LocalDateTime.parse(end)), organizer = EventAttendee(email = "Demo User"))

    private fun taskDateTime(date: java.time.LocalDate, hour: Int, minute: Int): com.unifiedcomms.data.model.TaskDateTime {
        val dt = LocalDateTime.parse(String.format("%sT%02d:%02d:00", date, hour, minute))
        return com.unifiedcomms.data.model.TaskDateTime(dateTime = dt, timeZone = java.time.ZoneId.systemDefault().id)
    }
}
