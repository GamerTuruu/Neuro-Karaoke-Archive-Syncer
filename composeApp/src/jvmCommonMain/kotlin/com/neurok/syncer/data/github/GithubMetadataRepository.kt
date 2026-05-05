package com.neurok.syncer.data.github

import com.neurok.syncer.domain.model.SongMetadata
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.repository.MetadataRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.hjson.HjsonParser

class GithubMetadataRepository(
    private val apiSource: GithubApiSource,
    private val songRepository: SongRepository,
) : MetadataRepository {

    override suspend fun syncFromGitHub(pat: String?): Int {
        val treeEntries = apiSource.fetchHjsonTree(pat)

        // Build a map of all hjsonPaths we already know -> their current sha in DB
        val existingBySha: Map<String, String> = songRepository.getAll()
            .associate { it.hjsonPath to it.hjsonSha }

        var processed = 0
        for (entry in treeEntries) {
            val currentSha = existingBySha[entry.path]
            if (currentSha == entry.sha) continue // Nothing changed for this file

            val text = apiSource.fetchHjsonContent(entry.downloadUrl, pat)
            val meta = try {
                HjsonParser.parse(text, entry.path, entry.sha)
            } catch (e: Exception) {
                continue // Skip malformed files; don't crash the full sync
            }

            val existing = songRepository.getByXxHash(meta.xxHash)
            if (existing == null) {
                // Brand new song — not yet in DB
                songRepository.upsert(meta, SyncStatus.NEW_AVAILABLE)
            } else {
                // Metadata changed for an existing song
                val isExcluded = existing.hjsonSha == "EXCLUDED" // checked via DB row
                songRepository.upsert(
                    song = meta,
                    syncStatus = if (existing.special) SyncStatus.NEEDS_UPDATE else SyncStatus.NEEDS_UPDATE,
                    localFileUri = null,
                )
            }
            processed++
        }

        // Mark anything in DB that no longer appears in the tree as ORPHAN (local-only)
        val remotePaths = treeEntries.map { it.path }.toSet()
        songRepository.getAll()
            .filter { it.hjsonPath !in remotePaths && it.hjsonPath.isNotBlank() }
            .forEach { songRepository.updateStatus(it.xxHash, SyncStatus.ORPHAN) }

        return processed
    }
}
