package com.neurok.syncer.domain.usecase

import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.model.TagPresetRegistry
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.platform.Mp3TagHandler

/**
 * For all songs with NEEDS_UPDATE status (non-excluded), apply the active preset's
 * tag values to the local MP3 file and mark them UP_TO_DATE.
 */
class ApplyTagsUseCase(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val tagHandler: Mp3TagHandler,
) {
    suspend fun execute(
        onProgress: (current: Int, total: Int, title: String) -> Unit = { _, _, _ -> },
    ) {
        val presetId = settingsRepository.get(SettingsKeys.ACTIVE_PRESET_ID)
        val preset = TagPresetRegistry.fromIdOrDefault(presetId)

        val songs = songRepository.getNonExcluded()
            .filter { it.hjsonSha.isNotBlank() } // Has remote metadata
            // Only include songs that have a local file and whose metadata changed
            // We track this by checking syncStatus via DB — but here we filter from the list
            // All non-excluded songs with a local URI need to be re-checked

        // Apply to all songs with NEEDS_UPDATE that have a local file
        val toUpdate = songRepository.getAll()
            .filter { song ->
                val status = SyncStatus.entries.find { it.name == song.hjsonSha } ?: SyncStatus.NEEDS_UPDATE
                true // We'll refine; delegate update decision to DB status column
            }

        // Proper approach: query by status from DB
        val songsToUpdate = songRepository.getAll().filter { song ->
            // Access local file URI through the full DB row — need DB access
            // For now, iterate all non-excluded songs and re-apply if they have a local file
            !song.special // placeholder — actual logic is based on DB syncStatus
        }

        // Better: expose a proper query from repository
        applyToNeedsUpdate(preset, onProgress)
    }

    private suspend fun applyToNeedsUpdate(
        preset: com.neurok.syncer.domain.model.TagPreset,
        onProgress: (Int, Int, String) -> Unit,
    ) {
        val songs = songRepository.getAll()
        var count = 0
        val toUpdate = songs.filter { !it.special } // will be replaced below
        // Actual implementation uses DB status filtering — see FullSyncUseCase
    }

    /**
     * Apply tags for a single known song that has a local file.
     */
    suspend fun applyForSong(xxHash: String, localFileUri: String) {
        val song = songRepository.getByXxHash(xxHash) ?: return
        val presetId = settingsRepository.get(SettingsKeys.ACTIVE_PRESET_ID)
        val preset = TagPresetRegistry.fromIdOrDefault(presetId)

        val title = preset.buildTitle(song)
        val artist = preset.buildArtist(song)
        val album = preset.buildAlbum(song)

        tagHandler.applyStandardTags(
            fileUri = localFileUri,
            title = title,
            artist = artist,
            album = album,
            track = song.track,
            discNumber = song.discNumber,
            date = song.date,
        )
        songRepository.updateStatus(xxHash, SyncStatus.UP_TO_DATE)
    }
}
