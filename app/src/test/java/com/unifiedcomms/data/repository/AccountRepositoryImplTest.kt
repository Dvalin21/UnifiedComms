package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.AccountDao
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.security.CryptoManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AccountRepositoryImplTest {

    private lateinit var dao: AccountDao
    private lateinit var crypto: CryptoManager
    private lateinit var repo: AccountRepositoryImpl

    @Before
    fun setUp() {
        dao = mock()
        crypto = mock()
        whenever(crypto.encryptAuthConfig(any())).thenAnswer { it.arguments[0] }
        repo = AccountRepositoryImpl(dao, crypto)
    }

    @Test
    fun `getEmailSyncAccounts filters enabled`() = runTest {
        val account = Account.createGoogle("user@example.com")
        whenever(dao.getEmailSyncAccounts()).thenReturn(flowOf(listOf(account)))
        val result = repo.getEmailSyncAccounts().first()
        org.junit.Assert.assertEquals(listOf(account), result)
    }

    @Test
    fun `getCalendarSyncAccounts filters enabled`() = runTest {
        val account = Account.createGoogle("user@example.com")
        whenever(dao.getCalendarSyncAccounts()).thenReturn(flowOf(listOf(account)))
        val result = repo.getCalendarSyncAccounts().first()
        org.junit.Assert.assertEquals(listOf(account), result)
    }

    @Test
    fun `getTaskSyncAccounts filters enabled`() = runTest {
        val account = Account.createGoogle("user@example.com")
        whenever(dao.getTaskSyncAccounts()).thenReturn(flowOf(listOf(account)))
        val result = repo.getTaskSyncAccounts().first()
        org.junit.Assert.assertEquals(listOf(account), result)
    }

    @Test
    fun `delegates insert and update`() = runTest {
        val account = Account.createGoogle("user@example.com")
        repo.insert(account)
        verify(dao).insert(account.copy(authConfig = account.authConfig))
        repo.update(account)
        verify(dao).update(account.copy(authConfig = account.authConfig))
    }
}
