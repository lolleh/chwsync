package org.openmrs.chwsync.android.data.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.openmrs.chwsync.android.data.ChwSyncJson
import org.openmrs.chwsync.android.data.local.RecordEntity

/**
 * Decodes a stored record's JSON payload into a strongly typed model. Decoding
 * is best-effort: a malformed payload (for example written by a newer client)
 * yields `null` rather than crashing the list screen.
 */
inline fun <reified T> RecordEntity.decodePayload(): T? =
    runCatching { ChwSyncJson.decodeFromJsonElement<T>(data) }.getOrNull()

/** Encodes any serialisable payload into the JsonObject stored on disk / sent on the wire. */
inline fun <reified T> toPayloadObject(value: T): JsonObject =
    ChwSyncJson.encodeToJsonElement(value).jsonObject
