package org.openmrs.chwsync.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openmrs.chwsync.android.data.model.EntityType
import org.openmrs.chwsync.android.data.model.PatientPayload
import org.openmrs.chwsync.android.data.model.decodePayload
import org.openmrs.chwsync.android.data.model.toPayloadObject
import org.openmrs.chwsync.android.di.AppContainer

/**
 * Patients list + save/void. The list is decoded from stored JSON on the fly;
 * patient volumes for a CHW catchment are small enough that this is cheaper and
 * simpler than a secondary normalised table.
 */
class PatientsViewModel(private val container: AppContainer) : ViewModel() {

    private val records = container.recordRepository

    val patients: StateFlow<List<PatientPayload>> = records.observe(EntityType.PATIENT)
        .map { rows -> rows.mapNotNull { it.decodePayload<PatientPayload>() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun patient(uuid: String): Flow<PatientPayload?> =
        records.observeOne(EntityType.PATIENT, uuid).map { it?.decodePayload<PatientPayload>() }

    fun save(payload: PatientPayload, onSaved: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                records.save(EntityType.PATIENT, payload.uuid, toPayloadObject(payload))
                onSaved()
            } catch (e: Exception) {
                onError(e.message ?: "Could not save patient")
            }
        }
    }

    fun delete(uuid: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            records.delete(EntityType.PATIENT, uuid)
            onDeleted()
        }
    }

    fun void(uuid: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            records.void(EntityType.PATIENT, uuid)
            onDone()
        }
    }
}
