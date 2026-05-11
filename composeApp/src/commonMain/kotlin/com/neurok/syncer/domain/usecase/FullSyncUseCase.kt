package com.neurok.syncer.domain.usecase

import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.SyncProgress
import com.neurok.syncer.domain.repository.SettingsRepository
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
    private val settingsRepository: SettingsRepository,
) {
    fun execute(): Flow<SyncProgress> = flow {
        var fetchErrored = false
        fetchMetadataUseCase.execute().collect { progress ->
            emit(progress)
            if (progress is SyncProgress.Error) fetchErrored = true
        }
        if (!fetchErrored) {
            // Read persisted sync-mode: if user selected "sync selected" then only process checked songs
            val syncSelected = settingsRepository.get(SettingsKeys.SYNC_SELECTED)?.toBoolean() ?: false
            val syncEntireArchive = !syncSelected
            syncTagsAndDownloadUseCase.execute(syncEntireArchive).collect { emit(it) }
        }
    }
}

