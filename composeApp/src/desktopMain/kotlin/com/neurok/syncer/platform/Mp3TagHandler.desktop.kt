package com.neurok.syncer.platform

import com.neurok.syncer.mp3.Mp3TagHandlerCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class Mp3TagHandler {

    actual suspend fun readCommVed(fileUri: String): String? = withContext(Dispatchers.IO) {
        Mp3TagHandlerCore.readCommVed(fileUri)
    }

    actual suspend fun applyStandardTags(
        fileUri: String,
        title: String,
        artist: String,
        album: String,
        track: String,
        discNumber: Int,
        date: String,
    ) = withContext(Dispatchers.IO) {
        Mp3TagHandlerCore.applyStandardTags(fileUri, title, artist, album, track, discNumber, date)
    }
}
