package org.openmrs.chwsync.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openmrs.chwsync.android.data.repo.AuthState
import org.openmrs.chwsync.android.di.AppContainer

/** Transient UI state for the sync control on the Sync screen / top bar. */
data class SyncUiState(
    val running: Boolean = false,
    val lastMessage: String? = null,
    val lastError: String? = null,
)

/**
 * App-level state shared by the whole tree: auth, connectivity, pending-change
 * count, and manual sync. Feature view models are intentionally separate so each
 * screen only holds what it needs.
 */
class AppViewModel(val container: AppContainer) : ViewModel() {

    private val authRepository = container.authRepository
    private val syncEngine = container.syncEngine

    /**
     * Emits `null` once first (via [onStart]) so the root composable can show a
     * splash instead of briefly flashing the login screen on a cold start.
     */
    val authState: StateFlow<AuthState?> = authRepository.authState
        .map<AuthState, AuthState?> { it }
        .onStart { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val online: StateFlow<Boolean> = container.networkMonitor.online
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val pendingCount: StateFlow<Int> = container.database.outbox().observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _sync = MutableStateFlow(SyncUiState())
    val sync: StateFlow<SyncUiState> = _sync.asStateFlow()

    fun login(serverUrl: String, username: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.login(serverUrl, username, password)
            } catch (e: Exception) {
                onError(e.message ?: "Sign-in failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    /** Manual round trip: push un-sent work, then pull remote changes. */
    fun syncNow() {
        if (_sync.value.running) return
        _sync.value = SyncUiState(running = true)
        viewModelScope.launch {
            try {
                val report = syncEngine.sync()
                _sync.value = SyncUiState(
                    running = false,
                    lastMessage = "Pushed ${report.pushed} change(s), pulled ${report.pulled}.",
                )
            } catch (e: Exception) {
                _sync.value = SyncUiState(running = false, lastError = e.message ?: "Sync failed")
            }
        }
    }
}
