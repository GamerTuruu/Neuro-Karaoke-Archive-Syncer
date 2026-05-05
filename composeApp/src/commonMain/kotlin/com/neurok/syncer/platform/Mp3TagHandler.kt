package com.neurok.syncer.platform

/**
 * Platform-specific MP3 ID3 tag operations.
 * Actual implementation in jvmCommonMain using JAudioTagger.
 * On Android an extra temp-file copy step is required.
 */
expect class Mp3TagHandler {
    /**
     * Read the raw JSON string from the COMM frame with language "ved".
     * Returns null if no such frame exists or the file cannot be read.
     */
    suspend fun readCommVed(fileUri: String): String?

    /**
     * Apply standard ID3 tags to an MP3 file in-place.
     * The COMM::ved frame and all other existing frames are preserved.
     */
    suspend fun applyStandardTags(
        fileUri: String,
        title: String,
        artist: String,
        album: String,
        track: String,
        discNumber: Int,
        date: String,
    )
}
