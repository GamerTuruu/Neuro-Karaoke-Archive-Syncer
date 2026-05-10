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
private const val FOLDER_MIME = "application/vnd.google-apps.folder"

class DriveApiSource(private val client: HttpClient) {

    /**
     * Recursively list all MP3 files inside the archive Drive folder and its sub-folders
     * (e.g. DISC 1, DISC 2, Extra Content, …).
     */
    suspend fun listFiles(folderId: String, apiKey: String): List<DriveFile> {
        val result = mutableListOf<DriveFile>()
        listRecursive(folderId, apiKey, result)
        return result
    }

    private suspend fun listRecursive(
        folderId: String,
        apiKey: String,
        result: MutableList<DriveFile>,
    ) {
        var pageToken: String? = null
        do {
            val response = client.get("$DRIVE_API/files") {
                parameter("q", "'$folderId' in parents and trashed=false and (mimeType='audio/mpeg' OR mimeType='$FOLDER_MIME')")
                parameter("fields", "nextPageToken,files(id,name,size,mimeType)")
                parameter("pageSize", PAGE_SIZE)
                parameter("key", apiKey)
                pageToken?.let { parameter("pageToken", it) }
            }
            val page = response.body<DriveFilesPage>()
            for (item in page.files) {
                if (item.mimeType == FOLDER_MIME) {
                    listRecursive(item.id, apiKey, result)
                } else {
                    result.add(DriveFile(id = item.id, name = item.name, size = item.size?.toLongOrNull() ?: 0L))
                }
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
    }

    /**
     * Quick sanity-check: make one lightweight Drive API call to verify the key is valid.
     * Throws with a human-readable message if the key is wrong or permissions are missing.
     */
    suspend fun testApiKey(apiKey: String, folderId: String): Result<Unit> = runCatching {
        val response = client.get("$DRIVE_API/files") {
            parameter("q", "'$folderId' in parents and trashed=false")
            parameter("fields", "files(id)")
            parameter("pageSize", 1)
            parameter("key", apiKey)
        }
        if (response.status.value >= 400) {
            val body = response.bodyAsText()
            error("HTTP ${response.status.value}: ${body.take(300)}")
        }
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
            if (response.status.value >= 400) {
                val body = response.bodyAsText()
                throw Exception("Drive download error ${response.status.value}: ${body.take(300)}")
            }
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
        if (response.status.value >= 400) {
            throw Exception("Drive partial download error ${response.status.value}")
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
    val mimeType: String = "",
)
