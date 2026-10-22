package com.boox.day.editor.utils

import android.util.Log
import com.boox.day.data.PageDataManager
import com.boox.day.data.db.StrokeRepository
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskRepository
import com.boox.day.tasks.TaskPagination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Centralized task slot allocation system.
 *
 * ## Contract
 *
 * A task slot is EMPTY if and only if ALL of these conditions are true:
 * - typedText: null or blank
 * - convertedText: null or blank
 * - recognizedText: null or blank
 * - isCompleted: false
 * - priority: empty string
 * - parentTaskId: null (not a child task)
 * - strokes: No intentional strokes in row bounds
 *
 * ## Key Invariants
 *
 * 1. **Single Source of Truth**: SlotAllocator is the ONLY place that determines slot emptiness
 * 2. **Allocation Returns Fresh Task**: The returned AllocatedSlot.task is current DB state
 * 3. **No Implicit Side Effects**: Allocation does NOT modify the slot; caller must write
 * 4. **Fail-Safe**: Returns null rather than risk overwriting content
 * 5. **Pre-Write Validation**: Use validateBeforeWrite() to catch race conditions
 *
 * ## Usage
 *
 * ```kotlin
 * // Single allocation (Voice-to-Task, Daily Summary, Smart Suggestions single)
 * val slot = SlotAllocator.allocateSingle(date, canvasHeight, taskRepository)
 * if (slot != null) {
 *     // OPTIONAL: Validate before write for race condition safety
 *     if (SlotAllocator.validateBeforeWrite(slot.task, canvasHeight, taskRepository)) {
 *         taskRepository.setTypedText(slot.task.id, "New task")
 *     }
 * }
 *
 * // Contiguous allocation (Task Breakdown)
 * val block = SlotAllocator.allocateContiguous(
 *     date, slotsNeeded, canvasHeight, taskRepository,
 *     excludeTaskIds = setOf(originalTask.id)  // Original will be cleared
 * )
 * ```
 *
 * @see TaskRepository.findFirstEmptyRow for underlying detection (wrapped, not called directly)
 */
object SlotAllocator {

    private const val TAG = "SlotAllocator"
    private const val ROWS_PER_PAGE = 10

    /**
     * Allocate a single empty slot on the given date.
     *
     * Scans pages starting from startPageIndex, creates new pages if needed.
     * The returned slot is guaranteed to be empty at the moment of allocation.
     *
     * @param date The target date (format: yyyy-MM-dd)
     * @param canvasHeight Canvas height for row bounds calculation
     * @param taskRepository Repository for task queries
     * @param startPageIndex Page to start scanning from (default: 0)
     * @param maxPages Maximum pages to scan/create (default: 10)
     * @return AllocatedSlot if found, null if no slots available
     */
    suspend fun allocateSingle(
        date: String,
        canvasHeight: Float,
        taskRepository: TaskRepository,
        startPageIndex: Int = 0,
        maxPages: Int = 10
    ): AllocatedSlot? = withContext(Dispatchers.IO) {
        // Full screen Tasks (Oct 2026): slots are found on pages of 10; callers leave full screen first
        if (TaskPagination.isReflowed(date)) return@withContext null
        var pageIndex = startPageIndex

        while (pageIndex < maxPages) {
            val (tasks, _) = taskRepository.getOrCreateTasksForPage(date, pageIndex)

            // Get strokes for this page (load from DB if not in cache)
            val pageId = tasks.firstOrNull()?.pageId
            val strokes = getStrokesForPage(pageId, taskRepository)

            // Find first empty row with comprehensive check
            for (rowIndex in 0 until ROWS_PER_PAGE) {
                val task = tasks.getOrNull(rowIndex) ?: continue

                if (isSlotEmptyInternal(task, canvasHeight, strokes)) {
                    Log.i(TAG, "ALLOC_SINGLE: $date page=$pageIndex row=$rowIndex")
                    return@withContext AllocatedSlot(task, pageIndex, rowIndex)
                }
            }

            pageIndex++
        }

        Log.w(TAG, "ALLOC_SINGLE: No slots available for $date (scanned $maxPages pages)")
        null
    }

