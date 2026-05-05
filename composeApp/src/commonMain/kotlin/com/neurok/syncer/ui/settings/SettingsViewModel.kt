package com.neurok.syncer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.TagPresetRegistry
import com.neurok.syncer.domain.repository.DriveRepository
import com.neurok.syncer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val folderUri: String = "",
    val syncScheduleHours: Int = 24,
    val activePresetId: String = "default",
    val driveApiKey: String = "",
    val githubPat: String = "",
    val isClearingCache: Boolean = false,
    val saveMessage: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val driveRepository: DriveRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _state.update {
            it.copy(
                folderUri = settingsRepository.get(SettingsKeys.LOCAL_FOLDER_URI) ?: "",
                syncScheduleHours = settingsRepository.getInt(SettingsKeys.SYNC_SCHEDULE_HOURS, 24),
                activePresetId = settingsRepository.get(SettingsKeys.ACTIVE_PRESET_ID) ?: "default",
                driveApiKey = settingsRepository.get(SettingsKeys.DRIVE_API_KEY) ?: "",
                githubPat = settingsRepository.get(SettingsKeys.GITHUB_PAT) ?: "",
            )
        }
    }

    fun setFolderUri(uri: String) = _state.update { it.copy(folderUri = uri) }
    fun setSyncSchedule(hours: Int) = _state.update { it.copy(syncScheduleHours = hours) }
    fun setPresetId(id: String) = _state.update { it.copy(activePresetId = id) }
    fun setDriveApiKey(key: String) = _state.update { it.copy(driveApiKey = key) }
    fun setGithubPat(pat: String) = _state.update { it.copy(githubPat = pat) }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            settingsRepository.set(SettingsKeys.LOCAL_FOLDER_URI, s.folderUri)
            settingsRepository.set(SettingsKeys.SYNC_SCHEDULE_HOURS, s.syncScheduleHours.toString())
            settingsRepository.set(SettingsKeys.ACTIVE_PRESET_ID, s.activePresetId)
            settingsRepository.set(SettingsKeys.DRIVE_API_KEY, s.driveApiKey)
            settingsRepository.set(SettingsKeys.GITHUB_PAT, s.githubPat)
            _state.update { it.copy(saveMessage = "Saved") }
        }
    }

    fun clearDriveCache() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true) }
            driveRepository.clearIndex()
            _state.update { it.copy(isClearingCache = false, saveMessage = "Drive cache cleared") }
        }
    }

    fun dismissMessage() = _state.update { it.copy(saveMessage = null) }
}
