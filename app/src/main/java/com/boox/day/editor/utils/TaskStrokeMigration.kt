package com.boox.day.editor.utils

import android.graphics.Rect
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.editor.state.Operation
import java.util.Date
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Get the measured Tasks toolbar top offset in pixels.
 *
 * Reads the actual measured value from [GlobalAppSettings.tasksTopOffsetPx]
 * (set by MainScreen when the Tasks tab is displayed). Falls back to 1f
 * if not yet measured (cold start before Tasks tab is rendered).
 *
 * This ensures all stroke-to-row functions use the same top offset as
 * the visual overlay (TasksLinesOverlay), which uses TASKS_TOOLBAR_OFFSET.toPx().
 */
fun tasksTopOffset(): Float {
    val measured = GlobalAppSettings.tasksTopOffsetPx
    return if (measured > 0f) measured else 1f
}

/**
 * Utility functions for migrating strokes when moving tasks between dates.
 *
 * In the Tasks tab, all 10 task rows share ONE Page entity. Strokes are
 * identified by their Y coordinates to determine which row they belong to.
 * When moving a task, we need to:
 * 1. Extract strokes from the source row bounds
 * 2. Transform their coordinates to the target row
 * 3. Create new stroke records with new IDs and target pageId
 */

/**
 * Represents the Y-axis bounds of a task row in canvas coordinates.
 *
 * @param rowIndex The row index (0-9)
 * @param topY The top Y coordinate of the row
 * @param bottomY The bottom Y coordinate of the row
 */
data class TaskRowBounds(
    val rowIndex: Int,
    val topY: Float,
    val bottomY: Float
) {
    val height: Float get() = bottomY - topY

    /**
     * Convert to Android Rect for use with PageDataManager.getStrokesInRectangle()
     * Uses full canvas width (0 to Int.MAX_VALUE) since we only filter by Y.
     * Uses floor/ceil to avoid losing strokes at fractional boundaries.
     */
    fun toRect(): Rect = Rect(
        0,
        floor(topY).toInt(),
        Int.MAX_VALUE,
        ceil(bottomY).toInt()
    )
}

/**
 * Calculate the Y bounds for a specific task row.
 *
 * Row layout:
 * - TASKS_TOOLBAR_OFFSET (~1dp) at top
 * - Remaining height divided equally among rows
 * - Each row = (canvasHeight - topOffset) / rowCount
 *
 * @param rowIndex The row index (0-9)
 * @param canvasHeight The total canvas height in pixels
 * @param rowCount Total number of rows (typically 10)
 * @param topOffset Offset from top in pixels (TASKS_TOOLBAR_OFFSET, typically ~1px)
 * @return TaskRowBounds with calculated Y coordinates
 */
fun calculateRowBounds(
    rowIndex: Int,
    canvasHeight: Float,
    rowCount: Int = 10,
    topOffset: Float = tasksTopOffset()
): TaskRowBounds {
    require(rowIndex in 0 until rowCount) { "rowIndex must be 0 to ${rowCount - 1}" }
    require(canvasHeight > topOffset) { "canvasHeight must be greater than topOffset" }

    val visibleHeight = canvasHeight - topOffset
    val rowHeight = visibleHeight / rowCount
    val topY = topOffset + rowIndex * rowHeight
    val bottomY = topOffset + (rowIndex + 1) * rowHeight

    return TaskRowBounds(rowIndex, topY, bottomY)
}

/**
 * Determine which row "owns" a stroke based on maximum vertical overlap.
 * Each stroke belongs to exactly one row - the row where most of its
 * vertical extent falls. This eliminates "neighbor stealing" where
 * two rows could both claim the same stroke.
 *
 * @param stroke The stroke to assign
 * @param canvasHeight Total canvas height in pixels
 * @param rowCount Number of rows (default 10 for tasks)
 * @param topOffset Top offset in pixels matching TASKS_TOOLBAR_OFFSET.toPx()
 * @return The row index (0 to rowCount-1) that owns this stroke
 */
fun getDominantRowIndex(stroke: Stroke, canvasHeight: Float, rowCount: Int = 10, topOffset: Float = tasksTopOffset()): Int {
    val rowHeight = (canvasHeight - topOffset) / rowCount
    var maxOverlap = 0f
    var dominantRow = 0

    for (rowIdx in 0 until rowCount) {
        val rowTop = topOffset + rowIdx * rowHeight
        val rowBottom = topOffset + (rowIdx + 1) * rowHeight
        val overlapTop = maxOf(stroke.top, rowTop)
        val overlapBottom = minOf(stroke.bottom, rowBottom)
        val overlap = maxOf(0f, overlapBottom - overlapTop)

        if (overlap > maxOverlap) {
            maxOverlap = overlap
            dominantRow = rowIdx
        }
    }

    // Fallback for zero-height strokes (horizontal lines) or strokes outside canvas:
    // use center-Y position instead of defaulting to row 0
    if (maxOverlap <= 0f) {
        val centerY = (stroke.top + stroke.bottom) / 2f
        dominantRow = ((centerY - topOffset) / rowHeight).toInt().coerceIn(0, rowCount - 1)
    }

    return dominantRow
}

