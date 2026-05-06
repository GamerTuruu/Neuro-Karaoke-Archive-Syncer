package com.neurok.syncer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neurok.syncer.ui.components.ConfirmDialog
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onPickFolder: (callback: (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = koinViewModel<SettingsViewModel>()
    val state by vm.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current
    val noRipple = remember { MutableInteractionSource() }
    var showApiHelp by remember { mutableStateOf(false) }
    var showPatHelp by remember { mutableStateOf(false) }

    // Intercept back press to warn about unsaved changes
    BackHandler(enabled = state.hasUnsavedChanges) {
        vm.requestExit()
    }

    // Auto-dismiss snackbar
    state.saveMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            vm.dismissMessage()
        }
    }

    // --- Dialogs ---
    if (state.showExitConfirm) {
        ConfirmDialog(
            title = "Unsaved Changes",
            message = "You have unsaved changes. Discard them and leave?",
            confirmLabel = "Discard",
            isDestructive = true,
            onConfirm = { vm.discardAndExit(onNavigateUp) },
            onDismiss = { vm.dismissExitConfirm() },
        )
    }
    if (state.showAdvancedWarning) {
        ConfirmDialog(
            title = "Advanced Settings",
            message = "These settings override the default Drive folder and GitHub repo. Incorrect values will break sync. Proceed?",
            confirmLabel = "I understand",
            onConfirm = { vm.confirmExpandAdvanced() },
            onDismiss = { vm.dismissAdvancedWarning() },
        )
    }
    if (state.showResetConfirm) {
        ConfirmDialog(
            title = "Reset Advanced Settings",
            message = "Reset Drive Folder ID and GitHub Repo to their defaults?",
            confirmLabel = "Reset",
            isDestructive = true,
            onConfirm = { vm.confirmReset() },
            onDismiss = { vm.dismissResetConfirm() },
        )
    }
    if (state.showClearCacheConfirm) {
        ConfirmDialog(
            title = "Clear Drive Cache",
            message = "This will clear the cached Drive file index. The next sync will re-fetch all file IDs from Google Drive.",
            confirmLabel = "Clear",
            onConfirm = { vm.confirmClearCache() },
            onDismiss = { vm.dismissClearCacheConfirm() },
        )
    }
    if (showPatHelp) {
        AlertDialog(
            onDismissRequest = { showPatHelp = false },
            title = { Text("Getting a GitHub Token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A token is optional but raises the API rate limit from 60 to 5,000 requests/hour.")
                    Text("1. Go to github.com → Settings")
                    Text("2. Developer settings → Personal access tokens → Tokens (classic)")
                    Text("3. Click \"Generate new token (classic)\"")
                    Text("4. Give it a name, set expiry")
                    Text("5. Under Scopes: only tick \"public_repo\" (read-only is enough)")
                    Text("6. Click Generate token and copy it")
                    Text("The token starts with \"ghp_\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { uriHandler.openUri("https://github.com/settings/tokens/new") }) {
                    Text("Open GitHub")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPatHelp = false }) { Text("Close") }
            },
        )
    }
    if (showApiHelp) {
        AlertDialog(
            onDismissRequest = { showApiHelp = false },
            title = { Text("Getting a Google Drive API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Open Google Cloud Console")
                    Text("2. Create or select a project")
                    Text("3. Go to APIs & Services → Library")
                    Text("4. Search for and enable \"Google Drive API\"")
                    Text("5. Go to APIs & Services → Credentials")
                    Text("6. Click \"Create Credentials\" → API key")
                    Text("7. Copy the key (starts with \"AIzaSy...\")")
                    Text("Restrict the key to the Drive API for security.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { uriHandler.openUri("https://console.cloud.google.com/apis/credentials") }) {
                    Text("Open Console")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiHelp = false }) { Text("Close") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.hasUnsavedChanges) vm.requestExit() else onNavigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.hasUnsavedChanges) {
                        TextButton(onClick = vm::save) { Text("Save") }
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
                .verticalScroll(rememberScrollState())
                .clickable(interactionSource = noRipple, indication = null) { focusManager.clearFocus() },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Theme ────────────────────────────────────────────────────────────
            SectionTitle("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (mode, label) ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Archive Folder ──────────────────────────────────────────────
            SectionTitle("Archive Folder")
            Text(
                "Select the folder on your device that contains (or will contain) your karaoke MP3 files. " +
                "This is typically the \"Neuro Karaoke Archive V3\" folder you downloaded from Google Drive. " +
                "The app does not move or copy files — it reads from and writes tags to whatever folder you choose.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (state.folderUri.isBlank()) "No folder selected" else state.folderUri
                        .substringAfterLast("%2F").substringAfterLast("/").ifBlank { state.folderUri },
                    onValueChange = {},
                    label = { Text("Selected folder") },
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                    singleLine = true,
                    isError = state.folderUri.isBlank(),
                )
                Button(onClick = { onPickFolder { uri -> vm.setFolderUri(uri) } }) {
                    Text("Choose")
                }
            }
            if (state.folderUri.isBlank()) {
                Text(
                    "⚠ No folder selected. The app cannot sync or play songs until a folder is chosen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // ── Sync Schedule ───────────────────────────────────────────────
            SectionTitle("Background Sync Schedule")
            listOf(0 to "Off", 12 to "Every 12 hours", 24 to "Daily", 168 to "Weekly").forEach { (hours, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { focusManager.clearFocus(); vm.setSyncSchedule(hours) }
                        .padding(vertical = 2.dp),
                ) {
                    RadioButton(selected = state.syncScheduleHours == hours, onClick = { vm.setSyncSchedule(hours) })
                    Text(label, modifier = Modifier.padding(start = 4.dp))
                }
            }

            // ── Google Drive API Key ────────────────────────────────────────
            SectionTitle("Google Drive API Key")
            Text(
                "Required to download new songs from Google Drive. " +
                "Without a key, Sync will only apply tags to songs you already have — new songs will be skipped. " +
                "Get a free key from Google Cloud Console → APIs & Services → Credentials.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = state.driveApiKey,
                    onValueChange = vm::setDriveApiKey,
                    label = { Text("API Key") },
                    modifier = Modifier.weight(1f),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    placeholder = { Text("AIzaSy...") },
                )
                IconButton(onClick = { showApiHelp = true }) {
                    Icon(Icons.Filled.HelpOutline, "How to get API key", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                OutlinedButton(
                    onClick = vm::testDriveApiKey,
                    enabled = state.driveApiKey.isNotBlank() && state.driveKeyTestResult != "Testing…",
                ) { Text("Test key") }
                state.driveKeyTestResult?.let { result ->
                    val isOk = result.startsWith("✓")
                    Text(
                        result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOk) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ── GitHub PAT ──────────────────────────────────────────────────
            SectionTitle("GitHub Token (optional)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = state.githubPat,
                    onValueChange = vm::setGithubPat,
                    label = { Text("Personal Access Token") },
                    modifier = Modifier.weight(1f),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    placeholder = { Text("ghp_...") },
                )
                IconButton(onClick = { showPatHelp = true }) {
                    Icon(Icons.Filled.HelpOutline, "How to get token", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                "Increases GitHub API rate limit from 60 to 5,000 requests/hour.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Actions ─────────────────────────────────────────────────────
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) { Text("Save Settings") }
            OutlinedButton(
                onClick = vm::requestClearCache,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isClearingCache,
            ) { Text("Clear Drive Cache") }

            // ── Advanced ────────────────────────────────────────────────────
            HorizontalDivider()

            if (!state.showAdvancedSection) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Advanced", style = MaterialTheme.typography.titleSmall)
                        Text("Override Drive folder and metadata repo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = vm::requestExpandAdvanced) { Text("Show") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text("Advanced — change only if you know what you're doing",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = state.driveFolderId,
                    onValueChange = vm::setDriveFolderId,
                    label = { Text("Drive Folder ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("1B1VaWp-mCKk15_7XpFnImsTdBJPOGx7a (default)") },
                    supportingText = { Text("Google Drive folder ID for the archive. Leave blank for default.") },
                )
                OutlinedTextField(
                    value = state.githubRepo,
                    onValueChange = vm::setGithubRepo,
                    label = { Text("GitHub Metadata Repo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Nyss777/Neuro-Karaoke-Archive-Metadata (default)") },
                    supportingText = { Text("Format: owner/repo — the source of HJSON metadata files.") },
                )
                OutlinedButton(
                    onClick = vm::requestReset,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Reset to Defaults") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
