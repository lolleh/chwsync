package org.openmrs.chwsync.android.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.openmrs.chwsync.android.ChwSyncApplication

/**
 * Single factory for every view model in the app. Each `initializer` pulls the
 * shared [org.openmrs.chwsync.android.di.AppContainer] off the Application, so
 * `viewModel(factory = ChwSyncViewModelFactory)` works from any composable.
 */
val ChwSyncViewModelFactory = viewModelFactory {
    initializer { AppViewModel(chwsyncApplication().container) }
    initializer { PatientsViewModel(chwsyncApplication().container) }
    initializer { ClinicalViewModel(chwsyncApplication().container) }
    initializer { ListsViewModel(chwsyncApplication().container) }
    initializer { SyncViewModel(chwsyncApplication().container) }
}

private fun CreationExtras.chwsyncApplication(): ChwSyncApplication =
    this[APPLICATION_KEY] as ChwSyncApplication
