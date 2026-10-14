package org.openmrs.chwsync.android.sync

import androidx.room.withTransaction
import kotlinx.serialization.json.JsonObject
import org.openmrs.chwsync.android.data.local.ChwSyncDatabase
import org.openmrs.chwsync.android.data.local.ConflictEntity
import org.openmrs.chwsync.android.data.local.RecordEntity
import org.openmrs.chwsync.android.data.local.SettingEntity
import org.openmrs.chwsync.android.data.model.EntityType
import org.openmrs.chwsync.android.data.model.Operation
import org.openmrs.chwsync.android.data.model.PushStatus
import org.openmrs.chwsync.android.data.remote.Change
import org.openmrs.chwsync.android.data.remote.PushItem
import org.openmrs.chwsync.android.data.remote.PushRequest
import org.openmrs.chwsync.android.data.remote.PushResult
import org.openmrs.chwsync.android.data.remote.ChwSyncApi
import org.openmrs.chwsync.android.data.remote.safeApiCall
import org.openmrs.chwsync.android.data.repo.SessionStore
import org.openmrs.chwsync.android.util.WireDate

/** Outcome of a full round trip, surfaced on the Sync screen. */
data class SyncReport(
    val pushed: Int,
    val pulled: Int,
    val conflicts: Int,
    val rejected: Int,
    val serverCursor: String?,
    val finishedAt: String,
)

/**
 * Pull/push engine implementing the documented sync semantics.
 *
 * The local store is always the source of truth for the UI; the engine only ever
 * reconciles it with the server. "Sync now" pushes first (to flush local intent)
 * then pulls (to receive everyone else's).
 */
class SyncEngine(
    private val api: ChwSyncApi,
    private val db: ChwSyncDatabase,
    private val session: SessionStore,
) {

    /**
     * Push then pull. A failure in push propagates so the worker can retry; we do
     * not pull over unsent work in a way that could confuse the user.
     */
    suspend fun sync(): SyncReport {
        val push = push()
        val pull = pull()
        return SyncReport(
            pushed = push,
            pulled = pull,
            conflicts = 0,
            rejected = 0,
            serverCursor = db.settings().get(KEY_CURSOR),
            finishedAt = WireDate.nowInstantIso(),
        )
    }

    // -----------------------------------------------------------------------
    // Push
    // -----------------------------------------------------------------------

    /** Sends one batch of outbox rows and applies the per-item results. */
    suspend fun push(): Int {
        val deviceUuid = session.ensureDeviceUuid()
        val batch = db.outbox().pending(MAX_PUSH_BATCH)
        if (batch.isEmpty()) return 0

        val request = PushRequest(
            deviceUuid = deviceUuid,
            changes = batch.map { item ->
                PushItem(
                    clientChangeId = item.clientChangeId,
                    entityType = item.entityType,
                    uuid = item.uuid,
                    operation = item.operation,
                    baseVersion = item.baseVersion,
                    payload = item.payload,
                )
            },
        )

        val response = safeApiCall { api.push(request) }
        val results: Map<String, PushResult> = response.results.associateBy { it.clientChangeId }

        var applied = 0
        db.withTransaction {
            for (item in batch) {
                // No result for this item -> leave it queued for the next attempt.
                val result = results[item.clientChangeId] ?: continue

                when (PushStatus.fromWire(result.status)) {
                    PushStatus.APPLIED, PushStatus.REPLAYED -> {
                        db.outbox().deleteById(item.id)
                        if (item.operation == Operation.DELETE.wire) {
                            db.records().delete(item.entityType, item.uuid)
                        } else {
                            db.records().upsert(
                                RecordEntity(
                                    type = item.entityType,
                                    uuid = item.uuid,
                                    version = result.serverVersion ?: item.baseVersion,
                                    data = result.data ?: item.payload ?: EMPTY,
                                    deleted = false,
                                    dirty = false,
                                    changedAt = WireDate.now(),
                                ),
                            )
                        }
                        applied++
                    }

                    PushStatus.CONFLICT -> {
                        // Server wins but keeps our data in the audit log.
                        db.outbox().deleteById(item.id)
                        db.records().upsert(
                            RecordEntity(
                                type = item.entityType,
                                uuid = item.uuid,
                                version = result.serverVersion,
                                data = result.data ?: item.payload ?: EMPTY,
                                deleted = false,
                                dirty = false,
                                changedAt = WireDate.now(),
                            ),
                        )
                        db.conflicts().insert(
                            ConflictEntity(
                                entityType = item.entityType,
                                uuid = item.uuid,
                                operation = item.operation,
                                resolution = PushStatus.CONFLICT.wire,
                                message = result.message,
                                localData = item.payload,
                                serverData = result.data,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    }

                    PushStatus.REJECTED -> {
                        db.outbox().deleteById(item.id)
                        if (item.operation == Operation.CREATE.wire) {
                            // The server refused a creation; drop the phantom record.
                            db.records().delete(item.entityType, item.uuid)
                        } else {
                            // Keep the last good server state, rebased, not dirty.
                            val existing = db.records().get(item.entityType, item.uuid)
                            if (existing != null) {
                                db.records().upsert(
                                    existing.copy(
                                        version = item.baseVersion ?: existing.version,
                                        dirty = false,
                                        changedAt = WireDate.now(),
                                    ),
                                )
                            }
                        }
                        db.conflicts().insert(
                            ConflictEntity(
                                entityType = item.entityType,
                                uuid = item.uuid,
                                operation = item.operation,
                                resolution = PushStatus.REJECTED.wire,
                                message = result.message,
                                localData = item.payload,
                                serverData = result.data,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    }

                    null -> Unit // Unknown status: leave it queued.
                }
            }
        }

        if (response.cursor.isNotBlank()) {
            db.settings().put(SettingEntity(KEY_CURSOR, response.cursor))
        }
        return applied
    }

    // -----------------------------------------------------------------------
    // Pull
    // -----------------------------------------------------------------------

    /** Pages through changes from the stored cursor until `hasMore` is false. */
    suspend fun pull(): Int {
        var since = db.settings().get(KEY_CURSOR)
        var total = 0
        var pages = 0

        while (pages++ < MAX_PULL_PAGES) {
            val response = safeApiCall {
                api.pull(since = since, types = ENTITY_TYPES, limit = PAGE_LIMIT)
            }
            db.withTransaction {
                response.changes.forEach { applyChange(it) }
            }
            total += response.changes.size
            if (response.cursor.isNotBlank()) {
                db.settings().put(SettingEntity(KEY_CURSOR, response.cursor))
                since = response.cursor
            }
            if (!response.hasMore) break
        }
        return total
    }

    private suspend fun applyChange(change: Change) {
        val local = db.records().get(change.entityType, change.uuid)

        // Never clobber unsent local work; the outbox will reconcile it later.
        if (local != null && local.dirty) return

        if (change.deleted) {
            if (local != null) db.records().delete(change.entityType, change.uuid)
            return
        }

        db.records().upsert(
            RecordEntity(
                type = change.entityType,
                uuid = change.uuid,
                version = change.version,
                data = change.data ?: EMPTY,
                deleted = false,
                dirty = false,
                changedAt = WireDate.now(),
            ),
        )
    }

    companion object {
        // Per-device cursor; there is one device per install, persisted in settings.
        const val KEY_CURSOR = "pull_cursor"
        const val PAGE_LIMIT = 100
        const val MAX_PUSH_BATCH = 50
        const val MAX_PULL_PAGES = 100

        private val EMPTY = JsonObject(emptyMap())
        private val ENTITY_TYPES: String = EntityType.allWires.joinToString(",")
    }
}
