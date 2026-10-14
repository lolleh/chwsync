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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import org.openmrs.chwsync.android.R
import org.openmrs.chwsync.android.data.model.PatientIdentifier
import org.openmrs.chwsync.android.data.model.PatientPayload
import org.openmrs.chwsync.android.data.model.PersonName
import org.openmrs.chwsync.android.util.Uuid

/**
 * Create/edit a patient. The uuid is generated client-side so records created
 * offline already have their final identity and can be referenced by other
 * local records (visits, appointments) before the first sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientFormScreen(
    appViewModel: AppViewModel,
    patientUuid: String?,
    onDone: () -> Unit,
    viewModel: PatientsViewModel = viewModel(factory = ChwSyncViewModelFactory),
) {
    val isEdit = !patientUuid.isNullOrBlank()

    var givenName by rememberSaveable { mutableStateOf("") }
    var familyName by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("F") }
    var birthdate by rememberSaveable { mutableStateOf("") }
    var identifier by rememberSaveable { mutableStateOf("") }
    var identifierTypeUuid by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Child uuids are kept stable across recompositions; on edit they are
    // replaced with the values already stored on the record.
    var nameUuid by remember(patientUuid) { mutableStateOf(Uuid.random()) }
    var identifierUuid by remember(patientUuid) { mutableStateOf(Uuid.random()) }

    // Load reference identifiers once so new identifiers use a real type uuid.
    LaunchedEffect(Unit) {
        val cached = appViewModel.container.referenceRepository.cached()
            ?: runCatching { appViewModel.container.referenceRepository.refresh() }.getOrNull()
        identifierTypeUuid = cached?.identifierTypes?.firstOrNull()?.uuid
    }

    // Populate fields when editing.
    LaunchedEffect(patientUuid) {
        val uuid = patientUuid
        if (!uuid.isNullOrBlank()) {
            viewModel.patient(uuid).first()?.let { existing ->
                givenName = existing.names.firstOrNull()?.givenName.orEmpty()
                familyName = existing.names.firstOrNull()?.familyName.orEmpty()
                gender = existing.gender ?: "F"
                birthdate = existing.birthdate.orEmpty()
                identifier = existing.identifiers.firstOrNull()?.identifier.orEmpty()
                existing.names.firstOrNull()?.uuid?.let { nameUuid = it }
                existing.identifiers.firstOrNull()?.let {
                    identifierUuid = it.uuid
                    identifierTypeUuid = it.identifierType
                }
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                text = if (isEdit) "Edit patient" else "Register patient",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = givenName,
                onValueChange = { givenName = it },
                label = { Text("Given name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = familyName,
                onValueChange = { familyName = it },
                label = { Text("Family name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = gender,
                onValueChange = { gender = it.take(1).uppercase() },
                label = { Text("Gender (M/F/O)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = birthdate,
                onValueChange = { birthdate = it },
                label = { Text("Birthdate (yyyy-MM-dd)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = { Text("Identifier") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    error = null
                    // Preserve child uuids when editing so the server sees an update,
                    // not a wholesale replacement of names/identifiers.
                    val uuid = patientUuid ?: Uuid.random()
                    val payload = PatientPayload(
                        uuid = uuid,
                        gender = gender.ifBlank { null },
                        birthdate = birthdate.ifBlank { null },
                        names = listOf(
                            PersonName(
                                uuid = nameUuid,
                                givenName = givenName.trim(),
                                familyName = familyName.trim(),
                                preferred = true,
                            ),
                        ),
                        identifiers = if (identifier.isBlank()) {
                            emptyList()
                        } else {
                            listOf(
                                PatientIdentifier(
                                    uuid = identifierUuid,
                                    identifier = identifier.trim(),
                                    identifierType = identifierTypeUuid.orEmpty(),
                                    preferred = true,
                                ),
                            )
                        },
                    )
                    viewModel.save(payload, onSaved = onDone, onError = { error = it })
                },
                enabled = givenName.isNotBlank() || familyName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
