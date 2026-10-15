package com.boox.day.io

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.boox.day.SCREEN_WIDTH
import com.boox.day.TAG
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.utils.Timing
import com.boox.day.utils.ensureNotMainThread
import android.util.Log
import com.boox.day.utils.TaggedLog
import java.io.File


private val log = TaggedLog("renderFromFile")

/**
 * Converts a URI to a Bitmap using the provided [context] and [uri].
 * Robust implementation that handles large images by downsampling.
 *
 * @param context The context used to access the content resolver.
 * @param uri The URI of the image to be converted to a Bitmap.
 * @return The Bitmap representation of the image, or null if conversion fails.
 */
fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val resolver = context.contentResolver

        // 1. Decode bounds first to check size
        var inputStream = resolver.openInputStream(uri)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        if (options.outWidth == -1 || options.outHeight == -1) {
            Log.e(TAG, "uriToBitmap: Failed to decode bounds for $uri")
            return null
        }

        // 2. Calculate inSampleSize to avoid OOM
        // Max dimension: 2500px (enough for Tab Ultra C 2480px height)
        val maxDim = 2500
        options.inSampleSize = calculateInSampleSize(options, maxDim, maxDim)
        options.inJustDecodeBounds = false
        // Ensure the bitmap is mutable so we can draw on it later if needed
        options.inMutable = true
        // Prefer ARGB_8888 for quality
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        // 3. Decode actual bitmap
        inputStream = resolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        if (bitmap == null) {
            Log.e(TAG, "uriToBitmap: BitmapFactory returned null for $uri")
        }
        bitmap
    } catch (e: SecurityException) {
        Log.e(TAG, "SecurityException: ${e.message}", e)
        null
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error in uriToBitmap: ${e.message}", e)
        null
    }
}

/**
 * Calculate inSampleSize for scaling down images
 */
private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}


fun loadBackgroundBitmap(filePath: String, pageNumber: Int, scale: Float): Bitmap? {
    // TODO: it's very slow, needs to be changed for better tool
    if (filePath.isEmpty()) return null
    ensureNotMainThread("loadBackgroundBitmap")
    log.v("Reloading background, path: $filePath, scale: $scale")
    val file = File(filePath)
    if (!file.exists()) {
        log.v("getOrLoadBackground: File does not exist at path: $filePath")
        return null
    }
    val timer = Timing("loadBackgroundBitmap")
    if (!filePath.endsWith(".pdf", ignoreCase = true)) {
        try {
            timer.step("decode bitmap image")
            val result = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            if (result == null)
                log.e("loadBackgroundBitmap: result is null, couldn't decode image, file name ends with ${filePath.takeLast(4)}")
            timer.end("loaded background")
            return result?.asAndroidBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "getOrLoadBackground: Error loading background - ${e.message}", e)
        }
    }
    val targetWidth = SCREEN_WIDTH * (scale.coerceAtMost(2f))
    timer.step("start android Pdf")
    log.d("Rendering using ${if (GlobalAppSettings.current.muPdfRendering) "MuPdf" else "Android Pdf"}")
    val newBitmap: Bitmap? = if (GlobalAppSettings.current.muPdfRendering)
        renderPdfPageMuPdf(filePath, pageNumber, targetWidth.toInt(), resolutionModifier = 1.5f)
    else
        renderPdfPageAndroid(file, pageNumber, targetWidth.toInt(), resolutionModifier = 1.2f)
    timer.end("loaded background")
    return newBitmap
}