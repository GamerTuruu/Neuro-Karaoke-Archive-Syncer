package com.neurok.syncer.domain.repository

import com.neurok.syncer.domain.model.DriveFile
import kotlinx.coroutines.flow.Flow

interface DriveRepository {
    /** Fetch (and cache) the filename→fileId mapping for the archive folder. */
    suspend fun refreshIndex(apiKey: String, folderId: String)
    /** Look up a Drive file ID by exact MP3 filename (as it appears in Google Drive). */
    suspend fun getFileId(filename: String): String?
    /** Check if the cached index is stale (older than [maxAgeMs] ago). */
    suspend fun isIndexStale(maxAgeMs: Long = 24 * 60 * 60 * 1000L): Boolean
    /** Clear the cached index. */
    suspend fun clearIndex()
    fun observeAll(): Flow<List<DriveFile>>
    /** Verify the API key is valid with a lightweight call. */
    suspend fun testApiKey(apiKey: String, folderId: String): Result<Unit>
}
