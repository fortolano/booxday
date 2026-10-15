package com.boox.day.io

import android.util.Log
import java.io.File
import java.util.zip.ZipFile

/**
 * Utility for extracting metadata from epub files without fully parsing them.
 * Uses ZipFile to read only the OPF metadata file for efficiency.
 */
object EpubMetadataExtractor {

    private const val TAG = "EpubMetadata"

    data class EpubMetadata(
        val title: String,
        val author: String?,
        val identifier: String?
    )

    /**
     * Extract metadata from an epub file.
     *
     * @param epubPath Absolute path to the epub file
     * @return EpubMetadata with title, author, identifier, or null if extraction fails
     */
    fun extractMetadata(epubPath: String): EpubMetadata? {
        val file = File(epubPath)
        if (!file.exists() || !file.canRead()) {
            Log.w(TAG, "Cannot read file: $epubPath")
            return null
        }

        return try {
            ZipFile(file).use { zip ->
                // 1. Read container.xml to find OPF path
                val containerEntry = zip.getEntry("META-INF/container.xml")
                if (containerEntry == null) {
                    Log.w(TAG, "No container.xml found in $epubPath")
                    return@use fallbackMetadata(epubPath)
                }

                val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                val opfPath = parseOpfPath(containerXml)

                // 2. Read OPF file for metadata
                val opfEntry = zip.getEntry(opfPath)
                if (opfEntry == null) {
                    Log.w(TAG, "No OPF file found at $opfPath in $epubPath")
                    return@use fallbackMetadata(epubPath)
                }

                val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()

                // 3. Extract metadata tags
                val title = extractTag(opfXml, "dc:title")
                    ?: extractTag(opfXml, "title")
                    ?: file.nameWithoutExtension

                val author = extractTag(opfXml, "dc:creator")
                    ?: extractTag(opfXml, "creator")

                val identifier = extractTag(opfXml, "dc:identifier")
                    ?: extractTag(opfXml, "identifier")

                EpubMetadata(title, author, identifier)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract metadata from $epubPath: ${e.message}")
            fallbackMetadata(epubPath)
        }
    }

    /**
     * Extract the OPF file path from container.xml
     */
    private fun parseOpfPath(containerXml: String): String {
        // Look for: full-path="OEBPS/content.opf" or full-path="content.opf"
        val regex = """full-path="([^"]+)"""".toRegex()
        val match = regex.find(containerXml)
        return match?.groupValues?.get(1) ?: "OEBPS/content.opf"
    }

    /**
     * Extract content from an XML tag.
     * Handles both <dc:title>Content</dc:title> and <title>Content</title> formats.
     */
    private fun extractTag(xml: String, tag: String): String? {
        // Try exact tag match first
        val exactRegex = """<$tag[^>]*>([^<]+)</$tag>""".toRegex(RegexOption.IGNORE_CASE)
        val exactMatch = exactRegex.find(xml)
        if (exactMatch != null) {
            return exactMatch.groupValues[1].trim()
        }

        // Try without namespace prefix
        val tagName = tag.substringAfter(":")
        val simpleRegex = """<$tagName[^>]*>([^<]+)</$tagName>""".toRegex(RegexOption.IGNORE_CASE)
        val simpleMatch = simpleRegex.find(xml)
        return simpleMatch?.groupValues?.get(1)?.trim()
    }

    /**
     * Fallback metadata when extraction fails - uses filename as title
     */
    private fun fallbackMetadata(epubPath: String): EpubMetadata {
        val file = File(epubPath)
        return EpubMetadata(
            title = file.nameWithoutExtension,
            author = null,
            identifier = null
        )
    }

    /**
     * Get file type extension from path
     */
    fun getFileType(filePath: String): String {
        return File(filePath).extension.lowercase()
    }

    /**
     * Get display name for a file - extracts epub title or uses filename
     */
    fun getDisplayName(filePath: String): String {
        val file = File(filePath)
        val extension = file.extension.lowercase()

        return when (extension) {
            "epub" -> extractMetadata(filePath)?.title ?: file.nameWithoutExtension
            else -> file.nameWithoutExtension
        }
    }
}
