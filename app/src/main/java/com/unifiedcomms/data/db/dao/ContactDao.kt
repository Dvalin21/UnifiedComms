package com.unifiedcomms.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.unifiedcomms.data.model.UnifiedContact
import kotlinx.coroutines.flow.Flow

// ponytail: this file was named MessageDao.kt but carried two DAOs — the
// `MessageDao` for the removed chat feature (dead) and the live `ContactDao`.
// Deleting the file to remove the dead DAO would have taken contacts with it.
// It now holds only ContactDao and is named for what it contains.

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: UnifiedContact): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<UnifiedContact>): List<Long>

    @Update
    suspend fun update(contact: UnifiedContact): Int

    @Delete
    suspend fun delete(contact: UnifiedContact): Int

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: String): UnifiedContact?

    @Query("SELECT * FROM contacts WHERE unifiedCommsId = :id")
    suspend fun getByUnifiedCommsId(id: String): UnifiedContact?

    @Query("SELECT * FROM contacts WHERE EXISTS (SELECT 1 FROM json_each(contacts.emails) WHERE value = :email)")
    suspend fun getByEmail(email: String): UnifiedContact?

    @Query("SELECT * FROM contacts WHERE EXISTS (SELECT 1 FROM json_each(contacts.phoneNumbers) WHERE value = :phone)")
    suspend fun getByPhone(phone: String): UnifiedContact?

    @Query("SELECT * FROM contacts WHERE accountId = :accountId ORDER BY displayName ASC")
    fun getByAccount(accountId: String): Flow<List<UnifiedContact>>

    @Query("SELECT * FROM contacts WHERE source = :source AND accountId = :accountId ORDER BY displayName ASC")
    fun getBySourceAndAccount(source: com.unifiedcomms.data.model.ContactSource, accountId: String): Flow<List<UnifiedContact>>

    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun getUnifiedCommsContacts(): Flow<List<UnifiedContact>>

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 ORDER BY displayName ASC")
    fun getFavorites(): Flow<List<UnifiedContact>>

    @Query("SELECT * FROM contacts WHERE (displayName LIKE :query OR EXISTS (SELECT 1 FROM json_each(contacts.emails) WHERE value LIKE :query) OR EXISTS (SELECT 1 FROM json_each(contacts.phoneNumbers) WHERE value LIKE :query)) LIMIT :limit")
    fun search(query: String, limit: Int): Flow<List<UnifiedContact>>

    @Query("SELECT * FROM contacts WHERE needsSync = 1")
    suspend fun getNeedingSync(): List<UnifiedContact>

    @Query("SELECT * FROM contacts WHERE accountId = :accountId AND sourceId = :sourceId LIMIT 1")
    suspend fun getBySourceId(accountId: String, sourceId: String): UnifiedContact?

    @Query("SELECT * FROM contacts WHERE accountId = :accountId AND source = :source")
    suspend fun getAllByAccountAndSource(accountId: String, source: com.unifiedcomms.data.model.ContactSource): List<UnifiedContact>

    @Transaction
    suspend fun mergeContacts(primaryId: String, secondaryIds: List<String>) {
        var current = getById(primaryId) ?: return
        secondaryIds.forEach { secondaryId ->
            val secondary = getById(secondaryId) ?: return@forEach
            val merged = current.copy(
                emails = (current.emails + secondary.emails).distinct(),
                phoneNumbers = (current.phoneNumbers + secondary.phoneNumbers).distinct(),
                addresses = (current.addresses + secondary.addresses).distinct(),
                websites = (current.websites + secondary.websites).distinct(),
                notes = "${current.notes ?: ""}\n\n-- Merged from ${secondary.displayName} --\n${secondary.notes ?: ""}",
                tags = (current.tags + secondary.tags).distinct()
            )
            update(merged)
            deleteById(secondaryId)
            // Chain each merge onto the row just written; computing from the
            // pre-loop snapshot would silently discard earlier merges.
            current = merged
        }
    }
}
