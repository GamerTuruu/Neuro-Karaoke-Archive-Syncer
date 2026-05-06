package com.neurok.syncer.data.github

import com.neurok.syncer.domain.model.RepoTreeEntry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

private const val GITHUB_API = "https://api.github.com"
const val DEFAULT_GITHUB_OWNER = "Nyss777"
const val DEFAULT_GITHUB_REPO = "Neuro-Karaoke-Archive-Metadata"
private const val BRANCH = "main"

class GithubApiSource(private val client: HttpClient) {

    suspend fun fetchHjsonTree(
        pat: String?,
        repoOwner: String = DEFAULT_GITHUB_OWNER,
        repoName: String = DEFAULT_GITHUB_REPO,
    ): List<RepoTreeEntry> {
        val response = client.get("$GITHUB_API/repos/$repoOwner/$repoName/git/trees/$BRANCH?recursive=1") {
            pat?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw Exception("GitHub API error ${response.status.value}: $body")
        }
        val tree = response.body<GitTreeResponse>()
        return tree.tree
            .filter { it.type == "blob" && it.path.endsWith(".hjson") }
            .map { entry ->
                RepoTreeEntry(
                    path = entry.path,
                    sha = entry.sha,
                    downloadUrl = "https://raw.githubusercontent.com/$repoOwner/$repoName/$BRANCH/${entry.path}",
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
    val sha: String = "",
    val tree: List<GitTreeEntry> = emptyList(),
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
