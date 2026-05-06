package com.neurok.syncer.ui.preset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.TagPresetRegistry
import com.neurok.syncer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PresetUiState(
    val activePresetId: String = "default",
    val savedMessage: String? = null,
)

class PresetViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PresetUiState())
    val state: StateFlow<PresetUiState> = _state

    init {
        viewModelScope.launch {
            val id = settingsRepository.get(SettingsKeys.ACTIVE_PRESET_ID) ?: "default"
            _state.update { it.copy(activePresetId = id) }
        }
    }

    fun selectPreset(id: String) {
        viewModelScope.launch {
            settingsRepository.set(SettingsKeys.ACTIVE_PRESET_ID, id)
            _state.update { it.copy(activePresetId = id, savedMessage = "Preset saved. Will apply on next sync.") }
        }
    }

    fun dismissMessage() = _state.update { it.copy(savedMessage = null) }
}
