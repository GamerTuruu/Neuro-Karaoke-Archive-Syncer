package com.neurok.syncer.data.drive

import com.neurok.syncer.database.NKSyncerDatabase
import com.neurok.syncer.domain.model.DriveFile
import com.neurok.syncer.domain.repository.DriveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers

class DriveRepositoryImpl(
    private val db: NKSyncerDatabase,
    private val apiSource: DriveApiSource,
) : DriveRepository {

    private val queries get() = db.driveFileIndexQueries

    override suspend fun refreshIndex(apiKey: String, folderId: String) {
        val files = apiSource.listFiles(folderId, apiKey)
        val now = currentTimeMs()
        db.transaction {
            queries.deleteAll()
            for (f in files) {
                queries.upsert(f.name, f.id, now)
            }
        }
    }

    override suspend fun getFileId(filename: String): String? =
        queries.getById(filename).executeAsOneOrNull()?.driveFileId

    override suspend fun isIndexStale(maxAgeMs: Long): Boolean {
        val oldest = queries.getOldestFetchTime().executeAsOneOrNull()?.oldest ?: return true
        return (currentTimeMs() - oldest) > maxAgeMs
    }

    override suspend fun clearIndex() = queries.deleteAll()

    override fun observeAll(): Flow<List<DriveFile>> =
        queries.getAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { DriveFile(id = it.driveFileId, name = it.driveFilename) } }
}

internal expect fun currentTimeMs(): Long