/**
 * Extract strokes that belong to a specific row based on dominant row assignment.
 * A stroke belongs to a row if that row has the maximum vertical overlap with
 * the stroke's bounding box. This ensures each stroke has exactly one owner,
 * eliminating "neighbor stealing" where two rows could both claim the same stroke.
 *
 * @param strokes All strokes from the page
 * @param rowBounds The bounds of the row to extract strokes from
 * @param canvasHeight Total canvas height for dominant row calculation
 * @param topOffset Top offset in pixels matching TASKS_TOOLBAR_OFFSET.toPx()
 * @return List of strokes whose dominant row matches rowBounds.rowIndex
 */
fun extractStrokesForRow(
    strokes: List<Stroke>,
    rowBounds: TaskRowBounds,
    canvasHeight: Float,
    topOffset: Float = tasksTopOffset()
): List<Stroke> {
    return strokes.filter { stroke ->
        getDominantRowIndex(stroke, canvasHeight, topOffset = topOffset) == rowBounds.rowIndex
    }
}

/**
 * Group strokes by task row using dominant vertical overlap.
 *
 * This is the **unified stroke-to-row assignment algorithm** used by all features
 * (OCR, task migration, content detection). It uses [calculateRowBounds] for row
 * boundaries, which matches the visual overlay (TasksLinesOverlay) exactly.
 *
 * Optionally filters strokes in the control column (for OCR use cases where
 * checkbox/priority marks should be excluded from text recognition).
 *
 * @param strokes All strokes from the page
 * @param canvasHeight Actual canvas height in pixels (from DrawCanvas.surfaceSize)
 * @param controlColumnPx Control column width in pixels. Strokes with right < this
 *        value are excluded. Pass 0f to include all strokes (e.g. for task migration).
 * @param rowCount Number of rows per page (default 10)
 * @param topOffset Top offset in pixels matching TASKS_TOOLBAR_OFFSET (default 1f)
 * @return Map of row index to list of strokes assigned to that row
 */
fun groupStrokesByRow(
    strokes: List<Stroke>,
    canvasHeight: Float,
    controlColumnPx: Float = 0f,
    rowCount: Int = 10,
    topOffset: Float = tasksTopOffset()
): Map<Int, List<Stroke>> {
    val result = mutableMapOf<Int, MutableList<Stroke>>()

    for (stroke in strokes) {
        // Optional: skip strokes entirely in control column
        if (controlColumnPx > 0f && stroke.right < controlColumnPx) continue

        val rowIndex = getDominantRowIndex(stroke, canvasHeight, rowCount, topOffset)
        if (rowIndex in 0 until rowCount) {
            result.getOrPut(rowIndex) { mutableListOf() }.add(stroke)
        }
    }

    return result
}

/**
 * Create migrated strokes with new IDs, new pageId, and transformed Y coordinates.
 *
 * The transformation:
 * 1. Calculate the stroke's position relative to the source row
 * 2. Apply the same relative position to the target row
 * 3. Handle potential row height differences (different canvas sizes)
 *
 * @param sourceStrokes Strokes to migrate
 * @param sourceRowBounds Bounds of the source row
 * @param targetRowBounds Bounds of the target row
 * @param targetPageId The page ID for the target task page
 * @return New stroke objects ready to insert into target page
 */
fun createMigratedStrokes(
    sourceStrokes: List<Stroke>,
    sourceRowBounds: TaskRowBounds,
    targetRowBounds: TaskRowBounds,
    targetPageId: String
): List<Stroke> {
    if (sourceStrokes.isEmpty()) return emptyList()

    val now = Date()

    // Calculate Y offset to shift strokes from source to target row
    // Simple offset if row heights are equal (typical case - same device)
    val yOffset = targetRowBounds.topY - sourceRowBounds.topY

    // Scale factor for different canvas heights (rare but handled)
    val yScale = if (sourceRowBounds.height > 0) {
        targetRowBounds.height / sourceRowBounds.height
    } else {
        1f
    }

    return sourceStrokes.map { stroke ->
        // Transform stroke coordinates
        val newPoints = stroke.points.map { point ->
            // Calculate position relative to source row top
            val relativeY = point.y - sourceRowBounds.topY
            // Scale and shift to target row
            val newY = targetRowBounds.topY + (relativeY * yScale)

            point.copy(y = newY)
        }

        // Transform bounds
        val relativeTop = stroke.top - sourceRowBounds.topY
        val relativeBottom = stroke.bottom - sourceRowBounds.topY

        stroke.copy(
            id = UUID.randomUUID().toString(),
            pageId = targetPageId,
            points = newPoints,
            top = targetRowBounds.topY + (relativeTop * yScale),
            bottom = targetRowBounds.topY + (relativeBottom * yScale),
            // left and right stay the same (X coordinates unchanged)
            createdAt = now,
            updatedAt = now
        )
    }
}

