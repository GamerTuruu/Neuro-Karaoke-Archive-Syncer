package com.neurok.syncer.platform

import com.neurok.syncer.mp3.Mp3TagHandlerCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class FileStorage {

    actual suspend fun listMp3s(folderUri: String): List<String> = withContext(Dispatchers.IO) {
        File(folderUri).walkTopDown()
            .filter { it.isFile && it.extension.equals("mp3", ignoreCase = true) }
            .map { it.absolutePath }
            .toList()
    }

    actual suspend fun getFolderSize(folderUri: String): Long = withContext(Dispatchers.IO) {
        File(folderUri).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    actual suspend fun fileExists(uri: String): Boolean = File(uri).exists()

    actual suspend fun deleteFile(uri: String): Boolean = File(uri).delete()

    actual suspend fun readBytes(uri: String): ByteArray = withContext(Dispatchers.IO) {
        File(uri).readBytes()
    }

    actual suspend fun writeFile(folderUri: String, filename: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val dest = File(folderUri, filename)
            dest.writeBytes(bytes)
            dest.absolutePath
        }

    actual fun getDisplayPath(folderUri: String): String = folderUri
}
