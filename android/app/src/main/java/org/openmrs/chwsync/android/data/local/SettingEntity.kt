package org.openmrs.chwsync.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Simple key/value store for small bits of sync bookkeeping (pull cursor,
 * cached reference-data ETag, etc.) that don't deserve their own table.
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
