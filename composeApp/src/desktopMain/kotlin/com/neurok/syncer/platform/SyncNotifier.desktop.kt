package com.neurok.syncer.platform

actual class SyncNotifier {
    actual fun postProgress(title: String, text: String) {}
    actual fun postCompleted(body: String) {}
    actual fun postError(message: String) {}
    actual fun cancel() {}
}
