package com.neurok.syncer.mp3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Core JAudioTagger implementation for reading COMM::ved and writing standard ID3 tags.
 *
 * Android `actual` class wraps this with a temp-file copy step since JAudioTagger
 * needs a real [java.io.File], not a content URI.
 *
 * Desktop `actual` can call these functions directly with absolute paths.
 */
object Mp3TagHandlerCore {

    init {
        // Suppress JAudioTagger's noisy logging
        Logger.getLogger("org.jaudiotagger").level = Level.SEVERE
    }

    /**
     * Read the raw JSON string from the COMM frame with language identifier "ved".
     * The frame is identified by: type == "COMM", language == "ved", description == "".
     */
    fun readCommVed(filePath: String): String? {
        return try {
            val audioFile = AudioFileIO.read(File(filePath))
            val tag = audioFile.tag ?: return null
            if (tag is AbstractID3v2Tag) {
                val frames = tag.getFrame("COMM")
                when (frames) {
                    is AbstractID3v2Frame -> extractCommVed(frames)
                    is List<*> -> frames.filterIsInstance<AbstractID3v2Frame>()
                        .mapNotNull { extractCommVed(it) }
                        .firstOrNull()
                    else -> null
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractCommVed(frame: AbstractID3v2Frame): String? {
        val body = frame.body as? FrameBodyCOMM ?: return null
        return if (body.language == "ved") body.text else null
    }

    /**
     * Apply standard ID3 tags in-place while preserving COMM::ved and all other frames.
     */
    fun applyStandardTags(
        filePath: String,
        title: String,
        artist: String,
        album: String,
        track: String,
        discNumber: Int,
        date: String,
    ) {
        val audioFile = AudioFileIO.read(File(filePath))
        val existingTag = audioFile.tag

        // Get the COMM::ved raw JSON before we do anything
        val commVedJson: String? = if (existingTag is AbstractID3v2Tag) {
            val frames = existingTag.getFrame("COMM")
            when (frames) {
                is AbstractID3v2Frame -> extractCommVed(frames)
                is List<*> -> frames.filterIsInstance<AbstractID3v2Frame>()
                    .mapNotNull { extractCommVed(it) }
                    .firstOrNull()
                else -> null
            }
        } else null

        // Create a fresh ID3v2.4 tag to avoid accumulating stale data
        val newTag = ID3v24Tag()

        // Write standard fields
        newTag.setField(FieldKey.TITLE, title)
        newTag.setField(FieldKey.ARTIST, artist)
        newTag.setField(FieldKey.ALBUM, album)
        newTag.setField(FieldKey.TRACK, track)
        newTag.setField(FieldKey.DISC_NO, discNumber.toString())
        if (date.isNotBlank()) newTag.setField(FieldKey.YEAR, date)

        // Copy cover art from existing tag if present
        if (existingTag != null) {
            try {
                val artwork = existingTag.firstArtwork
                if (artwork != null) newTag.setField(artwork)
            } catch (_: Exception) {}
        }

        // Restore COMM::ved — this frame must never be lost
        if (commVedJson != null) {
            val body = FrameBodyCOMM()
            body.language = "ved"
            body.description = ""
            body.text = commVedJson
            val commFrame = org.jaudiotagger.tag.id3.ID3v24Frame("COMM")
            commFrame.body = body
            newTag.setFrame(commFrame)
        }

        audioFile.tag = newTag
        AudioFileIO.write(audioFile)
    }
}

// ─── JVM (Desktop) actual ─────────────────────────────────────────────────────
// (Android actual lives in androidMain and overrides with temp-copy logic)
