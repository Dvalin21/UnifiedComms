package com.unifiedcomms.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unifiedcomms.data.model.Account
import com.unifiedcomms.data.repository.AccountRepository
import com.unifiedcomms.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts

    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _syncProgress = MutableStateFlow<Int>(0)
    val syncProgress: StateFlow<Int> = _syncProgress

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            accountRepo.getAllActive().collect { accounts ->
                _accounts.value = accounts
            }
        }
    }

    fun getActiveAccounts(): List<Account> = _accounts.value.filter { it.isActive }

    fun getDefaultAccount(): Account? = _accounts.value.find { it.isDefault }

    suspend fun addAccount(account: Account) {
        accountRepo.insert(account)
        loadAccounts()
    }

    suspend fun removeAccount(accountId: String) {
        // Delete associated data and account
        accountRepo.delete(accountId)
        loadAccounts()
    }

    suspend fun setDefaultAccount(accountId: String) {
        accountRepo.setDefault(accountId)
    }

    suspend fun syncAllAccounts() {
        _isSyncing.value = true
        _syncProgress.value = 0
        val accounts = getActiveAccounts()
        var completed = 0

        for (account in accounts) {
            val result = syncManager.performFullSync(account)
            completed++
            _syncProgress.value = (completed * 100 / accounts.size)
        }

        _isSyncing.value = false
        _syncProgress.value = 100
    }

    suspend fun syncAccount(account: Account) {
        _isSyncing.value = true
        syncManager.performFullSync(account)
        _isSyncing.value = false
    }

    fun getAccountColor(accountId: String): Int {
        return com.unifiedcomms.ui.theme.AccountColors.getColorForAccount(accountId).container
    }
}