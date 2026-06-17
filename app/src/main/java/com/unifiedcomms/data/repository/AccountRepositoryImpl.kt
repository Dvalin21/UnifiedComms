package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.AccountDao
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import kotlinx.coroutines.flow.Flow

class AccountRepositoryImpl(private val dao: AccountDao) : AccountRepository {
    override suspend fun insert(account: Account): Long = dao.insert(account)

    override suspend fun update(account: Account): Int = dao.update(account)

    override suspend fun delete(accountId: String): Int = dao.deleteById(accountId)

    override suspend fun getById(id: String): Account? = dao.getById(id)

    override suspend fun getByEmailAndType(email: String, type: AccountType): Account? =
        dao.getByEmailAndType(email, type)

    override fun getAllActive(): Flow<List<Account>> = dao.getAllActive()

    override fun getByType(type: AccountType): Flow<List<Account>> = dao.getByType(type)

    override suspend fun getDefault(): Account? = dao.getDefault()

    override fun getEmailSyncAccounts(): Flow<List<Account>> = dao.getEmailSyncAccounts()

    override fun getCalendarSyncAccounts(): Flow<List<Account>> = dao.getCalendarSyncAccounts()

    override fun getTaskSyncAccounts(): Flow<List<Account>> = dao.getTaskSyncAccounts()

    override suspend fun setDefault(accountId: String) = dao.setDefault(accountId)
}