    /**
     * Allocate multiple slots (not necessarily contiguous).
     *
     * For batch operations like Smart Suggestions "Add All".
     * Returns as many slots as available, up to the requested count.
     *
     * @param date The target date
     * @param count Number of slots requested
     * @param canvasHeight Canvas height for row bounds calculation
     * @param taskRepository Repository for task queries
     * @param startPageIndex Page to start scanning from
     * @param maxPages Maximum pages to scan/create
     * @return List of allocated slots (may be less than requested)
     */
    suspend fun allocateMultiple(
        date: String,
        count: Int,
        canvasHeight: Float,
        taskRepository: TaskRepository,
        startPageIndex: Int = 0,
        maxPages: Int = 10
    ): List<AllocatedSlot> = withContext(Dispatchers.IO) {
        // Full screen Tasks (Oct 2026): slots are found on pages of 10; callers leave full screen first
        if (TaskPagination.isReflowed(date)) return@withContext emptyList()
        val allocated = mutableListOf<AllocatedSlot>()
        var pageIndex = startPageIndex

        while (allocated.size < count && pageIndex < maxPages) {
            val (tasks, _) = taskRepository.getOrCreateTasksForPage(date, pageIndex)

            val pageId = tasks.firstOrNull()?.pageId
            val strokes = getStrokesForPage(pageId, taskRepository)

            for (rowIndex in 0 until ROWS_PER_PAGE) {
                if (allocated.size >= count) break

                val task = tasks.getOrNull(rowIndex) ?: continue
                if (isSlotEmptyInternal(task, canvasHeight, strokes)) {
                    allocated.add(AllocatedSlot(task, pageIndex, rowIndex))
                }
            }
            pageIndex++
        }

        Log.i(TAG, "ALLOC_MULTI: $date requested=$count allocated=${allocated.size}")
        allocated
    }

    /**
     * Allocate a contiguous block of slots on ONE page.
     *
     * For Task Breakdown where parent + children must be adjacent.
     * If no contiguous block found on existing pages, creates new pages.
     *
     * @param date The target date
     * @param count Number of contiguous slots needed
     * @param canvasHeight Canvas height for row bounds calculation
     * @param taskRepository Repository for task queries
     * @param excludeTaskIds Tasks to treat as "empty" (their slots will be cleared by caller)
     * @param startPageIndex Page to start scanning from
     * @param maxPages Maximum pages to scan/create
     * @return ContiguousAllocation if found, null if no contiguous block available
     */
    suspend fun allocateContiguous(
        date: String,
        count: Int,
        canvasHeight: Float,
        taskRepository: TaskRepository,
        excludeTaskIds: Set<String> = emptySet(),
        startPageIndex: Int = 0,
        maxPages: Int = 10
    ): ContiguousAllocation? = withContext(Dispatchers.IO) {
        // Full screen Tasks (Oct 2026): slots are found on pages of 10; callers leave full screen first
        if (TaskPagination.isReflowed(date)) return@withContext null
        var pageIndex = startPageIndex

        while (pageIndex < maxPages) {
            val (tasks, _) = taskRepository.getOrCreateTasksForPage(date, pageIndex)

            val pageId = tasks.firstOrNull()?.pageId
            val strokes = getStrokesForPage(pageId, taskRepository)

            // Find contiguous empty range
            var rangeStart: Int? = null

            for (rowIndex in 0 until ROWS_PER_PAGE) {
                val task = tasks.getOrNull(rowIndex)
                val isExcluded = task?.id in excludeTaskIds
                val isEmpty = isExcluded || isSlotEmptyInternal(task, canvasHeight, strokes)

                if (isEmpty) {
                    if (rangeStart == null) rangeStart = rowIndex

                    // Check if we have enough contiguous slots
                    val rangeSize = rowIndex - rangeStart + 1
                    if (rangeSize >= count) {
                        val slots = (rangeStart..rowIndex).mapNotNull { idx ->
                            tasks.getOrNull(idx)?.let { AllocatedSlot(it, pageIndex, idx) }
                        }
                        if (slots.size >= count) {
                            Log.i(TAG, "ALLOC_CONTIG: $date page=$pageIndex start=$rangeStart count=$count")
                            return@withContext ContiguousAllocation(
                                slots = slots.take(count),
                                pageIndex = pageIndex,
                                startRow = rangeStart
                            )
                        }
                    }
                } else {
                    rangeStart = null  // Reset - gap found
                }
            }
            pageIndex++
        }

        Log.w(TAG, "ALLOC_CONTIG: No contiguous block of $count for $date")
        null
    }

