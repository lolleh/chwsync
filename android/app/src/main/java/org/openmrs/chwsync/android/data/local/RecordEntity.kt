package org.openmrs.chwsync.android.data.local

import androidx.room.Entity

/**
 * The single source of truth for the UI. Keyed by (type, uuid).
 *
 * [dirty] means "has local edits not yet acknowledged by the server". Pull never
 * overwrites a dirty row, which is what stops a background sync from clobbering
 * work a CHW typed while offline.
 */
@Entity(tableName = "records", primaryKeys = ["type", "uuid"])
data class RecordEntity(
    val type: String,
    val uuid: String,
    val version: Long?,
    val data: kotlinx.serialization.json.JsonObject,
    val deleted: Boolean,
    val dirty: Boolean,
    val changedAt: String,
)
