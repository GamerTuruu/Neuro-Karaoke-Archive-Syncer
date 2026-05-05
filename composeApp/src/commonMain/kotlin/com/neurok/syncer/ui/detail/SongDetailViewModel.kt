package com.neurok.syncer.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurok.syncer.domain.model.SongMetadata
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.TagPresetRegistry
import com.neurok.syncer.domain.usecase.ApplyTagsUseCase
import com.neurok.syncer.domain.usecase.DownloadSongUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SongDetailUiState(
    val song: SongMetadata? = null,
    val builtTitle: String = "",
    val builtArtist: String = "",
    val isExcluded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val message: String? = null,
)

class SongDetailViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val applyTagsUseCase: ApplyTagsUseCase,
    private val downloadSongUseCase: DownloadSongUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SongDetailUiState())
    val state: StateFlow<SongDetailUiState> = _state

    fun load(xxHash: String) {
        viewModelScope.launch {
            val song = songRepository.getByXxHash(xxHash) ?: return@launch
            val presetId = settingsRepository.get(SettingsKeys.ACTIVE_PRESET_ID)
            val preset = TagPresetRegistry.fromIdOrDefault(presetId)
            _state.update {
                it.copy(
                    song = song,
                    builtTitle = preset.buildTitle(song),
                    builtArtist = preset.buildArtist(song),
                )
            }
        }
    }

    fun toggleExcluded() {
        val song = _state.value.song ?: return
        viewModelScope.launch {
            val newExcluded = !_state.value.isExcluded
            songRepository.updateExcluded(song.xxHash, newExcluded)
            _state.update { it.copy(isExcluded = newExcluded) }
        }
    }

    fun forceReapplyTags() {
        val song = _state.value.song ?: return
        viewModelScope.launch {
            // localFileUri not in domain model — this would need DB raw access
            // Placeholder: show a message
            _state.update { it.copy(message = "Re-apply tags feature coming soon") }
        }
    }

    fun download() {
        val song = _state.value.song ?: return
        viewModelScope.launch {
            _state.update { it.copy(isDownloading = true) }
            val result = downloadSongUseCase.execute(song.xxHash) { received, total ->
                if (total > 0) _state.update { it.copy(downloadProgress = received.toFloat() / total) }
            }
            _state.update {
                it.copy(
                    isDownloading = false,
                    downloadProgress = 0f,
                    message = if (result.isSuccess) "Downloaded successfully" else "Download failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}
