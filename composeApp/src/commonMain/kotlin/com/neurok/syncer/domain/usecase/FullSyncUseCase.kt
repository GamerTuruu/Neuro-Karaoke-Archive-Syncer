package com.neurok.syncer.domain.usecase

import com.neurok.syncer.domain.model.SyncProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Facade that chains [FetchMetadataUseCase] then [SyncTagsAndDownloadUseCase].
 * Used by the background [SyncWorker] for scheduled automatic sync.
 * For manual interactive sync the UI calls each use case separately.
 */
class FullSyncUseCase(
    private val fetchMetadataUseCase: FetchMetadataUseCase,
    private val syncTagsAndDownloadUseCase: SyncTagsAndDownloadUseCase,
) {
    fun execute(): Flow<SyncProgress> = flow {
        var fetchErrored = false
        fetchMetadataUseCase.execute().collect { progress ->
            emit(progress)
            if (progress is SyncProgress.Error) fetchErrored = true
        }
        if (!fetchErrored) {
            syncTagsAndDownloadUseCase.execute().collect { emit(it) }
        }
    }
}

