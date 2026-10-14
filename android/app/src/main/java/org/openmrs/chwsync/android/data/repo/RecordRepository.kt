package org.openmrs.chwsync.android.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import org.openmrs.chwsync.android.data.local.ChwSyncDatabase
import org.openmrs.chwsync.android.data.local.OutboxEntity
import org.openmrs.chwsync.android.data.local.RecordEntity
import org.openmrs.chwsync.android.data.model.EntityType
import org.openmrs.chwsync.android.data.model.Operation
import org.openmrs.chwsync.android.util.WireDate

/**
 * The write side of the offline-first store.
 *
 * Every user mutation does two things atomically: it updates the local `records`
 * table (so the UI is instantly consistent) and it enqueues/coalesces an outbox
 * row (so the change eventually reaches the server). There is no direct
 * network dependency here.
 */
class RecordRepository(private val db: ChwSyncDatabase) {

    fun observe(type: EntityType): Flow<List<RecordEntity>> = db.records().observeByType(type.wire)

    fun observeOne(type: EntityType, uuid: String): Flow<RecordEntity?> =
        db.records().observe(type.wire, uuid)

    suspend fun get(type: EntityType, uuid: String): RecordEntity? = db.records().get(type.wire, uuid)

    /**
     * Persists a payload and enqueues the correct operation:
     *  - a brand-new record (no known server version) is enqueued as CREATE;
     *  - an existing record is enqueued as UPDATE carrying its baseVersion;
     *  - if an unsent CREATE already exists, the new payload overwrites it in
     *    place (coalescing) instead of stacking a second change.
     */
    suspend fun save(type: EntityType, uuid: String, payload: JsonObject) {
        db.withTransaction {
            val existing = db.records().get(type.wire, uuid)
            val pendingCreate = db.outbox()
                .forRecord(uuid)
                .firstOrNull { it.operation == Operation.CREATE.wire }

            db.records().upsert(
                RecordEntity(
                    type = type.wire,
                    uuid = uuid,
                    version = existing?.version,
                    data = payload,
                    deleted = false,
                    dirty = true,
                    changedAt = WireDate.now(),
                ),
            )

            when {
                pendingCreate != null -> {
                    // Unsent CREATE stays a CREATE with the freshest payload.
                    db.outbox().update(pendingCreate.copy(payload = payload, entityType = type.wire))
                }

                existing?.version == null -> {
                    enqueue(
                        OutboxEntity(
                            clientChangeId = newClientChangeId(uuid, Operation.CREATE),
                            entityType = type.wire,
                            uuid = uuid,
                            operation = Operation.CREATE.wire,
                            baseVersion = null,
                            payload = payload,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }

                else -> {
                    enqueue(
                        OutboxEntity(
                            clientChangeId = newClientChangeId(uuid, Operation.UPDATE),
                            entityType = type.wire,
                            uuid = uuid,
                            operation = Operation.UPDATE.wire,
                            baseVersion = existing.version,
                            payload = payload,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Deletes a record locally and records the right intent:
     *  - a never-synced CREATE is simply dropped (local row + outbox row);
     *  - a synced record (or an existing unsent UPDATE) becomes a DELETE with
     *    baseVersion equal to the last known server version.
     */
    suspend fun delete(type: EntityType, uuid: String) {
        db.withTransaction {
            val local = db.records().get(type.wire, uuid) ?: return@withTransaction
            val rows = db.outbox().forRecord(uuid)
            val pendingCreate = rows.firstOrNull { it.operation == Operation.CREATE.wire }
            val pendingUpdate = rows.firstOrNull { it.operation == Operation.UPDATE.wire }

            db.records().delete(type.wire, uuid)

            when {
                pendingCreate != null -> db.outbox().deleteById(pendingCreate.id)

                pendingUpdate != null -> db.outbox().update(
                    pendingUpdate.copy(
                        operation = Operation.DELETE.wire,
                        baseVersion = pendingUpdate.baseVersion ?: local.version,
                        payload = null,
                        clientChangeId = newClientChangeId(uuid, Operation.DELETE),
                    ),
                )

                else -> enqueue(
                    OutboxEntity(
                        clientChangeId = newClientChangeId(uuid, Operation.DELETE),
                        entityType = type.wire,
                        uuid = uuid,
                        operation = Operation.DELETE.wire,
                        baseVersion = local.version,
                        payload = null,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /** Convenience for OpenMRS "void" semantics: set the flag and save as UPDATE. */
    suspend fun void(type: EntityType, uuid: String) {
        val local = db.records().get(type.wire, uuid) ?: return
        val voided = JsonObject(local.data.toMutableMap().apply {
            put("voided", kotlinx.serialization.json.JsonPrimitive(true))
        })
        save(type, uuid, voided)
    }

    /**
     * Merges a new outbox row into an existing one for the same record when the
     * operation is compatible. [OutboxEntity.clientChangeId] is only regenerated
     * when the logical operation changes (e.g. UPDATE -> DELETE).
     */
    private suspend fun enqueue(incoming: OutboxEntity) {
        val existing = db.outbox().latestForRecord(incoming.uuid)
        if (existing == null) {
            db.outbox().insert(incoming)
            return
        }
        when (existing.operation) {
            Operation.CREATE.wire -> db.outbox().update(
                existing.copy(payload = incoming.payload, entityType = incoming.entityType),
            )

            Operation.UPDATE.wire -> db.outbox().update(
                existing.copy(
                    operation = incoming.operation,
                    payload = incoming.payload,
                    baseVersion = existing.baseVersion ?: incoming.baseVersion,
                    entityType = incoming.entityType,
                ),
            )

            else -> db.outbox().update(
                existing.copy(
                    operation = incoming.operation,
                    payload = incoming.payload,
                    baseVersion = incoming.baseVersion,
                    entityType = incoming.entityType,
                ),
            )
        }
    }

    private fun newClientChangeId(uuid: String, operation: Operation): String =
        "$uuid:${operation.wire}:${System.currentTimeMillis()}"
}
