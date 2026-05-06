package com.neurok.syncer.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class FileStorage(private val context: Context) {

    actual suspend fun listMp3s(folderUri: String): List<String> = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(folderUri)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val result = mutableListOf<String>()
        collectMp3sRecursive(root, result)
        result
    }

    private fun collectMp3sRecursive(dir: DocumentFile, result: MutableList<String>) {
        for (child in dir.listFiles()) {
            when {
                child.isDirectory -> collectMp3sRecursive(child, result)
                child.isFile && child.name?.endsWith(".mp3", ignoreCase = true) == true ->
                    result.add(child.uri.toString())
            }
        }
    }

    actual suspend fun getFolderSize(folderUri: String): Long = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(folderUri)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0L
        sumSizeRecursive(root)
    }

    private fun sumSizeRecursive(dir: DocumentFile): Long {
        var total = 0L
        for (child in dir.listFiles()) {
            total += if (child.isDirectory) sumSizeRecursive(child) else child.length()
        }
        return total
    }

    actual suspend fun fileExists(uri: String): Boolean = withContext(Dispatchers.IO) {
        val docFile = DocumentFile.fromSingleUri(context, Uri.parse(uri))
        docFile?.exists() == true
    }

    actual suspend fun deleteFile(uri: String): Boolean = withContext(Dispatchers.IO) {
        val docFile = DocumentFile.fromSingleUri(context, Uri.parse(uri))
        docFile?.delete() == true
    }

    actual suspend fun readBytes(uri: String): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open: $uri")
    }

    actual suspend fun writeFile(folderUri: String, filename: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val treeUri = Uri.parse(folderUri)
            val dir = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IllegalStateException("Cannot open folder: $folderUri")
            val file = dir.createFile("audio/mpeg", filename)
                ?: throw IllegalStateException("Cannot create file: $filename")
            context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Cannot write: $filename")
            file.uri.toString()
        }

    actual suspend fun renameFile(currentUri: String, newFilename: String): String =
        withContext(Dispatchers.IO) {
            val docFile = DocumentFile.fromSingleUri(context, Uri.parse(currentUri))
                ?: throw IllegalStateException("Cannot open: $currentUri")
            val parent = docFile.parentFile
                ?: throw IllegalStateException("Cannot get parent of: $currentUri")
            // Remove any existing file with that name (except the current one)
            parent.findFile(newFilename)
                ?.takeIf { it.uri.toString() != currentUri }
                ?.delete()
            val ok = docFile.renameTo(newFilename)
            if (!ok) throw IllegalStateException("Failed to rename to $newFilename")
            parent.findFile(newFilename)?.uri?.toString()
                ?: throw IllegalStateException("Cannot find renamed file: $newFilename")
        }

    actual fun getDisplayPath(folderUri: String): String {
        return try {
            val uri = Uri.parse(folderUri)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            docId ?: folderUri
        } catch (_: Exception) {
            folderUri
        }
    }
}
