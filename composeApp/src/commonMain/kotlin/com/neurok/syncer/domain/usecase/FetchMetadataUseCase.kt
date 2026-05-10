package com.neurok.syncer.domain.usecase

import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.SyncProgress
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.repository.MetadataRepository
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.platform.FileStorage
import com.neurok.syncer.platform.Mp3TagHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Step 1 of the two-step manual sync:
 *  1. Scans the local archive folder — updates DB with current file URIs.
 *  2. Fetches the GitHub metadata repo tree — detects new / changed songs.
 *
 * Does NOT apply tags or download files.
 * Saves [SettingsKeys.LAST_SYNC_TIME_MS] on success so the Home screen
 * shows a fresh "Last fetched" timestamp.
 */
class FetchMetadataUseCase(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val metadataRepository: MetadataRepository,
    private val fileStorage: FileStorage,
    private val tagHandler: Mp3TagHandler,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun execute(): Flow<SyncProgress> = flow {
        emit(SyncProgress.Started)

        val folderUri = settingsRepository.get(SettingsKeys.LOCAL_FOLDER_URI)

        // Step 1: Fetch GitHub metadata first — this populates the DB so the scan can match
        emit(SyncProgress.FetchingMetadata("Fetching metadata from GitHub…"))
        val metaChanged = try {
            metadataRepository.syncFromGitHub(null) { msg ->
                emit(SyncProgress.FetchingMetadata(msg))
            }
        } catch (e: Exception) {
            emit(SyncProgress.Error("Metadata fetch failed: ${e.message}"))
            return@flow
        }
        emit(SyncProgress.FetchingMetadata("GitHub: $metaChanged entry(ies) changed"))

        // Step 2: Scan local files and match to DB by xxHash
        if (!folderUri.isNullOrBlank()) {
            val files = fileStorage.listMp3s(folderUri)
            files.forEachIndexed { idx, uri ->
                emit(SyncProgress.ScanningLocal(idx + 1, files.size))
                val commVed = tagHandler.readCommVed(uri) ?: return@forEachIndexed
                val xxHash = extractXxHash(commVed) ?: return@forEachIndexed

                val existingLocalUri = songRepository.getLocalUri(xxHash)
                if (existingLocalUri == null) {
                    // Song found locally for the first time (was NEW_AVAILABLE).
                    // Set NEEDS_UPDATE so Sync will apply tags on it.
                    songRepository.updateLocalUri(xxHash, uri, SyncStatus.NEEDS_UPDATE)
                } else {
                    // Song already tracked — just keep the URI current (don't change status).
                    songRepository.updateLocalUriOnly(xxHash, uri)
                }
            }
        }

        settingsRepository.set(SettingsKeys.LAST_SYNC_TIME_MS, System.currentTimeMillis().toString())

        val counts = songRepository.getStatusCounts()
        emit(SyncProgress.Completed(
            updated = metaChanged,
            downloaded = 0,
            newAvailable = counts.newAvailable,
            orphans = counts.orphans,
        ))
    }

    private fun extractXxHash(commVedJson: String): String? = try {
        json.parseToJsonElement(commVedJson).jsonObject["xxHash"]?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}
