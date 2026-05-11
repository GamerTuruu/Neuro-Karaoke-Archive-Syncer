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
import com.neurok.syncer.platform.SyncNotifier
import kotlinx.coroutines.Job
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
    val fetchLog: List<String> = emptyList(),
    // Sync = apply tags + download new songs
    val isSyncing: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val syncLog: List<String> = emptyList(),
    /** In-memory timestamp of the last Fetch this session; null if never fetched. */
    val lastFetchTimeMs: Long? = null,
    val folderConfigured: Boolean = false,
    val driveApiKeyConfigured: Boolean = false,
    val showSyncConfirm: Boolean = false,
    /** True while the initial settings load is in progress — suppresses "folder not configured" flash. */
    val isInitializing: Boolean = true,
    /** When false, only songs with userIncluded=true are synced. */
    val syncEntireArchive: Boolean = true,
    /** UI: show a confirmation dialog when changing sync mode */
    val showModeConfirm: Boolean = false,
    /** UI: pending mode value while confirmation is shown */
    val pendingSyncEntireArchive: Boolean? = null,
    /** number of checked / unchecked songs (for Home status chips) */
    val checkedCount: Int = 0,
    val uncheckedCount: Int = 0,
    /** Persisted UI states for Home panel visibility */
    val homeLogVisible: Boolean = true,
    val homeHowToCollapsed: Boolean = false,
    val showCancelConfirm: Boolean = false,
)

class HomeViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val fileStorage: FileStorage,
    private val fetchMetadataUseCase: FetchMetadataUseCase,
    private val syncTagsAndDownloadUseCase: SyncTagsAndDownloadUseCase,
    private val syncNotifier: SyncNotifier,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private var fetchJob: Job? = null
    private var syncJob: Job? = null

    init {
        viewModelScope.launch { doRefresh() }
    }

    /** Call whenever the screen re-enters composition (e.g. returning from Settings). */
    fun refresh() {
        viewModelScope.launch { doRefresh() }
    }

    /**
     * Combined Fetch → Sync in a single job (triggered by the main button).
     * Runs the full scan+metadata fetch, then immediately applies tags and downloads.
     * Both [fetchJob] and [syncJob] point to the same coroutine so cancel works from either state.
     */
    fun doFetchThenSync() {
        if (_state.value.isFetching || _state.value.isSyncing) return
        val job = viewModelScope.launch {
            // ── Phase 1: Fetch metadata ───────────────────────────────────
            _state.update { it.copy(isFetching = true, fetchProgress = null, fetchLog = emptyList(), syncLog = emptyList()) }
            syncNotifier.postProgress("Neuro Karaoke Archive", "Scanning & fetching metadata…")
            var fetchOk = false
            try {
                fetchMetadataUseCase.execute().collect { progress ->
                    _state.update { s -> s.copy(fetchProgress = progress, fetchLog = s.fetchLog + progressToLogLine(progress)) }
                    when (progress) {
                        is SyncProgress.ScanningLocal -> syncNotifier.postProgress("Scanning…", "${progress.current}/${progress.total} files")
                        is SyncProgress.FetchingMetadata -> syncNotifier.postProgress("Fetching metadata…", progress.message)
                        is SyncProgress.Completed -> {
                            fetchOk = true
                            _state.update { it.copy(isFetching = false, lastFetchTimeMs = System.currentTimeMillis()) }
                            doRefresh()
                        }
                        is SyncProgress.Error -> {
                            syncNotifier.postError(progress.message)
                            _state.update { it.copy(isFetching = false, lastFetchTimeMs = System.currentTimeMillis()) }
                            doRefresh()
                        }
                        else -> Unit
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                syncNotifier.cancel()
                _state.update { it.copy(isFetching = false, fetchProgress = SyncProgress.Error("Fetch cancelled"), fetchLog = it.fetchLog + "Fetch cancelled") }
                throw kotlinx.coroutines.CancellationException("Fetch cancelled")
            }
            if (!fetchOk) { fetchJob = null; syncJob = null; return@launch }

            // ── Phase 2: Sync (tags + download) ──────────────────────────
            val syncEntireArchive = _state.value.syncEntireArchive
            _state.update { it.copy(isSyncing = true, syncProgress = null, syncLog = emptyList()) }
            syncNotifier.postProgress("Neuro Karaoke Archive", "Sync started…")
            try {
                syncTagsAndDownloadUseCase.execute(syncEntireArchive).collect { progress ->
                    _state.update { s -> s.copy(syncProgress = progress, syncLog = s.syncLog + progressToLogLine(progress)) }
                    when (progress) {
                        is SyncProgress.ApplyingTags -> syncNotifier.postProgress("Applying tags…", "${progress.current}/${progress.total}: ${progress.songTitle}")
                        is SyncProgress.Downloading -> syncNotifier.postProgress("Downloading…", "${progress.current}/${progress.total}: ${progress.filename}")
                        is SyncProgress.Completed -> {
                            syncNotifier.postCompleted(buildString {
                                if (progress.downloaded > 0) append("${progress.downloaded} downloaded. ")
                                if (progress.updated > 0) append("${progress.updated} tagged. ")
                                if (progress.newAvailable > 0) append("${progress.newAvailable} still need download. ")
                                if (isEmpty()) append("Everything is up to date.")
                            })
                            _state.update { it.copy(isSyncing = false) }
                            doRefresh()
                        }
                        is SyncProgress.Error -> {
                            syncNotifier.postError(progress.message)
                            _state.update { it.copy(isSyncing = false) }
                            doRefresh()
                        }
                        else -> Unit
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                syncNotifier.cancel()
                _state.update { it.copy(isSyncing = false, syncProgress = SyncProgress.Error("Sync cancelled"), syncLog = it.syncLog + "Sync cancelled") }
                throw kotlinx.coroutines.CancellationException("Sync cancelled")
            } finally {
                _state.update { it.copy(isSyncing = false) }
                fetchJob = null
                syncJob = null
            }
        }
        fetchJob = job
        syncJob = job  // same job — cancel from either isFetching or isSyncing state
    }

    /** Fetch: scan local files + pull GitHub metadata. Updates status counts. */
    fun doFetch() {
        if (_state.value.isFetching || _state.value.isSyncing) return
        fetchJob = viewModelScope.launch {
            _state.update { it.copy(isFetching = true, fetchProgress = null, fetchLog = emptyList()) }
            syncNotifier.postProgress("Neuro Karaoke Archive", "Scanning & fetching metadata…")
            try {
                fetchMetadataUseCase.execute().collect { progress ->
                    _state.update { s ->
                        s.copy(
                            fetchProgress = progress,
                            fetchLog = s.fetchLog + progressToLogLine(progress),
                        )
                    }
                    when (progress) {
                        is SyncProgress.ScanningLocal ->
                            syncNotifier.postProgress("Scanning…", "${progress.current}/${progress.total} files")
                        is SyncProgress.FetchingMetadata ->
                            syncNotifier.postProgress("Fetching metadata…", progress.message)
                        is SyncProgress.Completed -> {
                            syncNotifier.postCompleted(
                                if (progress.updated == 0 && progress.downloaded == 0) "Fetch complete — nothing to do."
                                else "Fetch complete. ${progress.newAvailable} new song(s) available."
                            )
                            _state.update {
                                it.copy(isFetching = false, lastFetchTimeMs = System.currentTimeMillis())
                            }
                            doRefresh()
                        }
                        is SyncProgress.Error -> {
                            syncNotifier.postError(progress.message)
                            _state.update {
                                it.copy(isFetching = false, lastFetchTimeMs = System.currentTimeMillis())
                            }
                            doRefresh()
                        }
                        else -> Unit
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                syncNotifier.cancel()
                _state.update { it.copy(
                    isFetching = false,
                    fetchProgress = SyncProgress.Error("Fetch cancelled"),
                    fetchLog = it.fetchLog + "Fetch cancelled",
                ) }
                throw kotlinx.coroutines.CancellationException("Fetch cancelled")
            } finally {
                fetchJob = null
            }
        }
    }

    /** Show cancel-confirm dialog. */
    fun requestCancel() = _state.update { it.copy(showCancelConfirm = true) }
    fun dismissCancelConfirm() = _state.update { it.copy(showCancelConfirm = false) }
    fun confirmCancel() {
        _state.update { it.copy(showCancelConfirm = false) }
        if (_state.value.isFetching) cancelFetch() else cancelSync()
    }

    /** Cancel an in-progress Fetch. */
    fun cancelFetch() { fetchJob?.cancel() }

    /** Show confirmation before running Sync (tag + download). */
    fun requestSync() {
        if (_state.value.isFetching || _state.value.isSyncing) return
        _state.update { it.copy(showSyncConfirm = true) }
    }

    fun dismissSyncConfirm() = _state.update { it.copy(showSyncConfirm = false) }

    /** User requested to change sync mode (all vs selected) — show confirmation first. */
    fun requestChangeSyncMode(newSyncEntire: Boolean) = _state.update { it.copy(showModeConfirm = true, pendingSyncEntireArchive = newSyncEntire) }

    fun dismissChangeSyncMode() = _state.update { it.copy(showModeConfirm = false, pendingSyncEntireArchive = null) }

    fun confirmChangeSyncMode() {
        val pending = _state.value.pendingSyncEntireArchive ?: return
        viewModelScope.launch {
            // persist as "sync selected" = !syncEntireArchive
            settingsRepository.set(com.neurok.syncer.domain.model.SettingsKeys.SYNC_SELECTED, (!pending).toString())
            _state.update { it.copy(showModeConfirm = false, pendingSyncEntireArchive = null, syncEntireArchive = pending) }
        }
    }

    /** Sync: apply ID3 tags to updated songs + download new songs from Drive. */
    fun doSync() {
        _state.update { it.copy(showSyncConfirm = false) }
        if (_state.value.isFetching || _state.value.isSyncing) return
        val syncEntireArchive = _state.value.syncEntireArchive
        syncJob = viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, syncProgress = null, syncLog = emptyList()) }
            syncNotifier.postProgress("Neuro Karaoke Archive", "Sync started…")
            try {
                syncTagsAndDownloadUseCase.execute(syncEntireArchive).collect { progress ->
                    _state.update { s ->
                        s.copy(
                            syncProgress = progress,
                            syncLog = s.syncLog + progressToLogLine(progress),
                        )
                    }
                    when (progress) {
                        is SyncProgress.ApplyingTags ->
                            syncNotifier.postProgress("Applying tags…", "${progress.current}/${progress.total}: ${progress.songTitle}")
                        is SyncProgress.Downloading ->
                            syncNotifier.postProgress("Downloading…", "${progress.current}/${progress.total}: ${progress.filename}")
                        is SyncProgress.Completed -> {
                            val body = buildString {
                                if (progress.downloaded > 0) append("${progress.downloaded} downloaded. ")
                                if (progress.updated > 0) append("${progress.updated} tagged. ")
                                if (progress.newAvailable > 0) append("${progress.newAvailable} new available. ")
                                if (isEmpty()) append("Everything is up to date.")
                            }
                            syncNotifier.postCompleted(body)
                            _state.update { it.copy(isSyncing = false) }
                            doRefresh()
                        }
                        is SyncProgress.Error -> {
                            syncNotifier.postError(progress.message)
                            _state.update { it.copy(isSyncing = false) }
                            doRefresh()
                        }
                        else -> Unit
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                syncNotifier.cancel()
                _state.update { it.copy(
                    isSyncing = false,
                    syncProgress = SyncProgress.Error("Sync cancelled"),
                    syncLog = it.syncLog + "Sync cancelled",
                ) }
                throw kotlinx.coroutines.CancellationException("Sync cancelled")
            } finally {
                syncJob = null
            }
        }
    }

    fun toggleSyncEntireArchive() = requestChangeSyncMode(!_state.value.syncEntireArchive)

    fun setLogVisible(visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.set(com.neurok.syncer.domain.model.SettingsKeys.HOME_LOG_VISIBLE, visible.toString())
            _state.update { it.copy(homeLogVisible = visible) }
        }
    }

    fun setHowToCollapsed(collapsed: Boolean) {
        viewModelScope.launch {
            settingsRepository.set(com.neurok.syncer.domain.model.SettingsKeys.HOME_HOWTO_COLLAPSED, collapsed.toString())
            _state.update { it.copy(homeHowToCollapsed = collapsed) }
        }
    }

    /** Cancel an in-progress Sync. */
    fun cancelSync() { syncJob?.cancel() }

    private suspend fun doRefresh() {
        val folderUri = settingsRepository.get(SettingsKeys.LOCAL_FOLDER_URI)
        val apiKey = settingsRepository.get(SettingsKeys.DRIVE_API_KEY)
        val lastSync = settingsRepository.getLong(SettingsKeys.LAST_SYNC_TIME_MS, 0L)
        val counts = songRepository.getStatusCounts()
        val storage = if (folderUri != null) fileStorage.getFolderSize(folderUri) else 0L
        // load persisted sync-mode (stored as "sync_selected" boolean)
        val syncSelected = settingsRepository.get(com.neurok.syncer.domain.model.SettingsKeys.SYNC_SELECTED)?.toBoolean() ?: false
        // load persisted home panel visibility
        val logVisible = settingsRepository.get(com.neurok.syncer.domain.model.SettingsKeys.HOME_LOG_VISIBLE)?.toBoolean() ?: true
        val howToCollapsed = settingsRepository.get(com.neurok.syncer.domain.model.SettingsKeys.HOME_HOWTO_COLLAPSED)?.toBoolean() ?: false
        // compute checked/unchecked counts for Browse status chips
        val allSongs = songRepository.getAll()
        val checked = allSongs.count { it.userIncluded }
        val unchecked = allSongs.size - checked
        _state.update {
            it.copy(
                lastSyncTime = lastSync,
                counts = counts,
                storageBytes = storage,
                syncEntireArchive = !syncSelected,
                checkedCount = checked,
                uncheckedCount = unchecked,
                homeLogVisible = logVisible,
                homeHowToCollapsed = howToCollapsed,
                folderConfigured = !folderUri.isNullOrBlank(),
                driveApiKeyConfigured = !apiKey.isNullOrBlank(),
                isInitializing = false,
            )
        }
    }

    private fun progressToLogLine(progress: SyncProgress): String = when (progress) {
        is SyncProgress.Started -> "Starting…"
        is SyncProgress.ScanningLocal -> "Scanning local files (${progress.current}/${progress.total})…"
        is SyncProgress.FetchingMetadata -> progress.message
        is SyncProgress.ApplyingTags -> "Tagging (${progress.current}/${progress.total}): ${progress.songTitle}"
        is SyncProgress.Downloading -> "Downloading (${progress.current}/${progress.total}): ${progress.filename}"
        is SyncProgress.Completed -> buildString {
            append("Done.")
            if (progress.updated > 0) append(" ${progress.updated} tag(s) applied.")
            if (progress.downloaded > 0) append(" ${progress.downloaded} downloaded.")
            if (progress.newAvailable > 0) append(" ${progress.newAvailable} not yet downloaded (no API key or excluded).")
            if (progress.updated == 0 && progress.downloaded == 0 && progress.newAvailable == 0)
                append(" Nothing to do.")
        }
        is SyncProgress.Error -> "Error: ${progress.message}"
    }
}
