package org.openmrs.chwsync.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    @Query("SELECT * FROM records WHERE type = :type AND deleted = 0 ORDER BY changedAt DESC")
    fun observeByType(type: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE type = :type AND uuid = :uuid LIMIT 1")
    fun observe(type: String, uuid: String): Flow<RecordEntity?>

    @Query("SELECT * FROM records WHERE type = :type AND uuid = :uuid LIMIT 1")
    suspend fun get(type: String, uuid: String): RecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: RecordEntity)

    @Query("DELETE FROM records WHERE type = :type AND uuid = :uuid")
    suspend fun delete(type: String, uuid: String)

    @Query("SELECT COUNT(*) FROM records WHERE type = :type AND deleted = 0")
    fun countByType(type: String): Flow<Int>
}
