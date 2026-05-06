package com.neurok.syncer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.StatusCounts
import com.neurok.syncer.domain.model.SyncProgress
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.domain.usecase.FetchMetadataUseCase
import com.neurok.syncer.domain.usecase.SyncTagsAndDownloadUseCase
import com.neurok.syncer.platform.FileStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val lastSyncTime: Long = 0L,
    val counts: StatusCounts = StatusCounts(),
    val storageBytes: Long = 0L,
    // Fetch = scan local + pull GitHub metadata
    val isFetching: Boolean = false,
    val fetchProgress: SyncProgress? = null,
    // Sync = apply tags + download new songs
    val isSyncing: Boolean = false,
    val syncProgress: SyncProgress? = null,
    /** In-memory timestamp of the last Fetch this session; null if never fetched. */
    val lastFetchTimeMs: Long? = null,
    val folderConfigured: Boolean = false,
    val driveApiKeyConfigured: Boolean = false,
    val showSyncConfirm: Boolean = false,
)

class HomeViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val fileStorage: FileStorage,
    private val fetchMetadataUseCase: FetchMetadataUseCase,
    private val syncTagsAndDownloadUseCase: SyncTagsAndDownloadUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        viewModelScope.launch { doRefresh() }
    }

    /** Call whenever the screen re-enters composition (e.g. returning from Settings). */
    fun refresh() {
        viewModelScope.launch { doRefresh() }
    }

    /** Fetch: scan local files + pull GitHub metadata. Updates status counts. */
    fun doFetch() {
        if (_state.value.isFetching || _state.value.isSyncing) return
        viewModelScope.launch {
            _state.update { it.copy(isFetching = true, fetchProgress = null) }
            fetchMetadataUseCase.execute().collect { progress ->
                _state.update { it.copy(fetchProgress = progress) }
                if (progress is SyncProgress.Completed || progress is SyncProgress.Error) {
                    _state.update {
                        it.copy(isFetching = false, lastFetchTimeMs = System.currentTimeMillis())
                    }
                    doRefresh()
                }
            }
        }
    }

    /** Show confirmation before running Sync (tag + download). */
    fun requestSync() {
        if (_state.value.isFetching || _state.value.isSyncing) return
        _state.update { it.copy(showSyncConfirm = true) }
    }

    fun dismissSyncConfirm() = _state.update { it.copy(showSyncConfirm = false) }

    /** Sync: apply ID3 tags to updated songs + download new songs from Drive. */
    fun doSync() {
        _state.update { it.copy(showSyncConfirm = false) }
        if (_state.value.isFetching || _state.value.isSyncing) return
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, syncProgress = null) }
            syncTagsAndDownloadUseCase.execute().collect { progress ->
                _state.update { it.copy(syncProgress = progress) }
                if (progress is SyncProgress.Completed || progress is SyncProgress.Error) {
                    _state.update { it.copy(isSyncing = false) }
                    doRefresh()
                }
            }
        }
    }

    private suspend fun doRefresh() {
        val folderUri = settingsRepository.get(SettingsKeys.LOCAL_FOLDER_URI)
        val apiKey = settingsRepository.get(SettingsKeys.DRIVE_API_KEY)
        val lastSync = settingsRepository.getLong(SettingsKeys.LAST_SYNC_TIME_MS, 0L)
        val counts = songRepository.getStatusCounts()
        val storage = if (folderUri != null) fileStorage.getFolderSize(folderUri) else 0L
        _state.update {
            it.copy(
                lastSyncTime = lastSync,
                counts = counts,
                storageBytes = storage,
                folderConfigured = !folderUri.isNullOrBlank(),
                driveApiKeyConfigured = !apiKey.isNullOrBlank(),
            )
        }
    }
}
