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
    fun `mergeContacts merges fields and deletes secondary`() = runTest {
        val primary = UnifiedContact(
            id = "1",
            displayName = "Primary",
            emails = listOf("a@example.com"),
            tags = listOf("tag1"),
            notes = null
        )
        val secondary = UnifiedContact(
            id = "2",
            displayName = "Secondary",
            emails = listOf("b@example.com"),
            tags = listOf("tag2"),
            notes = null
        )
        whenever(dao.getById("1")).thenReturn(primary)
        whenever(dao.getById("2")).thenReturn(secondary)

        repo.mergeContacts("1", listOf("2"))

        verify(dao).update(
            primary.copy(
                emails = listOf("a@example.com", "b@example.com"),
                tags = listOf("tag1", "tag2"),
                notes = "\n\n-- Merged from Secondary --\n"
            )
        )
        verify(dao).deleteById("2")
    }

    @Test
    fun `search delegates to dao`() = runTest {
        val contacts = listOf(
            UnifiedContact(id = "1", displayName = "Alice", emails = listOf("alice@example.com"))
        )
        whenever(dao.search("Alice", 10)).thenReturn(flowOf(contacts))
        val result = repo.search("Alice", 10).first()
        assertEquals(contacts, result)
    }
}
