package com.neurok.syncer.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    xxHash: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = koinViewModel<SongDetailViewModel>()
    val state by vm.state.collectAsState()

    LaunchedEffect(xxHash) { vm.load(xxHash) }

    state.message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            vm.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.song?.title ?: "Song Detail") },
                navigationIcon = { IconButton(onClick = onNavigateUp) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        snackbarHost = {
            state.message?.let { Snackbar(modifier = Modifier.padding(8.dp)) { Text(it) } }
        }
    ) { padding ->
        val song = state.song
        if (song == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetaRow("Title", song.title)
                song.titleOG?.let { MetaRow("Original Title", it) }
                song.identify?.let { MetaRow("Identify", it) }
                MetaRow("Artist", song.artist)
                song.artistOG?.let { MetaRow("Original Artist", it) }
                MetaRow("Cover Artist", song.coverArtist)
                MetaRow("Disc", song.discNumber.toString())
                MetaRow("Track", song.track)
                MetaRow("Version", song.version.toString())
                MetaRow("Date", song.date)
                song.comment?.let { MetaRow("Comment", it) }
                if (song.special) MetaRow("Special", "Yes")

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text("Applied Tags (${tagPresetLabel(state.builtTitle)})", style = MaterialTheme.typography.titleSmall)
                MetaRow("TITLE tag", state.builtTitle)
                MetaRow("ARTIST tag", state.builtArtist)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::toggleExcluded, modifier = Modifier.weight(1f)) {
                        Text(if (state.isIncluded) "Uncheck from Sync" else "Include in Sync")
                    }
                    OutlinedButton(onClick = vm::forceReapplyTags, modifier = Modifier.weight(1f)) {
                        Text("Re-apply Tags")
                    }
                }

                Button(
                    onClick = vm::download,
                    enabled = !state.isDownloading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isDownloading) {
                        LinearProgressIndicator(
                            progress = { state.downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("Download")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun tagPresetLabel(title: String) = if (title.isNotBlank()) "preview" else "none"
