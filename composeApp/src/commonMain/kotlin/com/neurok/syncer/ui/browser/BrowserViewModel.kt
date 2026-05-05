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

data class BrowserUiState(
    val songs: List<SongMetadata> = emptyList(),
    val query: String = "",
    val filterStatus: SyncStatus? = null,
    val isLoading: Boolean = true,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BrowserViewModel(
    private val songRepository: SongRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _filterStatus = MutableStateFlow<SyncStatus?>(null)
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state

    init {
        viewModelScope.launch {
            combine(
                _query.debounce(200),
                _filterStatus,
            ) { q, filter -> q to filter }
                .flatMapLatest { (q, filter) ->
                    if (q.isBlank()) songRepository.observeAll()
                    else songRepository.searchSongs(q)
                }
                .map { songs ->
                    val filter = _filterStatus.value
                    if (filter == null) songs
                    else songs.filter { song ->
                        // We don't have syncStatus in domain model currently —
                        // reflect this gap and filter by checking special/other fields
                        // For now return all; status filtering requires DB status exposure
                        true
                    }
                }
                .collect { songs ->
                    _state.update {
                        it.copy(songs = songs, isLoading = false)
                    }
                }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
        _state.update { it.copy(query = q) }
    }

    fun setFilter(status: SyncStatus?) {
        _filterStatus.value = status
        _state.update { it.copy(filterStatus = status) }
    }

    fun toggleExcluded(xxHash: String, currentlyExcluded: Boolean) {
        viewModelScope.launch {
            songRepository.updateExcluded(xxHash, !currentlyExcluded)
        }
    }
}
