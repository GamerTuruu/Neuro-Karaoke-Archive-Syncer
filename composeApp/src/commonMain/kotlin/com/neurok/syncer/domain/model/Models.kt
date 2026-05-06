package com.neurok.syncer.domain.model

import kotlinx.serialization.Serializable

/** Metadata parsed from a .hjson file in the GitHub repo. */
@Serializable
data class SongMetadata(
    val xxHash: String,
    val date: String,
    val title: String,
    val titleOG: String? = null,
    val identify: String? = null,
    val artist: String,
    val artistOG: String? = null,
    val coverArtist: String,
    val version: Int,
    val discNumber: Int,
    val track: String,
    val comment: String? = null,
    val special: Boolean = false,
    /** Full path relative to repo root, e.g. "DISC 7 - .../059....hjson" */
    val hjsonPath: String,
    /** GitHub blob SHA — used to detect when the file changed without re-downloading */
    val hjsonSha: String,
)

/** A local MP3 file present in the user's archive folder. */
data class LocalSong(
    val xxHash: String,
    /** Android: DocumentFile URI string. Desktop: absolute file path. */
    val fileUri: String,
    /** Raw JSON string stored in the COMM::ved ID3 frame — never modified by this app. */
    val commVedJson: String,
)

enum class SyncStatus {
    /** Metadata up to date, file present locally. */
    UP_TO_DATE,
    /** Remote hjsonSha differs from DB — tags need to be re-applied. */
    NEEDS_UPDATE,
    /** HJSON found in repo but no local file. Offer to download. */
    NEW_AVAILABLE,
    /** Local file found but no matching HJSON in repo. User is notified. */
    ORPHAN,
    /** User has excluded this song — skip all sync operations. */
    EXCLUDED,
    /** Currently being downloaded. */
    DOWNLOADING,
}

/** Aggregated counts by sync status for the Home screen. */
data class StatusCounts(
    val upToDate: Int = 0,
    val needsUpdate: Int = 0,
    val newAvailable: Int = 0,
    val orphans: Int = 0,
    val excluded: Int = 0,
)

/** Represents one entry from the GitHub repo tree API. */
data class RepoTreeEntry(
    val path: String,
    val sha: String,
    val downloadUrl: String,
    val size: Long,
)

/** Represents a file entry in the Google Drive folder. */
data class DriveFile(
    val id: String,
    val name: String,
    val size: Long = 0L,
)

/** Progress event emitted by FullSyncUseCase. */
sealed class SyncProgress {
    data object Started : SyncProgress()
    data class ScanningLocal(val current: Int, val total: Int) : SyncProgress()
    data class FetchingMetadata(val message: String) : SyncProgress()
    data class ApplyingTags(val current: Int, val total: Int, val songTitle: String) : SyncProgress()
    data class Downloading(val current: Int, val total: Int, val filename: String, val bytesProgress: Long, val bytesTotal: Long) : SyncProgress()
    data class Completed(val updated: Int, val downloaded: Int, val newAvailable: Int, val orphans: Int) : SyncProgress()
    data class Error(val message: String) : SyncProgress()
}

/** Keys for the Settings table. */
object SettingsKeys {
    const val LOCAL_FOLDER_URI = "local_folder_uri"
    const val SYNC_SCHEDULE_HOURS = "sync_schedule_hours" // "0" = off
    const val ACTIVE_PRESET_ID = "active_preset_id"
    const val DRIVE_API_KEY = "drive_api_key"
    const val GITHUB_PAT = "github_pat"
    const val LAST_SYNC_TIME_MS = "last_sync_time_ms"
    // Advanced / overridable
    const val DRIVE_FOLDER_ID = "drive_folder_id"   // default: archive folder
    const val GITHUB_REPO = "github_repo"           // format: "owner/repo"
    const val THEME = "theme"                       // "dark" | "light" | "system"
    /** published_at of the last successfully downloaded metadata-zip.zip release. */
    const val METADATA_ZIP_PUBLISHED_AT = "metadata_zip_published_at"
}
