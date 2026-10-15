package com.boox.day.booxnotes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.boox.day.data.db.BooxNoteIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class BooxPreviewSource {
    NATIVE,
    STROKE_FALLBACK,
    RESOURCE_TEXT
}

data class BooxPreviewDiagnostics(
    val reasonCode: String,
    val message: String,
    val debugSummary: String? = null,
    val attemptedPageIds: List<String> = emptyList()
)

sealed interface BooxNotePreviewResult {
    val totalPages: Int
    val currentPageIndex: Int
    val pageIds: List<String>
    val diagnostics: BooxPreviewDiagnostics?

    data class ImagePreview(
        val bitmap: Bitmap,
        val pageId: String,
        val source: BooxPreviewSource,
        override val totalPages: Int,
        override val currentPageIndex: Int,
        override val pageIds: List<String>,
        override val diagnostics: BooxPreviewDiagnostics? = null
    ) : BooxNotePreviewResult

    data class TextPreview(
        val text: String,
        val source: BooxPreviewSource,
        override val totalPages: Int = 0,
        override val currentPageIndex: Int = 0,
        override val pageIds: List<String> = emptyList(),
        override val diagnostics: BooxPreviewDiagnostics? = null
    ) : BooxNotePreviewResult

    data class Unavailable(
        override val totalPages: Int,
        override val currentPageIndex: Int,
        override val pageIds: List<String>,
        override val diagnostics: BooxPreviewDiagnostics
    ) : BooxNotePreviewResult
}

