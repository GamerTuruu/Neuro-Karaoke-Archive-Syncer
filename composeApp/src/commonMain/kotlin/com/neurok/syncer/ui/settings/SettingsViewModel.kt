package com.neurok.syncer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.repository.DriveRepository
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    // Basic
    val folderUri: String = "",
    val syncScheduleHours: Int = 24,
    val syncScheduleHour: Int = 2,   // 0-23, local hour at which auto-sync runs
    val driveApiKey: String = "",
    val themeMode: String = "system",  // "dark" | "light" | "system"
    // Advanced
    val driveFolderId: String = "",
    val githubRepo: String = "",
    val metadataZipFolder: String = "",
    val metadataZipMaxCount: Int = 3,
    val showAdvancedSection: Boolean = false,
    val showAdvancedWarning: Boolean = false,
    // Dialogs
    val showExitConfirm: Boolean = false,
    val showResetConfirm: Boolean = false,
    val showClearCacheConfirm: Boolean = false,
    // Feedback
    val isClearingCache: Boolean = false,
    val saveMessage: String? = null,
    val hasUnsavedChanges: Boolean = false,
    // Drive API key test result (null = not tested, starts with "✓" = ok, else error)
    val driveKeyTestResult: String? = null,
)

// Snapshot of last-saved state for change detection
private data class SavedSnapshot(
    val folderUri: String = "",
    val syncScheduleHours: Int = 24,
    val syncScheduleHour: Int = 2,
    val driveApiKey: String = "",
    val driveFolderId: String = "",
    val githubRepo: String = "",
    val themeMode: String = "system",
    val metadataZipFolder: String = "",
    val metadataZipMaxCount: Int = 3,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val driveRepository: DriveRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state
    private var savedSnapshot = SavedSnapshot()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val folderUri = settingsRepository.get(SettingsKeys.LOCAL_FOLDER_URI) ?: ""
        val schedHours = settingsRepository.getInt(SettingsKeys.SYNC_SCHEDULE_HOURS, 24)
        val schedHour = settingsRepository.getInt(SettingsKeys.SYNC_SCHEDULE_HOUR, 2)
        val apiKey = settingsRepository.get(SettingsKeys.DRIVE_API_KEY) ?: ""
        val folderId = settingsRepository.get(SettingsKeys.DRIVE_FOLDER_ID) ?: ""
        val ghRepo = settingsRepository.get(SettingsKeys.GITHUB_REPO) ?: ""
        val theme = settingsRepository.get(SettingsKeys.THEME) ?: "system"
        val zipFolder = settingsRepository.get(SettingsKeys.METADATA_ZIP_FOLDER) ?: ""
        val zipMaxCount = settingsRepository.get(SettingsKeys.METADATA_ZIP_MAX_COUNT)?.toIntOrNull() ?: 3
        savedSnapshot = SavedSnapshot(
            folderUri = folderUri, syncScheduleHours = schedHours, syncScheduleHour = schedHour,
            driveApiKey = apiKey, driveFolderId = folderId, githubRepo = ghRepo,
            themeMode = theme, metadataZipFolder = zipFolder, metadataZipMaxCount = zipMaxCount,
        )
        AppTheme.set(theme)  // apply immediately on load
        _state.update {
            it.copy(
                folderUri = folderUri,
                syncScheduleHours = schedHours,
                syncScheduleHour = schedHour,
                driveApiKey = apiKey,
                driveFolderId = folderId,
                githubRepo = ghRepo,
                themeMode = theme,
                metadataZipFolder = zipFolder,
                metadataZipMaxCount = zipMaxCount,
                hasUnsavedChanges = false,
            )
        }
    }

    private fun markDirty() = _state.update {
        val s = it
        val dirty = s.folderUri != savedSnapshot.folderUri ||
                s.syncScheduleHours != savedSnapshot.syncScheduleHours ||
                s.syncScheduleHour != savedSnapshot.syncScheduleHour ||
                s.driveApiKey != savedSnapshot.driveApiKey ||
                s.driveFolderId != savedSnapshot.driveFolderId ||
                s.githubRepo != savedSnapshot.githubRepo ||
                s.themeMode != savedSnapshot.themeMode ||
                s.metadataZipFolder != savedSnapshot.metadataZipFolder ||
                s.metadataZipMaxCount != savedSnapshot.metadataZipMaxCount
        it.copy(hasUnsavedChanges = dirty)
    }

    fun setFolderUri(uri: String) { _state.update { it.copy(folderUri = uri) }; markDirty() }
    fun setSyncSchedule(hours: Int) { _state.update { it.copy(syncScheduleHours = hours) }; markDirty() }
    fun setSyncScheduleHour(hour: Int) { _state.update { it.copy(syncScheduleHour = hour.coerceIn(0, 23)) }; markDirty() }
    fun setDriveApiKey(key: String) { _state.update { it.copy(driveApiKey = key, driveKeyTestResult = null) }; markDirty() }
    fun setDriveFolderId(id: String) { _state.update { it.copy(driveFolderId = id) }; markDirty() }
    fun setGithubRepo(repo: String) { _state.update { it.copy(githubRepo = repo) }; markDirty() }
    fun setThemeMode(mode: String) { _state.update { it.copy(themeMode = mode) }; markDirty() }
    fun setMetadataZipFolder(path: String) { _state.update { it.copy(metadataZipFolder = path) }; markDirty() }
    fun setMetadataZipMaxCount(count: Int) { _state.update { it.copy(metadataZipMaxCount = count) }; markDirty() }

    fun requestExpandAdvanced() = _state.update { it.copy(showAdvancedWarning = true) }
    fun confirmExpandAdvanced() = _state.update { it.copy(showAdvancedWarning = false, showAdvancedSection = true) }
    fun dismissAdvancedWarning() = _state.update { it.copy(showAdvancedWarning = false) }

    fun requestExit() {
        if (_state.value.hasUnsavedChanges) _state.update { it.copy(showExitConfirm = true) }
        // else: caller should pop
    }
    fun dismissExitConfirm() = _state.update { it.copy(showExitConfirm = false) }
    fun discardAndExit(doExit: () -> Unit) {
        _state.update { it.copy(showExitConfirm = false) }
        doExit()
    }

    fun requestReset() = _state.update { it.copy(showResetConfirm = true) }
    fun dismissResetConfirm() = _state.update { it.copy(showResetConfirm = false) }
    fun confirmReset() {
        viewModelScope.launch {
            settingsRepository.set(SettingsKeys.DRIVE_FOLDER_ID, "")
            settingsRepository.set(SettingsKeys.GITHUB_REPO, "")
            load()
            _state.update { it.copy(showResetConfirm = false, saveMessage = "Advanced settings reset to defaults") }
        }
    }

    fun requestClearCache() = _state.update { it.copy(showClearCacheConfirm = true) }
    fun dismissClearCacheConfirm() = _state.update { it.copy(showClearCacheConfirm = false) }
    fun confirmClearCache() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true, showClearCacheConfirm = false) }
            driveRepository.clearIndex()
            _state.update { it.copy(isClearingCache = false, saveMessage = "Drive cache cleared") }
        }
    }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            settingsRepository.set(SettingsKeys.LOCAL_FOLDER_URI, s.folderUri)
            settingsRepository.set(SettingsKeys.SYNC_SCHEDULE_HOURS, s.syncScheduleHours.toString())
            settingsRepository.set(SettingsKeys.SYNC_SCHEDULE_HOUR, s.syncScheduleHour.toString())
            settingsRepository.set(SettingsKeys.DRIVE_API_KEY, s.driveApiKey)
            settingsRepository.set(SettingsKeys.DRIVE_FOLDER_ID, s.driveFolderId)
            settingsRepository.set(SettingsKeys.GITHUB_REPO, s.githubRepo)
            settingsRepository.set(SettingsKeys.THEME, s.themeMode)
            settingsRepository.set(SettingsKeys.METADATA_ZIP_FOLDER, s.metadataZipFolder)
            settingsRepository.set(SettingsKeys.METADATA_ZIP_MAX_COUNT, s.metadataZipMaxCount.toString())
            AppTheme.set(s.themeMode)  // apply immediately
            savedSnapshot = SavedSnapshot(
                folderUri = s.folderUri, syncScheduleHours = s.syncScheduleHours, syncScheduleHour = s.syncScheduleHour,
                driveApiKey = s.driveApiKey, driveFolderId = s.driveFolderId, githubRepo = s.githubRepo,
                themeMode = s.themeMode, metadataZipFolder = s.metadataZipFolder, metadataZipMaxCount = s.metadataZipMaxCount,
            )
            _state.update { it.copy(hasUnsavedChanges = false, saveMessage = "Saved") }
        }
    }

    fun dismissMessage() = _state.update { it.copy(saveMessage = null) }

    fun testDriveApiKey() {
        viewModelScope.launch {
            val key = _state.value.driveApiKey
            if (key.isBlank()) {
                _state.update { it.copy(driveKeyTestResult = "Enter a key first") }
                return@launch
            }
            _state.update { it.copy(driveKeyTestResult = "Testing…") }
            val folderId = _state.value.driveFolderId.ifBlank { com.neurok.syncer.data.drive.ARCHIVE_FOLDER_ID }
            val result = driveRepository.testApiKey(key, folderId)
            _state.update {
                it.copy(
                    driveKeyTestResult = if (result.isSuccess) "✓ Key is valid"
                    else "✗ ${result.exceptionOrNull()?.message?.take(120) ?: "Unknown error"}"
                )
            }
        }
    }
}
