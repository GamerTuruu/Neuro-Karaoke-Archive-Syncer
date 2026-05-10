package com.neurok.syncer.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

actual class SyncNotifier(private val context: Context) {

    private val nm: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    actual fun postProgress(title: String, text: String) {
        ensureChannel()
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build(),
        )
    }

    actual fun postCompleted(body: String) {
        nm.cancel(NOTIFICATION_ID)  // clear ongoing spinner before replacing
        ensureChannel()
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Neuro Karaoke Archive")
                .setContentText(body)
                .setAutoCancel(true)
                .build(),
        )
    }

    actual fun postError(message: String) {
        nm.cancel(NOTIFICATION_ID)  // clear ongoing spinner before replacing
        ensureChannel()
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Neuro Karaoke Archive — error")
                .setContentText(message.take(200))
                .setAutoCancel(true)
                .build(),
        )
    }

    actual fun cancel() {
        nm.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sync Status", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    companion object {
        // Use a different ID from SyncWorker (1001) to avoid collision
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "nksyncer_sync"
    }
}
