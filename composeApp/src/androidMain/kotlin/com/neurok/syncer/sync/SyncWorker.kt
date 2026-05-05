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

        fullSyncUseCase.execute().collect { progress ->
            when (progress) {
                is SyncProgress.Completed -> completed = progress
                is SyncProgress.Error -> error = progress
                else -> Unit
            }
        }

        return if (error != null) {
            Result.retry()
        } else {
            completed?.let { postNotification(it) }
            Result.success()
        }
    }

    private fun postNotification(result: SyncProgress.Completed) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "nksyncer_sync"
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Sync Status", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val body = buildString {
            if (result.updated > 0) append("${result.updated} updated. ")
            if (result.newAvailable > 0) append("${result.newAvailable} new song(s) available. ")
            if (result.orphans > 0) append("${result.orphans} orphan(s) found. ")
            if (isEmpty()) append("Sync complete — everything is up to date.")
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Neuro Karaoke Archive")
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "nksyncer_periodic_sync"
        const val NOTIFICATION_ID = 1001
    }
}
