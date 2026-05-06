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
    val driveApiKey: String = "",
    val githubPat: String = "",
    val themeMode: String = "dark",  // "dark" | "light" | "system"
    // Advanced
    val driveFolderId: String = "",
    val githubRepo: String = "",
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
)

// Snapshot of last-saved state for change detection
private data class SavedSnapshot(
    val folderUri: String = "",
    val syncScheduleHours: Int = 24,
    val driveApiKey: String = "",
    val githubPat: String = "",
    val driveFolderId: String = "",
    val githubRepo: String = "",
    val themeMode: String = "dark",
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
        val apiKey = settingsRepository.get(SettingsKeys.DRIVE_API_KEY) ?: ""
        val pat = settingsRepository.get(SettingsKeys.GITHUB_PAT) ?: ""
        val folderId = settingsRepository.get(SettingsKeys.DRIVE_FOLDER_ID) ?: ""
        val ghRepo = settingsRepository.get(SettingsKeys.GITHUB_REPO) ?: ""
        val theme = settingsRepository.get(SettingsKeys.THEME) ?: "dark"
        savedSnapshot = SavedSnapshot(folderUri, schedHours, apiKey, pat, folderId, ghRepo, theme)
        AppTheme.set(theme)  // apply immediately on load
        _state.update {
            it.copy(
                folderUri = folderUri,
                syncScheduleHours = schedHours,
                driveApiKey = apiKey,
                githubPat = pat,
                driveFolderId = folderId,
                githubRepo = ghRepo,
                themeMode = theme,
                hasUnsavedChanges = false,
            )
        }
    }

    private fun markDirty() = _state.update {
        val s = it
        val dirty = s.folderUri != savedSnapshot.folderUri ||
                s.syncScheduleHours != savedSnapshot.syncScheduleHours ||
                s.driveApiKey != savedSnapshot.driveApiKey ||
                s.githubPat != savedSnapshot.githubPat ||
                s.driveFolderId != savedSnapshot.driveFolderId ||
                s.githubRepo != savedSnapshot.githubRepo ||
                s.themeMode != savedSnapshot.themeMode
        it.copy(hasUnsavedChanges = dirty)
    }

    fun setFolderUri(uri: String) { _state.update { it.copy(folderUri = uri) }; markDirty() }
    fun setSyncSchedule(hours: Int) { _state.update { it.copy(syncScheduleHours = hours) }; markDirty() }
    fun setDriveApiKey(key: String) { _state.update { it.copy(driveApiKey = key) }; markDirty() }
    fun setGithubPat(pat: String) { _state.update { it.copy(githubPat = pat) }; markDirty() }
    fun setDriveFolderId(id: String) { _state.update { it.copy(driveFolderId = id) }; markDirty() }
    fun setGithubRepo(repo: String) { _state.update { it.copy(githubRepo = repo) }; markDirty() }
    fun setThemeMode(mode: String) { _state.update { it.copy(themeMode = mode) }; markDirty() }

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
            settingsRepository.set(SettingsKeys.DRIVE_API_KEY, s.driveApiKey)
            settingsRepository.set(SettingsKeys.GITHUB_PAT, s.githubPat)
            settingsRepository.set(SettingsKeys.DRIVE_FOLDER_ID, s.driveFolderId)
            settingsRepository.set(SettingsKeys.GITHUB_REPO, s.githubRepo)
            settingsRepository.set(SettingsKeys.THEME, s.themeMode)
            AppTheme.set(s.themeMode)  // apply immediately
            savedSnapshot = SavedSnapshot(s.folderUri, s.syncScheduleHours, s.driveApiKey, s.githubPat, s.driveFolderId, s.githubRepo, s.themeMode)
            _state.update { it.copy(hasUnsavedChanges = false, saveMessage = "Saved") }
        }
    }

    fun dismissMessage() = _state.update { it.copy(saveMessage = null) }
}