/**
 * Determines if a task row has "intentional content" vs just incidental stroke overflow.
 *
 * Problem: Users write freely on the canvas, and strokes may cross row boundaries.
 * A stroke from an adjacent row that clips the edge shouldn't mark this row as "having content".
 *
 * Solution: Use centroid-based detection with thresholds:
 * 1. Only count strokes whose vertical CENTER (midpoint of top/bottom) falls within the row
 * 2. Require either:
 *    - At least 2 strokes with centroids in the row (multiple intentional strokes), OR
 *    - 1 stroke that covers at least 30% of row height (intentional single stroke/line)
 *
 * @param strokes All strokes that intersect with the row bounds (from getStrokesInRectangle)
 * @param rowBounds The bounds of the row to check
 * @return true if the row has intentional handwritten content
 */
fun hasIntentionalStrokeContent(
    strokes: List<Stroke>?,
    rowBounds: TaskRowBounds
): Boolean {
    if (strokes.isNullOrEmpty()) return false

    val rowHeight = rowBounds.height
    val rowCenterY = (rowBounds.topY + rowBounds.bottomY) / 2

    // Filter to strokes whose vertical centroid falls within the row
    val centroidStrokes = strokes.filter { stroke ->
        val strokeCenterY = (stroke.top + stroke.bottom) / 2
        strokeCenterY >= rowBounds.topY && strokeCenterY <= rowBounds.bottomY
    }

    if (centroidStrokes.isEmpty()) return false

    // Check 1: Multiple strokes with centroids in row = intentional content
    if (centroidStrokes.size >= 2) return true

    // Check 2: Single stroke but covers meaningful vertical extent (15%+ of row height)
    // At ~112px rows, 15% = ~17px — catches underlines, strikethroughs, dashes,
    // compact handwriting while still rejecting pure overflow slivers (<5px)
    val singleStroke = centroidStrokes.first()
    val strokeHeightInRow = minOf(singleStroke.bottom, rowBounds.bottomY) -
                            maxOf(singleStroke.top, rowBounds.topY)
    val coverageRatio = strokeHeightInRow / rowHeight

    return coverageRatio >= 0.15f
}

/**
 * Combined check for whether a task row has content (typed text, metadata, or intentional strokes).
 * This is the authoritative function for determining chevron behavior.
 *
 * @param task The task to check
 * @param strokes Strokes that intersect the row (from getStrokesInRectangle)
 * @param rowBounds The bounds of the task's row
 * @return true if the row has any intentional content
 */
fun taskRowHasContent(
    task: com.boox.day.data.db.Task,
    strokes: List<Stroke>?,
    rowBounds: TaskRowBounds
): Boolean {
    // Check typed or converted text
    if (task.typedText?.isNotBlank() == true) return true
    if (task.convertedText?.isNotBlank() == true) return true
    if (task.recognizedText?.isNotBlank() == true) return true

    // Check metadata (completion status or priority)
    if (task.isCompleted || task.priority.isNotEmpty()) return true

    // Check intentional strokes (not just overflow from adjacent rows)
    return hasIntentionalStrokeContent(strokes, rowBounds)
}

/**
 * Result of a task move operation with stroke migration.
 *
 * @param success Whether the operation succeeded
 * @param errorReason Error reason if the operation failed
 * @param sourceStrokeIds IDs of strokes that were deleted from source
 * @param migratedStrokeCount Number of strokes migrated to target
 * @param undoOperations Undo operations for same-date moves (null for cross-date moves)
 */
data class MoveTaskResult(
    val success: Boolean,
    val errorReason: MoveTaskError? = null,
    val sourceStrokeIds: List<String> = emptyList(),
    val migratedStrokeCount: Int = 0,
    val undoOperations: List<Operation>? = null,
    val targetPageIndex: Int? = null,
    /** Row (task ID) that now holds the moved task (the group's parent) — plan T11 */
    val movedTaskId: String? = null
)

/**
 * Possible errors during task move operation.
 */
enum class MoveTaskError {
    /** All 10 rows on target page have content */
    TARGET_PAGE_FULL,
    /** Failed to load source page data */
    SOURCE_PAGE_NOT_LOADED,
    /** Database operation failed */
    DATABASE_ERROR,
    /** Target date is the same as source date with same row */
    SAME_LOCATION
}
