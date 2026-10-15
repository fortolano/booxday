package com.boox.day.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * After a successful restore, everything kept outside the database still describes the old data:
 * the page preview/thumbnail files on disk (read by thumbnails, OCR/search and AI context) and
 * every in-memory cache (PageDataManager, repositories, settings).
 * Delete the previews and restart the process, so nothing stale is shown or written back.
 */
object PostRestore {
    private const val TAG = "PostRestore"

    fun invalidateCachesAndRestart(context: Context) {
        deletePagePreviews(context)
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val component = launch?.component
            if (component != null) {
                context.startActivity(Intent.makeRestartActivityTask(component))
            }
        } catch (e: Exception) {
            // The data is restored either way; the user then reopens the app by hand.
            Log.e(TAG, "Could not schedule restart after restore: ${e.message}", e)
        }
        Log.i(TAG, "Restore complete - restarting process")
        Runtime.getRuntime().exit(0)
    }

    /** Previews are regenerated from the restored strokes when each page is next shown. */
    fun deletePagePreviews(context: Context) {
        File(context.filesDir, "pages/previews").listFiles()?.forEach { entry ->
            if (entry.isDirectory) {
                entry.listFiles()?.forEach { it.delete() }
            } else {
                entry.delete()
            }
        }
    }
}
