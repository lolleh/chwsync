package org.openmrs.chwsync.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openmrs.chwsync.android.R
import org.openmrs.chwsync.android.data.model.VisitPayload
import org.openmrs.chwsync.android.data.remote.RefNamed
import org.openmrs.chwsync.android.util.Uuid
import org.openmrs.chwsync.android.util.WireDate

/** Records a visit against a patient. Type/location come from cached reference data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitFormScreen(
    appViewModel: AppViewModel,
    patientUuid: String,
    onDone: () -> Unit,
    clinicalViewModel: ClinicalViewModel = viewModel(factory = ChwSyncViewModelFactory),
) {
    var visitTypes by remember { mutableStateOf<List<RefNamed>>(emptyList()) }
    var locations by remember { mutableStateOf<List<RefNamed>>(emptyList()) }
    var selectedVisitType by remember { mutableStateOf<RefNamed?>(null) }
    var selectedLocation by remember { mutableStateOf<RefNamed?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val reference = appViewModel.container.referenceRepository.cached()
            ?: runCatching { appViewModel.container.referenceRepository.refresh() }.getOrNull()
        visitTypes = reference?.visitTypes.orEmpty()
        locations = reference?.locations.orEmpty()
        selectedVisitType = visitTypes.firstOrNull()
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
            Text("New visit", style = MaterialTheme.typography.titleLarge)

            ReferenceDropdown(
                label = "Visit type",
                options = visitTypes,
                selected = selectedVisitType,
                onSelect = { selectedVisitType = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            ReferenceDropdown(
                label = "Location",
                options = locations,
                selected = selectedLocation,
                onSelect = { selectedLocation = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val visitType = selectedVisitType
                    val location = selectedLocation
                    if (visitType == null || location == null) {
                        error = "Select a visit type and location."
                        return@Button
                    }
                    val payload = VisitPayload(
                        uuid = Uuid.random(),
                        patient = patientUuid,
                        visitType = visitType.uuid,
                        location = location.uuid,
                        startDatetime = WireDate.now(),
                    )
                    clinicalViewModel.saveVisit(
                        payload = payload,
                        onSaved = onDone,
                        onError = { error = it },
                    )
                },
                enabled = selectedVisitType != null && selectedLocation != null,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
