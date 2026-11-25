package com.boox.day.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.boox.day.data.ensureImagesFolder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Serializable
data class BackupArchiveManifest(
    val archiveVersion: Int = BackupArchive.CURRENT_ARCHIVE_VERSION,
    val backupFile: String = BackupArchive.BACKUP_JSON_ENTRY,
    val media: List<ArchivedMediaEntry> = emptyList(),
    val warnings: List<String> = emptyList()
)

@Serializable
data class ArchivedMediaEntry(
    val entryPath: String,
    val relativeFileName: String,
    val kind: String = "image",
    val sizeBytes: Long = 0L
)

/**
 * Versioned ZIP backup archive helper (Phase 3E, Decision D2 / H9):
 * - Bundles `manifest.json`, `backup.json`, and app-owned memo images (`media/images/<name>`).
 * - Spools restore inputs once to a private temp file so validation, DB import, and media extraction
 *   always operate on the exact same immutable snapshot.
 * - Auto-detects ZIP archives vs legacy JSON-only backups by magic bytes.
 */
object BackupArchive {
    private const val TAG = "BackupArchive"
    const val CURRENT_ARCHIVE_VERSION = 1
    const val MANIFEST_ENTRY = "manifest.json"
    const val BACKUP_JSON_ENTRY = "backup.json"
    const val IMAGES_PREFIX = "media/images/"
    private const val MAX_MEDIA_FILE_BYTES = 50L * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** True when [file] starts with a standard ZIP local-file or empty-archive header. */
    fun isZipFile(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        return runCatching {
            file.inputStream().use { input ->
                val sig = ByteArray(4)
                if (input.read(sig) < 4) return false
                sig[0] == 0x50.toByte() && sig[1] == 0x4B.toByte() &&
                    ((sig[2] == 0x03.toByte() && sig[3] == 0x04.toByte()) ||
                        (sig[2] == 0x05.toByte() && sig[3] == 0x06.toByte()))
            }
        }.getOrDefault(false)
    }

