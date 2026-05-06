package com.neurok.syncer.domain.usecase

import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.model.TagPresetRegistry
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.platform.FileStorage
import com.neurok.syncer.platform.Mp3TagHandler

class ApplyTagsUseCase(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val tagHandler: Mp3TagHandler,
    private val fileStorage: FileStorage,
) {
    /**
     * Apply tags to a single song that already has a local file.
     * After applying, renames the file to match the HJSON filename.
     */
    suspend fun applyForSong(xxHash: String, localFileUri: String) {
        val song = songRepository.getByXxHash(xxHash) ?: return
        val presetId = settingsRepository.get(SettingsKeys.ACTIVE_PRESET_ID)
        val preset = TagPresetRegistry.fromIdOrDefault(presetId)

        tagHandler.applyStandardTags(
            fileUri = localFileUri,
            title = preset.buildTitle(song),
            artist = preset.buildArtist(song),
            album = preset.buildAlbum(song),
            track = song.track,
            discNumber = song.discNumber,
            date = song.date,
        )

        // Rename the file to match the HJSON filename (source of truth for names)
        val expectedFilename = song.hjsonPath.substringAfterLast('/').removeSuffix(".hjson") + ".mp3"
        val newUri = try {
            fileStorage.renameFile(localFileUri, expectedFilename)
        } catch (_: Exception) {
            localFileUri // rename failed — keep old URI
        }

        if (newUri != localFileUri) {
            songRepository.updateLocalUri(xxHash, newUri, SyncStatus.UP_TO_DATE)
        } else {
            songRepository.updateStatus(xxHash, SyncStatus.UP_TO_DATE)
        }
    }
}

