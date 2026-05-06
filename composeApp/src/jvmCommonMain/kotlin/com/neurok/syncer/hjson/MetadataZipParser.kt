package com.neurok.syncer.hjson

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

object MetadataZipParser {

    data class ZipHjsonEntry(val path: String, val content: String)

    /**
     * Extract all .hjson files from a metadata zip archive.
     *
     * Handles both "bare" zips (entries start directly with `DISC X …/`) and
     * zips that have a single top-level wrapper directory (e.g. from a GitHub
     * release of a repository).
     */
    fun parse(zipBytes: ByteArray): List<ZipHjsonEntry> {
        val result = mutableListOf<ZipHjsonEntry>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".hjson", ignoreCase = true)) {
                    val content = String(zis.readBytes(), Charsets.UTF_8)
                    val path = normalizePath(entry.name)
                    if (path.isNotBlank()) result.add(ZipHjsonEntry(path, content))
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return result
    }

    /**
     * Strip any leading top-level wrapper directory so that all returned paths
     * start with a `DISC …` component, matching the GitHub repo tree structure.
     */
    private fun normalizePath(rawPath: String): String {
        if (rawPath.startsWith("DISC", ignoreCase = false)) return rawPath
        val slashIdx = rawPath.indexOf('/')
        return if (slashIdx >= 0) rawPath.substring(slashIdx + 1) else rawPath
    }
}
