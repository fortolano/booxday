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
 * Reads only the pixel dimensions (width, height) of an image URI without allocating pixel memory.
 */
fun decodeImageBounds(context: Context, uri: Uri): Pair<Int, Int>? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            Log.e(TAG, "decodeImageBounds: Failed to decode bounds for $uri")
            null
        } else {
            options.outWidth to options.outHeight
        }
    } catch (e: SecurityException) {
        Log.e(TAG, "SecurityException reading bounds: ${e.javaClass.simpleName}")
        null
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error in decodeImageBounds: ${e.javaClass.simpleName}")
        null
    }
}

/**
 * Fits an image of [originalWidth] x [originalHeight] inside [availableWidth] x [availableHeight]
 * while preserving aspect ratio and guaranteeing dimensions >= 1 px.
 */
internal fun fittedImageSize(
    originalWidth: Int,
    originalHeight: Int,
    availableWidth: Int,
    availableHeight: Int
): Pair<Int, Int> {
    val safeW = originalWidth.coerceAtLeast(1)
    val safeH = originalHeight.coerceAtLeast(1)
    val availW = availableWidth.coerceAtLeast(1)
    val availH = availableHeight.coerceAtLeast(1)
    if (safeW <= availW && safeH <= availH) return safeW to safeH
    val scale = minOf(availW.toFloat() / safeW, availH.toFloat() / safeH)
    return (safeW * scale).toInt().coerceAtLeast(1) to (safeH * scale).toInt().coerceAtLeast(1)
}

/**
 * Converts a URI to a Bitmap using the provided [context] and [uri].
 * Bounded single-pass downsampling caps both the longest edge ([maxDim]) and total decoded pixels
 * ([maxPixels]) so wide panoramas cannot cause OOM while full tablet resolution stays intact.
 *
 * @param context The context used to access the content resolver.
 * @param uri The URI of the image to be converted to a Bitmap.
 * @return The Bitmap representation of the image, or null if conversion fails.
 */
fun uriToBitmap(
    context: Context,
    uri: Uri,
    maxDim: Int = 2500,
    maxPixels: Long = 6_250_000L
): Bitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = decodeImageBounds(context, uri) ?: return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.first, bounds.second, maxDim, maxPixels)
            inJustDecodeBounds = false
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        if (bitmap == null) {
            Log.e(TAG, "uriToBitmap: BitmapFactory returned null for $uri")
        }
        bitmap
    } catch (e: SecurityException) {
        Log.e(TAG, "SecurityException: ${e.javaClass.simpleName}")
        null
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error in uriToBitmap: ${e.javaClass.simpleName}")
        null
    }
}

/**
 * Power-of-two sample size bounding BOTH dimensions to [maxDim] and total pixel count to [maxPixels].
 */
internal fun calculateInSampleSize(
    width: Int,
    height: Int,
    maxDim: Int = 2500,
    maxPixels: Long = 6_250_000L
): Int {
    if (width <= 0 || height <= 0) return 1
    var inSampleSize = 1
    while (
        (width / inSampleSize) > maxDim ||
        (height / inSampleSize) > maxDim ||
        (width.toLong() / inSampleSize) * (height.toLong() / inSampleSize) > maxPixels
    ) {
        inSampleSize *= 2
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