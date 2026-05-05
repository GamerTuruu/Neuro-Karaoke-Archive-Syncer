package com.neurok.syncer.data.db

import com.neurok.syncer.database.NKSyncerDatabase
import com.neurok.syncer.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(private val db: NKSyncerDatabase) : SettingsRepository {

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        db.settingsQueries.get(key).executeAsOneOrNull()
    }

    override suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        db.settingsQueries.set(key, value)
    }

    override suspend fun getInt(key: String, default: Int): Int =
        get(key)?.toIntOrNull() ?: default

    override suspend fun getLong(key: String, default: Long): Long =
        get(key)?.toLongOrNull() ?: default
}
