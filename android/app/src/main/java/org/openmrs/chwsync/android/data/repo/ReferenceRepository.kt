package org.openmrs.chwsync.android.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openmrs.chwsync.android.data.local.ChwSyncDatabase
import org.openmrs.chwsync.android.data.remote.ChwSyncApi
import org.openmrs.chwsync.android.data.remote.ConceptRef
import org.openmrs.chwsync.android.data.remote.ReferenceResponse
import org.openmrs.chwsync.android.data.remote.safeApiCall

/**
 * Caches the module's reference data (locations, identifier types, visit/
 * encounter types, provider) and exposes concept search. Reference data changes
 * rarely, so it is held in memory for the session and refreshed on demand from
 * the Sync screen rather than on every form open.
 */
class ReferenceRepository(
    private val api: ChwSyncApi,
    private val db: ChwSyncDatabase,
) {

    private val _reference = MutableStateFlow<ReferenceResponse?>(null)
    val reference: StateFlow<ReferenceResponse?> = _reference.asStateFlow()

    suspend fun refresh(): ReferenceResponse {
        val response = safeApiCall { api.reference() }
        _reference.value = response
        return response
    }

    fun cached(): ReferenceResponse? = _reference.value

    suspend fun searchConcepts(query: String): List<ConceptRef> =
        safeApiCall { api.concepts(query.ifBlank { null }) }

    suspend fun limits() = safeApiCall { api.limits() }
}
