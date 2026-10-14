package org.openmrs.chwsync.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.openmrs.chwsync.android.data.model.AppointmentPayload
import org.openmrs.chwsync.android.data.model.EntityType
import org.openmrs.chwsync.android.data.model.HouseholdPayload
import org.openmrs.chwsync.android.data.model.ReferralPayload
import org.openmrs.chwsync.android.data.model.decodePayload
import org.openmrs.chwsync.android.data.model.toPayloadObject
import org.openmrs.chwsync.android.di.AppContainer

/** Backs the Households, Appointments and Referrals tabs (similar list+create shape). */
class ListsViewModel(private val container: AppContainer) : ViewModel() {

    private val records = container.recordRepository

    val households: StateFlow<List<HouseholdPayload>> = records.observe(EntityType.HOUSEHOLD)
        .map { rows -> rows.mapNotNull { it.decodePayload<HouseholdPayload>() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appointments: StateFlow<List<AppointmentPayload>> = records.observe(EntityType.APPOINTMENT)
        .map { rows -> rows.mapNotNull { it.decodePayload<AppointmentPayload>() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val referrals: StateFlow<List<ReferralPayload>> = records.observe(EntityType.REFERRAL)
        .map { rows -> rows.mapNotNull { it.decodePayload<ReferralPayload>() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveHousehold(payload: HouseholdPayload, onSaved: () -> Unit = {}) =
        save(EntityType.HOUSEHOLD, payload.uuid, toPayloadObject(payload), onSaved)

    fun saveAppointment(payload: AppointmentPayload, onSaved: () -> Unit = {}) =
        save(EntityType.APPOINTMENT, payload.uuid, toPayloadObject(payload), onSaved)

    fun saveReferral(payload: ReferralPayload, onSaved: () -> Unit = {}) =
        save(EntityType.REFERRAL, payload.uuid, toPayloadObject(payload), onSaved)

    fun delete(type: EntityType, uuid: String) {
        viewModelScope.launch { records.delete(type, uuid) }
    }

    private fun save(type: EntityType, uuid: String, payload: JsonObject, onSaved: () -> Unit) {
        viewModelScope.launch {
            runCatching { records.save(type, uuid, payload) }
            onSaved()
        }
    }
}
