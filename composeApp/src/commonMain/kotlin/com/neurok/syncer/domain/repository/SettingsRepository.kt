package com.neurok.syncer.domain.repository

interface SettingsRepository {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun getInt(key: String, default: Int): Int
    suspend fun getLong(key: String, default: Long): Long
}
