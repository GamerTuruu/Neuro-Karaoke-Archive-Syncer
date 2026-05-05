package com.neurok.syncer.hjson

import com.neurok.syncer.domain.model.SongMetadata
import org.hjson.JsonValue

/**
 * Parses HJSON text (from the GitHub metadata repo) into a [SongMetadata] object.
 *
 * HJSON is a relaxed JSON format — keys don't need quotes, comments are allowed, etc.
 * We use [org.hjson:hjson] to parse it into a standard JSON object first.
 */
object HjsonParser {

    /**
     * Parse a single HJSON string into [SongMetadata].
     *
     * @param text  Raw .hjson file content.
     * @param hjsonPath  Repo-relative path used to populate [SongMetadata.hjsonPath] and derive album name.
     * @param hjsonSha   GitHub blob SHA of this file.
     */
    fun parse(text: String, hjsonPath: String, hjsonSha: String): SongMetadata {
        val obj = JsonValue.readHjson(text).asObject()

        fun str(key: String): String? = obj.get(key)?.asString()?.takeIf { it.isNotBlank() }
        fun int(key: String): Int = obj.get(key)?.asInt() ?: error("Missing required int field: $key")

        return SongMetadata(
            xxHash      = str("xxHash") ?: error("Missing xxHash in $hjsonPath"),
            date        = str("Date") ?: "",
            title       = str("Title") ?: error("Missing Title in $hjsonPath"),
            titleOG     = str("TitleOG"),
            identify    = str("Identify"),
            artist      = str("Artist") ?: error("Missing Artist in $hjsonPath"),
            artistOG    = str("ArtistOG"),
            coverArtist = str("CoverArtist") ?: error("Missing CoverArtist in $hjsonPath"),
            version     = int("Version"),
            discNumber  = int("Discnumber"),
            track       = str("Track") ?: "",
            comment     = str("Comment"),
            special     = str("Special") == "1",
            hjsonPath   = hjsonPath,
            hjsonSha    = hjsonSha,
        )
    }
}
