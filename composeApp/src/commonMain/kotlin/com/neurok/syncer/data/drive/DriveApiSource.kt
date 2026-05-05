package com.neurok.syncer.data.drive

import com.neurok.syncer.domain.model.DriveFile
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
const val ARCHIVE_FOLDER_ID = "1B1VaWp-mCKk15_7XpFnImsTdBJPOGx7a"
private const val PAGE_SIZE = 1000

class DriveApiSource(private val client: HttpClient) {

    /**
     * List all MP3 files in the archive Drive folder.
     * Handles pagination automatically.
     */
    suspend fun listFiles(folderId: String, apiKey: String): List<DriveFile> {
        val results = mutableListOf<DriveFile>()
        var pageToken: String? = null

        do {
            val response = client.get("$DRIVE_API/files") {
                parameter("q", "'$folderId' in parents and trashed=false and mimeType='audio/mpeg'")
                parameter("fields", "nextPageToken,files(id,name,size)")
                parameter("pageSize", PAGE_SIZE)
                parameter("key", apiKey)
                pageToken?.let { parameter("pageToken", it) }
            }
            val page = response.body<DriveFilesPage>()
            results += page.files.map { DriveFile(id = it.id, name = it.name, size = it.size?.toLongOrNull() ?: 0L) }
            pageToken = page.nextPageToken
        } while (pageToken != null)

        return results
    }

    /**
     * Download a Drive file as a byte stream, invoking [onBytes] for each chunk.
     * Use this for large MP3 downloads to avoid loading the whole file into memory.
     */
    suspend fun downloadFile(
        fileId: String,
        apiKey: String,
        onProgress: (bytesReceived: Long, totalBytes: Long) -> Unit,
        onBytes: (ByteArray) -> Unit,
    ) {
        client.prepareGet("$DRIVE_API/files/$fileId") {
            parameter("alt", "media")
            parameter("key", apiKey)
        }.execute { response ->
            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
            val channel = response.bodyAsChannel()
            var received = 0L
            val buf = ByteArray(256 * 1024) // 256 KB chunks
            while (!channel.isClosedForRead) {
                val n = channel.readAvailable(buf, 0, buf.size)
                if (n <= 0) break
                val chunk = buf.copyOf(n)
                onBytes(chunk)
                received += n
                onProgress(received, contentLength)
            }
        }
    }

    /**
     * Stream a small partial download (for preview — first ~512 KB).
     */
    suspend fun downloadPartial(fileId: String, apiKey: String, maxBytes: Long = 512 * 1024): ByteArray {
        val response = client.get("$DRIVE_API/files/$fileId") {
            parameter("alt", "media")
            parameter("key", apiKey)
            header(HttpHeaders.Range, "bytes=0-${maxBytes - 1}")
        }
        return response.body()
    }
}

@Serializable
private data class DriveFilesPage(
    val files: List<DriveFileItem> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
private data class DriveFileItem(
    val id: String,
    val name: String,
    val size: String? = null,
)
