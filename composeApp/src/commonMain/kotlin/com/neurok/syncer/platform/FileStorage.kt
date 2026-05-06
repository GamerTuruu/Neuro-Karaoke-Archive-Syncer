package com.neurok.syncer.platform

/**
 * Platform-specific file storage operations.
 * Android: DocumentFile-based (scoped storage).
 * Desktop: java.io.File-based.
 */
expect class FileStorage {
    /** Returns all .mp3 file URIs/paths inside the configured archive folder. */
    suspend fun listMp3s(folderUri: String): List<String>

    /** Returns the total size in bytes of all files in the configured archive folder. */
    suspend fun getFolderSize(folderUri: String): Long

    /** Returns true if the given file URI refers to an existing file. */
    suspend fun fileExists(uri: String): Boolean

    /** Delete a file by URI. */
    suspend fun deleteFile(uri: String): Boolean

    /** Open a file URI as a byte array (for small files; used in tag reading). */
    suspend fun readBytes(uri: String): ByteArray

    /** Write bytes to new file inside folderUri. Returns the URI of the created file. */
    suspend fun writeFile(folderUri: String, filename: String, bytes: ByteArray): String

    /** Rename a file. Returns the new URI/path after rename. */
    suspend fun renameFile(currentUri: String, newFilename: String): String

    /** Returns the display path for a folder URI (shown in Settings). */
    fun getDisplayPath(folderUri: String): String
}
