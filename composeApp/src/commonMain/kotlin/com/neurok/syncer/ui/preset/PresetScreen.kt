package com.neurok.syncer.ui.preset

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neurok.syncer.domain.model.SongMetadata
import com.neurok.syncer.domain.model.TagPreset
import com.neurok.syncer.domain.model.TagPresetRegistry
import org.koin.compose.viewmodel.koinViewModel

// Sample song used to preview how each preset formats tags
private val SAMPLE_SONG = SongMetadata(
    xxHash = "preview",
    date = "2023",
    title = "PLACEHOLDER",
    titleOG = "プレースホルダー",
    identify = null,
    artist = "Neuro-sama",
    artistOG = "ニューロ様",
    coverArtist = "Neuro-sama",
    version = 1,
    discNumber = 7,
    track = "042",
    hjsonPath = "disc7/042_placeholder.hjson",
    hjsonSha = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetScreen(modifier: Modifier = Modifier) {
    val vm = koinViewModel<PresetViewModel>()
    val state by vm.state.collectAsState()

    state.savedMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            vm.dismissMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tag Preset") }) },
        snackbarHost = {
            state.savedMessage?.let {
                Snackbar(modifier = Modifier.padding(8.dp)) { Text(it) }
            }
        }
    ) { padding ->
        Column(
            modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                "Choose how ID3 tags are written to your MP3 files. " +
                        "The selected preset is applied on the next sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            TagPresetRegistry.all.forEach { preset ->
                PresetCard(
                    preset = preset,
                    isSelected = state.activePresetId == preset.id,
                    onClick = { vm.selectPreset(preset.id) },
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PresetCard(preset: TagPreset, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    OutlinedCard(
        onClick = onClick,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(preset.displayName, style = MaterialTheme.typography.titleMedium)
                if (isSelected) {
                    Text(
                        "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            // Tag preview
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            Text("Preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            PreviewRow("Title", preset.buildTitle(SAMPLE_SONG))
            PreviewRow("Artist", preset.buildArtist(SAMPLE_SONG))
            PreviewRow("Album", preset.buildAlbum(SAMPLE_SONG))
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}