class BooxNotePreviewCoordinator(
    private val ksyncBase: String = BooxNative.KSYNC_BASE_PATH
) {
    suspend fun loadPreview(
        note: BooxNoteIndex,
        requestedPageIndex: Int
    ): BooxNotePreviewResult = withContext(Dispatchers.IO) {
        val storedPageIds = parsePageIds(note.pageIdsJson)
        val livePages = BooxNative.getOnDevicePages(note.uniqueId)
        val recoveredPageIds = recoverPageIds(
            noteId = note.uniqueId,
            storedPageIds = storedPageIds,
            livePages = livePages
        )

        val totalPages = recoveredPageIds.size
        val targetPageIndex = if (totalPages > 0) {
            requestedPageIndex.coerceIn(0, totalPages - 1)
        } else {
            0
        }

        val attemptOrder = if (recoveredPageIds.isEmpty()) {
            emptyList()
        } else {
            val selectedPageId = recoveredPageIds[targetPageIndex]
            listOf(selectedPageId) + recoveredPageIds.filter { it != selectedPageId }
        }

        var lastDiagnostics: BooxPreviewDiagnostics? = null

        for (pageId in attemptOrder) {
            val resolvedIndex = recoveredPageIds.indexOf(pageId).coerceAtLeast(0)
            var nativeWasBlank = false

            tryNativeRender(note.uniqueId, pageId) { nativeWasBlank = true }?.let { bitmap ->
                return@withContext BooxNotePreviewResult.ImagePreview(
                    bitmap = bitmap,
                    pageId = pageId,
                    source = BooxPreviewSource.NATIVE,
                    totalPages = totalPages,
                    currentPageIndex = resolvedIndex,
                    pageIds = recoveredPageIds
                )
            }

            renderStrokeFallback(note.uniqueId, pageId)?.let { bitmap ->
                return@withContext BooxNotePreviewResult.ImagePreview(
                    bitmap = bitmap,
                    pageId = pageId,
                    source = BooxPreviewSource.STROKE_FALLBACK,
                    totalPages = totalPages,
                    currentPageIndex = resolvedIndex,
                    pageIds = recoveredPageIds,
                    diagnostics = lastDiagnostics
                )
            }

            lastDiagnostics = buildDiagnostics(
                noteId = note.uniqueId,
                pageId = pageId,
                attemptedPageIds = attemptOrder,
                nativeWasBlank = nativeWasBlank,
                livePages = livePages
            )
        }

        val textContent = fetchTextContent(note.uniqueId)
        if (!textContent.isNullOrBlank()) {
            val message = if (recoveredPageIds.isEmpty()) {
                "This note has typed text but no drawable handwriting pages."
            } else {
                "Showing typed text because the handwriting preview could not be rendered."
            }
            return@withContext BooxNotePreviewResult.TextPreview(
                text = textContent,
                source = BooxPreviewSource.RESOURCE_TEXT,
                diagnostics = BooxPreviewDiagnostics(
                    reasonCode = "TEXT_ONLY_NOTE",
                    message = message,
                    debugSummary = lastDiagnostics?.debugSummary,
                    attemptedPageIds = attemptOrder
                )
            )
        }

        val fallbackDiagnostics = lastDiagnostics ?: BooxPreviewDiagnostics(
            reasonCode = if (recoveredPageIds.isEmpty()) "NO_PAGE_IDS" else "NO_RENDERABLE_CONTENT",
            message = if (recoveredPageIds.isEmpty()) {
                "No valid Boox page files were found for this note."
            } else {
                "The note was found, but no drawable content could be rendered."
            },
            attemptedPageIds = attemptOrder
        )

        BooxNotePreviewResult.Unavailable(
            totalPages = totalPages,
            currentPageIndex = targetPageIndex,
            pageIds = recoveredPageIds,
            diagnostics = fallbackDiagnostics
        )
    }

    private fun recoverPageIds(
        noteId: String,
        storedPageIds: List<String>,
        livePages: List<BooxNative.OnDevicePage>
    ): List<String> {
        return buildList {
            addAll(storedPageIds)
            addAll(livePages.map { it.pageId })
            addAll(BooxNative.getPageIdsFromPointDirectory(noteId))
            addAll(BooxNative.getPageIdsFromShapeDirectory(noteId))
        }.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun tryNativeRender(
        noteId: String,
        pageId: String,
        onBlankBitmap: () -> Unit
    ): Bitmap? {
        return try {
            val booxBitmap = BooxNative.renderPageOnDevice(ksyncBase, noteId, pageId)
            if (booxBitmap == null || !booxBitmap.isValid()) {
                return null
            }

            val bitmap = Bitmap.createBitmap(
                booxBitmap.width,
                booxBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(booxBitmap.buffer))

            if (isLikelyBlank(bitmap)) {
                onBlankBitmap()
                bitmap.recycle()
                null
            } else {
                scaleBitmapForPreview(bitmap)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Native preview failed for $noteId/$pageId: ${t.message}")
            null
        }
    }

    private fun renderStrokeFallback(noteId: String, pageId: String): Bitmap? {
        return try {
            val strokes = BooxNative.loadPageStrokes(noteId, pageId)
                .filter { it.points.isNotEmpty() }
            if (strokes.isEmpty()) {
                return null
            }

            val allPoints = strokes.flatMap { it.points }
            val minX = allPoints.minOf { it.x }
            val maxX = allPoints.maxOf { it.x }
            val minY = allPoints.minOf { it.y }
            val maxY = allPoints.maxOf { it.y }

            val contentWidth = (maxX - minX).coerceAtLeast(1f)
            val contentHeight = (maxY - minY).coerceAtLeast(1f)
            val padding = 32f
            val maxWidth = 840f
            val maxHeight = 1120f
            val scale = min(
                maxWidth / (contentWidth + padding * 2f),
                maxHeight / (contentHeight + padding * 2f)
            ).coerceAtMost(1f)

            val bitmapWidth = max(320, ceil((contentWidth + padding * 2f) * scale).toInt())
            val bitmapHeight = max(320, ceil((contentHeight + padding * 2f) * scale).toInt())

            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = (3.5f * scale.coerceAtLeast(0.75f)).coerceAtLeast(2.5f)
            }
            val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }

            strokes.forEach { stroke ->
                if (stroke.points.size == 1) {
                    val point = stroke.points.first()
                    canvas.drawCircle(
                        ((point.x - minX) * scale) + padding,
                        ((point.y - minY) * scale) + padding,
                        strokePaint.strokeWidth,
                        pointPaint
                    )
                } else {
                    for (i in 1 until stroke.points.size) {
                        val prev = stroke.points[i - 1]
                        val next = stroke.points[i]
                        canvas.drawLine(
                            ((prev.x - minX) * scale) + padding,
                            ((prev.y - minY) * scale) + padding,
                            ((next.x - minX) * scale) + padding,
                            ((next.y - minY) * scale) + padding,
                            strokePaint
                        )
                    }
                }
            }

            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Stroke fallback failed for $noteId/$pageId: ${e.message}")
            null
        }
    }

    private fun fetchTextContent(noteId: String): String? {
        return try {
            BooxNative.getNoteTextContentOnDevice(ksyncBase, noteId)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "Text preview failed for $noteId: ${t.message}")
            null
        }
    }

    private fun buildDiagnostics(
        noteId: String,
        pageId: String,
        attemptedPageIds: List<String>,
        nativeWasBlank: Boolean,
        livePages: List<BooxNative.OnDevicePage>
    ): BooxPreviewDiagnostics {
        val livePage = livePages.firstOrNull { it.pageId == pageId }
        val debugJson = try {
            BooxNative.debugRenderOnDevice(ksyncBase, noteId, pageId)
        } catch (t: Throwable) {
            null
        }

        val debug = debugJson?.let {
            try {
                JSONObject(it)
            } catch (_: Exception) {
                null
            }
        }

        val shapeFilesFound = countJsonArray(debug, "shape_files_found")
        val pointFilesFound = countJsonArray(debug, "point_files_found")
        val shapeGroupsParsed = debug?.optInt("shape_groups_parsed", 0) ?: 0
        val pointsFilesParsed = debug?.optInt("points_files_parsed", 0) ?: 0
        val strokesRendered = debug?.optInt("strokes_rendered", 0) ?: 0
        val errors = jsonArrayToStrings(debug?.optJSONArray("errors"))
        val pointDirExists = debug?.optBoolean("point_dir_exists", livePage?.hasPoints == true)
            ?: (livePage?.hasPoints == true)

        val reasonCode = when {
            nativeWasBlank -> "BLANK_NATIVE_BITMAP"
            livePage == null && attemptedPageIds.isNotEmpty() -> "BAD_PAGE_ID"
            !pointDirExists || pointFilesFound == 0 -> "NO_POINT_FILES"
            shapeFilesFound == 0 && (livePage?.hasShapes != true) -> "BAD_PAGE_ID"
            shapeGroupsParsed > 0 && pointsFilesParsed > 0 && strokesRendered == 0 -> "SHAPE_POINTS_MISMATCH"
            errors.isNotEmpty() -> "NATIVE_RENDER_ERROR"
            else -> "NO_RENDERABLE_CONTENT"
        }

        val message = when (reasonCode) {
            "BLANK_NATIVE_BITMAP" -> "The page rendered as a blank bitmap, so the app treated it as no visible content."
            "BAD_PAGE_ID" -> "The stored page ID did not match a readable page on the device."
            "NO_POINT_FILES" -> "No stroke point files were found for this page."
            "SHAPE_POINTS_MISMATCH" -> "The page has shape metadata, but it did not match any stroke data."
            "NATIVE_RENDER_ERROR" -> errors.firstOrNull() ?: "Native rendering failed for this page."
            else -> "The note exists, but no renderable Boox content was found for this page."
        }

        val debugSummary = buildString {
            append("page=")
            append(pageId)
            append("; shapeFiles=")
            append(shapeFilesFound)
            append("; pointFiles=")
            append(pointFilesFound)
            append("; shapeGroups=")
            append(shapeGroupsParsed)
            append("; pointsParsed=")
            append(pointsFilesParsed)
            append("; strokesRendered=")
            append(strokesRendered)
            if (errors.isNotEmpty()) {
                append("; errors=")
                append(errors.joinToString(" | "))
            }
        }

        Log.w(TAG, "Preview unavailable for $noteId/$pageId ($reasonCode): $debugSummary")

        return BooxPreviewDiagnostics(
            reasonCode = reasonCode,
            message = message,
            debugSummary = debugSummary,
            attemptedPageIds = attemptedPageIds
        )
    }

    private fun scaleBitmapForPreview(bitmap: Bitmap): Bitmap {
        val maxWidth = 840
        val maxHeight = 1120
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
            return bitmap
        }

        val scale = min(
            maxWidth / bitmap.width.toFloat(),
            maxHeight / bitmap.height.toFloat()
        )

        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * scale).roundToInt()),
            max(1, (bitmap.height * scale).roundToInt()),
            true
        )

        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }
        return scaledBitmap
    }

    private fun isLikelyBlank(bitmap: Bitmap): Boolean {
        val stepX = max(1, bitmap.width / 96)
        val stepY = max(1, bitmap.height / 96)
        var nonWhiteSamples = 0

        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) > 0 &&
                    (Color.red(pixel) < 248 || Color.green(pixel) < 248 || Color.blue(pixel) < 248)
                ) {
                    nonWhiteSamples++
                    if (nonWhiteSamples >= 3) {
                        return false
                    }
                }
            }
        }

        return true
    }

    private fun parsePageIds(pageIdsJson: String): List<String> {
        return try {
            val json = JSONArray(pageIdsJson)
            buildList {
                for (i in 0 until json.length()) {
                    val pageId = json.optString(i).trim()
                    if (pageId.isNotEmpty()) {
                        add(pageId)
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun countJsonArray(json: JSONObject?, key: String): Int =
        json?.optJSONArray(key)?.length() ?: 0

    private fun jsonArrayToStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BooxPreviewCoordinator"
    }
}
