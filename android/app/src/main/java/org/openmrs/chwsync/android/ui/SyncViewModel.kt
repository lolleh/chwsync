package org.openmrs.chwsync.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openmrs.chwsync.android.data.local.ConflictEntity
import org.openmrs.chwsync.android.data.local.OutboxEntity
import org.openmrs.chwsync.android.data.remote.ReferenceResponse
import org.openmrs.chwsync.android.di.AppContainer
import org.openmrs.chwsync.android.sync.SyncEngine

/** Read-only diagnostics for the Sync tab plus reference-data refresh. */
class SyncViewModel(private val container: AppContainer) : ViewModel() {

    val outbox: StateFlow<List<OutboxEntity>> = container.database.outbox().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conflicts: StateFlow<List<ConflictEntity>> = container.database.conflicts().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cursor: StateFlow<String?> = container.database.settings().observe(SyncEngine.KEY_CURSOR)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reference: StateFlow<ReferenceResponse?> = container.referenceRepository.reference

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refreshReference() {
        viewModelScope.launch {
            _message.value = runCatching { container.referenceRepository.refresh() }
                .fold({ "Reference data updated" }, { "Reference refresh failed: ${it.message}" })
        }
    }

    fun clearConflicts() {
        viewModelScope.launch { container.database.conflicts().clear() }
    }
}
