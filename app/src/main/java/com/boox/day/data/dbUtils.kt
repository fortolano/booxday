package com.boox.day.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.io.createFileFromContentUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Returns the database directory in internal app storage.
 * This location does NOT require MANAGE_EXTERNAL_STORAGE permission.
 * Path: /data/data/com.boox.day/files/booxdaydb/
 */
fun getDbDir(context: Context): File {
    val dbDir = File(context.filesDir, "booxdaydb")
    if (!dbDir.exists()) {
        dbDir.mkdirs()
    }
    return dbDir
}

fun ensureImagesFolder(context: Context): File {
    val dbDir = getDbDir(context)
    val imagesDir = File(dbDir, "images")
    if (!imagesDir.exists()) {
        imagesDir.mkdirs()
    }
    // Create .nomedia file to hide these internal images from Gallery/Recents
    val noMedia = File(imagesDir, ".nomedia")
    if (!noMedia.exists()) {
        try {
            noMedia.createNewFile()
        } catch (e: Exception) {
            // Ignore error if file creation fails
        }
    }
    return imagesDir
}

fun ensureBackgroundsFolder(context: Context): File {
    val dbDir = getDbDir(context)
    val backgroundsDir = File(dbDir, "backgrounds")
    if (!backgroundsDir.exists()) {
        backgroundsDir.mkdirs()
    }
    // Create .nomedia file to hide backgrounds from Gallery/Recents
    val noMedia = File(backgroundsDir, ".nomedia")
    if (!noMedia.exists()) {
        try {
            noMedia.createNewFile()
        } catch (e: Exception) {
            // Ignore error if file creation fails
        }
    }
    return backgroundsDir
}

fun ensurePreviewsFullFolder(context: Context): File {
    val dir = File(context.filesDir, "pages/previews/full")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

private const val CLEANUP_TAG = "PreviewCacheCleanup"

/**
 * Cleans up old preview cache files from disk to prevent unbounded storage growth.
 *
 * Uses a hybrid approach:
 * 1. First removes files older than [maxAgeDays]
 * 2. Then removes oldest files if total size exceeds [maxSizeMB]
 *
 * @param context Android context for file access
 * @param maxAgeDays Remove preview files older than this (default: 30 days)
 * @param maxSizeMB Maximum total disk usage for previews (default: 100 MB)
 * @return Pair of (filesDeleted, bytesFreed)
 */
fun cleanupPreviewCache(
    context: Context,
    maxAgeDays: Int = 30,
    maxSizeMB: Int = 100
): Pair<Int, Long> {
    val dir = ensurePreviewsFullFolder(context)
    val files = dir.listFiles()?.toMutableList() ?: return Pair(0, 0L)

    if (files.isEmpty()) return Pair(0, 0L)

    val startTime = System.currentTimeMillis()
    val cutoffTime = startTime - (maxAgeDays * 24 * 60 * 60 * 1000L)
    val maxSizeBytes = maxSizeMB * 1024 * 1024L

    var filesDeleted = 0
    var bytesFreed = 0L

    // Phase 1: Remove files older than TTL
    val iterator = files.iterator()
    while (iterator.hasNext()) {
        val file = iterator.next()
        if (file.lastModified() < cutoffTime) {
            val size = file.length()
            if (file.delete()) {
                bytesFreed += size
                filesDeleted++
                iterator.remove()
            }
        }
    }

    // Phase 2: If still over size limit, remove oldest files first
    files.sortBy { it.lastModified() }
    var totalSize = files.sumOf { it.length() }

    for (file in files) {
        if (totalSize <= maxSizeBytes) break
        val size = file.length()
        if (file.delete()) {
            totalSize -= size
            bytesFreed += size
            filesDeleted++
        }
    }

    if (filesDeleted > 0) {
        Log.i(CLEANUP_TAG, "Cleaned up $filesDeleted preview files, freed ${bytesFreed / 1024}KB " +
                "(took ${System.currentTimeMillis() - startTime}ms)")
    } else {
        Log.d(CLEANUP_TAG, "No cleanup needed (${files.size} files, ${totalSize / 1024}KB)")
    }

    return Pair(filesDeleted, bytesFreed)
}


fun copyBackgroundToDatabase(context: Context, fileUri: Uri, subfolder: String): File {
    var outputDir = ensureBackgroundsFolder(context)
    outputDir = File(outputDir, subfolder)
    if (!outputDir.exists())
        outputDir.mkdirs()
    return createFileFromContentUri(context, fileUri, outputDir)
}

fun copyImageToDatabase(context: Context, fileUri: Uri, subfolder: String? = null): File {
    var outputDir = ensureImagesFolder(context)
    if (subfolder != null) {
        outputDir = File(outputDir, subfolder)
        if (!outputDir.exists())
            outputDir.mkdirs()
    }
    return createFileFromContentUri(context, fileUri, outputDir)
}

/**
 * Saves a Bitmap directly to the images folder.
 * Used for generated images like tables.
 *
 * @param context Android context for file access
 * @param bitmap The bitmap to save
 * @param filename The filename (should include .png extension)
 * @return The saved File
 */
fun saveBitmapToImagesFolder(context: Context, bitmap: Bitmap, filename: String): File {
    val outputDir = ensureImagesFolder(context)
    val file = File(outputDir, filename)
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return file
}


// TODO move this to repository
// Phase 3 (Oct 2026): runs on IO — it was called from a click and blocked the UI thread with
// runBlocking. The quick-nav list is updated through setAppSettings (writing the stored settings
// directly let the next save from memory put the deleted page back).
fun deletePage(context: Context, pageId: String) {
    val appContext = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        val appRepository = AppRepository(appContext)
        val page = appRepository.pageRepository.getById(pageId) ?: return@launch

        // remove from book
        if (page.notebookId != null) {
            appRepository.bookRepository.removePage(page.notebookId, pageId)
        }

        // remove from quick nav
        val settings = GlobalAppSettings.current
        if (settings.quickNavPages.contains(pageId)) {
            appRepository.kvProxy.setAppSettings(settings.copy(quickNavPages = settings.quickNavPages - pageId))
        }

        appRepository.pageRepository.delete(pageId)
        File(appContext.filesDir, "pages/previews/thumbs/$pageId").delete()
        // full previews are named "<pageId>-sy<scroll>.png" (and "<pageId>" in older versions)
        ensurePreviewsFullFolder(appContext).listFiles { f -> f.name.startsWith(pageId) }?.forEach { it.delete() }
    }
}