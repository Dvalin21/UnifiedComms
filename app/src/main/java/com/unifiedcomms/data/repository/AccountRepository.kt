package com.unifiedcomms.data.repository

import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    suspend fun insert(account: Account): Long
    suspend fun update(account: Account): Int
    suspend fun delete(accountId: String): Int
    suspend fun getById(id: String): Account?
    suspend fun getByEmailAndType(email: String, type: AccountType): Account?
    fun getAllActive(): Flow<List<Account>>
    fun getByType(type: AccountType): Flow<List<Account>>
    suspend fun getDefault(): Account?
    fun getEmailSyncAccounts(): Flow<List<Account>>
    fun getCalendarSyncAccounts(): Flow<List<Account>>
    fun getTaskSyncAccounts(): Flow<List<Account>>
    suspend fun setDefault(accountId: String)
}