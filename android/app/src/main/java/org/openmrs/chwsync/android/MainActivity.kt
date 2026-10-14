package org.openmrs.chwsync.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openmrs.chwsync.android.ui.AppViewModel
import org.openmrs.chwsync.android.ui.ChwSyncAppRoot
import org.openmrs.chwsync.android.ui.ChwSyncViewModelFactory
import org.openmrs.chwsync.android.ui.theme.ChwSyncTheme

/**
 * Single-activity host. The whole UI is a Compose navigation graph rooted at
 * [ChwSyncAppRoot], which gates on auth state.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChwSyncTheme {
                val appViewModel: AppViewModel = viewModel(factory = ChwSyncViewModelFactory)
                ChwSyncAppRoot(appViewModel)
            }
        }
    }
}
