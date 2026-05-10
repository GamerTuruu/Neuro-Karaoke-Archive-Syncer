package com.neurok.syncer.ui.preset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.TagPresetRegistry
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.domain.usecase.ApplyTagsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PresetUiState(
    val activePresetId: String = "default",
    val savedMessage: String? = null,
    /** Non-null while a preset change is pending confirmation. */
    val pendingPresetId: String? = null,
    /** True while immediately applying tags to all local songs. */
    val isApplying: Boolean = false,
    val applyProgress: String? = null,
)

class PresetViewModel(
    private val settingsRepository: SettingsRepository,
    private val songRepository: SongRepository,
    private val applyTagsUseCase: ApplyTagsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PresetUiState())
    val state: StateFlow<PresetUiState> = _state

    init {
        viewModelScope.launch {
            val id = settingsRepository.get(SettingsKeys.ACTIVE_PRESET_ID) ?: "default"
            _state.update { it.copy(activePresetId = id) }
        }
    }

    /** Opens the confirmation dialog for switching to [id]. */
    fun requestPreset(id: String) {
        if (id == _state.value.activePresetId) return
        _state.update { it.copy(pendingPresetId = id) }
    }

    fun dismissPresetDialog() = _state.update { it.copy(pendingPresetId = null) }

    /** Save preset and skip immediate re-tagging. */
    fun confirmPresetNextSync() {
        val id = _state.value.pendingPresetId ?: return
        viewModelScope.launch {
            settingsRepository.set(SettingsKeys.ACTIVE_PRESET_ID, id)
            _state.update { it.copy(activePresetId = id, pendingPresetId = null,
                savedMessage = "Preset saved. Tags will be applied on next sync.") }
        }
    }

    /** Save preset AND immediately re-tag all songs that already have local files. */
    fun confirmPresetApplyNow() {
        val id = _state.value.pendingPresetId ?: return
        viewModelScope.launch {
            settingsRepository.set(SettingsKeys.ACTIVE_PRESET_ID, id)
            _state.update { it.copy(activePresetId = id, pendingPresetId = null, isApplying = true, applyProgress = "Starting…") }
            try {
                val allSongs = songRepository.getAll()
                val withLocal = allSongs.mapNotNull { song ->
                    val uri = songRepository.getLocalUri(song.xxHash)
                    if (uri != null) song.xxHash to uri else null
                }
                if (withLocal.isEmpty()) {
                    _state.update { it.copy(isApplying = false, savedMessage = "Preset saved. No local files to re-tag.") }
                    return@launch
                }
                withLocal.forEachIndexed { index, (xxHash, uri) ->
                    _state.update { it.copy(applyProgress = "Tagging ${index + 1}/${withLocal.size}…") }
                    try { applyTagsUseCase.applyForSong(xxHash, uri) } catch (_: Exception) {}
                }
                _state.update { it.copy(isApplying = false, applyProgress = null,
                    savedMessage = "Preset applied to ${withLocal.size} song(s).") }
            } catch (e: Exception) {
                _state.update { it.copy(isApplying = false, applyProgress = null,
                    savedMessage = "Error applying preset: ${e.message}") }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(savedMessage = null) }
}