    /**
     * Check if a specific task slot is empty.
     *
     * Use this for UI indicators or pre-checks.
     * For allocation, use allocateSingle/allocateMultiple/allocateContiguous instead.
     *
     * @param task The task to check
     * @param canvasHeight Canvas height for row bounds calculation
     * @param taskRepository Repository for stroke access
     * @return true if slot is empty (no content of any kind)
     */
    suspend fun isSlotEmpty(
        task: Task,
        canvasHeight: Float,
        taskRepository: TaskRepository
    ): Boolean = withContext(Dispatchers.IO) {
        // Full screen Tasks (Oct 2026): slots are found on pages of 10; callers leave full screen first
        if (TaskPagination.isReflowed(task.dayDate)) return@withContext false
        val pageId = task.pageId
        val strokes = getStrokesForPage(pageId, taskRepository)
        isSlotEmptyInternal(task, canvasHeight, strokes)
    }

    /**
     * Validate that a slot is still empty before writing.
     *
     * Use this to catch race conditions between allocation and write.
     * Call immediately before setTypedText() or similar write operations.
     *
     * ```kotlin
     * val slot = SlotAllocator.allocateSingle(...)
     * if (slot != null && SlotAllocator.validateBeforeWrite(slot.task, canvasHeight, repo)) {
     *     repo.setTypedText(slot.task.id, text)
     * } else {
     *     // Slot was taken by another operation - reallocate
     * }
     * ```
     *
     * @param task The task from AllocatedSlot
     * @param canvasHeight Canvas height for row bounds calculation
     * @param taskRepository Repository for fresh task lookup
     * @return true if slot is still empty and safe to write
     */
    suspend fun validateBeforeWrite(
        task: Task,
        canvasHeight: Float,
        taskRepository: TaskRepository
    ): Boolean = withContext(Dispatchers.IO) {
        // Full screen Tasks (Oct 2026): slots are found on pages of 10; callers leave full screen first
        if (TaskPagination.isReflowed(task.dayDate)) return@withContext false
        // Re-fetch task from DB to get current state
        val freshTask = taskRepository.getTaskById(task.id)
        if (freshTask == null) {
            Log.w(TAG, "VALIDATE: Task ${task.id} no longer exists")
            return@withContext false
        }

        val pageId = freshTask.pageId
        val strokes = getStrokesForPage(pageId, taskRepository)

        val isEmpty = isSlotEmptyInternal(freshTask, canvasHeight, strokes)
        if (!isEmpty) {
            Log.w(TAG, "VALIDATE: Slot ${task.dayDate}/${task.taskPageIndex}/${task.rowIndex} was taken by another operation")
        }
        isEmpty
    }

    /**
     * Get capacity summary for pre-flight checks (AI gating).
     *
     * Returns how many empty slots are available and the largest contiguous block.
     *
     * @param date The date to check
     * @param canvasHeight Canvas height for row bounds calculation
     * @param taskRepository Repository for task queries
     * @param maxPages Maximum pages to consider
     * @return SlotCapacity with counts and availability info
     */
    suspend fun getCapacity(
        date: String,
        canvasHeight: Float,
        taskRepository: TaskRepository,
        maxPages: Int = 10
    ): SlotCapacity = withContext(Dispatchers.IO) {
        // Full screen Tasks (Oct 2026): slots are found on pages of 10; callers leave full screen first
        if (TaskPagination.isReflowed(date)) return@withContext SlotCapacity(totalEmpty = 0, emptyOnExistingPages = 0, canCreateMorePages = false, maxContiguousBlock = 0)
        var totalEmpty = 0
        var maxContiguous = 0
        val existingPages = taskRepository.getMaxTaskPageIndex(date) + 1
        val actualExistingPages = maxOf(1, existingPages)  // At least check page 0

        for (pageIndex in 0 until actualExistingPages) {
            val (tasks, _) = taskRepository.getOrCreateTasksForPage(date, pageIndex)
            val pageId = tasks.firstOrNull()?.pageId
            val strokes = getStrokesForPage(pageId, taskRepository)

            var currentContiguous = 0

            for (task in tasks) {
                if (isSlotEmptyInternal(task, canvasHeight, strokes)) {
                    totalEmpty++
                    currentContiguous++
                    maxContiguous = maxOf(maxContiguous, currentContiguous)
                } else {
                    currentContiguous = 0
                }
            }
        }

        val canCreateMore = actualExistingPages < maxPages
        val potentialFromNewPages = if (canCreateMore) (maxPages - actualExistingPages) * ROWS_PER_PAGE else 0

        SlotCapacity(
            totalEmpty = totalEmpty + potentialFromNewPages,
            emptyOnExistingPages = totalEmpty,
            canCreateMorePages = canCreateMore,
            maxContiguousBlock = maxOf(maxContiguous, if (canCreateMore) ROWS_PER_PAGE else 0)
        )
    }

