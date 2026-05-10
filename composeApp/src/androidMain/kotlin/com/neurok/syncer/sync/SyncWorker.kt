package com.neurok.syncer.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neurok.syncer.domain.model.SyncProgress
import com.neurok.syncer.domain.usecase.FullSyncUseCase
import kotlinx.coroutines.flow.collect
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val fullSyncUseCase: FullSyncUseCase by inject()

    override suspend fun doWork(): Result {
        var completed: SyncProgress.Completed? = null
        var error: SyncProgress.Error? = null

        postProgressNotification("Sync started…")

        fullSyncUseCase.execute().collect { progress ->
            when (progress) {
                is SyncProgress.Completed -> completed = progress
                is SyncProgress.Error -> error = progress
                is SyncProgress.FetchingMetadata -> postProgressNotification(progress.message)
                is SyncProgress.ScanningLocal -> postProgressNotification("Scanning local files… ${progress.current}/${progress.total}")
                is SyncProgress.ApplyingTags -> postProgressNotification("Tagging (${progress.current}/${progress.total}): ${progress.songTitle}")
                is SyncProgress.Downloading -> postProgressNotification("Downloading (${progress.current}/${progress.total}): ${progress.filename}")
                else -> Unit
            }
        }

        return if (error != null) {
            postErrorNotification(error!!.message)
            Result.retry()
        } else {
            completed?.let { postCompletedNotification(it) }
            Result.success()
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        val channelId = CHANNEL_ID
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Sync Status", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun postProgressNotification(text: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Neuro Karaoke Archive — syncing…")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun postCompletedNotification(result: SyncProgress.Completed) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val body = buildString {
            if (result.downloaded > 0) append("${result.downloaded} downloaded. ")
            if (result.updated > 0) append("${result.updated} tagged. ")
            if (result.newAvailable > 0) append("${result.newAvailable} new song(s) available. ")
            if (result.orphans > 0) append("${result.orphans} orphan(s) found. ")
            if (isEmpty()) append("Everything is up to date.")
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Neuro Karaoke Archive")
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun postErrorNotification(message: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Neuro Karaoke Archive — sync error")
            .setContentText(message.take(200))
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "nksyncer_periodic_sync"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "nksyncer_sync"
    }
}
