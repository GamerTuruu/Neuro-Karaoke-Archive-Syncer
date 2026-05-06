package com.neurok.syncer

import android.app.Application
import com.neurok.syncer.di.androidAppModule
import com.neurok.syncer.ui.home.HomeViewModel
import com.neurok.syncer.ui.browser.BrowserViewModel
import com.neurok.syncer.ui.settings.SettingsViewModel
import com.neurok.syncer.ui.detail.SongDetailViewModel
import com.neurok.syncer.ui.preset.PresetViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

class NKSyncerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@NKSyncerApp)
            workManagerFactory()
            modules(
                androidAppModule,
                module {
                    viewModelOf(::HomeViewModel)
                    viewModelOf(::BrowserViewModel)
                    viewModelOf(::SettingsViewModel)
                    viewModelOf(::SongDetailViewModel)
                    viewModelOf(::PresetViewModel)
                }
            )
        }
    }
}
