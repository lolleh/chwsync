package org.openmrs.chwsync.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openmrs.chwsync.android.data.model.EncounterPayload
import org.openmrs.chwsync.android.data.model.EntityType
import org.openmrs.chwsync.android.data.model.VisitPayload
import org.openmrs.chwsync.android.data.model.decodePayload
import org.openmrs.chwsync.android.data.model.toPayloadObject
import org.openmrs.chwsync.android.di.AppContainer

/** Backs the visit and encounter forms plus the clinical lists on the detail screen. */
class ClinicalViewModel(private val container: AppContainer) : ViewModel() {

    private val records = container.recordRepository

    val visits: StateFlow<List<VisitPayload>> = records.observe(EntityType.VISIT)
        .map { rows -> rows.mapNotNull { it.decodePayload<VisitPayload>() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val encounters: StateFlow<List<EncounterPayload>> = records.observe(EntityType.ENCOUNTER)
        .map { rows -> rows.mapNotNull { it.decodePayload<EncounterPayload>() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun visitsFor(patientUuid: String): Flow<List<VisitPayload>> =
        visits.map { all -> all.filter { it.patient == patientUuid } }

    fun encountersFor(patientUuid: String): Flow<List<EncounterPayload>> =
        encounters.map { all -> all.filter { it.patient == patientUuid } }

    fun saveVisit(payload: VisitPayload, onSaved: () -> Unit, onError: (String) -> Unit) =
        save(EntityType.VISIT, payload.uuid, toPayloadObject(payload), onSaved, onError)

    fun saveEncounter(payload: EncounterPayload, onSaved: () -> Unit, onError: (String) -> Unit) =
        save(EntityType.ENCOUNTER, payload.uuid, toPayloadObject(payload), onSaved, onError)

    fun voidVisit(uuid: String) {
        viewModelScope.launch { records.void(EntityType.VISIT, uuid) }
    }

    fun voidEncounter(uuid: String) {
        viewModelScope.launch { records.void(EntityType.ENCOUNTER, uuid) }
    }

    private fun save(
        type: EntityType,
        uuid: String,
        payload: kotlinx.serialization.json.JsonObject,
        onSaved: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                records.save(type, uuid, payload)
                onSaved()
            } catch (e: Exception) {
                onError(e.message ?: "Could not save")
            }
        }
    }
}
