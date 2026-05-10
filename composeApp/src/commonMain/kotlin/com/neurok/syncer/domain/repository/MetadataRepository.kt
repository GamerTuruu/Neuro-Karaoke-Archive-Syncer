package com.neurok.syncer.domain.repository

interface MetadataRepository {
    /**
     * Fetch the full repo tree from GitHub and return all .hjson entries.
     * Only entries whose sha differs from what's cached will be fully downloaded.
     * Updates the Song database in-place.
     * @param onProgress Called for each HJSON file downloaded (for UI feedback).
     * @return Count of new + updated files processed.
     */
    suspend fun syncFromGitHub(
        pat: String?,
        onProgress: suspend (String) -> Unit = {},
    ): Int
}
