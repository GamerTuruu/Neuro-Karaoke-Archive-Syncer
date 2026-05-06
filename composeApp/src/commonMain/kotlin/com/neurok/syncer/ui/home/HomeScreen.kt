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
            title = "Apply Tags & Download",
            message = buildString {
                append("This will:\n• Apply updated ID3 tags to any songs that changed.\n")
                if (state.counts.newAvailable > 0) {
                    if (state.driveApiKeyConfigured) {
                        append("• Download ${state.counts.newAvailable} new song(s) from Google Drive.\n")
                    } else {
                        append("• Skip ${state.counts.newAvailable} new song(s) — no Drive API key set.\n")
                    }
                }
                append("\nThis may take a while for large downloads.")
            },
            confirmLabel = "Sync",
            onConfirm = { vm.doSync() },
            onDismiss = { vm.dismissSyncConfirm() },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Neuro Karaoke Syncer") }) }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Warnings ──────────────────────────────────────────────────────
            if (!state.folderConfigured) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "No archive folder configured.\nGo to More → Settings to choose your karaoke folder.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            if (state.folderConfigured && !state.driveApiKeyConfigured && state.counts.newAvailable > 0) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "★ ${state.counts.newAvailable} new song(s) available to download",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Add a Google Drive API key in Settings to download them.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // ── Status chips ──────────────────────────────────────────────────
            Text(
                text = if (state.lastSyncTime > 0L) "Last fetched: ${formatTime(state.lastSyncTime)}" else "Never fetched",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            HorizontalDivider()

            // ── Action buttons ────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Fetch button: tap once to fetch, tap again while fetching to cancel
                Button(
                    onClick = { if (state.isFetching) vm.cancelFetch() else vm.doFetch() },
                    enabled = !state.isSyncing && state.folderConfigured,
                    modifier = Modifier.weight(1f),
                    colors = if (state.isFetching)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    else ButtonDefaults.buttonColors(),
                ) {
                    if (state.isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel Fetch", color = MaterialTheme.colorScheme.onErrorContainer)
                    } else {
                        Text("Fetch")
                    }
                }

                // Sync button: tap once to sync, tap again while syncing to cancel
                OutlinedButton(
                    onClick = { if (state.isSyncing) vm.cancelSync() else vm.requestSync() },
                    enabled = !state.isFetching && state.folderConfigured,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel Sync")
                    } else {
                        Text("Sync")
                    }
                }
            }

            // "Fetched X ago" hint
            state.lastFetchTimeMs?.let { fetchTime ->
                val agoSecs = (System.currentTimeMillis() - fetchTime) / 1000
                val agoText = when {
                    agoSecs < 60 -> "just now"
                    agoSecs < 3600 -> "${agoSecs / 60} min ago"
                    else -> "${agoSecs / 3600}h ago"
                }
                Text(
                    "Fetched $agoText — review counts above then tap Sync to apply changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Fetch progress ────────────────────────────────────────────────
            state.fetchProgress?.let { progress ->
                ProgressSection(progress, label = "Fetch")
            }

            // ── Sync progress ─────────────────────────────────────────────────
            state.syncProgress?.let { progress ->
                ProgressSection(progress, label = "Sync")
            }
        }
    }
}

@Composable
private fun ProgressSection(progress: SyncProgress, label: String) {
    when (progress) {
        is SyncProgress.ScanningLocal ->
            Column {
                Text("$label: scanning local files…", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { if (progress.total > 0) progress.current.toFloat() / progress.total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        is SyncProgress.FetchingMetadata ->
            Text("$label: ${progress.message}", style = MaterialTheme.typography.bodySmall)
        is SyncProgress.ApplyingTags ->
            Column {
                Text(
                    "$label: applying tags (${progress.current}/${progress.total}) — ${progress.songTitle}",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { progress.current.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        is SyncProgress.Downloading ->
            Column {
                Text(
                    "$label: downloading (${progress.current}/${progress.total}) — ${progress.filename}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (progress.bytesTotal > 0) {
                    LinearProgressIndicator(
                        progress = { progress.bytesProgress.toFloat() / progress.bytesTotal },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        is SyncProgress.Completed ->
            Text(
                buildString {
                    append("$label complete!")
                    if (label == "Fetch") {
                        if (progress.updated > 0) append(" ${progress.updated} metadata entry(ies) changed.")
                        else append(" Metadata is already up to date.")
                        if (progress.newAvailable > 0) append(" ${progress.newAvailable} song(s) ready to download.")
                    } else {
                        if (progress.updated > 0) append(" ${progress.updated} tag(s) applied.")
                        if (progress.downloaded > 0) append(" ${progress.downloaded} song(s) downloaded.")
                        if (progress.newAvailable > 0) append(" ${progress.newAvailable} still awaiting download.")
                        if (progress.updated == 0 && progress.downloaded == 0) append(" Nothing to do.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        is SyncProgress.Error ->
            Text(
                "$label error: ${progress.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        else -> Unit
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
