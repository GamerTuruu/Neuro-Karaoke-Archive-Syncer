package com.neurok.syncer.domain.usecase

import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.platform.FileStorage
import com.neurok.syncer.platform.Mp3TagHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Scans all local MP3 files in the archive folder, reads their COMM::ved frames,
 * extracts the xxHash, and upserts [LocalSong] state into the DB.
 */
class ScanLocalFilesUseCase(
    private val fileStorage: FileStorage,
    private val tagHandler: Mp3TagHandler,
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }) {
        val folderUri = settingsRepository.get(SettingsKeys.LOCAL_FOLDER_URI) ?: return
        val files = fileStorage.listMp3s(folderUri)

        files.forEachIndexed { index, uri ->
            onProgress(index + 1, files.size)
            val commVed = tagHandler.readCommVed(uri) ?: return@forEachIndexed
            val xxHash = extractXxHash(commVed) ?: return@forEachIndexed

            val existing = songRepository.getByXxHash(xxHash)
            if (existing != null) {
                // Update the local URI if it changed (e.g. user moved the folder)
                songRepository.updateLocalUri(xxHash, uri, computeNewStatus(existing.hjsonSha, xxHash))
            }
            // If the song doesn't exist in the DB yet, it's an ORPHAN (local file, no HJSON)
            // It will be properly resolved once GitHub metadata is synced
        }
    }

    private fun extractXxHash(commVedJson: String): String? = try {
        json.parseToJsonElement(commVedJson).jsonObject["xxHash"]?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }

    private fun computeNewStatus(hjsonSha: String, xxHash: String): SyncStatus =
        SyncStatus.UP_TO_DATE // Will be re-evaluated by ComputeSyncStatusUseCase
}
