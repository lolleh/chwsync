package org.openmrs.chwsync.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openmrs.chwsync.android.R

/** Diagnostics + manual controls: pending queue, conflicts, cursor, reference refresh, logout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    appViewModel: AppViewModel,
    viewModel: SyncViewModel = viewModel(factory = ChwSyncViewModelFactory),
) {
    val auth by appViewModel.authState.collectAsStateWithLifecycle()
    val online by appViewModel.online.collectAsStateWithLifecycle()
    val pending by appViewModel.pendingCount.collectAsStateWithLifecycle()
    val syncState by appViewModel.sync.collectAsStateWithLifecycle()

    val outbox by viewModel.outbox.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val cursor by viewModel.cursor.collectAsStateWithLifecycle()
    val reference by viewModel.reference.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(if (online) R.string.status_online else R.string.status_offline),
                style = MaterialTheme.typography.titleMedium,
                color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )

            auth?.let { state ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(state.displayName ?: state.username ?: "Signed in")
                        Text("Device: ${state.deviceUuid.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                        Text("Server: ${state.serverUrl.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("Pending changes: $pending", style = MaterialTheme.typography.titleSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { appViewModel.syncNow() },
                    enabled = !syncState.running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (syncState.running) "Syncing…" else stringResource(R.string.action_sync_now),
                    )
                }
                OutlinedButton(
                    onClick = { appViewModel.logout() },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_logout)) }
            }

            syncState.lastMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            syncState.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            message?.let { Text(it) }

            Text("Pull cursor: ${cursor ?: "(none)"}", style = MaterialTheme.typography.bodySmall)

            OutlinedButton(
                onClick = { viewModel.refreshReference() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Reference: ${reference?.locations?.size ?: 0} locations, " +
                        "${reference?.visitTypes?.size ?: 0} visit types",
                )
            }

            HorizontalDivider()
            Text("Outbox (${outbox.size})", style = MaterialTheme.typography.titleMedium)
            outbox.take(20).forEach { item ->
                Text(
                    "${item.operation} ${item.entityType} ${item.uuid.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Conflicts (${conflicts.size})", style = MaterialTheme.typography.titleMedium)
                if (conflicts.isNotEmpty()) {
                    OutlinedButton(onClick = { viewModel.clearConflicts() }) { Text("Clear") }
                }
            }
            conflicts.take(20).forEach { conflict ->
                Text(
                    "${conflict.resolution}: ${conflict.entityType} ${conflict.uuid.take(8)} " +
                        (conflict.message ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
