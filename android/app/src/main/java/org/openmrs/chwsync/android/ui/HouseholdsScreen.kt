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
import org.openmrs.chwsync.android.data.model.HouseholdPayload
import org.openmrs.chwsync.android.util.Uuid

/** Households tab: register village households and their head. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdsScreen(
    navController: NavHostController,
    viewModel: ListsViewModel = viewModel(factory = ChwSyncViewModelFactory),
) {
    val households by viewModel.households.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        },
    ) { padding ->
        if (households.isEmpty()) {
            Text("No households yet.", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(households, key = { it.uuid }) { household ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(household.name, style = MaterialTheme.typography.titleMedium)
                            Text("Village: ${household.village}")
                            Text("Members: ${household.members.size}")
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        HouseholdDialog(
            onDismiss = { showDialog = false },
            onSave = { name, village ->
                viewModel.saveHousehold(
                    HouseholdPayload(uuid = Uuid.random(), name = name, village = village),
                )
                showDialog = false
            },
        )
    }
}

@Composable
private fun HouseholdDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New household") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Household name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = village,
                    onValueChange = { village = it },
                    label = { Text("Village") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), village.trim()) },
                enabled = name.isNotBlank() && village.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
