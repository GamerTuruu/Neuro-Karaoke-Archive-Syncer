package com.neurok.syncer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neurok.syncer.domain.model.TagPresetRegistry
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = koinViewModel<SettingsViewModel>()
    val state by vm.state.collectAsState()

    state.saveMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            vm.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = {
            state.saveMessage?.let {
                Snackbar(modifier = Modifier.padding(8.dp)) { Text(it) }
            }
        }
    ) { padding ->
        Column(
            modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Folder
            SectionTitle("Archive Folder")
            OutlinedTextField(
                value = state.folderUri,
                onValueChange = vm::setFolderUri,
                label = { Text("Folder URI") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    TextButton(onClick = onPickFolder) { Text("Choose") }
                }
            )

            // Sync schedule
            SectionTitle("Sync Schedule")
            val scheduleOptions = listOf(0 to "Off", 12 to "Every 12 hours", 24 to "Daily", 168 to "Weekly")
            scheduleOptions.forEach { (hours, label) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.syncScheduleHours == hours,
                        onClick = { vm.setSyncSchedule(hours) },
                    )
                    Text(label)
                }
            }

            // Preset
            SectionTitle("Tag Preset")
            TagPresetRegistry.all.forEach { preset ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.activePresetId == preset.id,
                        onClick = { vm.setPresetId(preset.id) },
                    )
                    Text(preset.displayName)
                }
            }

            // Drive API key
            SectionTitle("Google Drive API Key")
            OutlinedTextField(
                value = state.driveApiKey,
                onValueChange = vm::setDriveApiKey,
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )

            // GitHub PAT
            SectionTitle("GitHub Personal Access Token (optional)")
            OutlinedTextField(
                value = state.githubPat,
                onValueChange = vm::setGithubPat,
                label = { Text("PAT (higher rate limit)") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::save, modifier = Modifier.weight(1f)) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = vm::clearDriveCache,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isClearingCache,
                ) {
                    Text("Clear Drive Cache")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
