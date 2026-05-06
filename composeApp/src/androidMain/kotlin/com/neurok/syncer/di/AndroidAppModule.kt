package com.neurok.syncer.di

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.neurok.syncer.data.db.SettingsRepositoryImpl
import com.neurok.syncer.data.db.SongRepositoryImpl
import com.neurok.syncer.data.drive.DriveApiSource
import com.neurok.syncer.data.drive.DriveRepositoryImpl
import com.neurok.syncer.data.github.GithubApiSource
import com.neurok.syncer.data.github.GithubMetadataRepository
import com.neurok.syncer.database.NKSyncerDatabase
import com.neurok.syncer.domain.repository.DriveRepository
import com.neurok.syncer.domain.repository.MetadataRepository
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.domain.repository.SongRepository
import com.neurok.syncer.domain.usecase.ApplyTagsUseCase
import com.neurok.syncer.domain.usecase.DownloadSongUseCase
import com.neurok.syncer.domain.usecase.FetchMetadataUseCase
import com.neurok.syncer.domain.usecase.FullSyncUseCase
import com.neurok.syncer.domain.usecase.ScanLocalFilesUseCase
import com.neurok.syncer.domain.usecase.SyncTagsAndDownloadUseCase
import com.neurok.syncer.platform.FileStorage
import com.neurok.syncer.platform.Mp3TagHandler
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val androidAppModule = module {

    // Database
    single<NKSyncerDatabase> {
        NKSyncerDatabase(AndroidSqliteDriver(NKSyncerDatabase.Schema, androidContext(), "nksyncer.db"))
    }

    // Platform
    single { FileStorage(androidContext()) }
    single { Mp3TagHandler(androidContext()) }

    // Ktor
    single<HttpClient> {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    // Data sources
    singleOf(::GithubApiSource)
    singleOf(::DriveApiSource)

    // Repositories
    single<SongRepository> { SongRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<DriveRepository> { DriveRepositoryImpl(get(), get()) }
    single<MetadataRepository> { GithubMetadataRepository(get(), get(), get()) }

    // Use cases
    singleOf(::ScanLocalFilesUseCase)
    singleOf(::ApplyTagsUseCase)
    singleOf(::DownloadSongUseCase)
    singleOf(::FetchMetadataUseCase)
    singleOf(::SyncTagsAndDownloadUseCase)
    singleOf(::FullSyncUseCase)
}
