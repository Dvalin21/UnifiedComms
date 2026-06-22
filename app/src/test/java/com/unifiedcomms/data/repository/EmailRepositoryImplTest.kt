package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.EmailDao
import com.unifiedcomms.data.model.Email
import com.unifiedcomms.data.model.EmailAddress
import com.unifiedcomms.data.model.EmailFlags
import com.unifiedcomms.data.model.EmailRecipients
import com.unifiedcomms.data.model.SystemLabels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EmailRepositoryImplTest {

    private lateinit var dao: EmailDao
    private lateinit var repo: EmailRepositoryImpl

    @Before
    fun setUp() {
        dao = mock()
        repo = EmailRepositoryImpl(dao)
    }

    private fun baseEmail(
        id: String = "e1",
        accountId: String = "a1",
        folder: String = "INBOX",
        uid: String = "1",
        messageId: String = "m1",
        threadId: String = "t1",
        subject: String = "Test",
        flags: EmailFlags = EmailFlags(),
        systemLabels: SystemLabels = SystemLabels()
    ): Email {
        val now = Clock.System.now()
        return Email(
            id = id,
            accountId = accountId,
            folder = folder,
            uid = uid,
            messageId = messageId,
            threadId = threadId,
            sender = EmailAddress(email = "sender@example.com"),
            recipients = EmailRecipients(to = listOf(EmailAddress(email = "to@example.com"))),
            subject = subject,
            sentAt = now,
            receivedAt = now,
            flags = flags,
            systemLabels = systemLabels
        )
    }

    @Test
    fun `insert delegates to dao`() = runTest {
        val email = baseEmail()
        repo.insert(email)
        verify(dao).insert(email)
    }

    @Test
    fun `getUnreadByAccountAndFolder filters unread`() = runTest {
        val read = baseEmail(id = "r", flags = EmailFlags(isRead = true))
        val unread = baseEmail(id = "u", flags = EmailFlags(isRead = false))
        whenever(dao.getUnreadByAccountAndFolder(any(), any())).thenReturn(flowOf(listOf(read, unread)))
        val result = repo.getUnreadByAccountAndFolder("a1", "INBOX").first()
        assertEquals(listOf(unread), result)
    }

    @Test
    fun `getUnifiedUnread filters unread`() = runTest {
        val read = baseEmail(id = "r", flags = EmailFlags(isRead = true))
        val unread = baseEmail(id = "u", flags = EmailFlags(isRead = false))
        whenever(dao.getUnifiedUnread(any(), any())).thenReturn(flowOf(listOf(read, unread)))
        val result = repo.getUnifiedUnread(listOf("a1"), 10).first()
        assertEquals(listOf(unread), result)
    }

    @Test
    fun `getDrafts filters drafts`() = runTest {
        val draft = baseEmail(id = "d", systemLabels = SystemLabels(draft = true))
        whenever(dao.getDrafts(any())).thenReturn(flowOf(listOf(draft)))
        val result = repo.getDrafts("a1").first()
        assertEquals(listOf(draft), result)
    }

    @Test
    fun `getFlagged filters flagged`() = runTest {
        val flagged = baseEmail(id = "f", flags = EmailFlags(isFlagged = true))
        whenever(dao.getFlagged(any())).thenReturn(flowOf(listOf(flagged)))
        val result = repo.getFlagged("a1").first()
        assertEquals(listOf(flagged), result)
    }
}
