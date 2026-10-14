package org.openmrs.chwsync.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Insert
    suspend fun insert(item: OutboxEntity): Long

    @Update
    suspend fun update(item: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC, id ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE uuid = :uuid ORDER BY createdAt ASC, id ASC")
    suspend fun forRecord(uuid: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE uuid = :uuid ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latestForRecord(uuid: String): OutboxEntity?

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM outbox")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<OutboxEntity>>
}
