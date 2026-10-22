package com.boox.day.tasks

import android.util.Log
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskRepository
import com.boox.day.tasks.TaskPagination
import com.boox.day.data.db.StrokeRepository

/**
 * Free task rows on a day, for AI features that add tasks (voice to task, smart suggestions,
 * break down, memo to tasks).
 *
 * Available slots = (empty rows on existing pages) + (rows on the pages that can still be added,
 * up to [MAX_PAGES_HARD_LIMIT] pages a day).
 */
object TaskSlotCalculator {

    private const val TAG = "TaskSlotCalculator"
    private const val SLOTS_PER_PAGE = 10
    private const val MAX_PAGES_HARD_LIMIT = 10  // Safety limit of task pages a day

    /**
     * Calculate available task slots for a given date.
     *
     * @param date The date to check (format: "yyyy-MM-dd")
     * @param taskRepository Repository for task queries
     * @param strokeRepository Repository for stroke queries (to detect handwritten content)
     * @param canvasHeight Canvas height for row bounds calculation
     * @return TaskSlotsResult with available slots and breakdown
     */
    suspend fun getAvailableSlots(
        date: String,
        taskRepository: TaskRepository,
        strokeRepository: StrokeRepository,
        canvasHeight: Float = 1872f
    ): TaskSlotsResult {
        // Full screen Tasks (Oct 2026): counted on pages of 10; callers leave full screen first
        if (TaskPagination.isReflowed(date)) {
            return TaskSlotsResult(totalAvailable = 0, emptyOnExistingPages = 0, slotsFromNewPages = 0,
                existingPageCount = 0, filledSlots = 0, canCreateMorePages = false)
        }
        // 1. Count existing task pages for this date
        val existingPageCount = taskRepository.getMaxTaskPageIndex(date) + 1
        Log.d(TAG, "Date $date has $existingPageCount existing task pages")

        // 2. Count filled slots (have content - typed, converted, recognized, or strokes)
        val filledSlots = countFilledSlots(
            date = date,
            pageCount = existingPageCount,
            taskRepository = taskRepository,
            strokeRepository = strokeRepository,
            canvasHeight = canvasHeight
        )
        Log.d(TAG, "Filled slots: $filledSlots")

        // 3. Calculate empty slots on existing pages
        val totalExistingSlots = existingPageCount * SLOTS_PER_PAGE
        val emptySlots = maxOf(0, totalExistingSlots - filledSlots)
        Log.d(TAG, "Empty slots on existing pages: $emptySlots")

        // 4. Rows on the pages that can still be added (a safety limit of pages per day)
        val additionalPages = maxOf(0, MAX_PAGES_HARD_LIMIT - existingPageCount)
        val slotsFromNewPages = additionalPages * SLOTS_PER_PAGE
        Log.d(TAG, "Slots from new pages: $slotsFromNewPages")

        val totalAvailable = emptySlots + slotsFromNewPages
        Log.d(TAG, "Total available slots: $totalAvailable")

        return TaskSlotsResult(
            totalAvailable = totalAvailable,
            emptyOnExistingPages = emptySlots,
            slotsFromNewPages = slotsFromNewPages,
            existingPageCount = existingPageCount,
            filledSlots = filledSlots,
            canCreateMorePages = slotsFromNewPages > 0
        )
    }

    /**
     * Count how many task slots are filled (have content).
     *
     * A slot is considered filled if it has:
     * - typedText (user typed content)
     * - convertedText (AI-converted from memo)
     * - recognizedText (OCR-extracted)
     * - isCompleted = true
     * - priority set (A, B, or C)
     * - handwritten strokes in the row bounds
     */
    private suspend fun countFilledSlots(
        date: String,
        pageCount: Int,
        taskRepository: TaskRepository,
        strokeRepository: StrokeRepository,
        canvasHeight: Float
    ): Int {
        var filled = 0

        for (pageIndex in 0 until pageCount) {
            val tasks = taskRepository.getTasksForPage(date, pageIndex)
            if (tasks.isEmpty()) continue

            // Get the shared page ID for stroke lookup
            val sharedPageId = tasks.firstOrNull()?.pageId

            // Load strokes once for the entire page
            val strokes = if (sharedPageId != null) {
                strokeRepository.getAllForPage(sharedPageId)
            } else {
                emptyList()
            }

            for (task in tasks) {
                // Check text content
                val hasTypedText = !task.typedText.isNullOrBlank()
                val hasConvertedText = !task.convertedText.isNullOrBlank()
                val hasRecognizedText = !task.recognizedText.isNullOrBlank()

                // Check metadata
                val hasMetadata = task.isCompleted || task.priority.isNotEmpty()

                // Check for strokes in this row's bounds
                val hasStrokes = if (strokes.isNotEmpty()) {
                    val rowBounds = getRowBounds(task.rowIndex, canvasHeight)
                    strokes.any { stroke ->
                        // Stroke intersects with row if it's not completely above or below
                        stroke.top < rowBounds.second && stroke.bottom > rowBounds.first
                    }
                } else {
                    false
                }

                if (hasTypedText || hasConvertedText || hasRecognizedText || hasMetadata || hasStrokes) {
                    filled++
                }
            }
        }

        return filled
    }

    /**
     * Get the vertical bounds for a task row.
     *
     * @param rowIndex The row index (0-9)
     * @param canvasHeight Total canvas height
     * @return Pair of (top, bottom) coordinates
     */
    private fun getRowBounds(rowIndex: Int, canvasHeight: Float): Pair<Float, Float> {
        val rowHeight = canvasHeight / SLOTS_PER_PAGE
        val top = rowIndex * rowHeight
        val bottom = (rowIndex + 1) * rowHeight
        return Pair(top, bottom)
    }
}

/**
 * Result of task slot calculation.
 *
 * @property totalAvailable Total slots available (empty + from new pages)
 * @property emptyOnExistingPages Empty slots on existing pages
 * @property slotsFromNewPages Potential slots from pages that can be created
 * @property existingPageCount Number of existing task pages
 * @property filledSlots Number of slots with content
 * @property canCreateMorePages Whether more pages can still be added
 */
data class TaskSlotsResult(
    val totalAvailable: Int,
    val emptyOnExistingPages: Int,
    val slotsFromNewPages: Int,
    val existingPageCount: Int,
    val filledSlots: Int,
    val canCreateMorePages: Boolean
)
