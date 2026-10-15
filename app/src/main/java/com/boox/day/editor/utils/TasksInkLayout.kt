package com.boox.day.editor.utils

import android.content.Context
import com.boox.day.data.PageDataManager
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.KvProxy
import com.boox.day.data.db.PageRepository
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokeRepository
import com.boox.day.data.db.UNDECODABLE_STROKE_POINTS
import com.boox.day.data.ensurePreviewsFullFolder
import android.util.Log
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps Tasks ink on its rows when the Tasks page changes height (Oct 2026).
 *
 * Ink is stored in canvas pixels while a row is (canvas height − top offset) ÷ 10, so a taller
 * header, other compact rows or a different Boox DPI setting made old handwriting straddle row
 * lines. The canvas geometry the stored ink belongs to is kept in AppSettings; when the measured
 * geometry differs, every Tasks stroke is scaled vertically by the same ratio, so each one stays in
 * its row (and with its checkbox, due date and other row data). Memos ink has no rows: untouched.
 *
 * First run (nothing stored yet): compact screens — the Go 6, whose layout changed in this release —
 * get a one-time best-effort realignment: a line of handwriting that is split over two rows, or
 * reaches well into the next one, moves into the row it mostly occupies. Other screens only record
 * the geometry (their layout didn't change), so their data is never touched.
 *
 * Runs once per app start (a Boox DPI change restarts the app; layout changes come with updates),
 * on a canvas height that has been stable for a moment — never on a passing measurement.
 */
object TasksInkLayout {
    private const val TAG = "TasksInkLayout"
    private const val ROWS = 10

    @Volatile
    private var doneThisRun = false

    /** Returns true when ink was moved (the caller redraws the Tasks page). Call off the main thread. */
    fun ensureInkMatchesRows(
        context: Context,
        canvasHeight: Float,
        topOffset: Float,
        realignOnFirstRun: Boolean
    ): Boolean {
        if (doneThisRun || canvasHeight <= topOffset + ROWS) return false
        doneThisRun = true

        val settings = GlobalAppSettings.current
        val oldHeight = settings.tasksInkCanvasHeight
        val oldTop = settings.tasksInkTopOffset
        val moved = when {
            oldHeight <= 0f -> if (realignOnFirstRun) realignStraddlingLines(context, canvasHeight, topOffset) else false
            abs(oldHeight - canvasHeight) < 1f && abs(oldTop - topOffset) < 1f -> false
            else -> {
                val ratio = (canvasHeight - topOffset) / (oldHeight - oldTop)
                if (ratio !in 0.4f..2.5f) {
                    // Implausible change (a bad measurement?): leave the ink and the record alone
                    Log.w(TAG, "Canvas height $oldHeight -> $canvasHeight looks wrong; ink not moved")
                    return false
                }
                rescaleAll(context, oldHeight, oldTop, canvasHeight, topOffset)
            }
        }
        KvProxy(context).setAppSettings(
            GlobalAppSettings.current.copy(tasksInkCanvasHeight = canvasHeight, tasksInkTopOffset = topOffset)
        )
        return moved
    }

    // ---- Exact: scale every Tasks stroke from the old rows to the new rows ---------------------------

    private fun rescaleAll(context: Context, oldHeight: Float, oldTop: Float, newHeight: Float, newTop: Float): Boolean {
        val scale = (newHeight - newTop) / (oldHeight - oldTop)
        val mapY: (Float) -> Float = { y -> newTop + (y - oldTop) * scale }
        val strokeRepository = StrokeRepository(context)
        var moved = false
        for (pageId in AppDatabase.getDatabase(context).taskDao().getAllTaskPageIds()) {
            val strokes = strokeRepository.getAllForPage(pageId).filter { it.points !== UNDECODABLE_STROKE_POINTS }
            if (strokes.isEmpty()) continue
            replaceStrokes(context, pageId, strokes, strokes.map { it.withY(mapY) })
            moved = true
        }
        Log.i(TAG, "Rescaled Tasks ink: height $oldHeight -> $newHeight (moved=$moved)")
        return moved
    }

    // ---- One-time: move handwriting lines that straddle two rows into their main row ----------------

    private fun realignStraddlingLines(context: Context, canvasHeight: Float, topOffset: Float): Boolean {
        val database = AppDatabase.getDatabase(context)
        val strokeRepository = StrokeRepository(context)
        val rowHeight = (canvasHeight - topOffset) / ROWS
        var movedAny = false
        for (pageId in database.taskDao().getAllTaskPageIds()) {
            val strokes = strokeRepository.getAllForPage(pageId).filter { it.points !== UNDECODABLE_STROKE_POINTS }
            if (strokes.isEmpty()) continue
            val lines = groupIntoLines(strokes)
            val rowOf = { line: List<Stroke> -> mainRow(line, topOffset, rowHeight) }
            val takenRows = lines.filter { !straddles(it, canvasHeight, topOffset, rowHeight) }.mapTo(HashSet()) { rowOf(it) }

            val shifted = mutableListOf<Stroke>()
            val originals = mutableListOf<Stroke>()
            val touchedRows = HashSet<Int>()
            for (line in lines) {
                if (!straddles(line, canvasHeight, topOffset, rowHeight)) continue
                val top = line.minOf { it.top }
                val bottom = line.maxOf { it.bottom }
                if (bottom - top > rowHeight) continue          // taller than a row: leave it
                val row = rowOf(line)
                if (row !in 0 until ROWS || !takenRows.add(row)) continue   // row already holds other ink
                val rowTop = topOffset + row * rowHeight
                var dy = rowTop + rowHeight / 2f - (top + bottom) / 2f
                dy = dy.coerceIn(rowTop - top, rowTop + rowHeight - bottom)  // stay inside the row
                line.forEach { stroke ->
                    touchedRows.add(getDominantRowIndex(stroke, canvasHeight, ROWS, topOffset))
                    originals.add(stroke)
                    shifted.add(stroke.withY { y -> y + dy })
                }
                touchedRows.add(row)
            }
            if (shifted.isEmpty()) continue
            replaceStrokes(context, pageId, originals, shifted)
            // Recognized text of the rows involved no longer matches their ink: recognize again
            database.taskDao().getTasksForPageId(pageId)
                .filter { it.rowIndex in touchedRows && !it.recognizedText.isNullOrBlank() }
                .forEach { database.taskDao().setRecognizedTextById(it.id, null) }
            movedAny = true
        }
        Log.i(TAG, "One-time realignment of Tasks ink (moved=$movedAny)")
        return movedAny
    }

    /** Strokes that overlap vertically form one line of handwriting. */
    private fun groupIntoLines(strokes: List<Stroke>): List<List<Stroke>> {
        val lines = mutableListOf<MutableList<Stroke>>()
        var lineBottom = Float.NEGATIVE_INFINITY
        for (stroke in strokes.sortedBy { it.top }) {
            if (lines.isNotEmpty() && stroke.top < lineBottom) {
                lines.last().add(stroke)
                lineBottom = max(lineBottom, stroke.bottom)
            } else {
                lines.add(mutableListOf(stroke))
                lineBottom = stroke.bottom
            }
        }
        return lines
    }

    /** The row holding most of the line. */
    private fun mainRow(line: List<Stroke>, topOffset: Float, rowHeight: Float): Int {
        val top = line.minOf { it.top }
        val bottom = line.maxOf { it.bottom }
        val first = ((top - topOffset) / rowHeight).toInt().coerceIn(0, ROWS - 1)
        val last = ((bottom - topOffset) / rowHeight).toInt().coerceIn(0, ROWS - 1)
        return (first..last).maxByOrNull { row ->
            val rowTop = topOffset + row * rowHeight
            min(bottom, rowTop + rowHeight) - max(top, rowTop)
        } ?: first
    }

    /** Split over two rows (its strokes belong to different rows), or reaching well into the next row. */
    private fun straddles(line: List<Stroke>, canvasHeight: Float, topOffset: Float, rowHeight: Float): Boolean {
        val rows = line.mapTo(HashSet()) { getDominantRowIndex(it, canvasHeight, ROWS, topOffset) }
        if (rows.size > 1) return true
        val row = mainRow(line, topOffset, rowHeight)
        val rowTop = topOffset + row * rowHeight
        val outside = max(0f, rowTop - line.minOf { it.top }) + max(0f, line.maxOf { it.bottom } - (rowTop + rowHeight))
        return outside > rowHeight * 0.15f
    }

    // ---- Shared -----------------------------------------------------------------------------------------

    private fun Stroke.withY(mapY: (Float) -> Float): Stroke = copy(
        points = points.map { it.copy(y = mapY(it.y)) },
        top = mapY(top),
        bottom = mapY(bottom),
        updatedAt = Date()
    )

    /** Same stroke IDs, new positions: database (one transaction), memory cache, previews. */
    private fun replaceStrokes(context: Context, pageId: String, old: List<Stroke>, new: List<Stroke>) {
        val ids = old.map { it.id }
        StrokeRepository(context).migrateStrokes(ids, new)
        if (PageDataManager.isPageLoaded(pageId)) {
            PageDataManager.removeStrokes(ids, pageId)
            PageDataManager.addStrokes(new, pageId)
        }
        PageDataManager.invalidatePreviewCache(pageId)
        ensurePreviewsFullFolder(context).listFiles { file -> file.name.startsWith(pageId) }?.forEach { it.delete() }
        PageRepository(context).updateUpdatedAt(pageId)  // OCR sees the page changed
    }
}
