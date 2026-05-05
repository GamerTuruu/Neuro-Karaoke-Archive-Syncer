package com.neurok.syncer.domain.model

/**
 * Determines how standard ID3 TITLE and ARTIST tags are built from [SongMetadata].
 * The [COMM::ved] frame is never modified by any preset.
 *
 * Implementing a new preset: create an object implementing this interface and
 * register it in [TagPresetRegistry].
 */
interface TagPreset {
    val id: String
    val displayName: String

    fun buildTitle(meta: SongMetadata): String
    fun buildArtist(meta: SongMetadata): String

    /** Album tag = disc folder name extracted from hjsonPath. */
    fun buildAlbum(meta: SongMetadata): String {
        val parts = meta.hjsonPath.split("/")
        return if (parts.size >= 2) parts[0] else "Neuro Karaoke Archive V3"
    }
}

/** All registered presets. Order matters — first entry is the default displayed in UI. */
object TagPresetRegistry {
    val all: List<TagPreset> = listOf(DefaultPreset, OGOnlyPreset, EnglishOnlyPreset)

    fun fromId(id: String): TagPreset = all.first { it.id == id }
    fun fromIdOrDefault(id: String?): TagPreset = all.firstOrNull { it.id == id } ?: DefaultPreset
}

// ─── Preset 1: Default ────────────────────────────────────────────────────────
// Title:
//   no TitleOG, no Identify   → Title
//   no TitleOG, has Identify  → Title - Identify
//   has TitleOG, no Identify  → TitleOG (Title)
//   has TitleOG, has Identify → TitleOG (Title) - Identify
// Artist:
//   no ArtistOG  → CoverArtist - Artist
//   has ArtistOG → CoverArtist - ArtistOG (Artist)

object DefaultPreset : TagPreset {
    override val id = "default"
    override val displayName = "Default"

    override fun buildTitle(meta: SongMetadata): String {
        val base = if (meta.titleOG != null) "${meta.titleOG} (${meta.title})" else meta.title
        return if (meta.identify != null) "$base - ${meta.identify}" else base
    }

    override fun buildArtist(meta: SongMetadata): String =
        if (meta.artistOG != null) "${meta.coverArtist} - ${meta.artistOG} (${meta.artist})"
        else "${meta.coverArtist} - ${meta.artist}"
}

// ─── Preset 2: OG Only ────────────────────────────────────────────────────────
// Title: TitleOG if present, else Title  (+ " - Identify" if present)
// Artist: CoverArtist - ArtistOG if present, else Artist

object OGOnlyPreset : TagPreset {
    override val id = "og_only"
    override val displayName = "OG Only"

    override fun buildTitle(meta: SongMetadata): String {
        val base = meta.titleOG ?: meta.title
        return if (meta.identify != null) "$base - ${meta.identify}" else base
    }

    override fun buildArtist(meta: SongMetadata): String =
        "${meta.coverArtist} - ${meta.artistOG ?: meta.artist}"
}

// ─── Preset 3: English Only ───────────────────────────────────────────────────
// Title: always Title  (+ " - Identify" if present)
// Artist: CoverArtist - Artist  (always English, ArtistOG ignored)

object EnglishOnlyPreset : TagPreset {
    override val id = "english_only"
    override val displayName = "English Only"

    override fun buildTitle(meta: SongMetadata): String =
        if (meta.identify != null) "${meta.title} - ${meta.identify}" else meta.title

    override fun buildArtist(meta: SongMetadata): String =
        "${meta.coverArtist} - ${meta.artist}"
}
