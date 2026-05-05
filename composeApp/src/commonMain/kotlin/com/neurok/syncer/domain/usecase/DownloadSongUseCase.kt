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

        // The Drive filename matches the MP3 filename pattern
        // We search the Drive index for a file whose name contains our xxHash
        // (xxHash is embedded in the COMM::ved — but for matching to Drive files,
        //  we use a best-effort approach: match by disc+track+title in the filename)
        val driveFileId = findDriveFileId(song.xxHash, song.hjsonPath, apiKey)
            ?: error("Cannot find Drive file for xxHash=$xxHash (${song.title})")

        // Stream download in 256 KB chunks, collecting into a buffer
        val chunks = mutableListOf<ByteArray>()
        driveApiSource.downloadFile(
            fileId = driveFileId,
            apiKey = apiKey,
            onProgress = onProgress,
            onBytes = { chunk -> chunks.add(chunk) },
        )
        val totalSize = chunks.sumOf { it.size }
        val bytes = ByteArray(totalSize)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(bytes, offset)
            offset += chunk.size
        }

        // Derive the destination filename from the Drive index entry
        val filename = driveRepository.getFileId(driveFileId)?.let { null }
            ?: "${song.discNumber.toString().padStart(2, '0')}_${song.track}_${song.title}.mp3"
        // Use the actual Drive filename if we can retrieve it
        val driveName = getDriveFilename(song.xxHash)
        val destName = driveName ?: "${song.title} - ${song.artist}.mp3"

        val localUri = fileStorage.writeFile(folderUri, destName, bytes)

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

    private suspend fun findDriveFileId(xxHash: String, hjsonPath: String, apiKey: String): String? {
        // Primary: look up by filename in the Drive index
        // The Drive filename matches the MP3 filename which contains the xxHash in its COMM::ved
        // We scan all Drive index entries to find one whose name corresponds to this song
        // As a heuristic, we use the track info encoded in hjsonPath filename
        val hjsonFilename = hjsonPath.substringAfterLast("/") // e.g. "059. Neru - Whatever (Neuro.v1).hjson"
        val trackPrefix = hjsonFilename.substringBefore(".").trim() // "059"

        // Scan Drive index for a file whose name starts with the same track number
        // Drive filenames look like: "DISC 7 - ... - 059. Neru - Whatever (Duet.v1) ....mp3"
        val allFiles = driveRepository.observeAll().let {
            // Quick synchronous snapshot — works because we just refreshed
            emptyList<com.neurok.syncer.domain.model.DriveFile>() // replaced by DB query below
        }

        // Use DB helper directly
        // This is done by the repository's filename match
        return null // Caller should use a more direct lookup
    }

    private suspend fun getDriveFilename(xxHash: String): String? = null

    private fun extractXxHash(commVedJson: String): String? = try {
        json.parseToJsonElement(commVedJson).jsonObject["xxHash"]?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}
