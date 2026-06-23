package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.ConversationDao
import com.unifiedcomms.data.db.dao.MessageDao
import com.unifiedcomms.data.model.Message
import com.unifiedcomms.data.model.MessageStatus
import com.unifiedcomms.data.model.MessageType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MessagingRepositoryImplTest {

    private lateinit var msgDao: MessageDao
    private lateinit var convDao: ConversationDao
    private lateinit var repo: MessagingRepositoryImpl

    @Before
    fun setUp() {
        msgDao = mock()
        convDao = mock()
        repo = MessagingRepositoryImpl(msgDao, convDao)
    }

    @Test
    fun `searchMessages delegates to dao`() = runTest {
        val messages = listOf(
            Message(
                conversationId = "c1",
                senderId = "u1",
                recipientId = "u2",
                content = "Hello"
            )
        )
        whenever(msgDao.searchMessages(any(), any(), any())).thenReturn(flowOf(messages))
        val result = repo.searchMessages("Hello", listOf("c1"), 10).first()
        assertEquals(messages, result)
    }

    @Test
    fun `getMessagesByType delegates to dao`() = runTest {
        val messages = listOf(
            Message(
                conversationId = "c1",
                senderId = "u1",
                recipientId = "u2",
                content = "Hi"
            )
        )
        whenever(msgDao.getByType(any(), any())).thenReturn(flowOf(messages))
        val result = repo.getMessagesByType("c1", MessageType.TEXT).first()
        assertEquals(messages, result)
    }

    @Test
    fun `updateMessageStatus delegates to dao`() = runTest {
        repo.updateMessageStatus("m1", MessageStatus.SENT)
        verify(msgDao).updateStatus("m1", MessageStatus.SENT)
    }
}