    /**
     * Copies [source] once to a private temporary snapshot file in cacheDir so multi-pass
     * validation + import never re-opens a SAF URI or mutable external file.
     */
    fun spoolToTempFile(context: Context, source: () -> InputStream): File {
        val dir = File(context.applicationContext.cacheDir, "backup-spool").apply { mkdirs() }
        val temp = File(dir, "restore-${UUID.randomUUID()}.tmp")
        try {
            source().use { input ->
                FileOutputStream(temp).buffered().use { output ->
                    input.copyTo(output)
                }
            }
            return temp
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    /**
     * Resolves an image URI/path strictly inside BooxDay's app-owned `booxdaydb/images/` directory.
     * Returns null for external `content://` URIs, relative traversal paths, or files outside app storage.
     */
    fun resolveOwnedImageFile(context: Context, uriString: String?): File? {
        val raw = uriString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val path = when {
            raw.startsWith("file://", ignoreCase = true) -> runCatching { Uri.parse(raw).path }.getOrNull()
            "://" in raw -> return null  // content://, http://, etc. — external URI
            else -> raw
        } ?: return null

        val imagesDir = ensureImagesFolder(context).canonicalFile
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val parentPrefix = imagesDir.path + File.separator
        return if (candidate.path.startsWith(parentPrefix)) candidate else null
    }

    /**
     * Extracts the safe single filename (no directory separators or `..`) if [uriString] points to an
     * owned image path (including from another device's `.../booxdaydb/images/<name>`).
     */
    fun extractOwnedImageFileName(uriString: String?): String? {
        val raw = uriString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val path = when {
            raw.startsWith("file://", ignoreCase = true) -> runCatching { Uri.parse(raw).path }.getOrNull()
            "://" in raw -> return null
            else -> raw
        } ?: return null
        val normalized = path.replace('\\', '/')
        val marker = "/booxdaydb/images/"
        val idx = normalized.lastIndexOf(marker)
        if (idx < 0) return null
        val fileName = normalized.substring(idx + marker.length)
        return fileName.takeIf { isSafeSingleFileName(it) }
    }

    private fun isSafeSingleFileName(name: String): Boolean =
        name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            '/' !in name &&
            '\\' !in name &&
            '\u0000' !in name

    /** Writes a versioned ZIP archive containing `manifest.json`, `backup.json`, and [ownedImages]. */
    fun writeZipArchive(
        jsonTempFile: File,
        targetZipPart: File,
        ownedImages: Map<String, File>,
        warnings: List<String>
    ) {
        val mediaEntries = ownedImages.entries
            .filter { (name, file) -> isSafeSingleFileName(name) && file.isFile && file.canRead() }
            .sortedBy { it.key }
            .map { (name, file) ->
                ArchivedMediaEntry(
                    entryPath = "$IMAGES_PREFIX$name",
                    relativeFileName = name,
                    kind = "image",
                    sizeBytes = file.length()
                )
            }
        val manifest = BackupArchiveManifest(
            archiveVersion = CURRENT_ARCHIVE_VERSION,
            backupFile = BACKUP_JSON_ENTRY,
            media = mediaEntries,
            warnings = warnings
        )

        ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZipPart))).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(json.encodeToString(BackupArchiveManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(BACKUP_JSON_ENTRY))
            jsonTempFile.inputStream().buffered().use { it.copyTo(zip) }
            zip.closeEntry()

            for (entry in mediaEntries) {
                val file = ownedImages[entry.relativeFileName] ?: continue
                zip.putNextEntry(ZipEntry(entry.entryPath))
                file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Reads `manifest.json` from a ZIP archive, or returns null for legacy JSON backups. */
    fun readManifest(spooledFile: File): BackupArchiveManifest? {
        if (!isZipFile(spooledFile)) return null
        ZipFile(spooledFile).use { zip ->
            val entry = zip.getEntry(MANIFEST_ENTRY) ?: return null
            val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val manifest = json.decodeFromString(BackupArchiveManifest.serializer(), text)
            if (manifest.archiveVersion > CURRENT_ARCHIVE_VERSION) {
                throw IOException("Backup archive version ${manifest.archiveVersion} is newer than supported version $CURRENT_ARCHIVE_VERSION")
            }
            return manifest
        }
    }

    /** Opens the backup JSON stream from either a ZIP archive or a legacy raw JSON file. */
    fun openJsonStream(spooledFile: File): InputStream {
        if (!isZipFile(spooledFile)) return spooledFile.inputStream()
        val zip = ZipFile(spooledFile)
        try {
            val manifestEntry = zip.getEntry(MANIFEST_ENTRY)
            var jsonEntryName = BACKUP_JSON_ENTRY
            if (manifestEntry != null) {
                val text = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val manifest = json.decodeFromString(BackupArchiveManifest.serializer(), text)
                if (manifest.archiveVersion > CURRENT_ARCHIVE_VERSION) {
                    throw IOException("Backup archive version ${manifest.archiveVersion} is newer than supported version $CURRENT_ARCHIVE_VERSION")
                }
                if (isSafeSingleFileName(manifest.backupFile)) {
                    jsonEntryName = manifest.backupFile
                }
            }
            val jsonEntry = zip.getEntry(jsonEntryName)
                ?: throw IOException("Backup archive is missing $jsonEntryName")
            val entryStream = zip.getInputStream(jsonEntry)
            return object : FilterInputStream(entryStream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        zip.close()
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { zip.close() }
            throw e
        }
    }

    /** Reads the raw backup JSON text from either a `.zip` archive or legacy `.json` file. */
    fun readBackupJsonText(file: File): String =
        openJsonStream(file).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Staged media extracted from a ZIP archive, ready to commit after the DB transaction succeeds. */
    class ExtractedMedia(
        private val stagingDir: File,
        val fileNames: Set<String>
    ) {
        fun targetUriFor(context: Context, fileName: String): String =
            File(ensureImagesFolder(context), fileName).toUri().toString()

        fun commit(context: Context) {
            val imagesDir = ensureImagesFolder(context)
            try {
                for (name in fileNames) {
                    val src = File(stagingDir, name)
                    if (!src.isFile) continue
                    val dst = File(imagesDir, name)
                    if (!src.renameTo(dst)) {
                        src.copyTo(dst, overwrite = true)
                        src.delete()
                    }
                }
            } finally {
                stagingDir.deleteRecursively()
            }
        }

        fun discard() {
            stagingDir.deleteRecursively()
        }
    }

    /**
     * Validates and extracts all bundled `media/images/<name>` entries from a ZIP archive into a
     * private staging directory. Rejects path-traversal entries. Returns null for legacy JSON files.
     */
    fun extractBundledImagesToStaging(context: Context, spooledFile: File): ExtractedMedia? {
        if (!isZipFile(spooledFile)) return null
        val imagesDir = ensureImagesFolder(context)
        val stagingDir = File(imagesDir, ".restore-stage-${UUID.randomUUID()}").apply { mkdirs() }
        val extracted = mutableSetOf<String>()
        try {
            ZipFile(spooledFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (".." in name || name.startsWith("/") || '\\' in name) {
                        throw IOException("Backup archive contains unsafe path entry")
                    }
                    if (!name.startsWith(IMAGES_PREFIX)) continue
                    val fileName = name.removePrefix(IMAGES_PREFIX)
                    if (!isSafeSingleFileName(fileName)) {
                        throw IOException("Backup archive contains invalid media filename")
                    }
                    val target = File(stagingDir, fileName)
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(target).buffered().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                val read = input.read(buf)
                                if (read < 0) break
                                total += read
                                if (total > MAX_MEDIA_FILE_BYTES) {
                                    throw IOException("Bundled image exceeds 50 MB safety limit")
                                }
                                output.write(buf, 0, read)
                            }
                        }
                    }
                    extracted += fileName
                }
            }
            return ExtractedMedia(stagingDir, extracted)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract backup media: ${e.javaClass.simpleName}")
            stagingDir.deleteRecursively()
            throw e
        }
    }
}
