package org.openmrs.chwsync.android.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.openmrs.chwsync.android.data.ChwSyncJson

/**
 * Room stores payloads as JSON text; these converters bridge [JsonObject].
 * A corrupt blob downgrades to `null` rather than failing the whole query.
 */
class Converters {

    @TypeConverter
    fun fromJsonObject(value: JsonObject?): String? = value?.toString()

    @TypeConverter
    fun toJsonObject(value: String?): JsonObject? =
        value?.let { runCatching { ChwSyncJson.parseToJsonElement(it).jsonObject }.getOrNull() }
}
