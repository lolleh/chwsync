package org.openmrs.chwsync.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import org.openmrs.chwsync.android.R
import org.openmrs.chwsync.android.data.model.PatientPayload

/**
 * Patients tab: searches the local store only. Because the store is the source
 * of truth, results are instant and available with no connectivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientsScreen(
    appViewModel: AppViewModel,
    navController: NavHostController,
    viewModel: PatientsViewModel = viewModel(factory = ChwSyncViewModelFactory),
) {
    val patients by viewModel.patients.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(patients, query) {
        if (query.isBlank()) {
            patients
        } else {
            val q = query.trim().lowercase()
            patients.filter { patient ->
                patient.displayName().lowercase().contains(q) ||
                    patient.identifiers.any { it.identifier.lowercase().contains(q) }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Routes.patientForm(null)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.nav_patients)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.nav_patients)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            if (filtered.isEmpty()) {
                Text(
                    text = "No patients yet. Tap + to register one.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.uuid }) { patient ->
                        PatientRow(patient) { navController.navigate(Routes.patientDetail(patient.uuid)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientRow(patient: PatientPayload, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = patient.displayName(), style = MaterialTheme.typography.titleMedium)
            val detail = buildString {
                patient.gender?.let { append(it) }
                patient.birthdate?.let {
                    if (isNotEmpty()) append(" • ")
                    append(it)
                }
                patient.identifiers.firstOrNull()?.let {
                    if (isNotEmpty()) append(" • ")
                    append(it.identifier)
                }
            }
            if (detail.isNotBlank()) {
                Text(text = detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
