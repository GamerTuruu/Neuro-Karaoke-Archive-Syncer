package com.neurok.syncer.platform

/**
 * Posts system notifications for manual Fetch/Sync operations started from the UI.
 * Android: uses NotificationManager. Desktop: no-op.
 */
expect class SyncNotifier {
    fun postProgress(title: String, text: String)
    fun postCompleted(body: String)
    fun postError(message: String)
    fun cancel()
}
