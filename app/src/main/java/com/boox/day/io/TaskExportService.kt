package com.boox.day.io

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.boox.day.data.AppRepository
import com.boox.day.data.db.Task
import com.boox.day.editor.ui.TaskExportAction
import com.boox.day.editor.ui.TaskExportFormat
import com.boox.day.editor.ui.TaskExportScope
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Service for exporting tasks to various formats.
 *
 * Supports:
 * - Text format: Plain text with checkboxes (☐/☑)
 * - CSV format: Spreadsheet-compatible with headers
 *
 * Actions:
 * - Copy to clipboard
 * - Share via Android share sheet
 */
class TaskExportService(private val context: Context) {

    companion object {
        private const val TAG = "TaskExportService"
        private const val CACHE_MAX_AGE_MS = 60 * 60 * 1000L  // 1 hour
    }

    private val appRepository = AppRepository(context)
    private val taskRepository = appRepository.taskRepository

    /**
     * Result of an export operation.
     */
    data class ExportResult(
        val success: Boolean,
        val taskCount: Int,
        val message: String
    )

    /**
     * Export tasks based on the selected scope, format, and action.
     *
     * @param scope The export scope (Current Page, Today, This Month, All Tasks)
     * @param format The export format (Text, CSV)
     * @param action The export action (Clipboard, Share)
     * @param dayDate The current day date (for Current Page and Today scopes)
     * @param currentTaskPageIndex The current task page index (for Current Page scope)
     * @param canvasHeight Canvas height for stroke detection (0 = skip stroke check)
     * @return ExportResult with success status and task count
     */
    fun export(
        scope: TaskExportScope,
        format: TaskExportFormat,
        action: TaskExportAction,
        dayDate: String,
        currentTaskPageIndex: Int,
        canvasHeight: Float = 0f
    ): ExportResult {
        // Get tasks based on scope
        val tasks = getTasksForScope(scope, dayDate, currentTaskPageIndex)

        // Filter to only tasks with content (including handwriting-only tasks)
        val tasksWithContent = taskRepository.filterTasksWithContent(tasks, canvasHeight)

        if (tasksWithContent.isEmpty()) {
            return ExportResult(
                success = false,
                taskCount = 0,
                message = "No tasks to export"
            )
        }

        // Generate content based on format (pass canvasHeight for handwriting detection)
        val content = when (format) {
            TaskExportFormat.TEXT -> generateTextFormat(tasksWithContent, scope, canvasHeight)
            TaskExportFormat.CSV -> generateCsvFormat(tasksWithContent, canvasHeight)
        }

        // Perform action
        return when (action) {
            TaskExportAction.CLIPBOARD -> copyToClipboard(content, tasksWithContent.size)
            TaskExportAction.SHARE -> shareContent(content, format, tasksWithContent.size)
        }
    }

    /**
     * Get tasks based on the selected scope.
     */
    private fun getTasksForScope(
        scope: TaskExportScope,
        dayDate: String,
        currentTaskPageIndex: Int
    ): List<Task> {
        return when (scope) {
            TaskExportScope.CURRENT_PAGE -> {
                taskRepository.getTasksForPage(dayDate, currentTaskPageIndex)
            }
            TaskExportScope.TODAY -> {
                val today = LocalDate.now().toString()
                taskRepository.getTasksForDay(today)
            }
            TaskExportScope.THIS_MONTH -> {
                val monthPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                taskRepository.getTasksForMonth(monthPrefix)
            }
            TaskExportScope.ALL_TASKS -> {
                taskRepository.getAllTasks()
            }
        }
    }

    /**
     * Generate plain text format with checkboxes.
     *
     * Example output:
     * ```
     * Tasks - December 11, 2025
     * ─────────────────────────
     * ☑ Buy groceries
     * ☐ [A] Call dentist
     * ☐ [Handwritten task]
     * ```
     */
    private fun generateTextFormat(tasks: List<Task>, scope: TaskExportScope, canvasHeight: Float): String {
        val sb = StringBuilder()

        // Header based on scope
        val header = when (scope) {
            TaskExportScope.CURRENT_PAGE -> "Tasks - Current Page"
            TaskExportScope.TODAY -> {
                val today = LocalDate.now()
                val formatted = today.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
                "Tasks - $formatted"
            }
            TaskExportScope.THIS_MONTH -> {
                val today = LocalDate.now()
                val formatted = today.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                "Tasks - $formatted"
            }
            TaskExportScope.ALL_TASKS -> "All Tasks"
        }

        sb.appendLine(header)
        sb.appendLine("─".repeat(header.length))
        sb.appendLine()

        // Group tasks by date for multi-day scopes
        val groupByDate = scope == TaskExportScope.THIS_MONTH || scope == TaskExportScope.ALL_TASKS

        if (groupByDate) {
            val tasksByDate = tasks.groupBy { it.dayDate }
            val sortedDates = tasksByDate.keys.sortedDescending()

            for (date in sortedDates) {
                val dateTasks = tasksByDate[date] ?: continue

                // Date subheader
                val localDate = LocalDate.parse(date)
                val formattedDate = localDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
                sb.appendLine("## $formattedDate")

                for (task in dateTasks) {
                    sb.appendLine(formatTaskAsText(task, canvasHeight))
                }
                sb.appendLine()
            }
        } else {
            for (task in tasks) {
                sb.appendLine(formatTaskAsText(task, canvasHeight))
            }
        }

        return sb.toString().trimEnd()
    }

