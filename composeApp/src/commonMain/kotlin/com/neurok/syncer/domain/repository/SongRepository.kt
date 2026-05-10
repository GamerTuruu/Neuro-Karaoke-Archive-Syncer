package com.neurok.syncer.domain.repository

import com.neurok.syncer.domain.model.SongMetadata
import com.neurok.syncer.domain.model.StatusCounts
import com.neurok.syncer.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun observeAll(): Flow<List<SongMetadata>>
    fun observeByStatus(status: SyncStatus): Flow<List<SongMetadata>>
    suspend fun getByXxHash(xxHash: String): SongMetadata?
    suspend fun upsert(song: SongMetadata, syncStatus: SyncStatus, localFileUri: String? = null)
    suspend fun updateStatus(xxHash: String, status: SyncStatus)
    suspend fun updateLocalUri(xxHash: String, localFileUri: String, status: SyncStatus)
    /** Update only the local file path without changing the sync status. */
    suspend fun updateLocalUriOnly(xxHash: String, localFileUri: String)
    suspend fun updateExcluded(xxHash: String, excluded: Boolean)
    suspend fun updateUserIncluded(xxHash: String, included: Boolean)
    suspend fun updateAllUserIncluded(included: Boolean)
    suspend fun updateHjsonSha(xxHash: String, sha: String, newStatus: SyncStatus)
    suspend fun getStatusCounts(): StatusCounts
    fun searchSongs(query: String): Flow<List<SongMetadata>>
    suspend fun getAll(): List<SongMetadata>
    suspend fun getNonExcluded(): List<SongMetadata>
    /** Synchronous snapshot of all songs with the given status. */
    suspend fun getByStatus(status: SyncStatus): List<SongMetadata>
    /** Returns the locally-stored file URI for a song, or null if not present. */
    suspend fun getLocalUri(xxHash: String): String?
}
