package com.neurok.syncer.domain.usecase

import com.neurok.syncer.data.drive.DriveApiSource
import com.neurok.syncer.data.drive.ARCHIVE_FOLDER_ID
import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.model.TagPresetRegistry
import com.neurok.syncer.domain.repository.DriveRepository
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.platform.FileStorage
import com.neurok.syncer.platform.Mp3TagHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DownloadSongUseCase(
    private val songRepository: SongRepository,
    private val driveRepository: DriveRepository,
    private val driveApiSource: DriveApiSource,
    private val settingsRepository: SettingsRepository,
    private val fileStorage: FileStorage,
    private val tagHandler: Mp3TagHandler,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Download a single song by xxHash.
     * @param xxHash The permanent identifier.
     * @param onProgress Called with (bytesReceived, totalBytes).
     */
    suspend fun execute(
        xxHash: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<Unit> = runCatching {
        val song = songRepository.getByXxHash(xxHash)
            ?: error("Song not found in DB: $xxHash")
        val apiKey = settingsRepository.get(SettingsKeys.DRIVE_API_KEY)
            ?: error("No Google Drive API key configured")
        val folderUri = settingsRepository.get(SettingsKeys.LOCAL_FOLDER_URI)
            ?: error("No local folder configured")

        // Refresh Drive index if stale
        if (driveRepository.isIndexStale()) {
            driveRepository.refreshIndex(apiKey, ARCHIVE_FOLDER_ID)
        }

        // Find the Drive file: exact match first, then by track-number prefix
        val (driveFileId, actualFilename) = findDriveFile(song.hjsonPath)
            ?: error("Cannot find Drive file for \"${song.title}\" (disc ${song.discNumber}, track ${song.track})")

        // Stream download in 256 KB chunks
        val chunks = mutableListOf<ByteArray>()
        driveApiSource.downloadFile(
            fileId = driveFileId,
            apiKey = apiKey,
            onProgress = onProgress,
            onBytes = { chunk -> chunks.add(chunk) },
        )
        val bytes = ByteArray(chunks.sumOf { it.size })
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(bytes, offset); offset += chunk.size }

        // Place the file in the correct disc sub-folder
        val discFolder = song.hjsonPath.substringBefore("/", "").trim()
        val targetFolderUri = if (discFolder.isNotBlank())
            try { fileStorage.getOrCreateSubFolder(folderUri, discFolder) }
            catch (_: Exception) { folderUri }
        else folderUri

        val localUri = fileStorage.writeFile(targetFolderUri, actualFilename, bytes)

        // Verify the downloaded file has the correct COMM::ved with matching xxHash
        val commVed = tagHandler.readCommVed(localUri)
        val downloadedXxHash = commVed?.let { extractXxHash(it) }
        if (downloadedXxHash != null && downloadedXxHash != xxHash) {
            fileStorage.deleteFile(localUri)
            error("xxHash mismatch after download: expected=$xxHash, got=$downloadedXxHash")
        }

        // Apply preset tags
        val presetId = settingsRepository.get(SettingsKeys.ACTIVE_PRESET_ID)
        val preset = TagPresetRegistry.fromIdOrDefault(presetId)
        tagHandler.applyStandardTags(
            fileUri = localUri,
            title = preset.buildTitle(song),
            artist = preset.buildArtist(song),
            album = preset.buildAlbum(song),
            track = song.track,
            discNumber = song.discNumber,
            date = song.date,
        )

        songRepository.updateLocalUri(xxHash, localUri, SyncStatus.UP_TO_DATE)
    }

    /**
     * Locate the Drive file for a song:
     * 1. Exact match using the hjson-derived MP3 filename.
     * 2. Fallback: match by the leading zero-padded track number (e.g. "059").
     * Returns (fileId, actualDriveFilename) or null if not found.
     */
    private suspend fun findDriveFile(hjsonPath: String): Pair<String, String>? {
        val derivedName = hjsonPath.substringAfterLast('/').removeSuffix(".hjson") + ".mp3"
        driveRepository.getFileId(derivedName)?.let { return it to derivedName }

        val trackPrefix = hjsonPath.substringAfterLast('/').takeWhile { it.isDigit() }
        if (trackPrefix.isNotEmpty()) {
            driveRepository.findFileByFilenamePrefix(trackPrefix)
                ?.let { return it.id to it.name }
        }
        return null
    }

    private fun extractXxHash(commVedJson: String): String? = try {
        json.parseToJsonElement(commVedJson).jsonObject["xxHash"]?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}
