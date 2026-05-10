package com.neurok.syncer.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neurok.syncer.domain.model.SongMetadata
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.repository.SongRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class FilterMode { ALL, MISSING, DOWNLOADED, UNCHECKED }

data class BrowserUiState(
    val songs: List<SongMetadata> = emptyList(),
    val query: String = "",
    val filterMode: FilterMode = FilterMode.ALL,
    val isGrouped: Boolean = true,
    val isLoading: Boolean = true,
    /** Disc folder names that are currently expanded. Empty = all collapsed. */
    val expandedDiscs: Set<String> = emptySet(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BrowserViewModel(
    private val songRepository: SongRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _filterMode = MutableStateFlow(FilterMode.ALL)
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state

    init {
        viewModelScope.launch {
            combine(
                _query.debounce(200),
                _filterMode,
            ) { q, filter -> q to filter }
                .flatMapLatest { (q, filter) ->
                    when {
                        q.isNotBlank() -> songRepository.searchSongs(q)
                        filter == FilterMode.MISSING ->
                            songRepository.observeByStatus(SyncStatus.NEW_AVAILABLE)
                        else -> songRepository.observeAll()
                    }
                }
                .map { songs ->
                    val filter = _filterMode.value
                    val q = _query.value
                    when {
                        q.isNotBlank() -> songs
                        filter == FilterMode.DOWNLOADED ->
                            songs.filter { it.syncStatus == SyncStatus.UP_TO_DATE || it.syncStatus == SyncStatus.NEEDS_UPDATE }
                        filter == FilterMode.UNCHECKED -> songs.filter { !it.userIncluded }
                        else -> songs
                    }
                }
                .collect { songs ->
                    val filter = _filterMode.value
                    _state.update {
                        it.copy(
                            songs = songs,
                            isLoading = false,
                            isGrouped = _query.value.isBlank() && filter == FilterMode.ALL,
                        )
                    }
                }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
        _state.update { it.copy(query = q, isGrouped = q.isBlank() && _filterMode.value == FilterMode.ALL) }
    }

    fun setFilter(mode: FilterMode) {
        _filterMode.value = mode
        _state.update {
            it.copy(
                filterMode = mode,
                isGrouped = _query.value.isBlank() && mode == FilterMode.ALL,
            )
        }
    }

    fun toggleDiscExpanded(discName: String) {
        _state.update { s ->
            val expanded = if (s.expandedDiscs.contains(discName))
                s.expandedDiscs - discName
            else
                s.expandedDiscs + discName
            s.copy(expandedDiscs = expanded)
        }
    }

    fun toggleExcluded(xxHash: String, currentlyExcluded: Boolean) {
        viewModelScope.launch {
            songRepository.updateExcluded(xxHash, !currentlyExcluded)
        }
    }

    fun toggleUserIncluded(xxHash: String, current: Boolean) {
        viewModelScope.launch {
            songRepository.updateUserIncluded(xxHash, !current)
        }
    }
}
