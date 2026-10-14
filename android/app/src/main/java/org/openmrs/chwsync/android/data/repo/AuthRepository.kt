package org.openmrs.chwsync.android.data.repo

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.openmrs.chwsync.android.data.remote.ChwSyncApi
import org.openmrs.chwsync.android.data.remote.LoginRequest
import org.openmrs.chwsync.android.data.remote.LoginResponse
import org.openmrs.chwsync.android.data.remote.UserResponse
import org.openmrs.chwsync.android.data.remote.safeApiCall
import org.openmrs.chwsync.android.util.Uuid

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "chwsync_session")

/** UI-facing view of the persisted session. `isLoggedIn` is driven by token presence. */
data class AuthState(
    val isLoggedIn: Boolean,
    val userUuid: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val privileges: List<String> = emptyList(),
    val serverUrl: String? = null,
    val deviceUuid: String? = null,
)

/**
 * Owns the persisted session (token, server URL, user, device uuid) and exposes
 * the token/base-URL to the HTTP stack via volatile caches so interceptors never
 * need to block on DataStore.
 */
class SessionStore(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val DEVICE_UUID = stringPreferencesKey("device_uuid")
        val USER_UUID = stringPreferencesKey("user_uuid")
        val USERNAME = stringPreferencesKey("username")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PRIVILEGES = stringPreferencesKey("privileges")
        val EXPIRES_AT = stringPreferencesKey("expires_at")
        val SERVER_URL = stringPreferencesKey("server_url")
    }

    @Volatile
    var token: String? = null
        private set

    @Volatile
    var baseUrl: HttpUrl? = null
        private set

    val authState: Flow<AuthState> = context.sessionDataStore.data.map { prefs ->
        AuthState(
            isLoggedIn = !prefs[Keys.TOKEN].isNullOrBlank(),
            userUuid = prefs[Keys.USER_UUID],
            username = prefs[Keys.USERNAME],
            displayName = prefs[Keys.DISPLAY_NAME],
            privileges = prefs[Keys.PRIVILEGES]
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
            serverUrl = prefs[Keys.SERVER_URL],
            deviceUuid = prefs[Keys.DEVICE_UUID],
        )
    }

    /** Warms the volatile caches from disk. Call once at startup. */
    suspend fun hydrate() {
        applyCached(context.sessionDataStore.data.first())
    }

    private fun applyCached(prefs: Preferences) {
        token = prefs[Keys.TOKEN]
        baseUrl = prefs[Keys.SERVER_URL]?.let { toBaseUrl(it) }
    }

    /**
     * Generates and persists a device uuid on first launch. It is sent on login
     * and every push, and is preserved across logout so the server keeps a stable
     * device identity.
     */
    suspend fun ensureDeviceUuid(): String {
        val prefs = context.sessionDataStore.data.first()
        prefs[Keys.DEVICE_UUID]?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = Uuid.random()
        context.sessionDataStore.edit { it[Keys.DEVICE_UUID] = generated }
        return generated
    }

    suspend fun setServerUrl(serverUrl: String) {
        val normalised = normalise(serverUrl)
        context.sessionDataStore.edit { it[Keys.SERVER_URL] = normalised }
        baseUrl = toBaseUrl(normalised)
    }

    suspend fun saveSession(login: LoginResponse) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = login.token
            prefs[Keys.DEVICE_UUID] = login.deviceUuid
            prefs[Keys.USER_UUID] = login.userUuid
            prefs[Keys.USERNAME] = login.username
            prefs[Keys.DISPLAY_NAME] = login.displayName
            prefs[Keys.PRIVILEGES] = login.privileges.joinToString(",")
            login.expiresAt?.let { prefs[Keys.EXPIRES_AT] = it }
        }
        token = login.token
    }

    /**
     * Clears the session but keeps the device uuid and server URL, so a re-login
     * on the same install is recognised as the same device.
     */
    suspend fun clear() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.USER_UUID)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.DISPLAY_NAME)
            prefs.remove(Keys.PRIVILEGES)
            prefs.remove(Keys.EXPIRES_AT)
        }
        token = null
    }

    private fun normalise(url: String): String = url.trim().trimEnd('/')

    private fun toBaseUrl(serverUrl: String): HttpUrl? =
        "${normalise(serverUrl)}/ws/chwsync/v1/".toHttpUrlOrNull()
}

/**
 * Authentication against the module. Tokens are opaque bearer strings stored in
 * DataStore. The device uuid is generated on first launch and reused on login
 * and push so the server can attribute changes to a physical device.
 */
class AuthRepository(
    private val session: SessionStore,
    private val api: ChwSyncApi,
    private val appVersion: String,
) {

    val authState: Flow<AuthState> = session.authState

    suspend fun hydrate() = session.hydrate()

    suspend fun ensureDeviceUuid(): String = session.ensureDeviceUuid()

    suspend fun login(serverUrl: String, username: String, password: String): AuthState {
        session.setServerUrl(serverUrl)
        val deviceUuid = session.ensureDeviceUuid()
        val response = safeApiCall {
            api.login(
                LoginRequest(
                    username = username,
                    password = password,
                    deviceUuid = deviceUuid,
                    deviceName = Build.MODEL ?: "android",
                    platform = "android",
                    appVersion = appVersion,
                ),
            )
        }
        session.saveSession(response)
        return AuthState(
            isLoggedIn = true,
            userUuid = response.userUuid,
            username = response.username,
            displayName = response.displayName,
            privileges = response.privileges,
            serverUrl = serverUrl,
            deviceUuid = response.deviceUuid,
        )
    }

    suspend fun me(): UserResponse = safeApiCall { api.me() }

    suspend fun logout() {
        // Best-effort server logout; always clear the local session.
        runCatching { safeApiCall { api.logout() } }
        session.clear()
    }

    suspend fun currentServerUrl(): String? = session.authState.first().serverUrl
}
