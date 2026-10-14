package org.openmrs.chwsync.android.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.openmrs.chwsync.android.BuildConfig
import org.openmrs.chwsync.android.data.local.ChwSyncDatabase
import org.openmrs.chwsync.android.data.remote.ApiClient
import org.openmrs.chwsync.android.data.remote.ChwSyncApi
import org.openmrs.chwsync.android.data.repo.AuthRepository
import org.openmrs.chwsync.android.data.repo.RecordRepository
import org.openmrs.chwsync.android.data.repo.ReferenceRepository
import org.openmrs.chwsync.android.data.repo.SessionStore
import org.openmrs.chwsync.android.sync.SyncEngine
import org.openmrs.chwsync.android.sync.SyncScheduler
import org.openmrs.chwsync.android.util.NetworkMonitor

/**
 * Manual dependency container (no DI framework needed at this size). One instance
 * lives on the [org.openmrs.chwsync.android.ChwSyncApplication] and is handed to view
 * models through the shared view-model factory.
 */
class AppContainer(private val appContext: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: ChwSyncDatabase by lazy { ChwSyncDatabase.build(appContext) }

    /**
     * The session store is created before the HTTP client because the client's
     * interceptors read the token/base URL from it.
     */
    val sessionStore = SessionStore(appContext)

    private val apiClient = ApiClient(
        tokenProvider = { sessionStore.token },
        baseUrlProvider = { sessionStore.baseUrl },
    )

    val api: ChwSyncApi get() = apiClient.api

    val networkMonitor = NetworkMonitor(appContext)

    val authRepository = AuthRepository(
        session = sessionStore,
        api = api,
        appVersion = BuildConfig.VERSION_NAME,
    )

    val recordRepository = RecordRepository(database)

    val referenceRepository = ReferenceRepository(api, database)

    val syncEngine = SyncEngine(api, database, sessionStore)

    val syncScheduler = SyncScheduler(appContext)

    /** Warms caches and schedules background sync. Safe to call once from onCreate. */
    fun initialize() {
        applicationScope.launch {
            authRepository.hydrate()
            syncScheduler.ensurePeriodic()
        }
    }
}
