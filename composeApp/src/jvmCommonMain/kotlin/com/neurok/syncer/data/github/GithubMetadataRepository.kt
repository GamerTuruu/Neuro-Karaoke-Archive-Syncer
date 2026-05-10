package com.neurok.syncer.data.github

import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.repository.MetadataRepository
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.hjson.HjsonParser
import com.neurok.syncer.hjson.MetadataZipParser
import com.neurok.syncer.platform.FileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Fallback maximum number of historical metadata zips to keep on disk. */
private const val DEFAULT_MAX_CACHED_ZIPS = 3

class GithubMetadataRepository(
    private val apiSource: GithubApiSource,
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val fileStorage: FileStorage,
) : MetadataRepository {

    override suspend fun syncFromGitHub(pat: String?, onProgress: suspend (String) -> Unit): Int {
        val repoStr = settingsRepository.get(SettingsKeys.GITHUB_REPO) ?: "$DEFAULT_GITHUB_OWNER/$DEFAULT_GITHUB_REPO"
        val parts = repoStr.split("/", limit = 2)
        val owner = if (parts.size == 2) parts[0] else DEFAULT_GITHUB_OWNER
        val repo  = if (parts.size == 2) parts[1] else DEFAULT_GITHUB_REPO

        // 1. Check if the release has a newer zip than what we have cached
        onProgress("Checking latest metadata release…")
        val releaseInfo = try {
            apiSource.fetchLatestReleaseInfo(pat, owner, repo)
        } catch (e: Exception) {
            throw Exception("Failed to check GitHub releases: ${e.message}", e)
        }

        val cachedPublishedAt = settingsRepository.get(SettingsKeys.METADATA_ZIP_PUBLISHED_AT)
        if (releaseInfo.publishedAt == cachedPublishedAt) {
            onProgress("Metadata is up to date (release ${releaseInfo.publishedAt.take(10)})")
            return 0
        }

        // 2. Download the metadata zip
        onProgress("Downloading metadata zip (${releaseInfo.publishedAt.take(10)})…")
        val zipBytes = try {
            apiSource.downloadZip(releaseInfo.downloadUrl, pat)
        } catch (e: Exception) {
            throw Exception("Failed to download metadata zip: ${e.message}", e)
        }
        onProgress("Downloaded ${zipBytes.size / 1024} KB — saving & extracting…")

        // 3. Save to cache (auto-delete old ones beyond MAX_CACHED_ZIPS)
        saveZipToCache(zipBytes, releaseInfo.publishedAt)

        // 4. Parse all .hjson entries from the zip
        val entries = MetadataZipParser.parse(zipBytes)
        val total = entries.size
        onProgress("Extracted $total HJSON files — updating database…")

        // 5. Determine which entries actually changed using a content hash
        val existingBySha: Map<String, String> = songRepository.getAll()
            .associate { it.hjsonPath to it.hjsonSha }

        var processed = 0
        for ((idx, entry) in entries.withIndex()) {
            if ((idx + 1) % 100 == 0)
                onProgress("Processing ${idx + 1}/$total…")

            // Use SHA-256 of the content so unchanged files are skipped efficiently
            val contentSha = contentSha256(entry.content)
            if (existingBySha[entry.path] == contentSha) continue

            val meta = try {
                HjsonParser.parse(entry.content, entry.path, contentSha)
            } catch (_: Exception) {
                continue
            }

            val existing = songRepository.getByXxHash(meta.xxHash)
            if (existing == null) {
                songRepository.upsert(meta, SyncStatus.NEW_AVAILABLE)
            } else {
                val localUri = songRepository.getLocalUri(meta.xxHash)
                val newStatus = if (localUri != null) SyncStatus.NEEDS_UPDATE else SyncStatus.NEW_AVAILABLE
                songRepository.upsert(song = meta, syncStatus = newStatus, localFileUri = null)
            }
            processed++
        }

        onProgress("Done — $processed changed, ${entries.size - processed} unchanged")

        // 6. Mark songs absent from this release as orphans
        val remotePaths = entries.map { it.path }.toSet()
        songRepository.getAll()
            .filter { it.hjsonPath !in remotePaths && it.hjsonPath.isNotBlank() }
            .forEach { songRepository.updateStatus(it.xxHash, SyncStatus.ORPHAN) }

        // 7. Persist the release timestamp so next run can skip if unchanged
        settingsRepository.set(SettingsKeys.METADATA_ZIP_PUBLISHED_AT, releaseInfo.publishedAt)

        return processed
    }

    private suspend fun saveZipToCache(zipBytes: ByteArray, publishedAt: String) {
        withContext(Dispatchers.IO) {
            val customFolder = settingsRepository.get(SettingsKeys.METADATA_ZIP_FOLDER)
            val cacheDir = if (!customFolder.isNullOrBlank()) {
                File(customFolder).also { it.mkdirs() }
            } else {
                File(fileStorage.getAppCacheDir(), "metadata_zips").also { it.mkdirs() }
            }
            val maxZips = settingsRepository.get(SettingsKeys.METADATA_ZIP_MAX_COUNT)
                ?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_MAX_CACHED_ZIPS
            val safeName = publishedAt.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            File(cacheDir, "metadata_$safeName.zip").writeBytes(zipBytes)
            // Prune: keep only the most recent maxZips files
            cacheDir.listFiles { f -> f.name.startsWith("metadata_") && f.name.endsWith(".zip") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(maxZips)
                ?.forEach { it.delete() }
        }
    }

    private fun contentSha256(content: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }
}

