package com.neurok.syncer.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neurok.syncer.domain.model.SyncProgress
import com.neurok.syncer.ui.components.ConfirmDialog
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val vm = koinViewModel<HomeViewModel>()
    val state by vm.state.collectAsState()

    // Refresh whenever the screen re-enters composition (e.g. returning from Settings)
    LaunchedEffect(Unit) { vm.refresh() }

    if (state.showSyncConfirm) {
        ConfirmDialog(
            title = "Start Sync",
            message = "This will scan your local folder, fetch metadata from GitHub, and apply tags to any songs that changed. Continue?",
            confirmLabel = "Sync Now",
            onConfirm = { vm.sync() },
            onDismiss = { vm.dismissSyncConfirm() },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sync") }) }
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
                        "No archive folder configured.\nGo to More → Settings to choose a folder.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Text(
                text = if (state.lastSyncTime > 0L) "Last synced: ${formatTime(state.lastSyncTime)}" else "Never synced",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("✓", state.counts.upToDate, "Up to date", MaterialTheme.colorScheme.primary)
                StatusChip("↑", state.counts.needsUpdate, "Updates", MaterialTheme.colorScheme.secondary)
                StatusChip("★", state.counts.newAvailable, "New", MaterialTheme.colorScheme.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("?", state.counts.orphans, "Orphan", MaterialTheme.colorScheme.error)
                StatusChip("✗", state.counts.excluded, "Excluded", MaterialTheme.colorScheme.outline)
            }

            Text("Storage: ${formatBytes(state.storageBytes)}", style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = { vm.requestSync() },
                enabled = !state.isSyncing && state.folderConfigured,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isSyncing) "Syncing…" else "Sync Now")
            }

            state.syncProgress?.let { progress ->
                when (progress) {
                    is SyncProgress.ScanningLocal ->
                        LinearProgressIndicator(
                            progress = { if (progress.total > 0) progress.current.toFloat() / progress.total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    is SyncProgress.FetchingMetadata -> Text(progress.message, style = MaterialTheme.typography.bodySmall)
                    is SyncProgress.ApplyingTags -> Text("Applying tags (${progress.current}/${progress.total}): ${progress.songTitle}", style = MaterialTheme.typography.bodySmall)
                    is SyncProgress.Completed -> Text("Done: ${progress.updated} updated, ${progress.newAvailable} new available", style = MaterialTheme.typography.bodySmall)
                    is SyncProgress.Error -> Text("Error: ${progress.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun StatusChip(icon: String, count: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.15f)) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$icon  $count",
                color = color, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                label,
                color = color.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val mins = (ms / 60000) % 60
    val hours = (ms / 3600000) % 24
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
