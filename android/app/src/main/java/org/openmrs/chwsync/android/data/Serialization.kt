package org.openmrs.chwsync.android.data

import kotlinx.serialization.json.Json

/**
 * Shared JSON configuration.
 *
 * - [Json.explicitNulls] is disabled so optional payload fields (e.g. `cityVillage`)
 *   are omitted rather than serialised as `null`, matching the module contract.
 * - [Json.encodeDefaults] is enabled so required booleans/lists such as `voided`
 *   and `names` are always present.
 * - [Json.ignoreUnknownKeys] keeps the client forward compatible with server
 *   additions (e.g. new payload fields on entities we don't fully model).
 */
val ChwSyncJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}
