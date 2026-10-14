package org.openmrs.chwsync.android.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.JsonObject

/**
 * Pending local changes waiting to be pushed.
 *
 * [id] is a local autoincrement used for stable ordering; [clientChangeId] is the
 * idempotency key sent to the server and is stable across retries of the same
 * logical change. The server uses it to collapse duplicate deliveries.
 */
@Entity(
    tableName = "outbox",
    indices = [Index("uuid"), Index("clientChangeId", unique = true)],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientChangeId: String,
    val entityType: String,
    val uuid: String,
    val operation: String,
    val baseVersion: Long?,
    val payload: JsonObject?,
    val createdAt: Long,
    val attempts: Int = 0,
)
