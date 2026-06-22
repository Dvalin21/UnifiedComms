package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.AccountDao
import com.unifiedcomms.data.model.Account
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

class AccountRepositoryImplTest {

    private lateinit var dao: AccountDao
    private lateinit var repo: AccountRepositoryImpl

    @Before
    fun setUp() {
        dao = mock()
        repo = AccountRepositoryImpl(dao)
    }

    @Test
    fun `getEmailSyncAccounts filters enabled`() = runTest {
        val account = Account.createGoogle("user@example.com")
        whenever(dao.getEmailSyncAccounts()).thenReturn(flowOf(listOf(account)))
        val result = repo.getEmailSyncAccounts().first()
        assertEquals(listOf(account), result)
    }

    @Test
    fun `getCalendarSyncAccounts filters enabled`() = runTest {
        val account = Account.createGoogle("user@example.com")
        whenever(dao.getCalendarSyncAccounts()).thenReturn(flowOf(listOf(account)))
        val result = repo.getCalendarSyncAccounts().first()
        assertEquals(listOf(account), result)
    }

    @Test
    fun `getTaskSyncAccounts filters enabled`() = runTest {
        val account = Account.createGoogle("user@example.com")
        whenever(dao.getTaskSyncAccounts()).thenReturn(flowOf(listOf(account)))
        val result = repo.getTaskSyncAccounts().first()
        assertEquals(listOf(account), result)
    }

    @Test
    fun `delegates insert and update`() = runTest {
        val account = Account.createGoogle("user@example.com")
        repo.insert(account)
        verify(dao).insert(account)
        repo.update(account)
        verify(dao).update(account)
    }
}