    /**
     * Format a single task as text.
     * Shows "[Handwritten task]" for tasks that only have strokes (no typed/converted/recognized text).
     */
    private fun formatTaskAsText(task: Task, canvasHeight: Float): String {
        val checkbox = if (task.isCompleted) "☑" else "☐"
        val priority = if (task.priority.isNotEmpty()) "[${task.priority}] " else ""
        val content = task.typedText?.trim()
            ?: task.convertedText?.trim()
            ?: task.recognizedText?.trim()
            ?: "[Handwritten task]"

        return "$checkbox $priority$content"
    }

    /**
     * Generate CSV format with headers.
     *
     * Columns: Date, Page, Row, Completed, Priority, Content
     */
    private fun generateCsvFormat(tasks: List<Task>, canvasHeight: Float): String {
        val sb = StringBuilder()

        // CSV header
        sb.appendLine("Date,Page,Row,Completed,Priority,Content")

        for (task in tasks) {
            val date = task.dayDate
            val page = task.taskPageIndex + 1  // 1-indexed for users
            val row = task.rowIndex + 1  // 1-indexed for users
            val completed = if (task.isCompleted) "Yes" else "No"
            val priority = task.priority.ifEmpty { "" }
            val content = escapeCsvField(
                task.typedText?.trim()
                    ?: task.convertedText?.trim()
                    ?: task.recognizedText?.trim()
                    ?: "[Handwritten task]"
            )

            sb.appendLine("$date,$page,$row,$completed,$priority,$content")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Escape a CSV field (wrap in quotes if contains comma, quote, or newline).
     */
    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }

    /**
     * Copy content to clipboard.
     */
    private fun copyToClipboard(content: String, taskCount: Int): ExportResult {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Tasks", content)
            clipboard.setPrimaryClip(clip)

            Log.i(TAG, "Copied $taskCount tasks to clipboard")
            ExportResult(
                success = true,
                taskCount = taskCount,
                message = "Copied $taskCount task(s) to clipboard"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to clipboard: ${e.message}", e)
            ExportResult(
                success = false,
                taskCount = 0,
                message = "Failed to copy: ${e.message}"
            )
        }
    }

    /**
     * Share content via Android share sheet.
     */
    private fun shareContent(content: String, format: TaskExportFormat, taskCount: Int): ExportResult {
        // Clean up old cached exports before creating new one
        cleanupOldExports()

        return try {
            val mimeType: String
            val fileName: String
            val fileExtension: String

            when (format) {
                TaskExportFormat.TEXT -> {
                    mimeType = "text/plain"
                    fileName = "tasks"
                    fileExtension = "txt"
                }
                TaskExportFormat.CSV -> {
                    mimeType = "text/csv"
                    fileName = "tasks"
                    fileExtension = "csv"
                }
            }

            // Write content to a cache file
            val cacheDir = File(context.cacheDir, "exports")
            cacheDir.mkdirs()
            val file = File(cacheDir, "$fileName.$fileExtension")
            FileWriter(file).use { writer ->
                writer.write(content)
            }

            // Get content URI via FileProvider
            val contentUri = FileProvider.getUriForFile(
                context,
                "com.boox.day.provider",
                file
            )

            // Create share intent
            val shareIntent = ShareCompat.IntentBuilder(context)
                .setStream(contentUri)
                .setType(mimeType)
                .intent
                .apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            context.startActivity(Intent.createChooser(shareIntent, "Share Tasks"))

            Log.i(TAG, "Shared $taskCount tasks as $fileExtension")
            ExportResult(
                success = true,
                taskCount = taskCount,
                message = "Shared $taskCount task(s)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share: ${e.message}", e)
            ExportResult(
                success = false,
                taskCount = 0,
                message = "Failed to share: ${e.message}"
            )
        }
    }

    /**
     * Delete cached export files older than CACHE_MAX_AGE_MS (1 hour).
     * Called before each share operation to prevent cache buildup.
     */
    private fun cleanupOldExports() {
        try {
            val cacheDir = File(context.cacheDir, "exports")
            if (!cacheDir.exists()) return

            val now = System.currentTimeMillis()
            val files = cacheDir.listFiles() ?: return

            var deletedCount = 0
            for (file in files) {
                if (now - file.lastModified() > CACHE_MAX_AGE_MS) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }

            if (deletedCount > 0) {
                Log.d(TAG, "Cleaned up $deletedCount old export files")
            }
        } catch (e: Exception) {
            // Non-critical, just log
            Log.w(TAG, "Failed to cleanup old exports: ${e.message}")
        }
    }
}
