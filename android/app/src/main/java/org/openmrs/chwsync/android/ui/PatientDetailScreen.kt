package org.openmrs.chwsync.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openmrs.chwsync.android.data.model.EncounterPayload
import org.openmrs.chwsync.android.data.model.VisitPayload

/** Read-only patient summary plus the patient's visits/encounters, all from cache. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(
    patientUuid: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAddVisit: (String) -> Unit,
    onAddEncounter: (String) -> Unit,
    patientsViewModel: PatientsViewModel = viewModel(factory = ChwSyncViewModelFactory),
    clinicalViewModel: ClinicalViewModel = viewModel(factory = ChwSyncViewModelFactory),
) {
    val patient by patientsViewModel.patient(patientUuid)
        .collectAsStateWithLifecycle(initialValue = null)
    val visits by clinicalViewModel.visitsFor(patientUuid)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val encounters by clinicalViewModel.encountersFor(patientUuid)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(patient?.displayName() ?: "Patient") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            patient?.let { p ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(p.displayName(), style = MaterialTheme.typography.titleLarge)
                        p.gender?.let { Text("Gender: $it") }
                        p.birthdate?.let { Text("Birthdate: $it") }
                        p.identifiers.firstOrNull()?.let { Text("ID: ${it.identifier}") }
                    }
                }
                Button(onClick = { onEdit(p.uuid) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit patient")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onAddVisit(p.uuid) },
                        modifier = Modifier.weight(1f),
                    ) { Text("New visit") }
                    OutlinedButton(
                        onClick = { onAddEncounter(p.uuid) },
                        modifier = Modifier.weight(1f),
                    ) { Text("New encounter") }
                }
            } ?: Text("Patient not found in local store.")

            Text("Visits (${visits.size})", style = MaterialTheme.typography.titleMedium)
            visits.forEach { visit -> VisitRow(visit) }

            Text("Encounters (${encounters.size})", style = MaterialTheme.typography.titleMedium)
            encounters.forEach { encounter -> EncounterRow(encounter) }
        }
    }
}

@Composable
private fun VisitRow(visit: VisitPayload) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Visit ${visit.uuid.take(8)}", style = MaterialTheme.typography.titleSmall)
            Text("Start: ${visit.startDatetime}")
            visit.stopDatetime?.let { Text("Stop: $it") }
        }
    }
}

@Composable
private fun EncounterRow(encounter: EncounterPayload) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Encounter ${encounter.uuid.take(8)}", style = MaterialTheme.typography.titleSmall)
            Text("At: ${encounter.encounterDatetime}")
            Text("Obs: ${encounter.obs.size}")
        }
    }
}
