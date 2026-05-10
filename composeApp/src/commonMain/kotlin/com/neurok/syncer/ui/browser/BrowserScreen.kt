package com.neurok.syncer.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neurok.syncer.domain.model.SongMetadata
import com.neurok.syncer.domain.model.SyncStatus
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onSongClick: (xxHash: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = koinViewModel<BrowserViewModel>()
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Browse") })
        }
    ) { padding ->
        Column(modifier.padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search title, artist…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
            )

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    FilterMode.ALL to "All",
                    FilterMode.MISSING to "Missing",
                    FilterMode.DOWNLOADED to "Downloaded",
                    FilterMode.UNCHECKED to "Unchecked",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = state.filterMode == mode,
                        onClick = { vm.setFilter(mode) },
                        label = { Text(label) },
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.songs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No songs yet — run a Fetch from the Sync tab first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = androidx.compose.ui.Modifier.padding(32.dp),
                    )
                }
            } else if (state.isGrouped) {
                // Collapsible disc-grouped view
                val grouped = remember(state.songs) {
                    state.songs
                        .groupBy { it.hjsonPath.substringBefore("/", "Disc ${it.discNumber}") }
                        .entries
                        .sortedBy { (_, songs) -> songs.firstOrNull()?.discNumber ?: 0 }
                }
                LazyColumn {
                    grouped.forEach { (discName, songs) ->
                        val isExpanded = discName in state.expandedDiscs
                        stickyHeader(key = "header_$discName") {
                            DiscHeader(
                                discName = discName,
                                songCount = songs.size,
                                isExpanded = isExpanded,
                                onClick = { vm.toggleDiscExpanded(discName) },
                            )
                        }
                        if (isExpanded) {
                            items(songs, key = { it.xxHash }) { song ->
                                SongRow(
                                    song = song,
                                    onClick = { onSongClick(song.xxHash) },
                                    onLongClick = { vm.toggleExcluded(song.xxHash, song.syncStatus == SyncStatus.EXCLUDED) },
                                    onToggleIncluded = { vm.toggleUserIncluded(song.xxHash, song.userIncluded) },
                                )
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                    }
                }
            } else {
                // Flat view for search / filtered results
                LazyColumn {
                    items(state.songs, key = { it.xxHash }) { song ->
                        SongRow(
                            song = song,
                            onClick = { onSongClick(song.xxHash) },
                            onLongClick = { vm.toggleExcluded(song.xxHash, song.syncStatus == SyncStatus.EXCLUDED) },
                            onToggleIncluded = { vm.toggleUserIncluded(song.xxHash, song.userIncluded) },
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscHeader(
    discName: String,
    songCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                discName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$songCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: SongMetadata,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleIncluded: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sync-selection checkbox
        Checkbox(
            checked = song.userIncluded,
            onCheckedChange = { onToggleIncluded() },
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.width(4.dp))
        // Status badge
        val statusColor = when (song.syncStatus) {
            SyncStatus.UP_TO_DATE -> MaterialTheme.colorScheme.primary
            SyncStatus.NEEDS_UPDATE -> MaterialTheme.colorScheme.secondary
            SyncStatus.NEW_AVAILABLE -> MaterialTheme.colorScheme.tertiary
            SyncStatus.ORPHAN -> MaterialTheme.colorScheme.error
            SyncStatus.EXCLUDED -> MaterialTheme.colorScheme.outline
            SyncStatus.DOWNLOADING -> MaterialTheme.colorScheme.tertiary
        }
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = statusColor.copy(alpha = 0.15f),
            modifier = Modifier.size(width = 8.dp, height = 8.dp),
        ) {}
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            val displayTitle = buildString {
                val base = if (song.titleOG != null) "${song.titleOG} (${song.title})" else song.title
                append(base)
                song.identify?.let { append(" $it") }
            }
            Text(
                displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (song.userIncluded) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            val displayArtist = buildString {
                if (song.coverArtist.isNotBlank()) { append(song.coverArtist); append(" - ") }
                if (song.artistOG != null) append("${song.artistOG} (${song.artist})")
                else append(song.artist)
            }
            Text(
                displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "#${song.track}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

