package com.neurok.syncer.domain.usecase

import com.neurok.syncer.data.drive.ARCHIVE_FOLDER_ID
import com.neurok.syncer.data.drive.DriveApiSource
import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.SyncProgress
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.model.TagPresetRegistry
import com.neurok.syncer.domain.repository.DriveRepository
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.platform.FileStorage
import com.neurok.syncer.platform.Mp3TagHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Step 2 of the two-step manual sync:
 *  1. Applies ID3 tags (and renames the file) for every song with NEEDS_UPDATE status.
 *  2. Downloads every song with NEW_AVAILABLE status from Google Drive, then tags it.
 *     Requires a Google Drive API key — songs are skipped with a warning if none is set.
 */
class SyncTagsAndDownloadUseCase(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val driveRepository: DriveRepository,
    private val driveApiSource: DriveApiSource,
    private val fileStorage: FileStorage,
    private val tagHandler: Mp3TagHandler,
) {
    fun execute(): Flow<SyncProgress> = flow {
        emit(SyncProgress.Started)

        val folderUri = settingsRepository.get(SettingsKeys.LOCAL_FOLDER_URI) ?: run {
            emit(SyncProgress.Error("No archive folder configured"))
            return@flow
        }
        val apiKey = settingsRepository.get(SettingsKeys.DRIVE_API_KEY)
        val presetId = settingsRepository.get(SettingsKeys.ACTIVE_PRESET_ID)
        val preset = TagPresetRegistry.fromIdOrDefault(presetId)

        // ── Step 1: Apply tags to NEEDS_UPDATE songs ──────────────────────────
        val toUpdate = songRepository.getByStatus(SyncStatus.NEEDS_UPDATE)
        var tagApplied = 0
        for ((index, song) in toUpdate.withIndex()) {
            val localUri = songRepository.getLocalUri(song.xxHash) ?: continue
            emit(SyncProgress.ApplyingTags(index + 1, toUpdate.size, song.title))
            try {
                tagHandler.applyStandardTags(
                    fileUri = localUri,
                    title = preset.buildTitle(song),
                    artist = preset.buildArtist(song),
                    album = preset.buildAlbum(song),
                    track = song.track,
                    discNumber = song.discNumber,
                    date = song.date,
                )
                val expectedName = song.hjsonPath.substringAfterLast('/').removeSuffix(".hjson") + ".mp3"
                val newUri = try {
                    fileStorage.renameFile(localUri, expectedName)
                } catch (_: Exception) { localUri }
                songRepository.updateLocalUri(song.xxHash, newUri, SyncStatus.UP_TO_DATE)
                tagApplied++
            } catch (_: Exception) { /* skip broken file, continue with next */ }
        }

        // ── Step 2: Download NEW_AVAILABLE songs from Google Drive ────────────
        val toDownload = songRepository.getByStatus(SyncStatus.NEW_AVAILABLE)
        var downloaded = 0
        if (toDownload.isNotEmpty()) {
            if (apiKey.isNullOrBlank()) {
                emit(SyncProgress.FetchingMetadata(
                    "Skipping download of ${toDownload.size} new song(s) — " +
                    "add a Google Drive API key in Settings to download them"
                ))
            } else {
                // Refresh Drive index if stale (older than 24 h)
                if (driveRepository.isIndexStale()) {
                    emit(SyncProgress.FetchingMetadata("Refreshing Drive file index…"))
                    try {
                        driveRepository.refreshIndex(apiKey, ARCHIVE_FOLDER_ID)
                    } catch (e: Exception) {
                        emit(SyncProgress.Error("Drive index refresh failed: ${e.message}"))
                        return@flow
                    }
                }

                for ((index, song) in toDownload.withIndex()) {
                    // Derive the MP3 filename that matches the Drive file name
                    val driveName = song.hjsonPath.substringAfterLast('/').removeSuffix(".hjson") + ".mp3"
                    val driveFileId = driveRepository.getFileId(driveName) ?: continue

                    // Put the download into the matching DISC sub-folder (create it if needed)
                    val discFolder = song.hjsonPath.substringBefore("/", "").trim()
                    val targetFolderUri = if (discFolder.isNotBlank())
                        try { fileStorage.getOrCreateSubFolder(folderUri, discFolder) }
                        catch (_: Exception) { folderUri }
                    else folderUri

                    emit(SyncProgress.Downloading(index + 1, toDownload.size, driveName, 0L, 0L))
                    songRepository.updateStatus(song.xxHash, SyncStatus.DOWNLOADING)
                    try {
                        val chunks = mutableListOf<ByteArray>()
                        driveApiSource.downloadFile(
                            fileId = driveFileId,
                            apiKey = apiKey,
                            onProgress = { _, _ -> },
                            onBytes = { chunks.add(it) },
                        )
                        val bytes = ByteArray(chunks.sumOf { it.size })
                        var offset = 0
                        for (chunk in chunks) { chunk.copyInto(bytes, offset); offset += chunk.size }

                        val localUri = fileStorage.writeFile(targetFolderUri, driveName, bytes)
                        // Register the file BEFORE tagging — this prevents re-downloading if tagging fails
                        songRepository.updateLocalUri(song.xxHash, localUri, SyncStatus.UP_TO_DATE)
                        downloaded++
                        try {
                            tagHandler.applyStandardTags(
                                fileUri = localUri,
                                title = preset.buildTitle(song),
                                artist = preset.buildArtist(song),
                                album = preset.buildAlbum(song),
                                track = song.track,
                                discNumber = song.discNumber,
                                date = song.date,
                            )
                        } catch (_: Exception) {
                            // Tag application failed — file is registered as UP_TO_DATE so it
                            // won't be re-downloaded; tags will be re-applied on next Sync.
                        }
                    } catch (_: Exception) {
                        // Revert transient DOWNLOADING status so the song can be retried
                        songRepository.updateStatus(song.xxHash, SyncStatus.NEW_AVAILABLE)
                    }
                }
            }
        }

        val counts = songRepository.getStatusCounts()
        emit(SyncProgress.Completed(
            updated = tagApplied,
            downloaded = downloaded,
            newAvailable = counts.newAvailable,
            orphans = counts.orphans,
        ))
    }
}
