package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.ContactDao
import com.unifiedcomms.data.model.UnifiedContact
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContactRepositoryImplTest {

    private lateinit var dao: ContactDao
    private lateinit var repo: ContactRepositoryImpl

    @Before
    fun setUp() {
        dao = mock()
        repo = ContactRepositoryImpl(dao)
    }

    @Test
    fun `mergeContacts delegates to dao`() = runTest {
        repo.mergeContacts("1", listOf("2"))

        verify(dao).mergeContacts("1", listOf("2"))
    }

    @Test
    fun `search delegates to dao`() = runTest {
        val contacts = listOf(
            UnifiedContact(id = "1", displayName = "Alice", emails = listOf("alice@example.com"))
        )
        whenever(dao.search("%Alice%", 10)).thenReturn(flowOf(contacts))
        val result = repo.search("Alice", 10).first()
        assertEquals(contacts, result)
    }

    @Test
    fun `People flow includes local and CardDAV contacts`() = runTest {
        val contacts = listOf(
            UnifiedContact(id = "local", displayName = "Local"),
            UnifiedContact(
                id = "carddav",
                displayName = "CardDAV",
                source = com.unifiedcomms.data.model.ContactSource.CARDDAV,
                accountId = "account"
            )
        )
        whenever(dao.getUnifiedCommsContacts()).thenReturn(flowOf(contacts))
        assertEquals(contacts, repo.getUnifiedCommsContacts().first())
    }
}
