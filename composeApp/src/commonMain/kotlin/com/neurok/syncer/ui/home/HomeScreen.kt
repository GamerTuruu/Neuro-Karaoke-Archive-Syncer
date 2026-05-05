package com.neurok.syncer.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neurok.syncer.domain.model.SyncProgress
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBrowser: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = koinViewModel<HomeViewModel>()
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neuro Karaoke Archive") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.folderConfigured) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "No archive folder configured. Go to Settings to choose a folder.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // Last sync
            Text(
                text = if (state.lastSyncTime > 0L)
                    "Last synced: ${formatTime(state.lastSyncTime)}"
                else "Never synced",
                style = MaterialTheme.typography.bodyMedium,
            )

            // Status counts
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("✓ ${state.counts.upToDate}", color = MaterialTheme.colorScheme.primary)
                StatusChip("↑ ${state.counts.needsUpdate}", color = MaterialTheme.colorScheme.secondary)
                StatusChip("★ ${state.counts.newAvailable}", color = MaterialTheme.colorScheme.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("? ${state.counts.orphans}", color = MaterialTheme.colorScheme.error)
                StatusChip("✗ ${state.counts.excluded}", color = MaterialTheme.colorScheme.outline)
            }

            // Storage
            Text(
                "Storage: ${formatBytes(state.storageBytes)}",
                style = MaterialTheme.typography.bodySmall,
            )

            // Sync button
            Button(
                onClick = { vm.sync() },
                enabled = !state.isSyncing && state.folderConfigured,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isSyncing) "Syncing…" else "Sync Now")
            }

            // Progress message
            state.syncProgress?.let { progress ->
                when (progress) {
                    is SyncProgress.ScanningLocal ->
                        LinearProgressIndicator(
                            progress = { if (progress.total > 0) progress.current.toFloat() / progress.total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    is SyncProgress.FetchingMetadata ->
                        Text(progress.message, style = MaterialTheme.typography.bodySmall)
                    is SyncProgress.Completed ->
                        Text("Done: ${progress.updated} updated, ${progress.newAvailable} new",
                            style = MaterialTheme.typography.bodySmall)
                    is SyncProgress.Error ->
                        Text("Error: ${progress.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    else -> Unit
                }
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(onClick = onNavigateToBrowser, modifier = Modifier.fillMaxWidth()) {
                Text("Browse Songs")
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
        modifier = Modifier,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = (ms / 60000) % 60
    val hours = (ms / 3600000) % 24
    // Simple epoch-based date string (UTC)
    val days = ms / 86400000L
    val year = 1970 + (days / 365).toInt()
    val dayOfYear = (days % 365).toInt()
    val month = dayOfYear / 30 + 1
    val day = dayOfYear % 30 + 1
    return "%04d-%02d-%02d %02d:%02d".format(year, month, day, hours, mins)
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}
