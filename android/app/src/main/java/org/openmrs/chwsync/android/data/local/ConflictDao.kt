package org.openmrs.chwsync.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConflictDao {

    @Insert
    suspend fun insert(conflict: ConflictEntity): Long

    @Query("SELECT * FROM conflicts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ConflictEntity>>

    @Query("SELECT COUNT(*) FROM conflicts")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM conflicts")
    suspend fun clear()
}
