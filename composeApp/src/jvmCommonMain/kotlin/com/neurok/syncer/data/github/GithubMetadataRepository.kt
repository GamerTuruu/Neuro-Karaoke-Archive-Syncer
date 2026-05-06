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

    override suspend fun syncFromGitHub(pat: String?, onProgress: suspend (String) -> Unit): Int {
        val repoStr = settingsRepository.get(SettingsKeys.GITHUB_REPO) ?: "$DEFAULT_GITHUB_OWNER/$DEFAULT_GITHUB_REPO"
        val parts = repoStr.split("/", limit = 2)
        val owner = if (parts.size == 2) parts[0] else DEFAULT_GITHUB_OWNER
        val repo  = if (parts.size == 2) parts[1] else DEFAULT_GITHUB_REPO

        onProgress("Fetching repository tree…")
        val treeEntries = apiSource.fetchHjsonTree(pat, owner, repo)
        onProgress("Tree fetched: ${treeEntries.size} .hjson files found")

        val existingBySha: Map<String, String> = songRepository.getAll()
            .associate { it.hjsonPath to it.hjsonSha }

        val toFetch = treeEntries.filter { it.sha != existingBySha[it.path] }
        val total = toFetch.size
        onProgress("$total file(s) changed — fetching content…")

        var processed = 0
        for ((idx, entry) in toFetch.withIndex()) {
            if ((idx + 1) % 10 == 0 || idx == 0)
                onProgress("Processing ${idx + 1}/$total: ${entry.path.substringAfterLast('/')}")

            val text = apiSource.fetchHjsonContent(entry.downloadUrl, pat)
            val meta = try {
                HjsonParser.parse(text, entry.path, entry.sha)
            } catch (e: Exception) {
                continue
            }

            val existing = songRepository.getByXxHash(meta.xxHash)
            if (existing == null) {
                songRepository.upsert(meta, SyncStatus.NEW_AVAILABLE)
            } else {
                val localUri = songRepository.getLocalUri(meta.xxHash)
                val newStatus = if (localUri != null) SyncStatus.NEEDS_UPDATE else SyncStatus.NEW_AVAILABLE
                songRepository.upsert(
                    song = meta,
                    syncStatus = newStatus,
                    localFileUri = null,
                )
            }
            processed++
        }

        onProgress("Metadata done — $processed updated, ${treeEntries.size - toFetch.size} unchanged")

        val remotePaths = treeEntries.map { it.path }.toSet()
        songRepository.getAll()
            .filter { it.hjsonPath !in remotePaths && it.hjsonPath.isNotBlank() }
            .forEach { songRepository.updateStatus(it.xxHash, SyncStatus.ORPHAN) }

        return processed
    }
}
