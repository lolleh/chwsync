package org.openmrs.chwsync.android.data.remote

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit
import org.openmrs.chwsync.android.data.ChwSyncJson
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Adds the bearer token to every authenticated request. Login and manifest are
 * anonymous by contract, so we skip the header for them.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val anonymous = path.endsWith("/auth/login") || path.endsWith("/manifest")
        val token = tokenProvider()
        val authed = if (!anonymous && !token.isNullOrBlank()) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        return chain.proceed(authed)
    }
}

/**
 * Retrofit bakes its baseUrl in at construction time, but the CHW server URL is
 * user-configurable at runtime. This interceptor rewrites each outgoing request
 * to the current base URL, preserving the relative path and query string.
 */
class BaseUrlInterceptor(private val baseUrlProvider: () -> HttpUrl?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = baseUrlProvider() ?: return chain.proceed(chain.request())
        val request = chain.request()
        val suffix = request.url.encodedPath.trimStart('/')
        val newUrl = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(base.encodedPath.trimEnd('/') + "/" + suffix)
            .build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}

/**
 * Builds the shared OkHttp/Retrofit stack. The providers are read on every
 * request so login/logout and server-URL changes take effect immediately without
 * rebuilding the client.
 */
class ApiClient(
    tokenProvider: () -> String?,
    baseUrlProvider: () -> HttpUrl?,
) {
    private val jsonConverter = ChwSyncJson.asConverterFactory("application/json".toMediaType())

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(BaseUrlInterceptor(baseUrlProvider))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            // Placeholder host; BaseUrlInterceptor replaces it per request.
            .baseUrl("http://api.invalid/")
            .client(okHttpClient)
            .addConverterFactory(jsonConverter)
            .build()
    }

    val api: ChwSyncApi by lazy { retrofit.create(ChwSyncApi::class.java) }
}
