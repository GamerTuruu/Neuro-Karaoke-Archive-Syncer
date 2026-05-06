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
    val isGrouped: Boolean = true,  // true when showing all songs grouped by disc
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
                    when {
                        q.isNotBlank() -> songRepository.searchSongs(q)
                        filter != null -> songRepository.observeByStatus(filter)
                        else -> songRepository.observeAll()
                    }
                }
                .collect { songs ->
                    _state.update {
                        it.copy(
                            songs = songs,
                            isLoading = false,
                            isGrouped = _query.value.isBlank() && _filterStatus.value == null,
                        )
                    }
                }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
        _state.update { it.copy(query = q, isGrouped = q.isBlank() && _filterStatus.value == null) }
    }

    fun setFilter(status: SyncStatus?) {
        _filterStatus.value = status
        _state.update { it.copy(filterStatus = status, isGrouped = _query.value.isBlank() && status == null) }
    }

    fun toggleExcluded(xxHash: String, currentlyExcluded: Boolean) {
        viewModelScope.launch {
            songRepository.updateExcluded(xxHash, !currentlyExcluded)
        }
    }
}
