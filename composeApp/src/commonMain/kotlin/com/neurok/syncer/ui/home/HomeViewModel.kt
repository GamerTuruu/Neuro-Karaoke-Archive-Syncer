package com.neurok.syncer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.StatusCounts
import com.neurok.syncer.domain.model.SyncProgress
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.domain.usecase.FullSyncUseCase
import com.neurok.syncer.platform.FileStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val lastSyncTime: Long = 0L,
    val counts: StatusCounts = StatusCounts(),
    val storageBytes: Long = 0L,
    val syncProgress: SyncProgress? = null,
    val isSyncing: Boolean = false,
    val folderConfigured: Boolean = false,
)

class HomeViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val fileStorage: FileStorage,
    private val fullSyncUseCase: FullSyncUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        viewModelScope.launch { refresh() }
    }

    fun sync() {
        if (_state.value.isSyncing) return
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true) }
            fullSyncUseCase.execute().collect { progress ->
                _state.update { it.copy(syncProgress = progress) }
                if (progress is SyncProgress.Completed || progress is SyncProgress.Error) {
                    _state.update { it.copy(isSyncing = false) }
                    refresh()
                }
            }
        }
    }

    private suspend fun refresh() {
        val folderUri = settingsRepository.get(SettingsKeys.LOCAL_FOLDER_URI)
        val lastSync = settingsRepository.getLong(SettingsKeys.LAST_SYNC_TIME_MS, 0L)
        val counts = songRepository.getStatusCounts()
        val storage = if (folderUri != null) fileStorage.getFolderSize(folderUri) else 0L
        _state.update {
            it.copy(
                lastSyncTime = lastSync,
                counts = counts,
                storageBytes = storage,
                folderConfigured = folderUri != null,
            )
        }
    }
}
