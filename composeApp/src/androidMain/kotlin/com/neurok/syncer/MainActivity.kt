package com.neurok.syncer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.neurok.syncer.ui.theme.AppTheme
import androidx.work.*
import com.neurok.syncer.domain.model.SettingsKeys
import com.neurok.syncer.domain.repository.SettingsRepository
import com.neurok.syncer.sync.SyncWorker
import com.neurok.syncer.ui.navigation.AppNavigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val settingsRepository: SettingsRepository by inject()
    private var pendingFolderCallback: ((String) -> Unit)? = null

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist the URI permission so we can access it across restarts
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val uriStr = uri.toString()
            pendingFolderCallback?.invoke(uriStr)
            pendingFolderCallback = null
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* accepted or denied — sync will still work, notifications just won't show */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS permission on Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val themeMode by AppTheme.mode.collectAsState()
            val colorScheme = when (themeMode) {
                "light" -> lightColorScheme()
                "system" -> if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
                else -> darkColorScheme() // "dark" default
            }
            MaterialTheme(colorScheme = colorScheme) {
                AppNavigation(
                    onPickFolderFromActivity = { callback ->
                        pendingFolderCallback = callback
                        folderPickerLauncher.launch(null)
                    },
                )
            }
        }

        scheduleSyncIfNeeded()
    }

    private fun scheduleSyncIfNeeded() {
        CoroutineScope(Dispatchers.IO).launch {
            val hours = settingsRepository.getInt(SettingsKeys.SYNC_SCHEDULE_HOURS, 24)
            if (hours <= 0) {
                WorkManager.getInstance(this@MainActivity).cancelUniqueWork(SyncWorker.WORK_NAME)
                return@launch
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(hours.toLong(), TimeUnit.HOURS)
                .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                SyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
