package com.neurok.syncer.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.neurok.syncer.database.NKSyncerDatabase
import com.neurok.syncer.database.Song
import com.neurok.syncer.domain.model.SongMetadata
import com.neurok.syncer.domain.model.StatusCounts
import com.neurok.syncer.domain.model.SyncStatus
import com.neurok.syncer.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SongRepositoryImpl(private val db: NKSyncerDatabase) : SongRepository {

    private val queries get() = db.songQueries

    override fun observeAll(): Flow<List<SongMetadata>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.IO).map { it.map(Song::toDomain) }

    override fun observeByStatus(status: SyncStatus): Flow<List<SongMetadata>> =
        queries.selectByStatus(status.name).asFlow().mapToList(Dispatchers.IO).map { it.map(Song::toDomain) }

    override suspend fun getByXxHash(xxHash: String): SongMetadata? = withContext(Dispatchers.IO) {
        queries.selectByXxHash(xxHash).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun upsert(song: SongMetadata, syncStatus: SyncStatus, localFileUri: String?) =
        withContext(Dispatchers.IO) {
            // Preserve excluded state and local URI for already-known songs
            val existing = queries.selectByXxHash(song.xxHash).executeAsOneOrNull()
            val isExcluded = existing?.isExcluded ?: 0L
            val uri = localFileUri ?: existing?.localFileUri
            val finalStatus = if (isExcluded == 1L) SyncStatus.EXCLUDED.name else syncStatus.name

            queries.upsert(
                xxHash = song.xxHash,
                date = song.date,
                title = song.title,
                titleOG = song.titleOG,
                identify = song.identify,
                artist = song.artist,
                artistOG = song.artistOG,
                coverArtist = song.coverArtist,
                version = song.version.toLong(),
                discNumber = song.discNumber.toLong(),
                track = song.track,
                comment = song.comment,
                special = if (song.special) 1L else 0L,
                hjsonPath = song.hjsonPath,
                hjsonSha = song.hjsonSha,
                isExcluded = isExcluded,
                localFileUri = uri,
                syncStatus = finalStatus,
            )
        }

    override suspend fun updateStatus(xxHash: String, status: SyncStatus) = withContext(Dispatchers.IO) {
        queries.updateStatus(status.name, xxHash)
    }

    override suspend fun updateLocalUri(xxHash: String, localFileUri: String, status: SyncStatus) =
        withContext(Dispatchers.IO) {
            queries.updateLocalUri(localFileUri, status.name, xxHash)
        }

    override suspend fun updateLocalUriOnly(xxHash: String, localFileUri: String) =
        withContext(Dispatchers.IO) {
            queries.updateLocalUriOnly(localFileUri, xxHash)
        }

    override suspend fun updateExcluded(xxHash: String, excluded: Boolean) = withContext(Dispatchers.IO) {
        queries.updateExcluded(if (excluded) 1L else 0L, xxHash)
    }

    override suspend fun updateHjsonSha(xxHash: String, sha: String, newStatus: SyncStatus) =
        withContext(Dispatchers.IO) {
            queries.updateHjsonSha(sha, newStatus.name, xxHash)
        }

    override suspend fun getStatusCounts(): StatusCounts = withContext(Dispatchers.IO) {
        val rows = queries.countByStatus().executeAsList()
        var upToDate = 0; var needsUpdate = 0; var newAvailable = 0; var orphans = 0; var excluded = 0
        for (row in rows) {
            when (row.syncStatus) {
                SyncStatus.UP_TO_DATE.name -> upToDate = row.cnt.toInt()
                SyncStatus.NEEDS_UPDATE.name -> needsUpdate = row.cnt.toInt()
                SyncStatus.NEW_AVAILABLE.name -> newAvailable = row.cnt.toInt()
                SyncStatus.ORPHAN.name -> orphans = row.cnt.toInt()
                SyncStatus.EXCLUDED.name -> excluded = row.cnt.toInt()
            }
        }
        StatusCounts(upToDate, needsUpdate, newAvailable, orphans, excluded)
    }

    override fun searchSongs(query: String): Flow<List<SongMetadata>> {
        val q = "%$query%"
        return queries.searchSongs(q).asFlow().mapToList(Dispatchers.IO).map { it.map(Song::toDomain) }
    }

    override suspend fun getAll(): List<SongMetadata> = withContext(Dispatchers.IO) {
        queries.selectAll().executeAsList().map(Song::toDomain)
    }

    override suspend fun getNonExcluded(): List<SongMetadata> = withContext(Dispatchers.IO) {
        queries.selectNonExcluded().executeAsList().map(Song::toDomain)
    }

    override suspend fun getByStatus(status: SyncStatus): List<SongMetadata> = withContext(Dispatchers.IO) {
        queries.selectByStatus(status.name).executeAsList().map(Song::toDomain)
    }

    override suspend fun getLocalUri(xxHash: String): String? = withContext(Dispatchers.IO) {
        queries.selectByXxHash(xxHash).executeAsOneOrNull()?.localFileUri
    }
}

private fun Song.toDomain() = SongMetadata(
    xxHash = xxHash,
    date = date,
    title = title,
    titleOG = titleOG,
    identify = identify,
    artist = artist,
    artistOG = artistOG,
    coverArtist = coverArtist,
    version = version.toInt(),
    discNumber = discNumber.toInt(),
    track = track,
    comment = comment,
    special = special == 1L,
    hjsonPath = hjsonPath,
    hjsonSha = hjsonSha,
)
