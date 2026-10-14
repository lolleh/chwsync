package org.openmrs.chwsync.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.openmrs.chwsync.android.R
import org.openmrs.chwsync.android.data.model.EncounterPayload
import org.openmrs.chwsync.android.data.model.ObsPayload
import org.openmrs.chwsync.android.data.remote.ConceptRef
import org.openmrs.chwsync.android.data.remote.RefNamed
import org.openmrs.chwsync.android.util.Uuid
import org.openmrs.chwsync.android.util.WireDate

/**
 * Records an encounter with a single observation for the common CHW workflow.
 * The obs is stored nested in the encounter payload, matching the module shape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncounterFormScreen(
    appViewModel: AppViewModel,
    patientUuid: String,
    onDone: () -> Unit,
    clinicalViewModel: ClinicalViewModel = viewModel(factory = ChwSyncViewModelFactory),
) {
    val scope = rememberCoroutineScope()
    var encounterTypes by remember { mutableStateOf<List<RefNamed>>(emptyList()) }
    var locations by remember { mutableStateOf<List<RefNamed>>(emptyList()) }
    var selectedEncounterType by remember { mutableStateOf<RefNamed?>(null) }
    var selectedLocation by remember { mutableStateOf<RefNamed?>(null) }

    var conceptQuery by remember { mutableStateOf("") }
    var conceptResults by remember { mutableStateOf<List<ConceptRef>>(emptyList()) }
    var selectedConcept by remember { mutableStateOf<ConceptRef?>(null) }
    var valueText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val reference = appViewModel.container.referenceRepository.cached()
            ?: runCatching { appViewModel.container.referenceRepository.refresh() }.getOrNull()
        encounterTypes = reference?.encounterTypes.orEmpty()
        locations = reference?.locations.orEmpty()
        selectedEncounterType = encounterTypes.firstOrNull()
        selectedLocation = locations.firstOrNull()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
        ) {
            Text("New encounter", style = MaterialTheme.typography.titleLarge)

            ReferenceDropdown(
                label = "Encounter type",
                options = encounterTypes,
                selected = selectedEncounterType,
                onSelect = { selectedEncounterType = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            ReferenceDropdown(
                label = "Location",
                options = locations,
                selected = selectedLocation,
                onSelect = { selectedLocation = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = conceptQuery,
                    onValueChange = { conceptQuery = it },
                    label = { Text("Concept search") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            conceptResults = runCatching {
                                appViewModel.container.referenceRepository.searchConcepts(conceptQuery)
                            }.getOrDefault(emptyList())
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Search") }
            }

            selectedConcept?.let { Text("Selected: ${it.name}", modifier = Modifier.padding(top = 8.dp)) }
            conceptResults.take(5).forEach { concept ->
                Text(
                    text = concept.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedConcept = concept }
                        .padding(vertical = 8.dp),
                )
            }

            OutlinedTextField(
                value = valueText,
                onValueChange = { valueText = it },
                label = { Text("Observation value") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val encounterType = selectedEncounterType
                    val location = selectedLocation
                    if (encounterType == null || location == null) {
                        error = "Select an encounter type and location."
                        return@Button
                    }
                    val concept = selectedConcept
                    val obs = if (concept != null && valueText.isNotBlank()) {
                        listOf(
                            ObsPayload(
                                uuid = Uuid.random(),
                                concept = concept.uuid,
                                obsDatetime = WireDate.now(),
                                valueText = valueText.trim(),
                            ),
                        )
                    } else {
                        emptyList()
                    }
                    val payload = EncounterPayload(
                        uuid = Uuid.random(),
                        patient = patientUuid,
                        encounterType = encounterType.uuid,
                        location = location.uuid,
                        encounterDatetime = WireDate.now(),
                        obs = obs,
                    )
                    clinicalViewModel.saveEncounter(
                        payload = payload,
                        onSaved = onDone,
                        onError = { error = it },
                    )
                },
                enabled = selectedEncounterType != null && selectedLocation != null,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
