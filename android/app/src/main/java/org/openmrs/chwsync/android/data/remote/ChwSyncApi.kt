package org.openmrs.chwsync.android.data.remote

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit surface for the CHW module. Relative paths are combined with the
 * dynamically configured base URL (`<server>/ws/chwsync/v1/`) by
 * [BaseUrlInterceptor]; Retrofit's own baseUrl is just a placeholder.
 */
interface ChwSyncApi {

    @GET("manifest")
    suspend fun getManifest(): ManifestResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("auth/me")
    suspend fun me(): UserResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("sync/pull")
    suspend fun pull(
        @Query("since") since: String?,
        @Query("types") types: String?,
        @Query("limit") limit: Int?,
    ): PullResponse

    @POST("sync/push")
    suspend fun push(@Body body: PushRequest): PushResponse

    @GET("sync/record/{type}/{uuid}")
    suspend fun getRecord(
        @Path("type") type: String,
        @Path("uuid") uuid: String,
    ): JsonObject

    @GET("sync/limits")
    suspend fun limits(): LimitsResponse

    @GET("reference")
    suspend fun reference(): ReferenceResponse

    @GET("reference/concepts")
    suspend fun concepts(@Query("q") query: String?): List<ConceptRef>

    @GET("devices")
    suspend fun devices(): List<DeviceView>

    @POST("devices/{uuid}/deactivate")
    suspend fun deactivateDevice(@Path("uuid") uuid: String)

    @POST("devices/{uuid}/activate")
    suspend fun activateDevice(@Path("uuid") uuid: String)
}
