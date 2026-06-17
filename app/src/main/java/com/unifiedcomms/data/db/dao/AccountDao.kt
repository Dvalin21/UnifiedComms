package com.unifiedcomms.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.model.AccountType

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<Account>): List<Long>

    @Update
    suspend fun update(account: Account): Int

    @Delete
    suspend fun delete(account: Account): Int

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): Account?

    @Query("SELECT * FROM accounts WHERE email = :email AND accountType = :type")
    suspend fun getByEmailAndType(email: String, type: AccountType): Account?

    @Query("SELECT * FROM accounts WHERE accountType = :type AND isActive = 1")
    fun getByType(type: AccountType): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY isDefault DESC, name ASC")
    fun getAllActive(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 AND isDefault = 1 LIMIT 1")
    suspend fun getDefault(): Account?

    @Query("SELECT * FROM accounts WHERE isActive = 1 AND syncConfig.syncEmail = 1")
    fun getEmailSyncAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 AND syncConfig.syncCalendar = 1")
    fun getCalendarSyncAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 AND syncConfig.syncTasks = 1")
    fun getTaskSyncAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE lastSyncAt IS NOT NULL ORDER BY lastSyncAt DESC LIMIT :limit")
    suspend fun getRecentlySynced(limit: Int): List<Account>

    @Transaction
    @Query("UPDATE accounts SET isDefault = 0 WHERE isDefault = 1")
    suspend fun clearDefault()

    @Transaction
    suspend fun setDefault(accountId: String) {
        clearDefault()
        val account = getById(accountId)
        if (account != null) {
            update(account.copy(isDefault = true, updatedAt = kotlinx.datetime.Instant.now()))
        }
    }
}