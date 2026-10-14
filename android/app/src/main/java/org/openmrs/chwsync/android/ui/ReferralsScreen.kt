package org.openmrs.chwsync.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import org.openmrs.chwsync.android.R
import org.openmrs.chwsync.android.data.model.ReferralPayload
import org.openmrs.chwsync.android.util.Uuid
import org.openmrs.chwsync.android.util.WireDate

/** Referrals tab: refer a patient to an external facility. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralsScreen(
    navController: NavHostController,
    viewModel: ListsViewModel = viewModel(factory = ChwSyncViewModelFactory),
) {
    val referrals by viewModel.referrals.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        },
    ) { padding ->
        if (referrals.isEmpty()) {
            Text("No referrals yet.", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(referrals, key = { it.uuid }) { referral ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                referral.facility ?: "Referral",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text("Patient: ${referral.patient.take(8)}")
                            referral.urgency?.let { Text("Urgency: $it") }
                            referral.status?.let { Text("Status: $it") }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ReferralDialog(
            onDismiss = { showDialog = false },
            onSave = { patient, facility, urgency ->
                viewModel.saveReferral(
                    ReferralPayload(
                        uuid = Uuid.random(),
                        patient = patient,
                        referralDate = WireDate.now(),
                        facility = facility.ifBlank { null },
                        urgency = urgency.ifBlank { null },
                        status = "Pending",
                    ),
                )
                showDialog = false
            },
        )
    }
}

@Composable
private fun ReferralDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var patient by remember { mutableStateOf("") }
    var facility by remember { mutableStateOf("") }
    var urgency by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New referral") },
        text = {
            Column {
                OutlinedTextField(
                    value = patient,
                    onValueChange = { patient = it },
                    label = { Text("Patient uuid") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = facility,
                    onValueChange = { facility = it },
                    label = { Text("Facility") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = urgency,
                    onValueChange = { urgency = it },
                    label = { Text("Urgency") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(patient.trim(), facility.trim(), urgency.trim()) },
                enabled = patient.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
