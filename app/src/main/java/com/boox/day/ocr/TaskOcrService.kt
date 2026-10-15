package com.boox.day.ocr

import android.content.Context
import android.util.Log
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskDao
import com.boox.day.editor.ui.TASKS_CONTROL_COLUMN_WIDTH
import com.boox.day.ui.convertDpToPixel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

/**
 * Result of OCR for a single task.
 */
data class TaskOcrResult(
    val taskId: String,
    val text: String,
    val source: TextSource,
    val isHandwritten: Boolean
)

/**
 * Source of the text content.
 */
enum class TextSource {
    TYPED,   // From task.typedText (user typed)
    STORED,  // From task.recognizedText (previously recognized)
    FRESH    // Just recognized via DigitalInkService
}

/**
 * Result of batch OCR operation.
 */
data class BatchOcrResult(
    val results: Map<String, TaskOcrResult>,
    val stats: OcrStats
)

/**
 * Statistics about batch OCR operation.
 */
data class OcrStats(
    val total: Int,
    val withTypedText: Int,
    val withStoredRecognition: Int,
    val freshlyRecognized: Int,
    val empty: Int
)

/**
 * Centralized service for task handwriting recognition.
 *
 * Wraps DigitalInkService with task-aware caching and batch optimization.
 * Uses on-device ML Kit Digital Ink API - no network calls after model download.
 *
 * Text Priority:
 * 1. typedText (user typed) - immediate return
 * 2. recognizedText (previously recognized) - immediate return
 * 3. Fresh Digital Ink recognition - calls DigitalInkService
 *
 * Thread Safety: All public methods are thread-safe.
 *
 * Usage:
 * ```
 * // Initialize once (e.g., in MainActivity or first use)
 * TaskOcrService.initialize(context, scope)
 *
 * // Get text for tasks
 * val result = TaskOcrService.getTextForTasks(tasks, density)
 * ```
 */
object TaskOcrService {
    private const val TAG = "TaskOcrService"

    // Initialization state
    @Volatile
    private var isInitialized = false
    private lateinit var context: Context
    private lateinit var scope: CoroutineScope

    // Dependencies (lazy initialized after initialize() call)
    private val digitalInkService: DigitalInkService by lazy {
        DigitalInkService.create(context, scope)
    }

    private val taskDao: TaskDao by lazy {
        AppDatabase.getDatabase(context).taskDao()
    }

    /**
     * The measured Tasks canvas height from GlobalAppSettings, or null when not measured yet.
     * Plan §13.2-2 (Oct 2026): no density-based guess — wrong rows put text on the wrong tasks;
     * callers return empty text instead (as when the ink model isn't ready).
     */
    private fun getCanvasHeight(@Suppress("UNUSED_PARAMETER") density: Float): Float? =
        GlobalAppSettings.tasksCanvasHeight.takeIf { it > 0f }

    /**
     * Get the control column width in pixels.
     */
    private fun getControlColumnPx(): Float {
        return convertDpToPixel(TASKS_CONTROL_COLUMN_WIDTH, context)
    }

