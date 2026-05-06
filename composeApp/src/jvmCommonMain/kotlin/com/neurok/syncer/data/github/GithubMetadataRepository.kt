package com.neurok.syncer.data.github

import com.neurok.syncer.data.github.DEFAULT_GITHUB_OWNER
import com.neurok.syncer.data.github.DEFAULT_GITHUB_REPO
import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.repository.MetadataRepository
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.hjson.HjsonParser

class GithubMetadataRepository(
    private val apiSource: GithubApiSource,
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
) : MetadataRepository {

    override suspend fun syncFromGitHub(pat: String?): Int {
        val repoStr = settingsRepository.get(SettingsKeys.GITHUB_REPO) ?: "$DEFAULT_GITHUB_OWNER/$DEFAULT_GITHUB_REPO"
        val parts = repoStr.split("/", limit = 2)
        val owner = if (parts.size == 2) parts[0] else DEFAULT_GITHUB_OWNER
        val repo  = if (parts.size == 2) parts[1] else DEFAULT_GITHUB_REPO

        val treeEntries = apiSource.fetchHjsonTree(pat, owner, repo)

        val existingBySha: Map<String, String> = songRepository.getAll()
            .associate { it.hjsonPath to it.hjsonSha }

        var processed = 0
        for (entry in treeEntries) {
            val currentSha = existingBySha[entry.path]
            if (currentSha == entry.sha) continue

            val text = apiSource.fetchHjsonContent(entry.downloadUrl, pat)
            val meta = try {
                HjsonParser.parse(text, entry.path, entry.sha)
            } catch (e: Exception) {
                continue
            }

            val existing = songRepository.getByXxHash(meta.xxHash)
            if (existing == null) {
                // Completely new song — no local file yet
                songRepository.upsert(meta, SyncStatus.NEW_AVAILABLE)
            } else {
                // Sha changed. Keep NEW_AVAILABLE if there's no local file;
                // set NEEDS_UPDATE if a local file exists (tags need to be re-applied).
                val localUri = songRepository.getLocalUri(meta.xxHash)
                val newStatus = if (localUri != null) SyncStatus.NEEDS_UPDATE else SyncStatus.NEW_AVAILABLE
                songRepository.upsert(
                    song = meta,
                    syncStatus = newStatus,
                    localFileUri = null, // preserved by upsert from existing row
                )
            }
            processed++
        }

        val remotePaths = treeEntries.map { it.path }.toSet()
        songRepository.getAll()
            .filter { it.hjsonPath !in remotePaths && it.hjsonPath.isNotBlank() }
            .forEach { songRepository.updateStatus(it.xxHash, SyncStatus.ORPHAN) }

        return processed
    }
}
