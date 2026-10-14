package org.openmrs.chwsync.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Domain enums mirroring the string values on the wire. We store the lowercase
 * `wire` value in Room and in DTOs so a schema change on the server only needs a
 * single mapping here.
 */
enum class EntityType(val wire: String) {
    PATIENT("patient"),
    VISIT("visit"),
    ENCOUNTER("encounter"),
    HOUSEHOLD("household"),
    APPOINTMENT("appointment"),
    REFERRAL("referral"),
    ;

    companion object {
        fun fromWire(value: String): EntityType? = entries.firstOrNull { it.wire == value }
        val allWires: List<String> = entries.map { it.wire }
    }
}

/** Operations the client is allowed to send on push. */
enum class Operation(val wire: String) {
    CREATE("CREATE"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    ;

    companion object {
        fun fromWire(value: String): Operation? = entries.firstOrNull { it.wire == value.uppercase() }
    }
}

/** Server push result statuses. */
enum class PushStatus(val wire: String) {
    APPLIED("APPLIED"),
    CONFLICT("CONFLICT"),
    REJECTED("REJECTED"),
    REPLAYED("REPLAYED"),
    ;

    companion object {
        fun fromWire(value: String): PushStatus? = entries.firstOrNull { it.wire == value.uppercase() }
    }
}

// ---------------------------------------------------------------------------
// Payload models. These are serialised into the `data` / `payload` JsonObject
// on the wire and stored verbatim as JSON in Room's RecordEntity.
// ---------------------------------------------------------------------------

@Serializable
data class PersonName(
    val uuid: String,
    val givenName: String,
    val middleName: String? = null,
    val familyName: String,
    val preferred: Boolean = true,
)

@Serializable
data class PersonAddress(
    val uuid: String,
    val address1: String? = null,
    val address2: String? = null,
    val cityVillage: String? = null,
    val countyDistrict: String? = null,
    val stateProvince: String? = null,
    val country: String? = null,
    val postalCode: String? = null,
)

@Serializable
data class PatientIdentifier(
    val uuid: String,
    val identifier: String,
    val identifierType: String,
    val location: String? = null,
    val preferred: Boolean = true,
)

@Serializable
data class PatientPayload(
    val uuid: String,
    val version: Long? = null,
    val voided: Boolean = false,
    val gender: String? = null,
    val birthdate: String? = null,
    val birthdateEstimated: Boolean? = null,
    val dead: Boolean? = null,
    val deathDate: String? = null,
    val names: List<PersonName> = emptyList(),
    val addresses: List<PersonAddress> = emptyList(),
    val identifiers: List<PatientIdentifier> = emptyList(),
    val attributes: List<JsonObject> = emptyList(),
) {
    fun displayName(): String =
        names.firstOrNull { it.preferred }?.let { "${it.givenName} ${it.familyName}".trim() }
            ?: names.firstOrNull()?.let { "${it.givenName} ${it.familyName}".trim() }
            ?: uuid
}

@Serializable
data class VisitPayload(
    val uuid: String,
    val version: Long? = null,
    val voided: Boolean = false,
    val patient: String,
    val visitType: String,
    val location: String,
    val indication: String? = null,
    val startDatetime: String,
    val stopDatetime: String? = null,
)

@Serializable
data class EncounterProvider(
    val provider: String,
    val role: String? = null,
)

@Serializable
data class ObsPayload(
    val uuid: String,
    val concept: String,
    val obsDatetime: String,
    val person: String? = null,
    val location: String? = null,
    val comment: String? = null,
    val valueNumeric: Double? = null,
    val valueText: String? = null,
    val valueCoded: String? = null,
    val valueDatetime: String? = null,
    val valueBoolean: Boolean? = null,
    val voided: Boolean = false,
    val groupMembers: List<ObsPayload> = emptyList(),
)

@Serializable
data class EncounterPayload(
    val uuid: String,
    val version: Long? = null,
    val voided: Boolean = false,
    val patient: String,
    val encounterType: String,
    val location: String,
    val form: String? = null,
    val visit: String? = null,
    val encounterDatetime: String,
    val providers: List<EncounterProvider> = emptyList(),
    val obs: List<ObsPayload> = emptyList(),
)

@Serializable
data class HouseholdMember(
    val patient: String,
    val relationship: String? = null,
    val headOfHousehold: Boolean = false,
)

@Serializable
data class HouseholdPayload(
    val uuid: String,
    val version: Long? = null,
    val voided: Boolean = false,
    val name: String,
    val village: String,
    val location: String? = null,
    val headPatient: String? = null,
    val members: List<HouseholdMember> = emptyList(),
)

@Serializable
data class AppointmentPayload(
    val uuid: String,
    val version: Long? = null,
    val voided: Boolean = false,
    val patient: String,
    val provider: String? = null,
    val location: String? = null,
    val appointmentDate: String,
    val reason: String? = null,
    val status: String? = null,
    val notes: String? = null,
)

@Serializable
data class ReferralPayload(
    val uuid: String,
    val version: Long? = null,
    val voided: Boolean = false,
    val patient: String,
    val referralDate: String,
    val facility: String? = null,
    val reason: String? = null,
    val urgency: String? = null,
    val status: String? = null,
    val notes: String? = null,
)
