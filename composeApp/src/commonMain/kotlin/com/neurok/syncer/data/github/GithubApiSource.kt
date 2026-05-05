package com.neurok.syncer.data.github

import com.neurok.syncer.domain.model.RepoTreeEntry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val GITHUB_API = "https://api.github.com"
private const val OWNER = "Nyss777"
private const val REPO = "Neuro-Karaoke-Archive-Metadata"
private const val BRANCH = "main"

class GithubApiSource(private val client: HttpClient) {

    /**
     * Fetch the complete recursive tree for the metadata repo.
     * Returns all blob entries whose path ends in ".hjson".
     *
     * The blob's `url` field is the GitHub API URL; we convert it to the
     * raw.githubusercontent.com URL for cheaper plain-text download.
     */
    suspend fun fetchHjsonTree(pat: String?): List<RepoTreeEntry> {
        val response = client.get("$GITHUB_API/repos/$OWNER/$REPO/git/trees/$BRANCH?recursive=1") {
            pat?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        val tree = response.body<GitTreeResponse>()
        return tree.tree
            .filter { it.type == "blob" && it.path.endsWith(".hjson") }
            .map { entry ->
                RepoTreeEntry(
                    path = entry.path,
                    sha = entry.sha,
                    downloadUrl = "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/${entry.path}",
                    size = entry.size ?: 0L,
                )
            }
    }

    /**
     * Download the raw text of a single .hjson file.
     */
    suspend fun fetchHjsonContent(url: String, pat: String?): String {
        return client.get(url) {
            pat?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }.body()
    }
}

@Serializable
private data class GitTreeResponse(
    val sha: String,
    val tree: List<GitTreeEntry>,
    val truncated: Boolean = false,
)

@Serializable
private data class GitTreeEntry(
    val path: String = "",
    val type: String = "",
    val sha: String = "",
    val size: Long? = null,
    val url: String = "",
)
