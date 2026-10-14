package org.openmrs.chwsync.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.json.JsonObject

/**
 * Audit trail of server-resolved conflicts / rejections. Kept so a supervisor can
 * review what the server decided during an unattended background sync.
 */
@Entity(tableName = "conflicts")
data class ConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val uuid: String,
    val operation: String,
    val resolution: String,
    val message: String?,
    val localData: JsonObject?,
    val serverData: JsonObject?,
    val createdAt: Long,
)
