package com.boox.day.data.db

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.boox.day.data.PageDataManager
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.editor.utils.calculateRowBounds
import com.boox.day.editor.utils.hasIntentionalStrokeContent
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class RecurringTaskMaterializationResult(
    val createdCount: Int = 0,
    val skippedExistingCount: Int = 0,
    val skippedExceptionCount: Int = 0,
    val skippedRuleCount: Int = 0,
    val noSlotAvailableCount: Int = 0,
    val createdTaskIds: List<String> = emptyList(),
    val createdPageIndices: Set<Int> = emptySet()
) {
    val changed: Boolean get() = createdCount > 0
}

class RecurringTaskMaterializer(
    private val context: Context,
    private val taskRepository: TaskRepository
) {
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val taskDao by lazy { database.taskDao() }
    private val templateDao by lazy { database.recurringTaskTemplateDao() }
    private val exceptionDao by lazy { database.recurringTaskExceptionDao() }
    private val strokeRepository by lazy { StrokeRepository(context) }

    suspend fun materializeDate(
        date: String,
        canvasHeight: Float = 0f
    ): RecurringTaskMaterializationResult = withContext(Dispatchers.IO) {
        materializationMutex.withLock {
            // T16 (Oct 2026): never back-fill — browsing to a past day (including days when the
            // series was paused) must not create occurrences there. ISO days compare as strings.
            if (date < java.time.LocalDate.now().toString()) {
                return@withLock RecurringTaskMaterializationResult()
            }
            // Plan §13.2-2 (Oct 2026): placement needs the measured row geometry to see ink; with
            // a guessed height an inked row could look empty. Unmeasured -> defer (MainScreen
            // runs this again once the Tasks canvas reports its height).
            val effectiveCanvasHeight = resolveCanvasHeight(canvasHeight) ?: run {
                Log.i(TAG, "Recurring placement for $date deferred until the Tasks canvas is measured")
                return@withLock RecurringTaskMaterializationResult()
            }
            val templates = templateDao.getActiveTemplatesForDate(date)
            if (templates.isEmpty()) {
                return@withLock RecurringTaskMaterializationResult()
            }

            val existingTemplateIds = taskDao.getTasksForDay(date)
                .mapNotNull { it.templateId }
                .toMutableSet()
            val exceptionTemplateIds = exceptionDao.getForDate(date)
                .map { it.templateId }
                .toHashSet()

            var createdCount = 0
            var skippedExistingCount = 0
            var skippedExceptionCount = 0
            var skippedRuleCount = 0
            var noSlotAvailableCount = 0
            val createdTaskIds = mutableListOf<String>()
            val createdPageIndices = mutableSetOf<Int>()

            for (template in templates) {
                if (!RecurringTaskRuleEvaluator.shouldGenerateOnDate(template, date)) {
                    skippedRuleCount++
                    continue
                }

                if (existingTemplateIds.contains(template.id)) {
                    skippedExistingCount++
                    continue
                }

                if (exceptionTemplateIds.contains(template.id)) {
                    skippedExceptionCount++
                    continue
                }

                val slotSearch = findEmptyMaterializationSlot(
                    date = date,
                    canvasHeight = effectiveCanvasHeight
                )

                if (slotSearch.task == null) {
                    noSlotAvailableCount++
                    continue
                }

                val taskToWrite = slotSearch.task.copy(
                    isCompleted = false,
                    priority = template.defaultPriority,
                    typedText = template.title,
                    recognizedText = null,
                    convertedText = null,
                    parentTaskId = null,
                    migrationCount = 0,
                    originalDate = null,
                    lastMigratedAt = null,
                    estimatedMinutes = template.defaultEstimatedMinutes,
                    dueDay = null,
                    templateId = template.id,
                    updatedAt = Date()
                )
                taskDao.insert(taskToWrite)

                existingTemplateIds.add(template.id)
                createdCount++
                createdTaskIds += taskToWrite.id
                createdPageIndices += taskToWrite.taskPageIndex
            }

            if (createdCount > 0) {
                taskRepository.invalidateTaskCache(date)
                taskRepository.invalidateIntelligenceCache()
                taskRepository.emitTaskChanged(date)
            }

            val result = RecurringTaskMaterializationResult(
                createdCount = createdCount,
                skippedExistingCount = skippedExistingCount,
                skippedExceptionCount = skippedExceptionCount,
                skippedRuleCount = skippedRuleCount,
                noSlotAvailableCount = noSlotAvailableCount,
                createdTaskIds = createdTaskIds,
                createdPageIndices = createdPageIndices
            )

            if (result.changed || result.noSlotAvailableCount > 0) {
                Log.i(
                    TAG,
                    "materializeDate date=$date created=${result.createdCount} " +
                        "skippedExisting=${result.skippedExistingCount} " +
                        "skippedException=${result.skippedExceptionCount} " +
                        "noSlot=${result.noSlotAvailableCount}"
                )
            }

            result
        }
    }

    private data class SlotSearchResult(
        val task: Task? = null
    )

    private fun findEmptyMaterializationSlot(
        date: String,
        canvasHeight: Float
    ): SlotSearchResult {
        val maxExistingPageIndex = taskRepository.getMaxTaskPageIndex(date)
        val highestRequestedPageIndex = when {
            maxExistingPageIndex < 0 -> 0
            maxExistingPageIndex >= MAX_TASK_PAGES - 1 -> MAX_TASK_PAGES - 1
            else -> maxExistingPageIndex + 1
        }

        for (requestedPageIndex in 0..highestRequestedPageIndex) {
            val (tasks, pageInfo) = taskRepository.getOrCreateTasksForPage(date, requestedPageIndex)
            if (requestedPageIndex > maxExistingPageIndex && pageInfo.currentIndex != requestedPageIndex) {
                return SlotSearchResult()
            }

            val pageId = tasks.firstOrNull()?.pageId
            val pageStrokes = if (pageId != null) {
                PageDataManager.getStrokesInRectangle(Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE), pageId)
                    ?: strokeRepository.getAllForPage(pageId)
            } else {
                emptyList()
            }

            val emptyTask = tasks
                .sortedBy { it.rowIndex }
                .firstOrNull { task -> isRecurringMaterializationSlotEmpty(task, pageStrokes, canvasHeight) }

            if (emptyTask != null) {
                return SlotSearchResult(task = emptyTask)
            }
        }

        return SlotSearchResult()
    }

    private fun isRecurringMaterializationSlotEmpty(
        task: Task,
        pageStrokes: List<Stroke>,
        canvasHeight: Float
    ): Boolean {
        if (!task.typedText.isNullOrBlank()) return false
        if (!task.convertedText.isNullOrBlank()) return false
        if (!task.recognizedText.isNullOrBlank()) return false
        if (task.isCompleted) return false
        if (task.priority.isNotBlank()) return false
        if (task.parentTaskId != null) return false
        if (task.dueDay != null) return false
        if (task.estimatedMinutes != null) return false
        if (task.templateId != null) return false
        if (task.migrationCount != 0) return false
        if (task.originalDate != null) return false
        if (task.lastMigratedAt != null) return false

        val rowBounds = calculateRowBounds(task.rowIndex, canvasHeight, 10)
        val hasStrokes = hasIntentionalStrokeContent(
            pageStrokes.filter { stroke ->
                stroke.bottom > rowBounds.topY && stroke.top < rowBounds.bottomY
            },
            rowBounds
        )
        return !hasStrokes
    }

    /** Measured Tasks canvas height, or null when not measured yet (never a guess). */
    private fun resolveCanvasHeight(canvasHeight: Float): Float? {
        if (canvasHeight > MIN_VALID_CANVAS_HEIGHT) return canvasHeight

        val sessionHeight = GlobalAppSettings.tasksCanvasHeight
        if (sessionHeight > MIN_VALID_CANVAS_HEIGHT) return sessionHeight

        return null
    }

    companion object {
        private const val TAG = "RecurringTaskMat"
        private const val MAX_TASK_PAGES = 10
        private const val MIN_VALID_CANVAS_HEIGHT = 2f
        private val materializationMutex = Mutex()
    }
}
