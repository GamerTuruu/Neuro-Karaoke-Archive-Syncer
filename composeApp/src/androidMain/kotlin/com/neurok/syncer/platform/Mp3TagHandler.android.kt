package com.neurok.syncer.platform

import android.content.Context
import android.net.Uri
import com.neurok.syncer.mp3.Mp3TagHandlerCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of [Mp3TagHandler].
 *
 * JAudioTagger requires a real [File] reference (not a content URI).
 * Workflow:
 *  1. Copy the MP3 from its DocumentFile URI to a temp file in cacheDir.
 *  2. Apply tags using [Mp3TagHandlerCore].
 *  3. Copy the modified temp file back to the original URI.
 *  4. Delete the temp file.
 */
actual class Mp3TagHandler(private val context: Context) {

    actual suspend fun readCommVed(fileUri: String): String? = withContext(Dispatchers.IO) {
        val tmp = copyToTemp(fileUri)
        try {
            Mp3TagHandlerCore.readCommVed(tmp.absolutePath)
        } finally {
            tmp.delete()
        }
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
        val tmp = copyToTemp(fileUri)
        try {
            Mp3TagHandlerCore.applyStandardTags(tmp.absolutePath, title, artist, album, track, discNumber, date)
            copyFromTemp(tmp, fileUri)
        } finally {
            tmp.delete()
        }
    }

    private fun copyToTemp(fileUri: String): File {
        val tmp = File(context.cacheDir, "nksyncer_tag_${System.nanoTime()}.mp3")
        context.contentResolver.openInputStream(Uri.parse(fileUri))?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open for reading: $fileUri")
        return tmp
    }

    private fun copyFromTemp(tmp: File, fileUri: String) {
        context.contentResolver.openOutputStream(Uri.parse(fileUri), "wt")?.use { output ->
            tmp.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open for writing: $fileUri")
    }
}
