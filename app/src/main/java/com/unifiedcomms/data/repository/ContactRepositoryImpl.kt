package com.unifiedcomms.data.repository

import com.unifiedcomms.data.db.dao.ContactDao
import com.unifiedcomms.data.model.UnifiedContact
import com.unifiedcomms.data.model.ContactSource
import kotlinx.coroutines.flow.Flow

class ContactRepositoryImpl(private val dao: ContactDao) : ContactRepository {
    override suspend fun insert(contact: UnifiedContact): Long = dao.insert(contact)

    override suspend fun insertAll(contacts: List<UnifiedContact>): List<Long> = dao.insertAll(contacts)

    override suspend fun update(contact: UnifiedContact): Int = dao.update(contact)

    override suspend fun delete(contact: UnifiedContact): Int = dao.delete(contact)

    override suspend fun deleteById(id: String): Int = dao.deleteById(id)

    override suspend fun getById(id: String): UnifiedContact? = dao.getById(id)

    override suspend fun getByUnifiedCommsId(id: String): UnifiedContact? = dao.getByUnifiedCommsId(id)

    override suspend fun getByEmail(email: String): UnifiedContact? = dao.getByEmail(email)

    override suspend fun getByPhone(phone: String): UnifiedContact? = dao.getByPhone(phone)

    override fun getByAccount(accountId: String): Flow<List<UnifiedContact>> = dao.getByAccount(accountId)

    override fun getBySourceAndAccount(source: ContactSource, accountId: String): Flow<List<UnifiedContact>> =
        dao.getBySourceAndAccount(source, accountId)

    override fun getUnifiedCommsContacts(): Flow<List<UnifiedContact>> = dao.getUnifiedCommsContacts()

    override fun getFavorites(): Flow<List<UnifiedContact>> = dao.getFavorites()

    override fun search(query: String, limit: Int): Flow<List<UnifiedContact>> = dao.search(query, limit)

    override suspend fun getNeedingSync(): List<UnifiedContact> = dao.getNeedingSync()

    override suspend fun getBySourceId(accountId: String, sourceId: String): UnifiedContact? = dao.getBySourceId(accountId, sourceId)

    override suspend fun getAllByAccountAndSource(accountId: String, source: ContactSource): List<UnifiedContact> =
        dao.getAllByAccountAndSource(accountId, source)

    @androidx.room.Transaction
    override suspend fun mergeContacts(primaryId: String, secondaryIds: List<String>) {
        dao.mergeContacts(primaryId, secondaryIds)
    }
}