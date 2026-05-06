package com.neurok.syncer.data.github

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val GITHUB_API = "https://api.github.com"
const val DEFAULT_GITHUB_OWNER = "Nyss777"
const val DEFAULT_GITHUB_REPO = "Neuro-Karaoke-Archive-Metadata"

/** Metadata about the latest GitHub release of the metadata repo. */
data class ReleaseInfo(
    val publishedAt: String,
    val downloadUrl: String,
)

class GithubApiSource(private val client: HttpClient) {

    /**
     * Fetch the latest release info from GitHub.
     * Returns the release's [publishedAt] timestamp and [downloadUrl] for
     * the `metadata-zip.zip` asset.
     */
    suspend fun fetchLatestReleaseInfo(
        pat: String?,
        repoOwner: String = DEFAULT_GITHUB_OWNER,
        repoName: String = DEFAULT_GITHUB_REPO,
    ): ReleaseInfo {
        val response = client.get("$GITHUB_API/repos/$repoOwner/$repoName/releases/tags/latest") {
            pat?.takeIf { it.isNotBlank() }?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw Exception("GitHub releases API error ${response.status.value}: $body")
        }
        val release = response.body<GitRelease>()
        val asset = release.assets.firstOrNull { it.name.equals("metadata-zip.zip", ignoreCase = true) }
            ?: throw Exception("metadata-zip.zip not found in latest release (assets: ${release.assets.map { it.name }})")
        return ReleaseInfo(release.publishedAt, asset.browserDownloadUrl)
    }

    /**
     * Download the metadata zip from the given URL.
     * Returns the full zip as a [ByteArray].
     */
    suspend fun downloadZip(url: String, pat: String?): ByteArray {
        val response = client.get(url) {
            // PAT not strictly needed for public assets — include as a courtesy
            pat?.takeIf { it.isNotBlank() }?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        if (!response.status.isSuccess()) {
            throw Exception("Zip download failed HTTP ${response.status.value}")
        }
        return response.body()
    }
}

@Serializable
private data class GitRelease(
    @SerialName("published_at") val publishedAt: String = "",
    val assets: List<GitReleaseAsset> = emptyList(),
)

@Serializable
private data class GitReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)