    // ========== INTERNAL HELPERS ==========

    /**
     * Internal comprehensive empty check - checks ALL 7 content types.
     *
     * CRITICAL: This is the authoritative definition of "empty".
     * A slot is NOT empty if it has ANY of:
     * - typedText
     * - convertedText
     * - recognizedText
     * - isCompleted = true
     * - priority set
     * - parentTaskId set (it's a child task)
     * - intentional strokes in row bounds
     */
    private fun isSlotEmptyInternal(
        task: Task?,
        canvasHeight: Float,
        strokes: List<com.boox.day.data.db.Stroke>
    ): Boolean {
        if (task == null) return true

        // Check text fields
        if (!task.typedText.isNullOrBlank()) return false
        if (!task.convertedText.isNullOrBlank()) return false
        if (!task.recognizedText.isNullOrBlank()) return false

        // Check metadata
        if (task.isCompleted) return false
        if (task.priority.isNotBlank()) return false

        // Check parent-child relationship
        // A child task is NOT empty even if all text fields are blank
        if (task.parentTaskId != null) return false

        // Check strokes (most expensive, do last)
        val rowBounds = calculateRowBounds(task.rowIndex, canvasHeight, ROWS_PER_PAGE)
        if (hasIntentionalStrokeContent(strokes, rowBounds)) return false

        return true
    }

    /**
     * Get strokes for a page, using cache if available, DB otherwise.
     */
    private fun getStrokesForPage(
        pageId: String?,
        taskRepository: TaskRepository
    ): List<com.boox.day.data.db.Stroke> {
        if (pageId == null) return emptyList()

        return if (PageDataManager.isPageLoaded(pageId)) {
            PageDataManager.getStrokes(pageId)
        } else {
            // Cache miss - load from DB
            Log.d(TAG, "STROKE_LOAD: Cache miss for page $pageId, loading from DB")
            StrokeRepository(taskRepository.context).getAllForPage(pageId)
        }
    }
}

/**
 * Result of a single slot allocation.
 *
 * @property task The Task entity for the allocated slot (current DB state)
 * @property pageIndex The page index (0-based)
 * @property rowIndex The row index within the page (0-9)
 */
data class AllocatedSlot(
    val task: Task,
    val pageIndex: Int,
    val rowIndex: Int
)

/**
 * Result of a contiguous slot allocation.
 *
 * @property slots List of allocated slots in order (first is parent position)
 * @property pageIndex The page where the block was found
 * @property startRow The starting row index of the block
 */
data class ContiguousAllocation(
    val slots: List<AllocatedSlot>,
    val pageIndex: Int,
    val startRow: Int
)

/**
 * Capacity summary for pre-flight checks.
 *
 * @property totalEmpty Total empty slots (existing + potential from new pages)
 * @property emptyOnExistingPages Empty slots on already-created pages
 * @property canCreateMorePages Whether more pages can be created within quota
 * @property maxContiguousBlock Largest contiguous block available
 */
data class SlotCapacity(
    val totalEmpty: Int,
    val emptyOnExistingPages: Int,
    val canCreateMorePages: Boolean,
    val maxContiguousBlock: Int
)