    /**
     * Get the measured top offset in pixels from GlobalAppSettings.
     * Falls back to density-based approximation if not yet measured.
     */
    private fun getTopOffsetPx(density: Float): Float {
        val stored = GlobalAppSettings.tasksTopOffsetPx
        if (stored > 0f) return stored
        return density  // 1.dp * density
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Initialize the service. Must be called before first use.
     *
     * Safe to call multiple times - subsequent calls are no-ops.
     *
     * @param context Application context (will be converted to applicationContext)
     * @param scope CoroutineScope for background operations
     */
    fun initialize(context: Context, scope: CoroutineScope) {
        if (isInitialized) return

        synchronized(this) {
            if (isInitialized) return

            this.context = context.applicationContext
            this.scope = scope
            isInitialized = true

            Log.i(TAG, "TaskOcrService initialized")
        }
    }

    /**
     * Check if the Digital Ink model is ready for recognition.
     *
     * @return true if model is downloaded and ready
     */
    suspend fun ensureModelReady(): Boolean {
        return DigitalInkModelManager.isModelReady()
    }

    /**
     * Check if service is initialized.
     */
    fun isInitialized(): Boolean = isInitialized

    // ═══════════════════════════════════════════════════════════════════════════
    // SINGLE TASK RECOGNITION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get text for a single task.
     *
     * Checks in order:
     * 1. typedText (or convertedText) - returns immediately if not blank
     * 2. recognizedText - returns immediately if not blank
     * 3. Fresh recognition via DigitalInkService
     *
     * @param task The task to get text for
     * @param density Screen density for dp-to-px conversion
     * @return TaskOcrResult with text and source information
     */
    suspend fun getTextForTask(task: Task, density: Float): TaskOcrResult {
        require(isInitialized) { "TaskOcrService not initialized. Call initialize() first." }

        // Priority 1: Check typedText (user typed this) or convertedText (AI)
        if (task.typedText?.isNotBlank() == true) {
            return TaskOcrResult(
                taskId = task.id,
                text = task.typedText!!,
                source = TextSource.TYPED,
                isHandwritten = false
            )
        }
        if (task.convertedText?.isNotBlank() == true) {
            return TaskOcrResult(
                taskId = task.id,
                text = task.convertedText!!,
                source = TextSource.TYPED,
                isHandwritten = false
            )
        }

        // Priority 2: Check stored recognizedText (previously recognized)
        if (task.recognizedText?.isNotBlank() == true) {
            return TaskOcrResult(
                taskId = task.id,
                text = task.recognizedText!!,
                source = TextSource.STORED,
                isHandwritten = true
            )
        }

        // Priority 3: Fresh recognition using DigitalInkService
        return recognizeTaskFresh(task, density)
    }

    /**
     * Perform fresh recognition for a task using DigitalInkService.
     * Stores successful results to database for future reuse.
     */
    private suspend fun recognizeTaskFresh(task: Task, density: Float): TaskOcrResult {
        // Check if model is ready
        if (!ensureModelReady()) {
            Log.w(TAG, "Model not ready, returning empty for task ${task.id}")
            return TaskOcrResult(
                taskId = task.id,
                text = "",
                source = TextSource.FRESH,
                isHandwritten = false
            )
        }

        // Call DigitalInkService to extract page using correct canvas dimensions
        val canvasHeight = getCanvasHeight(density) ?: run {
            Log.w(TAG, "Tasks canvas not measured, returning empty for task ${task.id}")
            return TaskOcrResult(
                taskId = task.id,
                text = "",
                source = TextSource.FRESH,
                isHandwritten = false
            )
        }
        val controlColumnPx = getControlColumnPx()
        val topOffset = getTopOffsetPx(density)
        val pageResults = withContext(Dispatchers.Default) {
            digitalInkService.extractPage(task.pageId, canvasHeight, controlColumnPx, topOffset)
        }

        // Find result for this task's row
        val slotResult = pageResults.find { it.slotIndex == task.rowIndex }

        if (slotResult != null && !slotResult.isEmpty && slotResult.text.isNotBlank()) {
            // Store for future use
            withContext(Dispatchers.IO) {
                try {
                    taskDao.setRecognizedTextById(task.id, slotResult.text)
                    Log.d(TAG, "Stored recognition for task ${task.id}: '${slotResult.text}'")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to store recognition for task ${task.id}: ${e.message}")
                }
            }

            return TaskOcrResult(
                taskId = task.id,
                text = slotResult.text,
                source = TextSource.FRESH,
                isHandwritten = true
            )
        }

        // No text found
        return TaskOcrResult(
            taskId = task.id,
            text = "",
            source = TextSource.FRESH,
            isHandwritten = false
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH TASK RECOGNITION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get text for multiple tasks efficiently.
     *
     * Optimization strategy:
     * 1. Immediately return typedText and recognizedText (no I/O)
     * 2. Group remaining tasks by pageId
     * 3. Call extractPage() once per unique page (not per task)
     * 4. Process pages in parallel with fault isolation
     *
     * @param tasks List of tasks to process
     * @param density Screen density for dp-to-px conversion
     * @return BatchOcrResult with all results and statistics
     */
    suspend fun getTextForTasks(tasks: List<Task>, density: Float): BatchOcrResult {
        require(isInitialized) { "TaskOcrService not initialized. Call initialize() first." }

        if (tasks.isEmpty()) {
            return BatchOcrResult(
                results = emptyMap(),
                stats = OcrStats(0, 0, 0, 0, 0)
            )
        }

        val results = mutableMapOf<String, TaskOcrResult>()
        var typedCount = 0
        var storedCount = 0
        var freshCount = 0
        var emptyCount = 0

        // First pass: collect immediate results (typedText/convertedText and recognizedText)
        val tasksNeedingRecognition = mutableListOf<Task>()

        for (task in tasks) {
            when {
                task.typedText?.isNotBlank() == true -> {
                    results[task.id] = TaskOcrResult(
                        taskId = task.id,
                        text = task.typedText!!,
                        source = TextSource.TYPED,
                        isHandwritten = false
                    )
                    typedCount++
                }
                task.convertedText?.isNotBlank() == true -> {
                    results[task.id] = TaskOcrResult(
                        taskId = task.id,
                        text = task.convertedText!!,
                        source = TextSource.TYPED,
                        isHandwritten = false
                    )
                    typedCount++
                }
                task.recognizedText?.isNotBlank() == true -> {
                    results[task.id] = TaskOcrResult(
                        taskId = task.id,
                        text = task.recognizedText!!,
                        source = TextSource.STORED,
                        isHandwritten = true
                    )
                    storedCount++
                }
                else -> {
                    tasksNeedingRecognition.add(task)
                }
            }
        }

        // Second pass: recognize remaining tasks grouped by pageId
        if (tasksNeedingRecognition.isNotEmpty() && ensureModelReady()) {
            val freshResults = recognizeTasksBatch(tasksNeedingRecognition, density)

            for ((taskId, result) in freshResults) {
                results[taskId] = result
                if (result.text.isNotBlank()) {
                    freshCount++
                } else {
                    emptyCount++
                }
            }
        } else {
            // Model not ready or no tasks - mark all as empty
            for (task in tasksNeedingRecognition) {
                results[task.id] = TaskOcrResult(
                    taskId = task.id,
                    text = "",
                    source = TextSource.FRESH,
                    isHandwritten = false
                )
                emptyCount++
            }
        }

        Log.i(TAG, "Batch OCR: ${tasks.size} tasks -> $typedCount typed, $storedCount stored, $freshCount fresh, $emptyCount empty")

        return BatchOcrResult(
            results = results,
            stats = OcrStats(
                total = tasks.size,
                withTypedText = typedCount,
                withStoredRecognition = storedCount,
                freshlyRecognized = freshCount,
                empty = emptyCount
            )
        )
    }

    /**
     * Recognize tasks in batch, grouped by pageId for efficiency.
     * Uses parallel processing with fault isolation.
     */
    private suspend fun recognizeTasksBatch(
        tasks: List<Task>,
        density: Float
    ): Map<String, TaskOcrResult> {
        // Group tasks by pageId
        val tasksByPage = tasks.groupBy { it.pageId }

        Log.d(TAG, "Batch recognition: ${tasks.size} tasks across ${tasksByPage.size} pages")

        // Compute canvas dimensions once for the batch (unmeasured -> empty text, never a guess)
        val canvasHeight = getCanvasHeight(density) ?: run {
            Log.w(TAG, "Tasks canvas not measured, batch returns empty text")
            return tasks.associate { it.id to TaskOcrResult(it.id, "", TextSource.FRESH, false) }
        }
        val controlColumnPx = getControlColumnPx()
        val topOffset = getTopOffsetPx(density)

        // Process each page in parallel with fault isolation
        val results = mutableMapOf<String, TaskOcrResult>()

        supervisorScope {
            val pageJobs = tasksByPage.map { (pageId, pageTasks) ->
                async(Dispatchers.Default) {
                    try {
                        recognizePage(pageId, pageTasks, canvasHeight, controlColumnPx, topOffset)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to recognize page $pageId: ${e.message}")
                        // Return empty results for this page
                        pageTasks.associate { task ->
                            task.id to TaskOcrResult(
                                taskId = task.id,
                                text = "",
                                source = TextSource.FRESH,
                                isHandwritten = false
                            )
                        }
                    }
                }
            }

            // Collect results from all pages
            for (job in pageJobs) {
                results.putAll(job.await())
            }
        }

        return results
    }

    /**
     * Recognize all tasks on a single page.
     * Calls extractPage() once and maps results to tasks.
     */
    private suspend fun recognizePage(
        pageId: String,
        tasks: List<Task>,
        canvasHeight: Float,
        controlColumnPx: Float,
        topOffset: Float = 1f
    ): Map<String, TaskOcrResult> {
        val pageResults = digitalInkService.extractPage(pageId, canvasHeight, controlColumnPx, topOffset)
        val resultMap = mutableMapOf<String, TaskOcrResult>()

        for (task in tasks) {
            val slotResult = pageResults.find { it.slotIndex == task.rowIndex }

            val ocrResult = if (slotResult != null && !slotResult.isEmpty && slotResult.text.isNotBlank()) {
                // Store for future use
                withContext(Dispatchers.IO) {
                    try {
                        taskDao.setRecognizedTextById(task.id, slotResult.text)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to store recognition for task ${task.id}: ${e.message}")
                    }
                }

                TaskOcrResult(
                    taskId = task.id,
                    text = slotResult.text,
                    source = TextSource.FRESH,
                    isHandwritten = true
                )
            } else {
                TaskOcrResult(
                    taskId = task.id,
                    text = "",
                    source = TextSource.FRESH,
                    isHandwritten = false
                )
            }

            resultMap[task.id] = ocrResult
        }

        return resultMap
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Build a simple map of taskId -> text.
     *
     * Convenience method for AI context building.
     * Only includes tasks with non-blank text.
     *
     * @param tasks List of tasks to process
     * @param density Screen density
     * @return Map of taskId to text (only non-empty entries)
     */
    suspend fun buildOcrMap(tasks: List<Task>, density: Float): Map<String, String> {
        val batchResult = getTextForTasks(tasks, density)

        return batchResult.results
            .filter { it.value.text.isNotBlank() }
            .mapValues { it.value.text }
    }
}
