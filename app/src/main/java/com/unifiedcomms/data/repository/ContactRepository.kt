package com.unifiedcomms.data.repository

import com.unifiedcomms.data.model.UnifiedContact
import com.unifiedcomms.data.model.ContactSource
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    suspend fun insert(contact: UnifiedContact): Long
    suspend fun insertAll(contacts: List<UnifiedContact>): List<Long>
    suspend fun update(contact: UnifiedContact): Int
    suspend fun delete(contact: UnifiedContact): Int
    suspend fun deleteById(id: String): Int
    suspend fun getById(id: String): UnifiedContact?
    suspend fun getByUnifiedCommsId(id: String): UnifiedContact?
    suspend fun getByEmail(email: String): UnifiedContact?
    suspend fun getByPhone(phone: String): UnifiedContact?
    fun getByAccount(accountId: String): Flow<List<UnifiedContact>>
    fun getBySourceAndAccount(source: ContactSource, accountId: String): Flow<List<UnifiedContact>>
    fun getUnifiedCommsContacts(): Flow<List<UnifiedContact>>
    fun getFavorites(): Flow<List<UnifiedContact>>
    fun search(query: String, limit: Int): Flow<List<UnifiedContact>>
    suspend fun getNeedingSync(): List<UnifiedContact>
    suspend fun mergeContacts(primaryId: String, secondaryIds: List<String>)
}