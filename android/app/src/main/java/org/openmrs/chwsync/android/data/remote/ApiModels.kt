package org.openmrs.chwsync.android.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DTOs for the `<server>/ws/chwsync/v1` contract. JSON field names match the
 * contract exactly; nullable optional fields default to null so
 * `explicitNulls = false` omits them on the wire.
 */

@Serializable
data class ManifestResponse(
    val module: String,
    val apiVersion: String,
    val namespace: String,
    val entityTypes: List<String> = emptyList(),
    val serverTime: String,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceUuid: String,
    val deviceName: String,
    val platform: String = "android",
    val appVersion: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val deviceUuid: String,
    val userUuid: String,
    val username: String,
    val displayName: String,
    val privileges: List<String> = emptyList(),
    val expiresAt: String? = null,
    val serverTime: String,
)

@Serializable
data class UserResponse(
    val userUuid: String,
    val username: String,
    val displayName: String,
    val privileges: List<String> = emptyList(),
    val serverTime: String? = null,
)

@Serializable
data class Change(
    val revision: Long,
    val entityType: String,
    val uuid: String,
    val operation: String,
    val version: Long? = null,
    val deleted: Boolean = false,
    val changedAt: String,
    val data: JsonObject? = null,
)

@Serializable
data class PullResponse(
    val cursor: String,
    val hasMore: Boolean = false,
    val changes: List<Change> = emptyList(),
)

@Serializable
data class PushItem(
    val clientChangeId: String,
    val entityType: String,
    val uuid: String,
    val operation: String,
    val baseVersion: Long? = null,
    val payload: JsonObject? = null,
)

@Serializable
data class PushRequest(
    val deviceUuid: String,
    val changes: List<PushItem> = emptyList(),
)

@Serializable
data class PushResult(
    val clientChangeId: String,
    val entityType: String,
    val uuid: String,
    val status: String,
    val serverVersion: Long? = null,
    val message: String? = null,
    val data: JsonObject? = null,
)

@Serializable
data class PushResponse(
    val cursor: String,
    val applied: Int = 0,
    val conflicts: Int = 0,
    val rejected: Int = 0,
    val results: List<PushResult> = emptyList(),
)

@Serializable
data class LimitsResponse(
    val pageSize: Int,
    val maxPushBatch: Int,
    val serverTime: String,
)

@Serializable
data class ApiError(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class RefNamed(
    val uuid: String,
    val name: String,
)

@Serializable
data class IdentifierTypeRef(
    val uuid: String,
    val name: String,
    val format: String? = null,
    val required: Boolean = false,
)

@Serializable
data class ConceptRef(
    val uuid: String,
    val name: String,
    val datatype: String? = null,
    val conceptClass: String? = null,
)

@Serializable
data class ReferenceResponse(
    val locations: List<RefNamed> = emptyList(),
    val identifierTypes: List<IdentifierTypeRef> = emptyList(),
    val visitTypes: List<RefNamed> = emptyList(),
    val encounterTypes: List<RefNamed> = emptyList(),
    val provider: RefNamed? = null,
)

@Serializable
data class DeviceView(
    val uuid: String,
    val name: String? = null,
    val platform: String? = null,
    val active: Boolean = true,
    val lastSeen: String? = null,
)
