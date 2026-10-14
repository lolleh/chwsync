package org.openmrs.chwsync.android.data.remote

import java.io.IOException
import retrofit2.HttpException
import org.openmrs.chwsync.android.data.ChwSyncJson

/**
 * Normalised API failure. Server error bodies are `{code, message}`; transport
 * failures become [CODE_NETWORK]. Callers can branch on [httpStatus] to detect
 * expired sessions (401).
 */
class ChwSyncApiException(
    val code: String?,
    message: String,
    val httpStatus: Int,
) : Exception(message) {

    companion object {
        const val CODE_NETWORK = "network_error"
        const val CODE_UNAUTHORIZED = "unauthorized"
    }
}

/**
 * Wraps a Retrofit call so we always throw [ChwSyncApiException] with a parsed
 * server code/message, instead of raw [HttpException] or [IOException].
 */
suspend fun <T> safeApiCall(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: ChwSyncApiException) {
        throw e
    } catch (e: HttpException) {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val parsed = body?.let {
            runCatching { ChwSyncJson.decodeFromString(ApiError.serializer(), it) }.getOrNull()
        }
        throw ChwSyncApiException(
            code = parsed?.code ?: if (e.code() == 401) ChwSyncApiException.CODE_UNAUTHORIZED else "http_${e.code()}",
            message = parsed?.message ?: (e.message() ?: "Request failed"),
            httpStatus = e.code(),
        )
    } catch (e: IOException) {
        throw ChwSyncApiException(ChwSyncApiException.CODE_NETWORK, e.message ?: "Network error", httpStatus = 0)
    }
}
