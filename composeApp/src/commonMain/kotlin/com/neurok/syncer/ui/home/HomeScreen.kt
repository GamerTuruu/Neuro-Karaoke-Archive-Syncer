package com.neurok.syncer.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neurok.syncer.domain.model.SyncProgress
import com.neurok.syncer.ui.components.ConfirmDialog
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
                if (!state.syncEntireArchive) append("\nSelective sync: only checked songs will be processed.")
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
            // ── Startup placeholder ───────────────────────────────────────────
            if (state.isInitializing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Checking archive…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // ── Warnings (only shown after init) ──────────────────────────
                if (!state.folderConfigured) {
                    WarningCard(
                        message = "No archive folder configured. Tap to go to Settings.",
                        onClick = onNavigateToSettings,
                    )
                }
                if (!state.driveApiKeyConfigured) {
                    WarningCard(
                        message = if (state.counts.newAvailable > 0)
                            "★ ${state.counts.newAvailable} new song(s) available — add a Drive API key in Settings to download."
                        else
                            "No Drive API key — new songs won't be downloaded until one is set.",
                        onClick = onNavigateToSettings,
                        isInfo = true,
                    )
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

            // ── Sync-entire-archive toggle ────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = state.syncEntireArchive,
                    onCheckedChange = { vm.toggleSyncEntireArchive() },
                )
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Sync entire archive", style = MaterialTheme.typography.bodyMedium)
                    if (!state.syncEntireArchive) {
                        Text(
                            "Only checked songs (in Browse tab) will be synced.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Action buttons (split button design) ──────────────────────────
            val busy = state.isFetching || state.isSyncing
            var showDropdown by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                // Main button — Check & Sync (or cancel while running)
                Button(
                    onClick = {
                        when {
                            state.isFetching -> vm.cancelFetch()
                            state.isSyncing -> vm.cancelSync()
                            else -> vm.doFetch()
                        }
                    },
                    enabled = state.folderConfigured || busy,
                    modifier = Modifier.weight(1f),
                    colors = if (busy)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    else ButtonDefaults.buttonColors(),
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.isFetching) "Cancel Fetch" else "Cancel Sync",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        Text("Check & Sync")
                    }
                }
                // Dropdown arrow button
                Box {
                    Button(
                        onClick = { showDropdown = true },
                        enabled = !busy && state.folderConfigured,
                        modifier = Modifier.width(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp),
                    ) {
                        Text("▾")
                    }
                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Scan Only (no sync)") },
                            onClick = { showDropdown = false; vm.doFetch() },
                        )
                        DropdownMenuItem(
                            text = { Text("Sync Only (skip scan)") },
                            onClick = { showDropdown = false; vm.requestSync() },
                        )
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
                    "Fetched $agoText — review counts above, then use ▾ → Sync Only to apply changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Log panels ────────────────────────────────────────────────────
            val activeLogs = when {
                state.isFetching || state.fetchLog.isNotEmpty() && state.syncLog.isEmpty() -> state.fetchLog
                state.isSyncing || state.syncLog.isNotEmpty() -> state.syncLog
                else -> emptyList()
            }
            val logLabel = when {
                state.isFetching -> "Fetch log"
                state.fetchLog.isNotEmpty() && state.syncLog.isEmpty() -> "Fetch log"
                else -> "Sync log"
            }
            if (activeLogs.isNotEmpty()) {
                LogPanel(label = logLabel, lines = activeLogs, isRunning = state.isFetching || state.isSyncing)
            }
        }
    }
}

@Composable
private fun WarningCard(message: String, onClick: () -> Unit, isInfo: Boolean = false) {
    val containerColor = if (isInfo)
        MaterialTheme.colorScheme.tertiaryContainer
    else
        MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isInfo)
        MaterialTheme.colorScheme.onTertiaryContainer
    else
        MaterialTheme.colorScheme.onErrorContainer

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = contentColor,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onClick) {
                Text("Settings", color = contentColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LogPanel(label: String, lines: List<String>, isRunning: Boolean) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // Track whether user has scrolled up (pauses auto-scroll)
    var userScrolled by remember { mutableStateOf(false) }

    // Auto-scroll to bottom on new lines, unless the user has scrolled up
    LaunchedEffect(lines.size) {
        if (!userScrolled && lines.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(lines.size - 1)
            }
        }
    }

    // Detect user scroll: if not at bottom, mark as user-scrolled
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val layoutInfo = listState.layoutInfo
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val isAtBottom = lastVisible >= lines.size - 1
        if (isAtBottom) userScrolled = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                userScrollEnabled = true,
            ) {
                items(lines) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            line.startsWith("Error") -> MaterialTheme.colorScheme.error
                            line.startsWith("Done") -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
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

/** Convert a Unix-epoch millisecond timestamp to a human-readable date/time string. */
private fun formatTime(ms: Long): String {
    // Work in seconds from epoch
    var remaining = ms / 1000L
    val secs = (remaining % 60).toInt(); remaining /= 60
    val mins = (remaining % 60).toInt(); remaining /= 60
    val hours = (remaining % 24).toInt(); remaining /= 24
    // remaining is now total days since 1970-01-01
    var year = 1970
    while (true) {
        val daysInYear = if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 366L else 365L
        if (remaining < daysInYear) break
        remaining -= daysInYear
        year++
    }
    val isLeap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val monthDays = intArrayOf(31, if (isLeap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var month = 1
    for (days in monthDays) {
        if (remaining < days) break
        remaining -= days
        month++
    }
    val day = (remaining + 1).toInt()
    return "%04d-%02d-%02d %02d:%02d".format(year, month, day, hours, mins)
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

