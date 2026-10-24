package com.boox.day.data.db

import android.content.Context
import android.graphics.Rect
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import android.util.Log
import com.boox.day.data.PageDataManager
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.TasksCanvasRefresh
import com.boox.day.editor.utils.MoveTaskError
import com.boox.day.notifications.TaskNotificationScheduler
import com.boox.day.ocr.TaskTextIndexer
import com.boox.day.editor.utils.MoveTaskResult
import com.boox.day.editor.state.Operation
import com.boox.day.editor.utils.calculateRowBounds
import com.boox.day.editor.utils.hasIntentionalStrokeContent
import com.boox.day.editor.utils.createMigratedStrokes
import com.boox.day.editor.utils.extractStrokesForRow
import com.boox.day.editor.utils.groupStrokesByRow
import com.boox.day.editor.utils.tasksTopOffset
import com.boox.day.tasks.TaskPagination
import java.util.Collections
import java.util.Date
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Task entity for the Tasks tab.
 *
 * Each task belongs to a specific day and task page group.
 * Each task has its own dedicated Page for stroke storage (handwritten content).
 *
 * Hierarchy:
 * - Day (dayDate: "2025-12-02")
 *   - Task Page Group (taskPageIndex: 0, 1, 2...)
 *     - Task Rows (rowIndex: 0-9, 10 tasks per group)
 *       - Each task → Page (for strokes)
 */
@Entity(
    tableName = "task",
    foreignKeys = [
        ForeignKey(
            entity = Page::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
        // Note: parentTaskId intentionally has NO ForeignKey constraint.
        // SQLite doesn't support adding FK via ALTER TABLE, so we handle
        // parent-child relationships at the app level via queries.
        // If a parent is deleted, children become orphans (parentTaskId still set).
    ],
    indices = [
        Index(value = ["dayDate", "taskPageIndex", "rowIndex"], unique = true),
        Index(value = ["pageId"]),
        Index(value = ["dayDate"]),
        Index(value = ["parentTaskId"]),  // For efficient child lookups
        Index(value = ["dueDate"]),  // Legacy (≤ v26) epoch-ms due date column
        Index(value = ["templateId"]),
        Index(value = ["dueDay"]),  // For due date queries (overdue, due today, etc.)
        // One occurrence per recurring template per day (v27; NULLs don't collide)
        Index(value = ["dayDate", "templateId"], unique = true)
    ]
)
data class Task(
    @PrimaryKey val id: String = NanoIdUtils.randomNanoId(),

    // Location identifiers
    val dayDate: String,           // "yyyy-MM-dd" - which day this task belongs to
    val taskPageIndex: Int,        // 0, 1, 2... - which task page group
    val rowIndex: Int,             // 0-9 - position within the group (10 tasks per group)

    // Content reference - each task has its own Page for stroke storage
    val pageId: String,            // FK to Page entity

    // Task metadata
    val isCompleted: Boolean = false,
    val priority: String = "",     // "", "A", "B", "C"
    val typedText: String? = null, // Typed task content (mutually exclusive with strokes)
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val recognizedText: String? = null, // Auto-extracted handwriting (coexists with strokes)
    // AI-generated task text from Convert Memo to Tasks feature
    // Separate from typedText to track content source
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val convertedText: String? = null,

    // Parent-child relationship (for Break Down Task feature)
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val parentTaskId: String? = null,  // FK to Task.id, null = standalone/parent task

    // Rollover tracking (for Agentic Planning System - zombie detection)
    @androidx.room.ColumnInfo(defaultValue = "0")
    val migrationCount: Int = 0,          // Times this task was moved to another day
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val originalDate: String? = null,     // First creation date (yyyy-MM-dd), set when migrationCount > 0
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val lastMigratedAt: Long? = null,     // Epoch ms of last migration
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val estimatedMinutes: Int? = null,    // Duration estimate for capacity planning

    // Legacy due date (epoch ms, ≤ v26). Read only by the v27 migration and old backups;
    // never written. Use [dueDay].
    @androidx.room.ColumnInfo(name = "dueDate", defaultValue = "NULL")
    val legacyDueDateMs: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val templateId: String? = null,

    // Due day "yyyy-MM-dd" (local calendar day, v27). See TaskDueDates for the rules.
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val dueDay: String? = null,

    // Reminder time for this task, minutes after midnight (v28, plan T3 / 13.6A).
    // null = the shared default reminder time from settings.
    @androidx.room.ColumnInfo(defaultValue = "NULL")
    val reminderMinuteOfDay: Int? = null,

    // Timestamps
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

/**
 * Lightweight page info for Tasks pagination (per day).
 *
 * @param dayDate The day these tasks belong to ("yyyy-MM-dd")
 * @param currentIndex 0-based index of the current task page group
 * @param totalPages Total number of task page groups for the day
 */
data class TaskPageInfo(
    val dayDate: String,
    val currentIndex: Int,
    val totalPages: Int
)

/**
 * Information about a task group (parent + children) for bulk operations.
 *
 * Used by group-aware move/copy to track:
 * - The parent task (or standalone task if no children)
 * - All child tasks in the group
 * - Total slots needed (1 + children.size)
 *
 * @param parentTask The parent task (first in the group)
 * @param children Child tasks in row order (may be empty for standalone)
 * @param totalSlots Total number of slots needed (1 for parent + children count)
 */
data class TaskGroupInfo(
    val parentTask: Task,
    val children: List<Task>,
    val totalSlots: Int
)

/**
 * Helper class for bulk querying task pages.
 */
data class TaskPageMapping(
    val dayDate: String,
    val pageId: String
)

/**
 * Result of a due date operation.
 * Used to communicate success/failure with reason to the UI.
 */
sealed class DueDateResult {
    /** Due date was set successfully */
    data class Success(val adjustedChildCount: Int = 0) : DueDateResult()

    /** Child due date cannot be later than parent's due date */
    data class BlockedByParent(val parentDueDay: String) : DueDateResult()
}

/**
 * Result of a copy tasks operation.
 * Provides detailed statistics for user feedback.
 */
data class CopyTasksResult(
    /** Total number of tasks copied */
    val copiedCount: Int,
    /** Number of due dates that were preserved (target date <= due date) */
    val dueDatesPreserved: Int = 0,
    /** Number of due dates that were cleared (target date > due date, already past) */
    val dueDatesCleared: Int = 0
)

/**
 * Data Access Object for Task entity.
 */
@Dao
interface TaskDao {
    /**
     * Get all task pages for a month (mapping dayDate to pageId).
     * Used for efficient batch loading of task indicators.
     */
    @Query("SELECT DISTINCT dayDate, pageId FROM task WHERE dayDate LIKE :monthPrefix || '%'")
    fun getTaskPagesForMonth(monthPrefix: String): List<TaskPageMapping>

    /**
     * Get all tasks for a specific task page group, ordered by row index.
     */
    @Query("SELECT * FROM task WHERE dayDate = :date AND taskPageIndex = :pageIndex ORDER BY rowIndex")
    fun getTasksForPage(date: String, pageIndex: Int): List<Task>

    /**
     * Get the shared pageId for a specific task page group.
     */
    @Query("SELECT pageId FROM task WHERE dayDate = :date AND taskPageIndex = :pageIndex LIMIT 1")
    fun getPageIdForDateAndPageIndex(date: String, pageIndex: Int): String?

    /**
     * Get all tasks with AI-converted text for a date.
     */
    @Query("SELECT * FROM task WHERE convertedText IS NOT NULL AND dayDate = :date ORDER BY taskPageIndex, rowIndex")
    fun getConvertedTasksForDate(date: String): List<Task>

    /**
     * Get a single task by ID.
     */
    @Query("SELECT * FROM task WHERE id = :taskId")
    fun getById(taskId: String): Task?

    /**
     * Get the maximum task page index for a given date.
     * Returns null if no tasks exist for that date.
     */
    @Query("SELECT MAX(taskPageIndex) FROM task WHERE dayDate = :date")
    fun getMaxTaskPageIndex(date: String): Int?

    /**
     * Get all dates that have meaningful tasks for a given month prefix (e.g., "2025-12").
     * "Meaningful" = has at least one completed task, one with priority set, typed/converted text, OR recognized handwriting text.
     * Used for showing task indicators on the calendar.
     * NOTE: Does not include handwriting-only tasks without extraction; use dateHasTaskContent() for comprehensive check.
     */
    @Query("SELECT DISTINCT dayDate FROM task WHERE dayDate LIKE :monthPrefix || '%' AND (isCompleted = 1 OR priority != '' OR typedText IS NOT NULL OR convertedText IS NOT NULL OR recognizedText IS NOT NULL)")
    fun getDatesWithTasksForMonth(monthPrefix: String): List<String>

    /**
     * Get all dates that have any task pages for a given month prefix (e.g., "2025-12").
     * Includes dates with only handwritten content (no metadata).
     * Used to find candidates for stroke-based content check.
     */
    @Query("SELECT DISTINCT dayDate FROM task WHERE dayDate LIKE :monthPrefix || '%'")
    fun getAllDatesWithTaskPagesForMonth(monthPrefix: String): List<String>

    /**
     * Get the count of tasks in a specific task page group.
     */
    @Query("SELECT COUNT(*) FROM task WHERE dayDate = :date AND taskPageIndex = :pageIndex")
    fun getTaskCountForPage(date: String, pageIndex: Int): Int

    /**
     * Update task completion status.
     */
    @Query("UPDATE task SET isCompleted = :completed, updatedAt = :updatedAt WHERE id = :taskId")
    fun setCompleted(taskId: String, completed: Boolean, updatedAt: Date = Date())

    /**
     * Update task priority.
     */
    @Query("UPDATE task SET priority = :priority, updatedAt = :updatedAt WHERE id = :taskId")
    fun setPriority(taskId: String, priority: String, updatedAt: Date = Date())

    /**
     * Update task typed text.
     */
    @Query("UPDATE task SET typedText = :typedText, updatedAt = :updatedAt WHERE id = :taskId")
    fun setTypedText(taskId: String, typedText: String?, updatedAt: Date = Date())

    /**
     * Set AI-converted text for a task (from Convert Memo to Tasks feature).
     */
    @Query("UPDATE task SET convertedText = :text, updatedAt = :now WHERE id = :taskId")
    fun setConvertedText(taskId: String, text: String?, now: Date = Date())

    /**
     * Update task recognized text (from Digital Ink extraction).
     * Maps slot results to tasks by dayDate, rowIndex, and taskPageIndex.
     */
    @Query("UPDATE task SET recognizedText = :text, updatedAt = :updatedAt WHERE dayDate = :dayDate AND rowIndex = :rowIndex AND taskPageIndex = :taskPageIndex")
    fun setRecognizedText(dayDate: String, rowIndex: Int, taskPageIndex: Int, text: String?, updatedAt: Date = Date())

    /**
     * Update task recognized text by task ID.
     * Used when moving tasks to copy recognizedText to target.
     */
    @Query("UPDATE task SET recognizedText = :text, updatedAt = :updatedAt WHERE id = :taskId")
    fun setRecognizedTextById(taskId: String, text: String?, updatedAt: Date = Date())

    /**
     * Update task due date.
     * Pass null to clear the due date.
     */
    @Query("UPDATE task SET dueDay = :dueDay, updatedAt = :updatedAt WHERE id = :taskId")
    fun setDueDay(taskId: String, dueDay: String?, updatedAt: Date = Date())

    /**
     * Update or clear the recurring template link for a task occurrence.
     */
    @Query("UPDATE task SET templateId = :templateId, updatedAt = :updatedAt WHERE id = :taskId")
    fun setTemplateId(taskId: String, templateId: String?, updatedAt: Date = Date())

    /**
     * Clear the recurring template link from all materialized occurrences in a series.
     * Used when deleting a recurring template while keeping existing rows as one-off tasks.
     */
    @Query("UPDATE task SET templateId = NULL, updatedAt = :updatedAt WHERE templateId = :templateId")
    fun clearTemplateIdForTemplate(templateId: String, updatedAt: Date = Date())

    // ========== OPTIMIZED QUERIES (avoid double round-trip) ==========

    /**
     * Toggle task completion status using SQL (no read required).
     * PERFORMANCE: Single UPDATE instead of SELECT + UPDATE pattern.
     */
    @Query("UPDATE task SET isCompleted = NOT isCompleted, updatedAt = :updatedAt WHERE id = :taskId")
    fun toggleCompletedById(taskId: String, updatedAt: Date = Date())

    /**
     * Get only the dayDate for a task (lightweight query for cache invalidation).
     * PERFORMANCE: Single column vs full Task object.
     */
    @Query("SELECT dayDate FROM task WHERE id = :taskId")
    fun getDayDateById(taskId: String): String?

    // ========== PARENT-CHILD RELATIONSHIP QUERIES ==========

    /**
     * Set or clear the parent task reference.
     * Used by Break Down Task to link subtasks to their parent.
     */
    @Query("UPDATE task SET parentTaskId = :parentId, updatedAt = :updatedAt WHERE id = :taskId")
    fun setParentTask(taskId: String, parentId: String?, updatedAt: Date = Date())

    /**
     * Get all child tasks of a parent, ordered by row index.
     */
    @Query("SELECT * FROM task WHERE parentTaskId = :parentId ORDER BY taskPageIndex, rowIndex")
    fun getChildTasks(parentId: String): List<Task>

    /**
     * Get only the IDs of child tasks (lightweight query for notification cancellation).
     * PERFORMANCE: Returns only IDs instead of full Task objects.
     */
    @Query("SELECT id FROM task WHERE parentTaskId = :parentId")
    fun getChildTaskIds(parentId: String): List<String>

    /**
     * Check if a task has any children.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM task WHERE parentTaskId = :taskId)")
    fun hasChildren(taskId: String): Boolean

    /**
     * Get the parent task of a child task.
     */
    @Query("SELECT * FROM task WHERE id = (SELECT parentTaskId FROM task WHERE id = :taskId)")
    fun getParentTask(taskId: String): Task?

    /**
     * Clear parent reference for all children of a task (orphan them).
     */
    @Query("UPDATE task SET parentTaskId = NULL WHERE parentTaskId = :parentId")
    fun orphanChildren(parentId: String)

    /**
     * Mark all child tasks of a parent as completed.
     * PERFORMANCE: Single batch UPDATE for all children at once.
     */
    @Query("UPDATE task SET isCompleted = :completed, updatedAt = :updatedAt WHERE parentTaskId = :parentId")
    fun setChildrenCompleted(parentId: String, completed: Boolean, updatedAt: Date = Date())

    /**
     * Check if all children of a parent are completed.
     * Returns true if parent has no children OR all children are completed.
     */
    @Query("SELECT NOT EXISTS(SELECT 1 FROM task WHERE parentTaskId = :parentId AND isCompleted = 0)")
    fun areAllChildrenCompleted(parentId: String): Boolean

    /**
     * Clear all task metadata (for clearing a slot).
     * Resets ALL user-facing fields to empty/default state:
     * - Content: isCompleted, priority, typedText, convertedText, recognizedText
     * - Relationships: parentTaskId
     * - Scheduling: dueDate, estimatedMinutes
     * - Rollover tracking: migrationCount, originalDate, lastMigratedAt
     * - Recurrence: templateId
     *
     * NOTE: Caller must cancel notifications separately before calling this
     * if the task has a dueDate set.
     */
    @Query("UPDATE task SET isCompleted = 0, priority = '', typedText = NULL, convertedText = NULL, recognizedText = NULL, parentTaskId = NULL, dueDate = NULL, dueDay = NULL, reminderMinuteOfDay = NULL, estimatedMinutes = NULL, migrationCount = 0, originalDate = NULL, lastMigratedAt = NULL, templateId = NULL, updatedAt = :updatedAt WHERE id = :taskId")
    fun clearTaskMetadata(taskId: String, updatedAt: Date = Date())

    /**
     * Move a task to a different date/page/row.
     */
    @Query("UPDATE task SET dayDate = :newDate, taskPageIndex = :newPageIndex, rowIndex = :newRowIndex, updatedAt = :updatedAt WHERE id = :taskId")
    fun moveTask(taskId: String, newDate: String, newPageIndex: Int, newRowIndex: Int, updatedAt: Date = Date())

    /**
     * Full screen Tasks (Oct 2026, tasks/TaskPagination): the row itself moves to another slot of the
     * same day, on another page if needed — same ID, content untouched.
     */
    @Query("UPDATE task SET taskPageIndex = :pageIndex, rowIndex = :rowIndex, pageId = :pageId WHERE id = :taskId")
    fun moveTaskToSlot(taskId: String, pageIndex: Int, rowIndex: Int, pageId: String)

    /** Days with a row at index [rowIndex] or higher (pages of more than 10 rows: full screen Tasks). */
    @Query("SELECT DISTINCT dayDate FROM task WHERE rowIndex >= :rowIndex")
    fun getDaysWithRowsFrom(rowIndex: Int): List<String>

    @Query("SELECT MAX(rowIndex) FROM task WHERE dayDate = :date")
    fun getMaxRowIndex(date: String): Int?

    /**
     * Insert a single task.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(task: Task)

    /**
     * Insert multiple tasks.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tasks: List<Task>)

    /**
     * Delete a task.
     */
    @Delete
    fun delete(task: Task)

    /**
     * Delete all tasks for a specific date.
     */
    @Query("DELETE FROM task WHERE dayDate = :date")
    fun deleteAllForDate(date: String)

    /**
     * Delete all tasks for a specific page index on a date.
     */
    @Query("DELETE FROM task WHERE dayDate = :date AND taskPageIndex = :pageIndex")
    fun deleteTasksForPage(date: String, pageIndex: Int)

    /**
     * Get all distinct page IDs for tasks on a given date.
     * Used for clearing all task pages when "Clear all pages" is invoked.
     */
    @Query("SELECT DISTINCT pageId FROM task WHERE dayDate = :date")
    fun getDistinctPageIdsForDate(date: String): List<String>

    /** Every Tasks page (TasksInkLayout keeps their ink on the rows). */
    @Query("SELECT DISTINCT pageId FROM task")
    fun getAllTaskPageIds(): List<String>

    @Query("SELECT * FROM task WHERE pageId = :pageId")
    fun getTasksForPageId(pageId: String): List<Task>

    /**
     * Decrement taskPageIndex for all tasks with index greater than the deleted one.
     * Used after deleting a page to compact page indices.
     */
    @Query("UPDATE task SET taskPageIndex = taskPageIndex - 1 WHERE dayDate = :date AND taskPageIndex > :deletedPageIndex")
    fun decrementPageIndicesAfter(date: String, deletedPageIndex: Int)

    /**
     * Get all tasks for a date range (inclusive).
     * Used for weekly/monthly statistics.
     */
    @Query("SELECT * FROM task WHERE dayDate >= :startDate AND dayDate <= :endDate")
    fun getTasksForDateRange(startDate: String, endDate: String): List<Task>

    /**
     * Get count of completed tasks for a date range.
     */
    @Query("SELECT COUNT(*) FROM task WHERE dayDate >= :startDate AND dayDate <= :endDate AND isCompleted = 1")
    fun getCompletedCountForDateRange(startDate: String, endDate: String): Int

    /**
     * Get count of tasks with content for a date range.
     * "With content" = text (typed, converted or recognized handwriting), completed, a priority,
     * a due day, or a subtask (plan T18, Oct 2026: handwritten rows used to be left out).
     */
    @Query("SELECT COUNT(*) FROM task WHERE dayDate >= :startDate AND dayDate <= :endDate AND (isCompleted = 1 OR priority != '' OR typedText IS NOT NULL OR convertedText IS NOT NULL OR (recognizedText IS NOT NULL AND recognizedText != '') OR parentTaskId IS NOT NULL OR dueDay IS NOT NULL)")
    fun getTasksWithContentCountForDateRange(startDate: String, endDate: String): Int

    /**
     * Get all distinct dates that have at least one completed task, ordered by date.
     * Used for streak calculation.
     */
    @Query("SELECT DISTINCT dayDate FROM task WHERE isCompleted = 1 ORDER BY dayDate DESC")
    fun getDatesWithCompletedTasks(): List<String>

    /**
     * Get completed task counts grouped by date.
     * Returns pairs of (date, count) for finding best days.
     */
    @Query("SELECT dayDate, COUNT(*) as count FROM task WHERE isCompleted = 1 GROUP BY dayDate ORDER BY count DESC LIMIT 1")
    fun getBestDay(): BestDayResult?

    /**
     * Get all dates with their completed counts for analysis.
     */
    @Query("SELECT dayDate, COUNT(*) as completedCount FROM task WHERE isCompleted = 1 GROUP BY dayDate")
    fun getCompletedCountsByDate(): List<DateCompletedCount>

    // ========== TASK EXPORT QUERIES (Phase 2) ==========

    /**
     * Get all tasks for a specific day, ordered by page index and row index.
     * Used for "Today" scope in task export.
     */
    @Query("SELECT * FROM task WHERE dayDate = :date ORDER BY taskPageIndex, rowIndex")
    fun getTasksForDay(date: String): List<Task>

    /**
     * Get all tasks for a month (by prefix like "2025-12").
     * Ordered by date descending, then page index, then row index.
     * Used for "This Month" scope in task export.
     */
    @Query("SELECT * FROM task WHERE dayDate LIKE :monthPrefix || '%' ORDER BY dayDate DESC, taskPageIndex, rowIndex")
    fun getTasksForMonth(monthPrefix: String): List<Task>

    /**
     * Get ALL tasks across all dates.
     * Ordered by date descending, then page index, then row index.
     * Used for "All Tasks" scope in task export.
     */
    @Query("SELECT * FROM task ORDER BY dayDate DESC, taskPageIndex, rowIndex")
    fun getAllTasks(): List<Task>

    /**
     * Get count of tasks with content for a specific day.
     * "With content" = completed, has priority, or has typed/converted text.
     */
    @Query("SELECT COUNT(*) FROM task WHERE dayDate = :date AND (isCompleted = 1 OR priority != '' OR typedText IS NOT NULL OR convertedText IS NOT NULL OR (recognizedText IS NOT NULL AND recognizedText != '') OR parentTaskId IS NOT NULL OR dueDay IS NOT NULL)")
    fun getTaskCountWithContentForDay(date: String): Int

    /**
     * Get count of tasks with content for a month.
     * "With content" = completed, has priority, or has typed/converted text.
     */
    @Query("SELECT COUNT(*) FROM task WHERE dayDate LIKE :monthPrefix || '%' AND (isCompleted = 1 OR priority != '' OR typedText IS NOT NULL OR convertedText IS NOT NULL OR (recognizedText IS NOT NULL AND recognizedText != '') OR parentTaskId IS NOT NULL OR dueDay IS NOT NULL)")
    fun getTaskCountWithContentForMonth(monthPrefix: String): Int

    /**
     * Get total count of tasks with content across all dates.
     */
    @Query("SELECT COUNT(*) FROM task WHERE isCompleted = 1 OR priority != '' OR typedText IS NOT NULL OR convertedText IS NOT NULL OR (recognizedText IS NOT NULL AND recognizedText != '') OR parentTaskId IS NOT NULL OR dueDay IS NOT NULL")
    fun getTotalTaskCountWithContent(): Int

    /**
     * Get count of distinct days with tasks for a month.
     * Used for summary display in export dialog.
     */
    @Query("SELECT COUNT(DISTINCT dayDate) FROM task WHERE dayDate LIKE :monthPrefix || '%' AND (isCompleted = 1 OR priority != '' OR typedText IS NOT NULL OR convertedText IS NOT NULL OR (recognizedText IS NOT NULL AND recognizedText != '') OR parentTaskId IS NOT NULL OR dueDay IS NOT NULL)")
    fun getTaskDayCountForMonth(monthPrefix: String): Int

    /**
     * Get count of all distinct days with any tasks.
     * Used for "All Tasks" scope summary.
     */
    @Query("SELECT COUNT(DISTINCT dayDate) FROM task WHERE isCompleted = 1 OR priority != '' OR typedText IS NOT NULL OR convertedText IS NOT NULL OR (recognizedText IS NOT NULL AND recognizedText != '') OR parentTaskId IS NOT NULL OR dueDay IS NOT NULL")
    fun getTotalTaskDayCount(): Int

    /**
     * Get count of task pages for a specific day.
     * Returns MAX(taskPageIndex) + 1, or 0 if no tasks.
     */
    @Query("SELECT COALESCE(MAX(taskPageIndex) + 1, 0) FROM task WHERE dayDate = :date")
    fun getTaskPageCountForDay(date: String): Int

    // ========== ROLLOVER TRACKING / ZOMBIE DETECTION (Phase 16) ==========

    /**
     * Get all "zombie" tasks - incomplete tasks that have been migrated 3+ times.
     * These are tasks the user keeps deferring and may need attention.
     * Returns tasks ordered by migration count (highest first).
     */
    @Query("SELECT * FROM task WHERE isCompleted = 0 AND migrationCount >= :minMigrations ORDER BY migrationCount DESC")
    fun getZombieTasks(minMigrations: Int = 3): List<Task>

    /**
     * Get zombie tasks for a specific date.
     * Used for daily planning where we want to surface zombies that landed on this day.
     */
    @Query("SELECT * FROM task WHERE dayDate = :date AND isCompleted = 0 AND migrationCount >= :minMigrations ORDER BY migrationCount DESC")
    fun getZombieTasksForDate(date: String, minMigrations: Int = 3): List<Task>

    /**
     * Get incomplete tasks for a date range with content (for capacity planning).
     * "With content" = has typed/converted text or priority set.
     */
    @Query("SELECT * FROM task WHERE dayDate >= :startDate AND dayDate <= :endDate AND isCompleted = 0 AND (typedText IS NOT NULL OR convertedText IS NOT NULL OR priority != '') ORDER BY dayDate, taskPageIndex, rowIndex")
    fun getIncompleteTasksWithContentForDateRange(startDate: String, endDate: String): List<Task>

    /**
     * Get overdue incomplete tasks (tasks from before today that are not completed).
     * Used for daily planning to surface tasks that need attention.
     */
    @Query("SELECT * FROM task WHERE dayDate < :today AND isCompleted = 0 AND (typedText IS NOT NULL OR convertedText IS NOT NULL OR priority != '') ORDER BY dayDate DESC, taskPageIndex, rowIndex")
    fun getOverdueTasks(today: String): List<Task>

    /**
     * Update migration tracking when a task is moved to a different day.
     * - Increments migrationCount
     * - Sets originalDate if this is the first migration
     * - Updates lastMigratedAt timestamp
     */
    @Query("UPDATE task SET migrationCount = migrationCount + 1, originalDate = COALESCE(originalDate, :originalDate), lastMigratedAt = :timestamp, updatedAt = :timestamp WHERE id = :taskId")
    fun incrementMigrationCount(taskId: String, originalDate: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update estimated minutes for capacity planning.
     * AI-assigned duration estimate.
     */
    @Query("UPDATE task SET estimatedMinutes = :minutes, updatedAt = :updatedAt WHERE id = :taskId")
    fun setEstimatedMinutes(taskId: String, minutes: Int?, updatedAt: Date = Date())

    /** Per-task reminder time in minutes after midnight; null = the shared default (v28). */
    @Query("UPDATE task SET reminderMinuteOfDay = :minuteOfDay, updatedAt = :updatedAt WHERE id = :taskId")
    fun setReminderMinuteOfDay(taskId: String, minuteOfDay: Int?, updatedAt: Date = Date())

    /**
     * Get total estimated minutes for incomplete tasks on a date.
     * Used for capacity analysis.
     */
    @Query("SELECT COALESCE(SUM(estimatedMinutes), 0) FROM task WHERE dayDate = :date AND isCompleted = 0")
    fun getTotalEstimatedMinutesForDate(date: String): Int

    /**
     * Clear rollover tracking (reset migration count and related fields).
     * Used when a task is completed or user wants to reset its history.
     */
    @Query("UPDATE task SET migrationCount = 0, originalDate = NULL, lastMigratedAt = NULL, updatedAt = :updatedAt WHERE id = :taskId")
    fun clearRolloverTracking(taskId: String, updatedAt: Date = Date())

    /**
     * Transfer rollover tracking metadata from one task to another.
     * Used during child task compaction to preserve migration history.
     */
    @Query("""
        UPDATE task SET
          migrationCount = :migrationCount,
          originalDate = :originalDate,
          lastMigratedAt = :lastMigratedAt,
          updatedAt = :updatedAt
        WHERE id = :taskId
    """)
    fun updateRolloverTracking(
        taskId: String,
        migrationCount: Int,
        originalDate: String?,
        lastMigratedAt: Long?,
        updatedAt: Date = Date()
    )

    // ========== DUE DATE QUERIES (Events Panel "Due" Tab) ==========

    /**
     * Get tasks due on a specific date (incomplete only).
     * dateString format: "yyyy-MM-dd"
     * Returns Flow for reactive UI updates.
     */
    @Query("""
        SELECT * FROM task
        WHERE dueDay = :dateString
        AND isCompleted = 0
        ORDER BY dayDate ASC, taskPageIndex ASC, rowIndex ASC
    """)
    fun getTasksDueOnDateFlow(dateString: String): kotlinx.coroutines.flow.Flow<List<Task>>

    /**
     * Get overdue tasks (due before today, still incomplete).
     * todayString format: "yyyy-MM-dd"
     * All of them, oldest first (the Due panel pages the list; it used to stop at 10).
     */
    @Query("""
        SELECT * FROM task
        WHERE dueDay IS NOT NULL
        AND isCompleted = 0
        AND dueDay < :todayString
        ORDER BY dueDay ASC, dayDate ASC, taskPageIndex ASC, rowIndex ASC
    """)
    fun getOverdueTasksFlow(todayString: String): kotlinx.coroutines.flow.Flow<List<Task>>

    /**
     * Get count of tasks due on a specific date (for tab badge).
     * Returns Flow for reactive badge updates.
     */
    @Query("""
        SELECT COUNT(*) FROM task
        WHERE dueDay = :dateString
        AND isCompleted = 0
    """)
    fun getTasksDueOnDateCount(dateString: String): kotlinx.coroutines.flow.Flow<Int>

    /**
     * Get count of overdue tasks (for tab badge).
     */
    @Query("""
        SELECT COUNT(*) FROM task
        WHERE dueDay IS NOT NULL
        AND isCompleted = 0
        AND dueDay < :todayString
    """)
    fun getOverdueTasksCount(todayString: String): kotlinx.coroutines.flow.Flow<Int>

    // ========== NOTIFICATION QUERIES ==========

    /**
     * Check if task is completed (lightweight for notification logic).
     */
    @Query("SELECT isCompleted FROM task WHERE id = :taskId")
    fun isCompleted(taskId: String): Boolean

    /**
     * Get all incomplete tasks that have due dates set.
     * Used by BootReceiver and TimeZoneChangeReceiver to reschedule alarms.
     */
    @Query("""
        SELECT * FROM task
        WHERE isCompleted = 0
        AND dueDay IS NOT NULL
    """)
    suspend fun getIncompleteTasWithDueDates(): List<Task>

    /**
     * Get incomplete tasks due between two days (inclusive, "yyyy-MM-dd").
     * Used by DueDateNotificationWorker for daily scan.
     */
    @Query("""
        SELECT * FROM task
        WHERE isCompleted = 0
        AND dueDay IS NOT NULL
        AND dueDay >= :startDay
        AND dueDay <= :endDay
        ORDER BY dueDay ASC
    """)
    fun getTasksDueBetween(startDay: String, endDay: String): List<Task>

    // ========== DIGITAL INK RECOGNITION QUERIES ==========

    /**
     * Check if a pageId belongs to a Task page.
     * Returns count > 0 if it's a task page.
     * Used by DigitalInkService to determine if extraction should run.
     */
    @Query("SELECT COUNT(*) FROM task WHERE pageId = :pageId LIMIT 1")
    fun isTaskPage(pageId: String): Int

    /**
     * Get dayDate for a given pageId.
     * Used by TaskSemanticBridge to create unified OCR entries.
     */
    @Query("SELECT dayDate FROM task WHERE pageId = :pageId LIMIT 1")
    fun getDayDateByPageId(pageId: String): String?

    /** Task pages with text read from handwriting (TaskTextExtractor reads them again when its rules change). */
    @Query("SELECT DISTINCT pageId FROM task WHERE recognizedText IS NOT NULL AND recognizedText != ''")
    fun getPageIdsWithRecognizedText(): List<String>

    /**
     * Get taskPageIndex for a given pageId.
     * Used by TaskSemanticBridge to query all tasks for combined text.
     */
    @Query("SELECT taskPageIndex FROM task WHERE pageId = :pageId LIMIT 1")
    fun getTaskPageIndexByPageId(pageId: String): Int?

    /**
     * Search tasks by word-boundary match on text content.
     * Matches "Mary" at start of field or after a space — NOT inside "primary".
     */
    @Query("""
        SELECT * FROM task
        WHERE typedText LIKE :query || '%' OR typedText LIKE '% ' || :query || '%'
           OR convertedText LIKE :query || '%' OR convertedText LIKE '% ' || :query || '%'
           OR recognizedText LIKE :query || '%' OR recognizedText LIKE '% ' || :query || '%'
        ORDER BY dayDate DESC
        LIMIT :limit
    """)
    suspend fun searchTasksExact(query: String, limit: Int = 30): List<Task>

    /**
     * Search tasks by substring match (partial/fuzzy).
     */
    @Query("""
        SELECT * FROM task
        WHERE typedText LIKE '%' || :query || '%'
           OR convertedText LIKE '%' || :query || '%'
           OR recognizedText LIKE '%' || :query || '%'
        ORDER BY dayDate DESC
        LIMIT :limit
    """)
    suspend fun searchTasksPartial(query: String, limit: Int = 30): List<Task>
}

/**
 * Result class for best day query.
 */
data class BestDayResult(
    val dayDate: String,
    val count: Int
)

/**
 * Result class for date-based completed counts.
 */
data class DateCompletedCount(
    val dayDate: String,
    val completedCount: Int
)

/**
 * Combined due tasks data for Events Panel "Due" tab.
 * Separates tasks due today from overdue tasks from previous days.
 */
data class DueTasksData(
    val dueTodayTasks: List<Task>,
    val overdueTasks: List<Task>
) {
    /** True if there are no due or overdue tasks */
    val isEmpty: Boolean get() = dueTodayTasks.isEmpty() && overdueTasks.isEmpty()

    /** Total count of all due and overdue tasks */
    val totalCount: Int get() = dueTodayTasks.size + overdueTasks.size

    companion object {
        val EMPTY = DueTasksData(emptyList(), emptyList())
    }
}

// Task page cache entry (shared by all TaskRepository instances)
private data class TaskPageCacheEntry(
    val tasks: List<Task>,
    val pageInfo: TaskPageInfo
)

/**
 * Repository for Task operations.
 */
class TaskRepository(internal val context: Context) {
    private val taskDao = AppDatabase.getDatabase(context).taskDao()
    private val pageDao = AppDatabase.getDatabase(context).pageDao()
    private val strokeDao = AppDatabase.getDatabase(context).strokeDao()
    private val recurringTaskTemplateDao = AppDatabase.getDatabase(context).recurringTaskTemplateDao()
    private val recurringTaskExceptionDao = AppDatabase.getDatabase(context).recurringTaskExceptionDao()
    private val recurringTaskMaterializer by lazy { RecurringTaskMaterializer(context, this) }

    companion object {
        private const val TAG = "TaskRepo"
        private const val RECURRING_EXCEPTION_SKIP = "SKIP"
        private const val RECURRING_EXCEPTION_DETACHED = "DETACHED"

        // Signal emitted when a task is modified (completed/priority changed)
        // MainScreen listens to this to update calendar Tasks badges in real-time
        // Buffer capacity 2 to handle task move emitting both source and target dates
        private val _taskChanged = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 2)
        val taskChanged = _taskChanged.asSharedFlow()

        // PERFORMANCE: Cache for task page queries (saves ~25-260ms per cached hit).
        // One cache for the whole process (plan §13.2-6, Oct 2026): the app creates several
        // TaskRepository instances, and per-instance caches missed each other's invalidations.
        // Thread-safe: Wrapped with synchronizedMap for concurrent access from main/background threads
        private val taskPageCache = Collections.synchronizedMap(
            object : LinkedHashMap<String, TaskPageCacheEntry>(32, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TaskPageCacheEntry>): Boolean {
                    return size > 60  // Cache ~2 months of task pages (matches other repository caches)
                }
            }
        )
    }

    // Lazy-initialized notification scheduler (only created when first needed)
    private val notificationScheduler by lazy { TaskNotificationScheduler(context) }

    /**
     * Schedule notification for a task if notifications are enabled.
     * Safe to call - catches all exceptions to avoid breaking core functionality.
     */
    private fun scheduleTaskNotificationIfEnabled(task: Task) {
        try {
            val settings = GlobalAppSettings.current
            if (!settings.taskNotificationsEnabled) return
            if (task.isCompleted || task.dueDay == null) return

            notificationScheduler.scheduleTaskAlarm(
                task = task,
                reminderHour = settings.defaultTaskReminderHour,
                reminderMinute = settings.defaultTaskReminderMinute
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule task notification: ${e.message}")
        }
    }

    /**
     * Cancel notification for a task.
     * Safe to call even if no notification was scheduled.
     */
    private fun cancelTaskNotification(taskId: String) {
        try {
            notificationScheduler.cancelTaskAlarm(taskId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel task notification: ${e.message}")
        }
    }

    /**
     * The one due-day write for task rows (plan T2/T12, Oct 2026): writes the day and keeps the
     * reminder in step (cancelled, then scheduled again when a day is set). Inside an
     * [inTransaction] operation the reminder work runs after the commit.
     */
    private fun writeDueDay(taskId: String, dueDay: String?) {
        taskDao.setDueDay(taskId, dueDay)
        afterCommit {
            cancelTaskNotification(taskId)
            if (dueDay != null) taskDao.getById(taskId)?.let { scheduleTaskNotificationIfEnabled(it) }
        }
    }

    /**
     * Moves a task's content from [source] onto the row [targetTaskId] — the ONE place that
     * carries every content column (plan T2 §13.2-3, Oct 2026; chosen instead of swapping row
     * positions): check mark, priority, typed/converted/recognized text, estimate, deferral
     * history, repeat link and due day with its reminder. A new column only needs adding here.
     *
     * @param keepTemplateLink false when the task moves to another day (the occurrence detaches)
     * @param releaseSource unlink the source's repeat link (v27 unique (dayDate, templateId)) and
     *   cancel its reminder. Sort passes false: its sources and targets overlap and its wipe
     *   phase has already released every slot.
     * The caller sets the parent link and clears the source row.
     */
    private fun transferTaskContent(
        source: Task,
        targetTaskId: String,
        keepTemplateLink: Boolean,
        releaseSource: Boolean = true,
        priority: String = source.priority
    ) {
        if (releaseSource) {
            if (source.templateId != null) taskDao.setTemplateId(source.id, null)
            if (source.dueDay != null) afterCommit { cancelTaskNotification(source.id) }
        }
        taskDao.setCompleted(targetTaskId, source.isCompleted)
        taskDao.setPriority(targetTaskId, priority)
        taskDao.setTypedText(targetTaskId, source.typedText)
        taskDao.setConvertedText(targetTaskId, source.convertedText)
        // recognizedText is not displayed (TaskDrawingAreaOverlay), so it always moves with the
        // row (search/AI/Convert keep working on moved ink)
        taskDao.setRecognizedTextById(targetTaskId, source.recognizedText)
        taskDao.setEstimatedMinutes(targetTaskId, source.estimatedMinutes)
        taskDao.updateRolloverTracking(targetTaskId, source.migrationCount, source.originalDate, source.lastMigratedAt)
        taskDao.setTemplateId(targetTaskId, if (keepTemplateLink) source.templateId else null)
        taskDao.setReminderMinuteOfDay(targetTaskId, source.reminderMinuteOfDay)
        // Due date moves with the task, also to another day (T11), and its reminder follows (T12)
        writeDueDay(targetTaskId, source.dueDay)
    }

    // Effects queued by the multi-step operation running in this thread's transaction.
    private val pendingAfterCommit = ThreadLocal<MutableList<() -> Unit>?>()

    /**
     * Runs a multi-step task operation's database writes in ONE transaction, so a failure part-way
     * rolls back every write instead of leaving rows half-moved or half-deleted. In-memory effects
     * (stroke cache, reminders) are queued with [afterCommit] and run only after the commit.
     * A call made while another operation's transaction is running joins it.
     */
    private fun <T> inTransaction(block: () -> T): T {
        if (pendingAfterCommit.get() != null) return block()
        val effects = mutableListOf<() -> Unit>()
        pendingAfterCommit.set(effects)
        val result = try {
            AppDatabase.getDatabase(context).runInTransaction<T> { block() }
        } finally {
            pendingAfterCommit.remove()
        }
        effects.forEach { it() }
        return result
    }

    /** Runs [effect] after the current [inTransaction] commits (immediately outside one). */
    private fun afterCommit(effect: () -> Unit) {
        val effects = pendingAfterCommit.get()
        if (effects != null) effects += effect else effect()
    }

    private fun insertRecurringException(
        templateId: String,
        occurrenceDate: String,
        exceptionType: String
    ) {
        recurringTaskExceptionDao.insert(
            RecurringTaskException(
                templateId = templateId,
                occurrenceDate = occurrenceDate,
                exceptionType = exceptionType
            )
        )
    }

    private fun markRecurringOccurrenceSkipped(task: Task) {
        val templateId = task.templateId ?: return
        insertRecurringException(
            templateId = templateId,
            occurrenceDate = task.dayDate,
            exceptionType = RECURRING_EXCEPTION_SKIP
        )
    }

    private fun markRecurringOccurrenceDetached(task: Task, targetDate: String) {
        val templateId = task.templateId ?: return
        if (targetDate == task.dayDate) return
        insertRecurringException(
            templateId = templateId,
            occurrenceDate = task.dayDate,
            exceptionType = RECURRING_EXCEPTION_DETACHED
        )
    }

    private fun getRecurringSourceText(task: Task): String? {
        return task.typedText?.takeIf { it.isNotBlank() }
            ?: task.convertedText?.takeIf { it.isNotBlank() }
            ?: task.recognizedText?.takeIf { it.isNotBlank() }
    }

    fun getRecurringSeriesBlockedReason(task: Task): String? {
        if (task.parentTaskId != null) {
            return "Subtasks cannot repeat on their own yet."
        }

        if (taskDao.hasChildren(task.id)) {
            return "Tasks with subtasks cannot repeat yet."
        }

        if (getRecurringSourceText(task) == null) {
            return "This task needs typed or recognized text before it can repeat."
        }

        return null
    }

    fun getRecurringTemplateById(templateId: String): RecurringTaskTemplate? {
        return recurringTaskTemplateDao.getById(templateId)
    }

    fun getRecurringTemplatesByIds(templateIds: Collection<String>): Map<String, RecurringTaskTemplate> {
        val uniqueIds = templateIds
            .filter { it.isNotBlank() }
            .distinct()
        if (uniqueIds.isEmpty()) return emptyMap()
        return recurringTaskTemplateDao.getByIds(uniqueIds).associateBy { it.id }
    }

    fun saveRecurringTemplateForTask(
        taskId: String,
        draft: RecurringTaskDraft
    ): SaveRecurringTaskTemplateResult {
        val task = taskDao.getById(taskId)
            ?: return SaveRecurringTaskTemplateResult(blockedReason = "Task no longer exists.")

        val blockedReason = getRecurringSeriesBlockedReason(task)
        if (blockedReason != null) {
            return SaveRecurringTaskTemplateResult(blockedReason = blockedReason)
        }

        val sourceText = getRecurringSourceText(task)
            ?: return SaveRecurringTaskTemplateResult(
                blockedReason = "This task needs typed or recognized text before it can repeat."
            )

        val recurrenceType = draft.recurrenceType.uppercase()
        val anchorDate = try {
            LocalDate.parse(draft.anchorDate)
        } catch (_: Exception) {
            return SaveRecurringTaskTemplateResult(blockedReason = "Repeat start date is invalid.")
        }
        val endDate = draft.endDate?.takeIf { it.isNotBlank() }?.let {
            try {
                LocalDate.parse(it)
            } catch (_: Exception) {
                return SaveRecurringTaskTemplateResult(blockedReason = "Repeat end date is invalid.")
            }
        }

        if (endDate != null && endDate.isBefore(anchorDate)) {
            return SaveRecurringTaskTemplateResult(
                blockedReason = "Repeat end date must be on or after the start date."
            )
        }

        val normalizedDaysOfWeek = draft.daysOfWeek
            .filter { it in 1..7 }
            .distinct()
            .sorted()
        if (recurrenceType == "CUSTOM_WEEKDAYS" && normalizedDaysOfWeek.isEmpty()) {
            return SaveRecurringTaskTemplateResult(
                blockedReason = "Choose at least one weekday for this repeating task."
            )
        }

        val normalizedDayOfMonth = when (recurrenceType) {
            "MONTHLY" -> {
                val day = draft.dayOfMonth
                if (day !in 1..28) {
                    return SaveRecurringTaskTemplateResult(
                        blockedReason = "Monthly repeat needs a day between 1 and 28."
                    )
                }
                day
            }
            else -> null
        }

        val normalizedIntervalDays = draft.intervalDays.coerceAtLeast(1)
        val existingTemplate = task.templateId?.let { recurringTaskTemplateDao.getById(it) }
        val template = (existingTemplate ?: RecurringTaskTemplate(
            title = sourceText,
            defaultPriority = task.priority,
            defaultEstimatedMinutes = task.estimatedMinutes,
            recurrenceType = recurrenceType,
            daysOfWeek = null,
            dayOfMonth = null,
            intervalDays = normalizedIntervalDays,
            anchorDate = anchorDate.toString(),
            endDate = endDate?.toString(),
            isActive = true,
            isHabit = draft.isHabit
        )).copy(
            title = sourceText,
            defaultPriority = task.priority,
            defaultEstimatedMinutes = task.estimatedMinutes,
            recurrenceType = recurrenceType,
            daysOfWeek = if (recurrenceType == "CUSTOM_WEEKDAYS") {
                "[${normalizedDaysOfWeek.joinToString(",")}]"
            } else {
                null
            },
            dayOfMonth = normalizedDayOfMonth,
            intervalDays = if (recurrenceType == "DAILY") normalizedIntervalDays else 1,
            anchorDate = anchorDate.toString(),
            endDate = endDate?.toString(),
            isHabit = draft.isHabit,
            updatedAt = System.currentTimeMillis()
        )

        AppDatabase.getDatabase(context).runInTransaction {
            // T4: upsert, never REPLACE — REPLACE cascades away the series' exceptions
            recurringTaskTemplateDao.upsert(template)
            taskDao.setTemplateId(task.id, template.id)
        }

        invalidateTaskCache(task.dayDate)
        _taskChanged.tryEmit(task.dayDate)
        invalidateIntelligenceCache()

        return SaveRecurringTaskTemplateResult(template = template)
    }

    fun setRecurringTemplateActive(templateId: String, isActive: Boolean) {
        val template = recurringTaskTemplateDao.getById(templateId) ?: return
        if (template.isActive == isActive) return

        recurringTaskTemplateDao.setActive(templateId, isActive)
        invalidateTaskCache()
        invalidateIntelligenceCache()
    }

    fun deleteRecurringSeries(templateId: String) {
        val template = recurringTaskTemplateDao.getById(templateId) ?: return
        AppDatabase.getDatabase(context).runInTransaction {
            taskDao.clearTemplateIdForTemplate(templateId)
            recurringTaskTemplateDao.delete(template)
        }
        invalidateTaskCache()
        invalidateIntelligenceCache()
    }

    /**
     * Schedule notifications for existing tasks when task notifications are enabled.
     * Safe to call from background; no-op if task notifications are disabled.
     */
    suspend fun scheduleNotificationsForExistingTasks() {
        val settings = GlobalAppSettings.current
        if (!settings.taskNotificationsEnabled) return

        try {
            notificationScheduler.scheduleDailyScan()
            val tasksWithDueDates = taskDao.getIncompleteTasWithDueDates()
            tasksWithDueDates.forEach { task ->
                cancelTaskNotification(task.id)
                notificationScheduler.scheduleTaskAlarm(
                    task = task,
                    reminderHour = settings.defaultTaskReminderHour,
                    reminderMinute = settings.defaultTaskReminderMinute
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule existing task notifications: ${e.message}")
        }
    }

    /**
     * Invalidate task page cache for a specific date or all dates.
     * Call this when tasks are modified.
     *
     * Thread-safe: Keys are snapshot under synchronized block to prevent
     * ConcurrentModificationException during iteration.
     */
    fun invalidateTaskCache(date: String? = null) {
        if (date != null) {
            // SAFE: Snapshot keys under lock, then remove outside lock
            val keysToRemove = synchronized(taskPageCache) {
                taskPageCache.keys.filter { it.startsWith("$date:") }.toList()
            }
            keysToRemove.forEach { taskPageCache.remove(it) }
        } else {
            taskPageCache.clear()
        }
    }

    /**
     * Invalidate Intelligence Engine cache when tasks are modified.
     * This ensures AI features (Smart Suggestions, Prioritization, etc.)
     * receive fresh scoring after task changes.
     *
     * Call after:
     * - Task created/deleted/completed
     * - Task due date changed
     * - Task moved to different day
     * - Task priority changed
     */
    fun invalidateIntelligenceCache() {
        try {
            // Use static method for efficient cache invalidation (no need to create IntelligenceEngine instance)
            com.boox.day.ai.intelligence.IntelligenceCache.invalidateAll()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to invalidate intelligence cache: ${e.message}")
        }
    }

    /**
     * Non-suspending cache lookup for fast day switching.
     */
    fun getCachedTasksForPage(
        date: String,
        pageIndex: Int
    ): Pair<List<Task>, TaskPageInfo>? {
        val requestedIndex = pageIndex.coerceAtLeast(0)
        val cacheKey = "$date:$requestedIndex"
        return taskPageCache[cacheKey]?.let { cached ->
            cached.tasks to cached.pageInfo
        }
    }

    /**
     * Non-suspending cache lookup for Tasks page ID (for bitmap preloading).
     * Returns the page ID for the first task page (index 0) of a given date,
     * or null if not in cache.
     */
    fun getCachedTaskPageIdForDate(date: String): String? {
        return getCachedTasksForPage(date, 0)?.first?.firstOrNull()?.pageId
    }

    /**
     * Lightweight lookup for the shared pageId of a task page group.
     *
     * This is used to switch the visible Tasks canvas to the new day sooner,
     * without waiting for the full task list load to complete.
     */
    fun getOrCreateTaskPageId(date: String, pageIndex: Int): String? {
        val requestedIndex = pageIndex.coerceAtLeast(0)
        val cacheKey = "$date:$requestedIndex"

        taskPageCache[cacheKey]?.tasks?.firstOrNull()?.pageId?.let { cachedPageId ->
            return cachedPageId
        }

        val maxExistingIndex = getMaxTaskPageIndex(date)

        if (maxExistingIndex < 0) {
            val tasks = createTaskPage(date, 0)
            val info = TaskPageInfo(
                dayDate = date,
                currentIndex = 0,
                totalPages = 1
            )
            taskPageCache["$date:0"] = TaskPageCacheEntry(tasks, info)
            return tasks.firstOrNull()?.pageId
        }

        return when {
            requestedIndex <= maxExistingIndex -> {
                taskDao.getPageIdForDateAndPageIndex(date, requestedIndex)
            }
            requestedIndex == maxExistingIndex + 1 -> {
                val tasks = createTaskPage(date, requestedIndex)
                val info = TaskPageInfo(
                    dayDate = date,
                    currentIndex = requestedIndex,
                    totalPages = requestedIndex + 1
                )
                taskPageCache[cacheKey] = TaskPageCacheEntry(tasks, info)
                tasks.firstOrNull()?.pageId
            }
            else -> {
                taskDao.getPageIdForDateAndPageIndex(date, maxExistingIndex)
            }
        }
    }

    /**
     * Pre-warm the task cache for all dates with tasks in a month.
     * Called when month changes to speed up Tasks tab loading.
     * @param yearMonth Format: "yyyy-MM" (e.g., "2025-12")
     * @return Set of dates with meaningful tasks (for calendar badges)
     */
    fun preWarmCacheForMonth(yearMonth: String): Set<String> {
        val start = System.currentTimeMillis()
        val datesWithTasks = taskDao.getDatesWithTasksForMonth(yearMonth)
        var cachedCount = 0
        datesWithTasks.forEach { date ->
            val cacheKey = "$date:0"  // Pre-warm first page (most common)
            if (!taskPageCache.containsKey(cacheKey)) {
                val tasks = taskDao.getTasksForPage(date, 0)
                if (tasks.isNotEmpty()) {
                    val maxIndex = taskDao.getMaxTaskPageIndex(date) ?: 0
                    val pageInfo = TaskPageInfo(
                        dayDate = date,
                        currentIndex = 0,
                        totalPages = maxIndex + 1
                    )
                    taskPageCache[cacheKey] = TaskPageCacheEntry(tasks, pageInfo)
                    cachedCount++
                }
            }
        }
        Log.i(TAG, "PERF: preWarmCacheForMonth $yearMonth - cached $cachedCount dates (${System.currentTimeMillis() - start}ms)")
        return datesWithTasks.toSet()
    }

    /**
     * Get all tasks for a specific task page group.
     */
    fun getTasksForPage(date: String, pageIndex: Int): List<Task> {
        return taskDao.getTasksForPage(date, pageIndex)
    }

    /**
     * Get a single task by ID.
     */
    fun getById(taskId: String): Task? {
        return taskDao.getById(taskId)
    }

    /**
     * Get the maximum task page index for a date.
     * Returns -1 if no tasks exist.
     */
    fun getMaxTaskPageIndex(date: String): Int {
        return taskDao.getMaxTaskPageIndex(date) ?: -1
    }

    /**
     * Get high-level page info (current index and total pages) for Tasks on a date.
     * Returns null if there are no task pages for the given date.
     */
    fun getTaskPageInfo(date: String): TaskPageInfo? {
        val maxIndex = getMaxTaskPageIndex(date)
        if (maxIndex < 0) return null
        // Default to the first page (index 0) – caller can override currentIndex as needed
        return TaskPageInfo(
            dayDate = date,
            currentIndex = 0,
            totalPages = maxIndex + 1
        )
    }

    /**
     * Get all dates with tasks for a given month (for calendar indicators).
     * Includes dates with only handwritten content (strokes) when canvasHeight is provided.
     * @param yearMonth Format: "yyyy-MM" (e.g., "2025-12")
     * @param canvasHeight Optional canvas height for stroke content check. If 0 or not provided,
     *                     only metadata-based tasks are returned (faster but incomplete).
     */
    fun getDatesWithTasksForMonth(yearMonth: String, canvasHeight: Float = 0f): List<String> {
        val start = System.currentTimeMillis()
        
        // 1. Fast path: get dates with metadata tasks (completed, priority, typed text)
        // This query is already efficient (single SQL scan)
        val resultDates = taskDao.getDatesWithTasksForMonth(yearMonth).toMutableSet()

        // If no canvas height provided, return metadata-only results
        if (canvasHeight <= 0f) {
            return resultDates.toList()
        }

        // 2. Batch check for handwritten content
        // Instead of iterating 31 days -> 31 queries, we do:
        // Query A: Get all (day, pageId) pairs for the month
        // Query B: Check which of those pageIds have strokes
        
        val taskPages = taskDao.getTaskPagesForMonth(yearMonth)
        if (taskPages.isEmpty()) {
            return resultDates.toList()
        }
        
        val allPageIds = taskPages.map { it.pageId }.distinct()
        
        // Split into chunks to avoid SQLite variable limit
        val chunks = allPageIds.chunked(900)
        val pagesWithStrokes = mutableSetOf<String>()
        
        for (chunk in chunks) {
            pagesWithStrokes.addAll(strokeDao.getPagesWithStrokes(chunk))
        }
        
        // 3. Add dates that have strokes
        val datesWithStrokes = taskPages
            .filter { pagesWithStrokes.contains(it.pageId) }
            .map { it.dayDate }
            
        resultDates.addAll(datesWithStrokes)
        
        Log.d(TAG, "PERF: getDatesWithTasksForMonth optimized - ${taskPages.size} pages checked in ${System.currentTimeMillis() - start}ms")
        return resultDates.toList()
    }

    /**
     * Check if a specific date has meaningful task content.
     * "Meaningful" includes:
     * - Tasks with metadata (isCompleted=true OR priority set)
     * - Tasks with typed or converted text
     * - Tasks with INTENTIONAL handwritten strokes (not just overflow from adjacent rows)
     *
     * This is used for real-time calendar badge updates when strokes are drawn.
     *
     * PERFORMANCE: Uses two-pass approach to avoid N+1 queries:
     * 1. First pass checks in-memory cache (fast, returns early if found)
     * 2. Second pass batch-fetches strokes for uncached pages (single query)
     *
     * @param date The date to check, format "yyyy-MM-dd"
     * @param canvasHeight The canvas height in pixels (needed for row bounds calculation)
     * @return true if the date has any meaningful task content
     */
    fun dateHasTaskContent(date: String, canvasHeight: Float): Boolean {
        // First check: Does the date have tasks with metadata? (fast DB query)
        val hasMetadataTasks = taskDao.getDatesWithTasksForMonth(date.substring(0, 7)).contains(date)
        if (hasMetadataTasks) return true

        // Second check: Look for typed/converted text or recognized handwriting text
        val tasksForDate = taskDao.getTasksForDay(date)
        val hasTextContent = tasksForDate.any {
            it.typedText?.isNotBlank() == true ||
                it.convertedText?.isNotBlank() == true ||
                it.recognizedText?.isNotBlank() == true
        }
        if (hasTextContent) return true

        // Third check: Look for intentional strokes on any task page
        val pageIds = taskDao.getDistinctPageIdsForDate(date)
        if (pageIds.isEmpty()) return false

        val rowCount = 10  // Standard task row count
        val uncachedPages = mutableSetOf<String>()

        // PERFORMANCE: First pass - check in-memory cache (fast, early return)
        for (pageId in pageIds) {
            val tasksOnPage = tasksForDate.filter { it.pageId == pageId }

            for (task in tasksOnPage) {
                if (task.rowIndex !in 0 until rowCount) continue

                val rowBounds = com.boox.day.editor.utils.calculateRowBounds(
                    task.rowIndex, canvasHeight, rowCount
                )
                // Try in-memory cache first
                val cachedStrokes = PageDataManager.getStrokesInRectangle(rowBounds.toRect(), pageId)
                if (cachedStrokes != null) {
                    if (com.boox.day.editor.utils.hasIntentionalStrokeContent(cachedStrokes, rowBounds)) {
                        return true
                    }
                } else {
                    // Page not in cache - collect for batch fetch
                    uncachedPages.add(pageId)
                }
            }
        }

        // PERFORMANCE: Second pass - batch fetch strokes for uncached pages (single query vs N queries)
        if (uncachedPages.isEmpty()) return false

        val allStrokes = strokeDao.getAllForPages(uncachedPages.toList())
        val strokesByPage = allStrokes.groupBy { it.pageId }

        for (pageId in uncachedPages) {
            val pageStrokes = strokesByPage[pageId] ?: continue
            val tasksOnPage = tasksForDate.filter { it.pageId == pageId }

            for (task in tasksOnPage) {
                if (task.rowIndex !in 0 until rowCount) continue

                val rowBounds = com.boox.day.editor.utils.calculateRowBounds(
                    task.rowIndex, canvasHeight, rowCount
                )
                val strokesInRow = pageStrokes.filter { stroke ->
                    stroke.bottom > rowBounds.topY && stroke.top < rowBounds.bottomY
                }

                if (com.boox.day.editor.utils.hasIntentionalStrokeContent(strokesInRow, rowBounds)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Data class for period statistics.
     */
    data class PeriodStats(
        val completedCount: Int,
        val totalWithContent: Int,
        val periodLabel: String
    )

    /**
     * Get task statistics for a date range.
     * @param startDate Start date (inclusive), format "yyyy-MM-dd"
     * @param endDate End date (inclusive), format "yyyy-MM-dd"
     * @param periodLabel Label for the period (e.g., "Today", "This Week")
     */
    fun getStatsForDateRange(startDate: String, endDate: String, periodLabel: String): PeriodStats {
        val completed = taskDao.getCompletedCountForDateRange(startDate, endDate)
        val withContent = taskDao.getTasksWithContentCountForDateRange(startDate, endDate)
        return PeriodStats(
            completedCount = completed,
            totalWithContent = withContent,
            periodLabel = periodLabel
        )
    }

    /**
     * Get task statistics for today.
     */
    fun getTodayStats(): PeriodStats {
        val today = java.time.LocalDate.now().toString()
        return getStatsForDateRange(today, today, "Today")
    }

    /**
     * Get task statistics for the current week (Monday to Sunday).
     */
    fun getWeekStats(): PeriodStats {
        val today = java.time.LocalDate.now()
        val startOfWeek = today.with(java.time.DayOfWeek.MONDAY)
        val endOfWeek = today.with(java.time.DayOfWeek.SUNDAY)
        return getStatsForDateRange(startOfWeek.toString(), endOfWeek.toString(), "This Week")
    }

    /**
     * Get task statistics for the current month.
     */
    fun getMonthStats(): PeriodStats {
        val today = java.time.LocalDate.now()
        val startOfMonth = today.withDayOfMonth(1)
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
        return getStatsForDateRange(startOfMonth.toString(), endOfMonth.toString(), "This Month")
    }

    /**
     * Get task statistics for a specific week containing the given date.
     */
    fun getWeekStatsForDate(date: java.time.LocalDate): PeriodStats {
        val startOfWeek = date.with(java.time.DayOfWeek.MONDAY)
        val endOfWeek = date.with(java.time.DayOfWeek.SUNDAY)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d")
        val label = "${startOfWeek.format(formatter)} - ${endOfWeek.format(formatter)}"
        return getStatsForDateRange(startOfWeek.toString(), endOfWeek.toString(), label)
    }

    /**
     * Get task statistics for a specific month containing the given date.
     */
    fun getMonthStatsForDate(date: java.time.LocalDate): PeriodStats {
        val startOfMonth = date.withDayOfMonth(1)
        val endOfMonth = date.withDayOfMonth(date.lengthOfMonth())
        val label = date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
        return getStatsForDateRange(startOfMonth.toString(), endOfMonth.toString(), label)
    }

    /**
     * Data class for engagement stats (streaks, bests, etc.)
     */
    data class EngagementStats(
        val currentStreak: Int,
        val longestStreak: Int,
        val bestDay: BestRecord?,
        val bestWeek: BestRecord?,
        val bestMonth: BestRecord?,
        val totalCompleted: Int,
        val totalDaysActive: Int
    )

    data class BestRecord(
        val label: String,
        val count: Int
    )

    /**
     * Calculate current streak (consecutive days with completed tasks ending today or yesterday).
     */
    fun getCurrentStreak(): Int {
        val today = java.time.LocalDate.now()
        // T18 (Oct 2026): completed rows moved to a future day don't count (they made the
        // most recent completion lie in the future, which read as a broken streak)
        val dates = taskDao.getDatesWithCompletedTasks()
            .mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            .filter { !it.isAfter(today) }
            .sortedDescending()
        if (dates.isEmpty()) return 0

        // Check if streak is still active (today or yesterday has completions)
        val mostRecent = dates.first()
        if (mostRecent != today && mostRecent != today.minusDays(1)) {
            return 0  // Streak broken
        }

        var streak = 1
        var currentDate = mostRecent

        for (i in 1 until dates.size) {
            val prevDate = dates[i]
            if (prevDate == currentDate.minusDays(1)) {
                streak++
                currentDate = prevDate
            } else {
                break
            }
        }

        return streak
    }

    /**
     * Calculate longest streak ever.
     */
    fun getLongestStreak(): Int {
        val datesWithCompleted = taskDao.getDatesWithCompletedTasks()
        if (datesWithCompleted.isEmpty()) return 0

        val dates = datesWithCompleted.map { java.time.LocalDate.parse(it) }.sorted()

        var longestStreak = 1
        var currentStreak = 1

        for (i in 1 until dates.size) {
            if (dates[i] == dates[i - 1].plusDays(1)) {
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }

        return longestStreak
    }

    /**
     * Get best day (most completed tasks in a single day).
     */
    fun getBestDay(): BestRecord? {
        val result = taskDao.getBestDay() ?: return null
        val date = java.time.LocalDate.parse(result.dayDate)
        val label = date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
        return BestRecord(label, result.count)
    }

    /**
     * Get best week (most completed tasks in a single week).
     */
    fun getBestWeek(): BestRecord? {
        val countsByDate = taskDao.getCompletedCountsByDate()
        if (countsByDate.isEmpty()) return null

        // Group by week (Monday-Sunday)
        val weekCounts = mutableMapOf<java.time.LocalDate, Int>()
        for (entry in countsByDate) {
            val date = java.time.LocalDate.parse(entry.dayDate)
            val weekStart = date.with(java.time.DayOfWeek.MONDAY)
            weekCounts[weekStart] = (weekCounts[weekStart] ?: 0) + entry.completedCount
        }

        val best = weekCounts.maxByOrNull { it.value } ?: return null
        val weekEnd = best.key.with(java.time.DayOfWeek.SUNDAY)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d")
        val label = "${best.key.format(formatter)} - ${weekEnd.format(formatter)}"
        return BestRecord(label, best.value)
    }

    /**
     * Get best month (most completed tasks in a single month).
     */
    fun getBestMonth(): BestRecord? {
        val countsByDate = taskDao.getCompletedCountsByDate()
        if (countsByDate.isEmpty()) return null

        // Group by month
        val monthCounts = mutableMapOf<String, Int>()
        for (entry in countsByDate) {
            val date = java.time.LocalDate.parse(entry.dayDate)
            val monthKey = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
            monthCounts[monthKey] = (monthCounts[monthKey] ?: 0) + entry.completedCount
        }

        val best = monthCounts.maxByOrNull { it.value } ?: return null
        val date = java.time.LocalDate.parse("${best.key}-01")
        val label = date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
        return BestRecord(label, best.value)
    }

    /**
     * Get all engagement stats in one call (for efficiency).
     */
    fun getEngagementStats(): EngagementStats {
        val countsByDate = taskDao.getCompletedCountsByDate()
        val totalCompleted = countsByDate.sumOf { it.completedCount }

        return EngagementStats(
            currentStreak = getCurrentStreak(),
            longestStreak = getLongestStreak(),
            bestDay = getBestDay(),
            bestWeek = getBestWeek(),
            bestMonth = getBestMonth(),
            totalCompleted = totalCompleted,
            totalDaysActive = countsByDate.size
        )
    }

    /**
     * Create a new task page group with 10 task metadata entries.
     * All tasks share ONE Page for stroke storage (single canvas approach).
     *
     * NEW APPROACH: Tasks are just metadata (checkbox, priority) that track
     * "rows" on a single canvas. User writes tasks directly on horizontal
     * lines on the shared canvas.
     *
     * @param date The date for the tasks (format: "yyyy-MM-dd")
     * @param pageIndex The task page group index (0, 1, 2...)
     * @return List of created tasks
     */
    fun createTaskPage(date: String, pageIndex: Int): List<Task> {
        // Create ONE shared Page for all tasks in this task page group
        // This is the single canvas where user handwrites their tasks
        val sharedPage = Page()
        pageDao.create(sharedPage)

        // 10 rows; more while full screen shows this day's Tasks (Oct 2026, tasks/TaskPagination)
        val tasks = (0 until TaskPagination.rowsPerPage(date)).map { rowIndex ->
            Task(
                dayDate = date,
                taskPageIndex = pageIndex,
                rowIndex = rowIndex,
                pageId = sharedPage.id  // All tasks reference the same page
            )
        }
        taskDao.insertAll(tasks)
        return tasks
    }

    /**
     * Ensure that a task page has 10 Task rows (0-9) for a given date/index.
     * If some rows are missing, lightweight metadata entries are created that
     * all share the same Page as the existing tasks.
     */
    private fun ensureTenTasks(date: String, pageIndex: Int, existingTasks: List<Task>): List<Task> {
        if (existingTasks.isEmpty()) return existingTasks

        val usedRows = existingTasks.map { it.rowIndex }.toSet()
        // 10 rows; more while full screen shows this day's Tasks (Oct 2026, tasks/TaskPagination)
        val missingRows = (0 until TaskPagination.rowsPerPage(date)).filter { it !in usedRows }
        if (missingRows.isEmpty()) return existingTasks.sortedBy { it.rowIndex }

        val sharedPageId = existingTasks.first().pageId
        val newTasks = missingRows.map { rowIndex ->
            Task(
                dayDate = date,
                taskPageIndex = pageIndex,
                rowIndex = rowIndex,
                pageId = sharedPageId
            )
        }
        taskDao.insertAll(newTasks)
        return (existingTasks + newTasks).sortedBy { it.rowIndex }
    }

    /**
     * Get a single task by its ID.
     * Used for cache deserialization.
     */
    fun getTaskById(taskId: String): Task? {
        return taskDao.getById(taskId)
    }


    /**
     * Get tasks for a specific page, creating the page (and its 10 tasks) if needed.
     *
     * @param date The date for the tasks (format: "yyyy-MM-dd")
     * @param pageIndex Desired task page index (0, 1, 2...). If this is one past
     *        the last existing index, a new page will be created. If it is larger
     *        than that, it will be clamped to the last existing page.
     */
    fun getOrCreateTasksForPage(date: String, pageIndex: Int): Pair<List<Task>, TaskPageInfo> {
        val start = System.currentTimeMillis()
        val requestedIndex = pageIndex.coerceAtLeast(0)
        val cacheKey = "$date:$requestedIndex"

        // PERFORMANCE: Check cache first
        taskPageCache[cacheKey]?.let { cached ->
            Log.i(TAG, "PERF: getOrCreateTasksForPage CACHE HIT for $cacheKey (0ms)")
            return cached.tasks to cached.pageInfo
        }

        val maxExistingIndex = getMaxTaskPageIndex(date)

        // No tasks yet for this date – always create the first page
        if (maxExistingIndex < 0) {
            val tasks = createTaskPage(date, 0)
            val info = TaskPageInfo(
                dayDate = date,
                currentIndex = 0,
                totalPages = 1
            )
            // Cache the result
            taskPageCache["$date:0"] = TaskPageCacheEntry(tasks, info)
            Log.i(TAG, "PERF: getOrCreateTasksForPage CACHE MISS (created) for $date:0 (${System.currentTimeMillis() - start}ms)")
            return tasks to info
        }

        // If requested index is within existing range, just load that page
        if (requestedIndex <= maxExistingIndex) {
            val rawTasks = getTasksForPage(date, requestedIndex)
            val tasks = ensureTenTasks(date, requestedIndex, rawTasks)
            val info = TaskPageInfo(
                dayDate = date,
                currentIndex = requestedIndex,
                totalPages = maxExistingIndex + 1
            )
            // Cache the result
            taskPageCache[cacheKey] = TaskPageCacheEntry(tasks, info)
            Log.i(TAG, "PERF: getOrCreateTasksForPage CACHE MISS (loaded) for $cacheKey (${System.currentTimeMillis() - start}ms)")
            return tasks to info
        }

        // If requested index is exactly one past the last, try to create a new page
        if (requestedIndex == maxExistingIndex + 1) {

            // Create the new page
            val tasks = createTaskPage(date, requestedIndex)
            val info = TaskPageInfo(
                dayDate = date,
                currentIndex = requestedIndex,
                totalPages = requestedIndex + 1
            )
            // Cache the result
            taskPageCache[cacheKey] = TaskPageCacheEntry(tasks, info)
            Log.i(TAG, "PERF: getOrCreateTasksForPage CACHE MISS (new page) for $cacheKey (${System.currentTimeMillis() - start}ms)")
            return tasks to info
        }

        // Otherwise, clamp to the last existing page
        val clampedIndex = maxExistingIndex
        val rawTasks = getTasksForPage(date, clampedIndex)
        val tasks = ensureTenTasks(date, clampedIndex, rawTasks)
        val info = TaskPageInfo(
            dayDate = date,
            currentIndex = clampedIndex,
            totalPages = maxExistingIndex + 1
        )
        // Cache the result (use clamped key)
        val clampedKey = "$date:$clampedIndex"
        taskPageCache[clampedKey] = TaskPageCacheEntry(tasks, info)
        Log.i(TAG, "PERF: getOrCreateTasksForPage CACHE MISS (clamped) for $clampedKey (${System.currentTimeMillis() - start}ms)")
        return tasks to info
    }

    /**
     * Toggle task completion status.
     * Delegates to setCompleted() for consistent notification handling.
     */
    fun toggleCompleted(taskId: String) {
        // Delegate to setCompleted() which handles notifications consistently
        // Read current state and flip it
        val task = taskDao.getById(taskId) ?: return
        setCompleted(taskId, !task.isCompleted)

        // Note: setCompleted() already handles:
        // - Database update
        // - Cache invalidation
        // - Badge updates
        // - Notification cancel/reschedule
    }

    /**
     * Set task completion status.
     * PERFORMANCE: UPDATE first, then lightweight dayDate fetch (vs SELECT * + UPDATE pattern).
     * GROUP-AWARE: Bidirectional cascade between parent and child tasks.
     *
     * Cascade rules:
     * - Parent checked → all children checked
     * - Parent unchecked → all children unchecked
     * - Child checked + all siblings checked → parent checked
     * - Child unchecked → parent unchecked
     */
    fun setCompleted(taskId: String, completed: Boolean) {
        // Do the update first
        taskDao.setCompleted(taskId, completed)

        // Get task to check if it's a parent or child
        val task = taskDao.getById(taskId)

        // GROUP-AWARE CASCADE: Handle parent-child relationships
        if (task != null) {
            val childIds = taskDao.getChildTaskIds(taskId)
            val isParent = childIds.isNotEmpty()
            val isChild = task.parentTaskId != null

            if (isParent) {
                // This is a PARENT task - cascade to children in both directions
                taskDao.setChildrenCompleted(taskId, completed)

                if (completed) {
                    // Cancel notifications for all completed children
                    childIds.forEach { childId ->
                        cancelTaskNotification(childId)
                    }
                    Log.i(TAG, "TASK_COMPLETION: Completed ${childIds.size} child task(s) of parent $taskId")
                } else {
                    // Reschedule notifications for uncompleted children with due dates
                    childIds.forEach { childId ->
                        taskDao.getById(childId)?.let { childTask ->
                            if (childTask.dueDay != null) {
                                scheduleTaskNotificationIfEnabled(childTask)
                            }
                        }
                    }
                    Log.i(TAG, "TASK_COMPLETION: Uncompleted ${childIds.size} child task(s) of parent $taskId")
                }
            } else if (isChild) {
                // This is a CHILD task - may affect parent
                val parentId = task.parentTaskId!!

                if (completed) {
                    // Child completed - check if ALL siblings are now complete
                    if (taskDao.areAllChildrenCompleted(parentId)) {
                        // All children complete → auto-complete parent
                        taskDao.setCompleted(parentId, true)
                        cancelTaskNotification(parentId)
                        Log.i(TAG, "TASK_COMPLETION: Auto-completed parent $parentId (all children done)")
                    }
                } else {
                    // Child uncompleted - parent cannot be complete
                    val parent = taskDao.getById(parentId)
                    if (parent?.isCompleted == true) {
                        taskDao.setCompleted(parentId, false)
                        // Reschedule parent notification if it has due date
                        if (parent.dueDay != null) {
                            scheduleTaskNotificationIfEnabled(parent)
                        }
                        Log.i(TAG, "TASK_COMPLETION: Auto-uncompleted parent $parentId (child unchecked)")
                    }
                }
            }
        }

        // Lightweight fetch of just dayDate for cache invalidation
        val dayDate = taskDao.getDayDateById(taskId)
        dayDate?.let {
            invalidateTaskCache(it)
            // Signal for real-time badge updates
            _taskChanged.tryEmit(it)
        }

        // Invalidate Intelligence Engine cache for AI features
        invalidateIntelligenceCache()

        // Handle notification: cancel when completed, reschedule when un-completed
        if (completed) {
            cancelTaskNotification(taskId)
        } else {
            // Task un-completed - reschedule notification if it has a due date
            taskDao.getById(taskId)?.let { t ->
                if (t.dueDay != null) {
                    scheduleTaskNotificationIfEnabled(t)
                }
            }
        }
    }

    /**
     * Compare priorities. Returns negative if p1 < p2, 0 if equal, positive if p1 > p2.
     * Priority ranking: A > B > C > "" (empty)
     */
    private fun comparePriority(p1: String, p2: String): Int {
        val order = mapOf("A" to 3, "B" to 2, "C" to 1, "" to 0)
        return (order[p1] ?: 0) - (order[p2] ?: 0)
    }

    /**
     * Set task priority.
     * PERFORMANCE: UPDATE first, then lightweight dayDate fetch (vs SELECT * + UPDATE pattern).
     * GROUP-AWARE: Maintains invariant that parent priority >= highest child priority.
     *
     * Cascade rules:
     * - Child priority set higher than parent → auto-upgrade parent
     * - Parent priority set lower than highest child → auto-downgrade children to match
     *
     * @param priority One of: "", "A", "B", "C"
     * @param canvasHeight Tasks canvas height; when > 0, handwriting in the row counts as content
     * (plan T2, Oct 2026). 0 = text/child check only.
     */
    fun setPriority(taskId: String, priority: String, canvasHeight: Float = 0f) {
        // GUARD: Fetch task first to check content
        val task = taskDao.getById(taskId) ?: return

        // Allow clearing priority on any slot (cleanup operation)
        if (priority.isBlank()) {
            taskDao.setPriority(taskId, priority)
            invalidateTaskCache(task.dayDate)
            _taskChanged.tryEmit(task.dayDate)
            invalidateIntelligenceCache()
            return
        }

        // GUARD: Block setting priority on empty slots (prevents orphaned metadata)
        // Real content = text, a child task, or (with canvasHeight) handwriting in the row
        val hasRealContent = taskHasRealContent(task, canvasHeight)

        if (!hasRealContent) {
            Log.w(TAG, "BLOCKED: Cannot set priority '$priority' on empty slot $taskId (no text content)")
            return
        }

        // Do the update
        taskDao.setPriority(taskId, priority)

        // GROUP-AWARE CASCADE: Handle parent-child priority relationships
        val childIds = taskDao.getChildTaskIds(taskId)
        val isParent = childIds.isNotEmpty()
        val isChild = task.parentTaskId != null

        if (isParent) {
            // This is a PARENT task - ensure no child has higher priority
            val children = taskDao.getChildTasks(taskId)
            for (child in children) {
                if (comparePriority(child.priority, priority) > 0) {
                    // Child has higher priority than new parent priority → downgrade child
                    taskDao.setPriority(child.id, priority)
                    Log.i(TAG, "TASK_PRIORITY: Downgraded child ${child.id} to $priority (parent constraint)")
                }
            }
        } else if (isChild) {
            // This is a CHILD task - may need to upgrade parent
            val parentId = task.parentTaskId!!
            val parent = taskDao.getById(parentId)
            if (parent != null && comparePriority(priority, parent.priority) > 0) {
                // Child priority > parent priority → upgrade parent
                taskDao.setPriority(parentId, priority)
                Log.i(TAG, "TASK_PRIORITY: Upgraded parent $parentId to $priority (child constraint)")
            }
        }

        // Cache invalidation
        invalidateTaskCache(task.dayDate)
        _taskChanged.tryEmit(task.dayDate)
        invalidateIntelligenceCache()
    }

    /**
     * Set task typed text.
     * Triggers debounced semantic indexing via TaskTextIndexer.
     * @param typedText The text content, or null to clear
     */
    fun setTypedText(taskId: String, typedText: String?) {
        // Do the update first
        taskDao.setTypedText(taskId, typedText)

        // Fetch full task info for cache invalidation and indexing
        val task = taskDao.getById(taskId)
        task?.let {
            invalidateTaskCache(it.dayDate)
            // Signal for real-time badge updates
            _taskChanged.tryEmit(it.dayDate)
            invalidateIntelligenceCache()

            // Trigger typed text indexing (debounced, semantic extraction)
            // Only if TaskTextIndexer is already initialized (lazy startup)
            TaskTextIndexer.getInstanceOrNull()?.onTypedTextChanged(
                taskId = taskId,
                dayDate = it.dayDate,
                taskPageIndex = it.taskPageIndex,
                pageId = it.pageId
            )
        }
    }

    /**
     * Set task recognized text (from Digital Ink extraction).
     * Called per-slot when handwriting is converted to text in background.
     */
    fun setRecognizedText(dayDate: String, rowIndex: Int, taskPageIndex: Int, text: String?) {
        taskDao.setRecognizedText(dayDate, rowIndex, taskPageIndex, text)
        // Note: No cache invalidation needed - this is background indexing
    }

    /**
     * Set task due date.
     * GROUP-AWARE: Enforces parent-child due date constraints.
     *
     * Constraint rules:
     * - Child due date cannot be later than parent's due date (blocks the action)
     * - Parent due date set earlier than children → auto-adjusts children to match
     * - If parent has no due date, no constraint on children
     *
     * @param dueDay The due day "yyyy-MM-dd" (see TaskDueDates), or null to clear
     * @param canvasHeight Tasks canvas height; when > 0, handwriting in the row counts as content
     * (plan T2, Oct 2026). 0 = text/child check only.
     * @return DueDateResult indicating success or blocked reason
     */
    fun setDueDay(taskId: String, dueDay: String?, canvasHeight: Float = 0f): DueDateResult {
        // Get task to check parent-child relationships
        val task = taskDao.getById(taskId) ?: return DueDateResult.Success()

        // GUARD: Allow clearing dueDay on any slot (cleanup operation)
        if (dueDay == null) {
            taskDao.setDueDay(taskId, null)
            cancelTaskNotification(taskId)
            invalidateTaskCache(task.dayDate)
            _taskChanged.tryEmit(task.dayDate)
            invalidateIntelligenceCache()
            return DueDateResult.Success()
        }

        // GUARD: Block setting dueDay on empty slots (prevents orphaned metadata)
        // Real content = text, a child task, or (with canvasHeight) handwriting in the row
        val hasRealContent = taskHasRealContent(task, canvasHeight)

        if (!hasRealContent) {
            Log.w(TAG, "BLOCKED: Cannot set dueDay on empty slot $taskId (no text content)")
            return DueDateResult.Success() // Return success to not break UI, but silently block
        }

        val childIds = taskDao.getChildTaskIds(taskId)
        val isParent = childIds.isNotEmpty()
        val isChild = task.parentTaskId != null

        // CONSTRAINT: Child due date cannot exceed parent's due date
        if (isChild && dueDay != null) {
            val parent = taskDao.getById(task.parentTaskId!!)
            if (parent?.dueDay != null && dueDay > parent.dueDay) {
                // Block: child due date is later than parent's
                Log.i(TAG, "TASK_DUE_DATE: Blocked - child dueDay ($dueDay) exceeds parent (${parent.dueDay})")
                return DueDateResult.BlockedByParent(parent.dueDay)
            }
        }

        // Apply the update to this task
        taskDao.setDueDay(taskId, dueDay)

        // CONSTRAINT: Parent due date set earlier → auto-adjust children
        var adjustedChildCount = 0
        if (isParent && dueDay != null) {
            val children = taskDao.getChildTasks(taskId)
            for (child in children) {
                if (child.dueDay != null && child.dueDay > dueDay) {
                    // Child has later due date → adjust to match parent
                    taskDao.setDueDay(child.id, dueDay)
                    // Update notification for adjusted child
                    taskDao.getById(child.id)?.let { updatedChild ->
                        scheduleTaskNotificationIfEnabled(updatedChild)
                    }
                    adjustedChildCount++
                    Log.i(TAG, "TASK_DUE_DATE: Auto-adjusted child ${child.id} dueDay to $dueDay")
                }
            }
        }

        // Cache invalidation
        val dayDate = taskDao.getDayDateById(taskId)
        dayDate?.let {
            invalidateTaskCache(it)
            _taskChanged.tryEmit(it)
        }

        // Invalidate Intelligence Engine cache for AI features
        invalidateIntelligenceCache()

        // Handle notification for this task
        if (dueDay != null) {
            taskDao.getById(taskId)?.let { t ->
                scheduleTaskNotificationIfEnabled(t)
            }
        } else {
            cancelTaskNotification(taskId)
        }

        return DueDateResult.Success(adjustedChildCount)
    }

    /**
     * Set or clear the parent task reference.
     * Used by Break Down Task to link subtasks to their parent.
     * @param taskId The child task to update
     * @param parentId The parent task ID, or null to make it standalone
     */
    fun setParentTask(taskId: String, parentId: String?) {
        val task = taskDao.getById(taskId)
        taskDao.setParentTask(taskId, parentId)
        task?.let {
            invalidateTaskCache(it.dayDate)
        }
    }

    /**
     * Get all child tasks of a parent task.
     */
    fun getChildTasks(parentId: String): List<Task> {
        return taskDao.getChildTasks(parentId)
    }

    /**
     * Check if a task has any children.
     */
    fun hasChildren(taskId: String): Boolean {
        return taskDao.hasChildren(taskId)
    }

    /**
     * Get the parent task of a child task.
     */
    fun getParentTask(taskId: String): Task? {
        return taskDao.getParentTask(taskId)
    }

    /**
     * Get the complete task group (parent + all children) for any task.
     *
     * This is the foundation for group-aware move/copy operations.
     * Given any task ID (parent, child, or standalone), returns the complete
     * group as an ordered list: [parent, child1, child2, ...].
     *
     * For standalone tasks (no parent, no children), returns single-element list.
     * For orphaned children (parent deleted), treats as standalone.
     *
     * @param taskId ID of any task in the group
     * @return Ordered list: parent first, then children by row index. Empty if task not found.
     */
    fun getTaskGroup(taskId: String): List<Task> {
        val task = taskDao.getById(taskId) ?: return emptyList()

        // Determine the parent ID
        val parentId: String
        val parent: Task

        if (task.parentTaskId != null) {
            // This is a child task - get its parent
            val parentTask = taskDao.getParentTask(taskId)
            if (parentTask == null) {
                // Orphaned child (parent deleted) - treat as standalone
                Log.i(TAG, "TASK_GROUP: Task $taskId is orphaned child, treating as standalone")
                return listOf(task)
            }
            parentId = parentTask.id
            parent = parentTask
        } else {
            // This might be a parent or standalone
            parentId = task.id
            parent = task
        }

        // Get all children of the parent
        val children = taskDao.getChildTasks(parentId)

        // If no children and task is not a child itself, it's standalone
        if (children.isEmpty() && task.parentTaskId == null) {
            return listOf(task)
        }

        // Return parent + children (children already sorted by taskPageIndex, rowIndex)
        return listOf(parent) + children
    }

    /**
     * Find a contiguous block of empty slots on a task page.
     *
     * Used for placing parent-child task groups which must stay together.
     * Groups cannot be split across non-contiguous rows.
     *
     * @param needed Number of consecutive empty slots required
     * @param usedRows Set of row indices (0-9) that are already occupied
     * @return Starting row index of the contiguous block, or null if not found
     */
    fun findContiguousSlots(needed: Int, usedRows: Set<Int>): Int? {
        if (needed <= 0 || needed > 10) return null

        // Find all available rows (sorted)
        val availableRows = (0 until 10).filter { it !in usedRows }.sorted()

        if (availableRows.size < needed) return null  // Not enough total slots

        // Find contiguous block
        for (i in 0..(availableRows.size - needed)) {
            var isContiguous = true
            for (j in 0 until needed) {
                if (availableRows[i + j] != availableRows[i] + j) {
                    isContiguous = false
                    break
                }
            }
            if (isContiguous) {
                return availableRows[i]  // Starting row of contiguous block
            }
        }

        return null  // No contiguous block found
    }

    /**
     * Clears every row of one task page in ONE transaction (plan §13.2-4, Oct 2026): ink,
     * text, scheduling and links. A failure part-way rolls the whole page back.
     */
    fun clearTasksOnPage(date: String, pageIndex: Int, canvasHeight: Float) {
        inTransaction {
            for (task in taskDao.getTasksForPage(date, pageIndex)) {
                clearTaskSlot(task, canvasHeight)
            }
        }
    }

    /**
     * Clear a task slot completely (metadata + strokes + notifications).
     * Resets ALL user-facing fields and deletes any strokes in the row bounds.
     * If this task was a parent, its children become orphans (parentTaskId cleared).
     *
     * @param task The task to clear
     * @param canvasHeight The canvas height for row bounds calculation
     * @param recordRecurringException When true, clearing a recurring occurrence
     * records a one-day exception so it does not re-materialize immediately.
     */
    fun clearTaskSlot(
        task: Task,
        canvasHeight: Float,
        recordRecurringException: Boolean = true
    ) {
        val sourcePageId = task.pageId
        val sourceRowIndex = task.rowIndex

        // Cancel notification FIRST (before clearing dueDate) — after commit inside a transaction
        if (task.dueDay != null) {
            afterCommit { cancelTaskNotification(task.id) }
        }

        if (recordRecurringException) {
            markRecurringOccurrenceSkipped(task)
        }

        // If this task is a parent, orphan its children first
        // This prevents dangling parentTaskId references
        if (taskDao.hasChildren(task.id)) {
            taskDao.orphanChildren(task.id)
            Log.i(TAG, "CLEAR_SLOT: Orphaned children of parent task ${task.id}")
        }

        // Full screen Tasks (Oct 2026): pages of more rows of the same height (tasks/TaskPagination)
        val rowCount = TaskPagination.rowsPerPage(task.dayDate)
        val rowsHeight = TaskPagination.rowsHeight(canvasHeight, rowCount, tasksTopOffset())

        // Calculate row bounds
        val rowBounds = calculateRowBounds(
            rowIndex = sourceRowIndex,
            canvasHeight = rowsHeight,
            rowCount = rowCount
        )

        // Get strokes in this row — from memory, or the DB when the page isn't loaded
        // (plan §13.2-4, Oct 2026: a cleared row's ink used to stay behind on unloaded pages)
        val strokeRepository = StrokeRepository(context)
        val sourceRect = rowBounds.toRect()
        val allStrokes = PageDataManager.getStrokesInRectangle(sourceRect, sourcePageId)
            ?: strokeRepository.getAllForPage(sourcePageId)
        val strokesInRow = extractStrokesForRow(allStrokes, rowBounds, rowsHeight, rowCount = rowCount)

        // Delete strokes if any (page-scoped; the cache follows after commit)
        if (strokesInRow.isNotEmpty()) {
            val strokeIds = strokesInRow.map { it.id }
            strokeRepository.deleteByIdsOnPage(sourcePageId, strokeIds)
            afterCommit { PageDataManager.removeStrokes(strokeIds, sourcePageId) }

            // Trigger canvas refresh (non-blocking)
            TasksCanvasRefresh.request()  // coalesced (plan §13.4)
            Log.i(TAG, "CLEAR_SLOT: Deleted ${strokeIds.size} strokes from row $sourceRowIndex")
        }

        // Clear metadata (this also clears parentTaskId on the task itself)
        taskDao.clearTaskMetadata(task.id)

        // Invalidate cache and signal change
        invalidateTaskCache(task.dayDate)
        _taskChanged.tryEmit(task.dayDate)

        Log.i(TAG, "CLEAR_SLOT: Cleared task slot ${task.dayDate} row $sourceRowIndex")
    }

    /**
     * Clean orphaned metadata from empty slots on a given date.
     *
     * An empty slot should have NO metadata (priority, dueDate, isCompleted).
     * This function finds slots that are empty (no text, no strokes, not a child task)
     * but have orphaned metadata, and clears that metadata.
     *
     * This is a data integrity maintenance operation that should be called:
     * - Before Smart Prioritization to ensure clean scoring
     * - Periodically as a cleanup task
     *
     * @param date The date to clean (format: "yyyy-MM-dd")
     * @param canvasHeight Canvas height for stroke detection
     * @return Number of slots cleaned
     */
    fun cleanOrphanedMetadata(date: String, canvasHeight: Float): Int {
        var cleanedCount = 0
        val maxPageIndex = getMaxTaskPageIndex(date)
        if (maxPageIndex < 0) return 0

        for (pageIdx in 0..maxPageIndex) {
            val tasks = getTasksForPage(date, pageIdx)
            val pageId = tasks.firstOrNull()?.pageId ?: continue

            // Load strokes for this page once
            val pageStrokes = PageDataManager.getStrokesInRectangle(
                Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                pageId
            ) ?: StrokeRepository(context).getAllForPage(pageId)

            for (task in tasks) {
                // Check if slot is truly empty (no text, not a child)
                val hasText = !task.typedText.isNullOrBlank() ||
                              !task.convertedText.isNullOrBlank() ||
                              !task.recognizedText.isNullOrBlank()
                val isChild = task.parentTaskId != null

                // Skip if has text or is a child
                if (hasText || isChild) continue

                // Check for strokes in this row
                val rowBounds = calculateRowBounds(task.rowIndex, canvasHeight, 10)
                val hasStrokes = hasIntentionalStrokeContent(
                    pageStrokes.filter { stroke ->
                        stroke.bottom > rowBounds.topY && stroke.top < rowBounds.bottomY
                    },
                    rowBounds
                )

                // Skip if has strokes
                if (hasStrokes) continue

                // This slot is truly empty - check for orphaned metadata
                val hasOrphanedMetadata = task.isCompleted ||
                                          task.priority.isNotBlank() ||
                                          task.dueDay != null

                if (hasOrphanedMetadata) {
                    Log.i(TAG, "CLEAN_ORPHAN: Clearing orphaned metadata from $date page $pageIdx row ${task.rowIndex} " +
                            "(completed=${task.isCompleted}, priority='${task.priority}', dueDate=${task.dueDay})")

                    // Cancel notification if dueDate was set
                    if (task.dueDay != null) {
                        cancelTaskNotification(task.id)
                    }

                    // Clear just the metadata
                    taskDao.clearTaskMetadata(task.id)
                    cleanedCount++
                }
            }
        }

        if (cleanedCount > 0) {
            invalidateTaskCache(date)
            _taskChanged.tryEmit(date)
            Log.i(TAG, "CLEAN_ORPHAN: Cleaned $cleanedCount orphaned metadata entries for $date")
        }

        return cleanedCount
    }

    /**
     * Delete a child task (subtask) and compact remaining siblings upward.
     *
     * When a child task is deleted, siblings below it are moved up to maintain
     * the contiguous parent-child group structure. This prevents gaps in the group.
     *
     * Example: Parent at row 2, children at rows 3,4,5,6,7
     * - Delete child at row 5
     * - Children at rows 6,7 move up to rows 5,6
     * - Row 7 is cleared
     * - Group is now: Parent at 2, children at 3,4,5,6
     *
     * @param task The child task to delete (must have parentTaskId set)
     * @param canvasHeight The canvas height for row bounds calculation
     */
    fun deleteChildTaskAndCompact(task: Task, canvasHeight: Float) {
        // Verify this is a child task
        if (task.parentTaskId == null) {
            Log.w(TAG, "DELETE_CHILD: Task ${task.id} is not a child task, using regular clearTaskSlot")
            clearTaskSlot(task, canvasHeight)
            return
        }

        val parentId = task.parentTaskId
        val deletedRowIndex = task.rowIndex
        val pageId = task.pageId
        val date = task.dayDate
        // Full screen Tasks (Oct 2026): pages of more rows of the same height (tasks/TaskPagination)
        val rowCount = TaskPagination.rowsPerPage(date)
        val rowsHeight = TaskPagination.rowsHeight(canvasHeight, rowCount, tasksTopOffset())

        // Get all siblings (children of the same parent)
        val allSiblings = taskDao.getChildTasks(parentId)

        // Find siblings BELOW the deleted task (higher rowIndex, same page)
        val siblingsBelow = allSiblings.filter { sibling ->
            sibling.rowIndex > deletedRowIndex &&
            sibling.taskPageIndex == task.taskPageIndex
        }.sortedBy { it.rowIndex }

        Log.i(TAG, "DELETE_CHILD: Deleting child at row $deletedRowIndex, ${siblingsBelow.size} siblings below to compact")

        // Get all tasks on this page for finding target task entities
        val pageTasks = getTasksForPage(date, task.taskPageIndex)

        // Get stroke repository
        val strokeRepository = StrokeRepository(context)

        // Load all strokes from the page once
        val pageStrokes = PageDataManager.getStrokesInRectangle(
            Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
            pageId
        ) ?: strokeRepository.getAllForPage(pageId)

        // TASK-OP TRANSACTION (Oct 2026): every write below commits together or not at all;
        // stroke-cache updates and reminders are applied after the commit.
        inTransaction {
            // Step 1: Delete the target child's content (strokes + metadata)
            val deletedRowBounds = calculateRowBounds(deletedRowIndex, rowsHeight, rowCount)
            val deletedRowStrokes = extractStrokesForRow(pageStrokes, deletedRowBounds, rowsHeight, rowCount = rowCount)
            if (deletedRowStrokes.isNotEmpty()) {
                val strokeIds = deletedRowStrokes.map { it.id }
                strokeRepository.deleteAll(strokeIds)
                afterCommit { PageDataManager.removeStrokes(strokeIds, pageId) }
            }
            // Cancel notification for deleted child if it has dueDate
            if (task.dueDay != null) {
                afterCommit { cancelTaskNotification(task.id) }
            }
            markRecurringOccurrenceSkipped(task)
            taskDao.clearTaskMetadata(task.id)

            // Step 2: Move each sibling below UP by one row
            for (sibling in siblingsBelow) {
                val sourceRowIndex = sibling.rowIndex
                val targetRowIndex = sourceRowIndex - 1

                val sourceRowBounds = calculateRowBounds(sourceRowIndex, rowsHeight, rowCount)
                val targetRowBounds = calculateRowBounds(targetRowIndex, rowsHeight, rowCount)

                // Find target task entity at the target row
                val targetTask = pageTasks.find { it.rowIndex == targetRowIndex }
                if (targetTask == null) {
                    Log.e(TAG, "DELETE_CHILD: Could not find task at target row $targetRowIndex")
                    continue
                }

                // Move strokes from source to target row
                val siblingStrokes = extractStrokesForRow(pageStrokes, sourceRowBounds, rowsHeight, rowCount = rowCount)
                if (siblingStrokes.isNotEmpty()) {
                    // Create migrated strokes with new Y positions
                    val migratedStrokes = createMigratedStrokes(
                        sourceStrokes = siblingStrokes,
                        sourceRowBounds = sourceRowBounds,
                        targetRowBounds = targetRowBounds,
                        targetPageId = pageId
                    )

                    // Delete old strokes, add new ones
                    val sourceStrokeIds = siblingStrokes.map { it.id }
                    strokeRepository.deleteAll(sourceStrokeIds)
                    strokeRepository.create(migratedStrokes)
                    afterCommit {
                        PageDataManager.removeStrokes(sourceStrokeIds, pageId)
                        PageDataManager.addStrokes(migratedStrokes, pageId)
                    }
                }

                // Move every content column (and the reminder) from the sibling to the target slot
                transferTaskContent(sibling, targetTask.id, keepTemplateLink = true)

                // Update parentTaskId on the target task (link to same parent)
                taskDao.setParentTask(targetTask.id, parentId)

                // Clear the source slot metadata (it's now empty or will be filled by next sibling)
                taskDao.clearTaskMetadata(sibling.id)

                Log.i(TAG, "DELETE_CHILD: Moved sibling from row $sourceRowIndex to row $targetRowIndex")
            }

            // Step 3: Clear the last row (previously occupied by the bottom sibling)
            // This is now empty since all siblings shifted up
            if (siblingsBelow.isNotEmpty()) {
                val lastSibling = siblingsBelow.last()
                val lastRowBounds = calculateRowBounds(lastSibling.rowIndex, rowsHeight, rowCount)
                val lastRowStrokes = extractStrokesForRow(pageStrokes, lastRowBounds, rowsHeight, rowCount = rowCount)
                if (lastRowStrokes.isNotEmpty()) {
                    val strokeIds = lastRowStrokes.map { it.id }
                    strokeRepository.deleteAll(strokeIds)
                    afterCommit { PageDataManager.removeStrokes(strokeIds, pageId) }
                }
                // Metadata already cleared in the loop above
            }
        }

        // Trigger canvas refresh (non-blocking)
        TasksCanvasRefresh.request()  // coalesced (plan §13.4)

        // Invalidate cache and signal change
        invalidateTaskCache(date)
        _taskChanged.tryEmit(date)

        Log.i(TAG, "DELETE_CHILD: Completed deletion and compaction for $date row $deletedRowIndex")
    }

    /**
     * Clear only task metadata (priority, isCompleted) without touching strokes.
     * Used by eraser enhancement: when user erases all strokes in a row,
     * we auto-clear the metadata to reset the slot.
     *
     * NOTE: Does NOT clear typedText or convertedText - typed/AI tasks are not auto-cleared.
     * User must explicitly delete typed/AI tasks via the MoveTaskDialog.
     *
     * @param task The task whose metadata to clear
     */
    fun clearMetadataOnly(task: Task) {
        // Only clear priority and isCompleted, not typedText/convertedText
        taskDao.setPriority(task.id, "")
        taskDao.setCompleted(task.id, false)

        // Invalidate cache and signal change
        invalidateTaskCache(task.dayDate)
        _taskChanged.tryEmit(task.dayDate)

        Log.i(TAG, "CLEAR_METADATA: Auto-cleared metadata for ${task.dayDate} row ${task.rowIndex}")
    }

    /**
     * Eraser path (plan T1, Oct 2026): the row's ink was erased, usually to rewrite it.
     * Only the recognized text of the erased ink is dropped; priority, due date, reminder,
     * recurrence link and subtasks are kept. Explicit clears still use [clearTaskSlot].
     */
    /**
     * Break Down (plan T15, Oct 2026): the original task's scheduling — priority, due date
     * (with its reminder), estimate, repeat link and deferral history — moves to the new parent
     * row, and the original row is cleared completely (it used to keep an orphaned priority and
     * due date, so the reminder fired for an empty row).
     */
    fun moveTaskSchedulingTo(source: Task, targetTaskId: String) {
        val fresh = taskDao.getById(source.id) ?: source
        // v27 unique (dayDate, templateId): unlink the source before linking the target
        if (fresh.templateId != null) taskDao.setTemplateId(fresh.id, null)
        taskDao.setPriority(targetTaskId, fresh.priority)
        taskDao.setEstimatedMinutes(targetTaskId, fresh.estimatedMinutes)
        taskDao.setTemplateId(targetTaskId, fresh.templateId)
        taskDao.updateRolloverTracking(targetTaskId, fresh.migrationCount, fresh.originalDate, fresh.lastMigratedAt)
        writeDueDay(targetTaskId, fresh.dueDay)
        if (fresh.dueDay != null) cancelTaskNotification(fresh.id)
        taskDao.clearTaskMetadata(fresh.id)
        invalidateTaskCache(fresh.dayDate)
    }

    /**
     * Sets this task's own reminder time (minutes after midnight), or null for the shared
     * default (plan T3 / 13.6A, v28). The reminder is rescheduled when the task has a due day.
     */
    fun setReminderMinuteOfDay(taskId: String, minuteOfDay: Int?) {
        taskDao.setReminderMinuteOfDay(taskId, minuteOfDay?.coerceIn(0, 24 * 60 - 1))
        val task = taskDao.getById(taskId) ?: return
        cancelTaskNotification(taskId)
        if (task.dueDay != null) scheduleTaskNotificationIfEnabled(task)
        invalidateTaskCache(task.dayDate)
        _taskChanged.tryEmit(task.dayDate)
    }

    fun clearRecognizedTextAfterErase(task: Task) {
        taskDao.setRecognizedTextById(task.id, null)
        invalidateTaskCache(task.dayDate)
        _taskChanged.tryEmit(task.dayDate)
        Log.i(TAG, "ERASE_ROW: Cleared recognized text for ${task.dayDate} row ${task.rowIndex}")
    }

    /**
     * Clear only the handwriting strokes for a task row, keeping typedText/convertedText and metadata intact.
     * Used after handwriting recognition to remove the original strokes once converted to typed text.
     *
     * @param task The task whose strokes to clear
     * @param canvasHeight The canvas height for row bounds calculation
     * @return Number of strokes deleted
     */
    fun clearStrokesForRow(task: Task, canvasHeight: Float): Int {
        val sourcePageId = task.pageId
        val sourceRowIndex = task.rowIndex

        // Full screen Tasks (Oct 2026): pages of more rows of the same height (tasks/TaskPagination)
        val rowCount = TaskPagination.rowsPerPage(task.dayDate)
        val rowsHeight = TaskPagination.rowsHeight(canvasHeight, rowCount, tasksTopOffset())

        // Calculate row bounds
        val rowBounds = calculateRowBounds(
            rowIndex = sourceRowIndex,
            canvasHeight = rowsHeight,
            rowCount = rowCount
        )

        // Get strokes in this row — from memory, or the DB when the page isn't loaded
        // (plan §13.2-4, Oct 2026: a cleared row's ink used to stay behind on unloaded pages)
        val strokeRepository = StrokeRepository(context)
        val sourceRect = rowBounds.toRect()
        val allStrokes = PageDataManager.getStrokesInRectangle(sourceRect, sourcePageId)
            ?: strokeRepository.getAllForPage(sourcePageId)
        val strokesInRow = extractStrokesForRow(allStrokes, rowBounds, rowsHeight, rowCount = rowCount)

        // Delete strokes if any (page-scoped; the cache follows after commit)
        if (strokesInRow.isNotEmpty()) {
            val strokeIds = strokesInRow.map { it.id }
            strokeRepository.deleteByIdsOnPage(sourcePageId, strokeIds)
            afterCommit { PageDataManager.removeStrokes(strokeIds, sourcePageId) }

            Log.i(TAG, "CLEAR_STROKES: Deleted ${strokeIds.size} strokes from row $sourceRowIndex (keeping typedText/convertedText)")
            return strokeIds.size
        }

        return 0
    }

    /**
     * Move a task to a different date.
     * The task's Page (with all strokes) moves with it automatically
     * since we only update the task's location fields.
     *
     * @param task The task to move
     * @param targetDate The target date (format: "yyyy-MM-dd")
     * @param targetPageIndex The target task page group (default: 0)
     * @return true if move was successful, false if target is full
     */
    fun moveTask(task: Task, targetDate: String, targetPageIndex: Int = 0): Boolean {
        val sourceDate = task.dayDate  // Save before move
        val isCrossDateMove = targetDate != sourceDate

        // Find the next available row in target page
        val existingTasks = getTasksForPage(targetDate, targetPageIndex)
        val usedRows = existingTasks.map { it.rowIndex }.toSet()

        // Find first available row (0-9)
        val nextRow = (0 until 10).firstOrNull { it !in usedRows }

        if (nextRow == null) {
            // Target page is full
            return false
        }

        if (isCrossDateMove) {
            markRecurringOccurrenceDetached(task, targetDate)
        }

        taskDao.moveTask(task.id, targetDate, targetPageIndex, nextRow)
        if (isCrossDateMove) {
            taskDao.setTemplateId(task.id, null)
        }
        // PERFORMANCE: Invalidate cache for both source and target dates
        invalidateTaskCache(sourceDate)
        if (targetDate != sourceDate) {
            invalidateTaskCache(targetDate)
        }
        // Signal for real-time badge updates (both source and target)
        _taskChanged.tryEmit(sourceDate)
        if (targetDate != sourceDate) {
            _taskChanged.tryEmit(targetDate)
        }
        // Invalidate Intelligence Engine cache for AI features
        invalidateIntelligenceCache()
        return true
    }

    /**
     * Move a task to a different date WITH stroke migration.
     *
     * GROUP-AWARE: If the task is part of a parent-child group, the ENTIRE group
     * is moved together to maintain the relationship. The group is placed in
     * contiguous slots on the target page.
     *
     * Unlike moveTask() which only updates metadata, this method:
     * 1. Gets the complete task group (parent + children)
     * 2. Finds contiguous slots for the entire group
     * 3. Extracts strokes from source row bounds for each task
     * 4. Creates new strokes on the target page with transformed coordinates
     * 5. Deletes the original strokes from the source page
     * 6. Updates the task metadata and parentTaskId references
     *
     * This is necessary because all 10 tasks in a task page group share ONE Page.
     * Strokes are stored by pageId, so we must physically move them.
     *
     * @param task The task to move (if part of a group, entire group moves)
     * @param targetDate The target date (format: "yyyy-MM-dd")
     * @param targetPageIndex The target task page group (default: 0)
     * @param canvasHeight The canvas height in pixels (needed for row bounds calculation)
     * @param context Android context for database access
     * @return MoveTaskResult with success status and stroke migration details
     */
    fun moveTaskWithStrokes(
        task: Task,
        targetDate: String,
        targetPageIndex: Int = 0,
        canvasHeight: Float,
        context: Context
    ): MoveTaskResult {
        // Full screen Tasks (Oct 2026): this works on pages of 10; the tools calling it leave full screen first
        if (TaskPagination.isReflowed(task.dayDate) || TaskPagination.isReflowed(targetDate)) {
            Log.w(TAG, "Not moving task ${task.id}: Tasks on full screen pages")
            return MoveTaskResult(success = false, errorReason = MoveTaskError.DATABASE_ERROR)
        }
        // GROUP-AWARE: Get the complete task group (parent + all children)
        val taskGroup = getTaskGroup(task.id)
        if (taskGroup.isEmpty()) {
            return MoveTaskResult(
                success = false,
                errorReason = MoveTaskError.DATABASE_ERROR
            )
        }

        val groupSize = taskGroup.size
        val sourceDate = taskGroup[0].dayDate
        val sourcePageIndex = taskGroup[0].taskPageIndex

        Log.i(TAG, "TASK_GROUP_MOVE: Moving group of $groupSize task(s) from $sourceDate to $targetDate")

        // Check for same location move (no-op) - check if ALL tasks are already on target
        if (targetDate == sourceDate && targetPageIndex == sourcePageIndex) {
            return MoveTaskResult(
                success = false,
                errorReason = MoveTaskError.SAME_LOCATION
            )
        }

        // Detect same-date move for undo support
        // Same-date moves can be undone; cross-date moves cannot (different History instances)
        val isSameDateMove = targetDate == sourceDate

        // Collect undo operations for same-date moves
        val sameDateUndoOperations = mutableListOf<Operation>()

        // Get or create target page
        val (targetTasks, _) = getOrCreateTasksForPage(targetDate, targetPageIndex)

        // Get target page ID from the target tasks
        val targetPageId = targetTasks.firstOrNull()?.pageId
            ?: return MoveTaskResult(
                success = false,
                errorReason = MoveTaskError.DATABASE_ERROR
            )

        // Find rows that have content - MUST check BOTH metadata AND strokes
        // A child task slot (parentTaskId != null) is also considered "used"
        val metadataUsedRows = targetTasks
            .filter {
                it.isCompleted ||
                    it.priority.isNotEmpty() ||
                    it.typedText?.isNotBlank() == true ||
                    it.convertedText?.isNotBlank() == true ||
                    it.recognizedText?.isNotBlank() == true ||
                    it.parentTaskId != null  // Child tasks are NOT empty slots
            }
            .map { it.rowIndex }
            .toMutableSet()

        // Load target page strokes and check which rows have content
        val strokeRepo = StrokeRepository(context)
        val targetPageStrokes = PageDataManager.getStrokesInRectangle(
            Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
            targetPageId
        ) ?: run {
            Log.i(TAG, "TARGET_PAGE_STROKES: Page $targetPageId not in memory, querying database")
            strokeRepo.getAllForPage(targetPageId)
        }

        // Calculate which rows have strokes
        val strokeUsedRows = mutableSetOf<Int>()
        if (targetPageStrokes.isNotEmpty()) {
            for (rowIdx in 0 until 10) {
                val rowBounds = calculateRowBounds(rowIdx, canvasHeight, 10)
                val hasStrokes = targetPageStrokes.any { stroke ->
                    stroke.bottom > rowBounds.topY && stroke.top < rowBounds.bottomY
                }
                if (hasStrokes) {
                    strokeUsedRows.add(rowIdx)
                }
            }
        }

        // Combine metadata and stroke-based row usage
        var usedRows = (metadataUsedRows + strokeUsedRows).toMutableSet()

        // IMPORTANT: If moving within same date, the source slots will be freed
        // Remove source rows from usedRows since they'll become available
        if (sourceDate == targetDate) {
            for (sourceTask in taskGroup) {
                if (sourceTask.taskPageIndex == targetPageIndex) {
                    usedRows.remove(sourceTask.rowIndex)
                    Log.i(TAG, "TASK_GROUP_MOVE: Accounting for freed slot ${sourceTask.rowIndex}")
                }
            }
        }

        Log.i(TAG, "TARGET_ROW_CHECK: usedRows=$usedRows, groupSize=$groupSize")

        // GROUP-AWARE: Find CONTIGUOUS slots for the entire group
        val startRow = findContiguousSlots(groupSize, usedRows)

        if (startRow == null) {
            // Try next page if current page doesn't have enough contiguous space
            Log.i(TAG, "TASK_GROUP_MOVE: No contiguous block of $groupSize on page $targetPageIndex, trying next page")
            return moveTaskWithStrokes(task, targetDate, targetPageIndex + 1, canvasHeight, context)
        }

        Log.i(TAG, "TASK_GROUP_MOVE: Found contiguous block starting at row $startRow for $groupSize tasks")

        // Move each task in the group to consecutive target slots
        var totalMigratedCount = 0
        val allSourceStrokeIds = mutableListOf<String>()

        for ((index, sourceTask) in taskGroup.withIndex()) {
            val targetRowIndex = startRow + index
            val sourcePageId = sourceTask.pageId
            val sourceRowIndex = sourceTask.rowIndex

            // Calculate row bounds
            val sourceRowBounds = calculateRowBounds(sourceRowIndex, canvasHeight, 10)
            val targetRowBounds = calculateRowBounds(targetRowIndex, canvasHeight, 10)

            // Get strokes for this source row
            val sourceRect = sourceRowBounds.toRect()
            val allSourceStrokes = PageDataManager.getStrokesInRectangle(sourceRect, sourcePageId)
                ?: strokeRepo.getAllForPage(sourcePageId)

            val strokesInRow = extractStrokesForRow(allSourceStrokes, sourceRowBounds, canvasHeight)
            val sourceStrokeIds = strokesInRow.map { it.id }
            allSourceStrokeIds.addAll(sourceStrokeIds)

            // Migrate strokes if any
            if (strokesInRow.isNotEmpty()) {
                val migratedStrokes = createMigratedStrokes(
                    sourceStrokes = strokesInRow,
                    sourceRowBounds = sourceRowBounds,
                    targetRowBounds = targetRowBounds,
                    targetPageId = targetPageId
                )

                // Use atomic transaction to prevent data loss on crash
                strokeRepo.migrateStrokes(sourceStrokeIds, migratedStrokes)

                PageDataManager.removeStrokes(sourceStrokeIds, sourcePageId)
                PageDataManager.addStrokes(migratedStrokes, targetPageId)

                totalMigratedCount += migratedStrokes.size

                // Collect undo operations for same-date moves
                // To undo: delete migrated strokes, restore original strokes
                if (isSameDateMove) {
                    sameDateUndoOperations.add(Operation.DeleteStroke(migratedStrokes.map { it.id }))
                    sameDateUndoOperations.add(Operation.AddStroke(strokesInRow))
                }

                Log.i(TAG, "STROKE_MIGRATE: Moved ${migratedStrokes.size} strokes from row $sourceRowIndex to row $targetRowIndex")
            }

            // Transfer metadata to target task slot
            val targetTask = targetTasks.find { it.rowIndex == targetRowIndex }
            if (targetTask != null) {
                // Every content column moves (T11/T12: due date + reminder too, also to another day)
                transferTaskContent(sourceTask, targetTask.id, keepTemplateLink = isSameDateMove)
            }

            if (!isSameDateMove) {
                markRecurringOccurrenceDetached(sourceTask, targetDate)
            }

            // Clear source task metadata
            taskDao.clearTaskMetadata(sourceTask.id)

            Log.i(TAG, "TASK_MOVE: Transferred task from $sourceDate:$sourceRowIndex to $targetDate:$targetRowIndex")
        }

        // GROUP-AWARE: Update parentTaskId references for children in new location
        if (groupSize > 1) {
            val newParentTask = targetTasks.find { it.rowIndex == startRow }
            if (newParentTask != null) {
                for (i in 1 until groupSize) {
                    val childTargetTask = targetTasks.find { it.rowIndex == startRow + i }
                    if (childTargetTask != null) {
                        taskDao.setParentTask(childTargetTask.id, newParentTask.id)
                        Log.i(TAG, "TASK_GROUP_MOVE: Linked child at row ${startRow + i} to new parent ${newParentTask.id}")
                    }
                }
            }
        }

        // Trigger canvas refresh (non-blocking)
        TasksCanvasRefresh.request()  // coalesced (plan §13.4)

        // Invalidate caches for both dates
        invalidateTaskCache(sourceDate)
        if (targetDate != sourceDate) {
            invalidateTaskCache(targetDate)
        }

        // Signal task change for UI updates (badge refresh)
        val sourceEmitted = _taskChanged.tryEmit(sourceDate)
        Log.i(TAG, "BADGE_SIGNAL: sourceDate=$sourceDate emitted=$sourceEmitted")
        if (targetDate != sourceDate) {
            val targetEmitted = _taskChanged.tryEmit(targetDate)
            Log.i(TAG, "BADGE_SIGNAL: targetDate=$targetDate emitted=$targetEmitted")
        }

        // T19 (Oct 2026): re-index both pages so search doesn't keep the task under the old day
        TaskTextIndexer.getInstanceOrNull()?.let { indexer ->
            indexer.onTypedTextChanged(task.id, sourceDate, task.taskPageIndex, task.pageId)
            targetTasks.firstOrNull()?.let { indexer.onTypedTextChanged(it.id, targetDate, it.taskPageIndex, it.pageId) }
        }

        Log.i(TAG, "TASK_GROUP_MOVE: Successfully moved $groupSize task(s), $totalMigratedCount strokes")

        return MoveTaskResult(
            success = true,
            sourceStrokeIds = allSourceStrokeIds,
            migratedStrokeCount = totalMigratedCount,
            undoOperations = if (isSameDateMove && sameDateUndoOperations.isNotEmpty()) {
                sameDateUndoOperations
            } else {
                null
            },
            targetPageIndex = targetPageIndex,
            movedTaskId = targetTasks.find { it.rowIndex == startRow }?.id
        )
    }

    /**
     * Delete a task.
     * Note: The associated Page will be cascade-deleted due to foreign key.
     */
    fun deleteTask(task: Task) {
        // Cancel any pending notification before deleting
        cancelTaskNotification(task.id)
        markRecurringOccurrenceSkipped(task)

        val date = task.dayDate  // Save before delete
        taskDao.delete(task)
        // PERFORMANCE: Invalidate cache for this date
        invalidateTaskCache(date)
        // Signal for real-time badge updates
        _taskChanged.tryEmit(date)
        // Invalidate Intelligence Engine cache for AI features
        invalidateIntelligenceCache()
    }

    /**
     * Delete a task page and its strokes, then compact remaining page indices.
     * Also cancels any scheduled notifications for tasks on this page.
     * Used after moving all content from a page to clean up empty pages.
     *
     * @param date The date of the task page
     * @param pageIndex The page index to delete
     */
    fun deleteTaskPage(date: String, pageIndex: Int) {
        // TASK-OP TRANSACTION (Oct 2026): delete + index compaction commit together or not at all;
        // stroke-cache updates and reminder cancels are applied after the commit.
        inTransaction {
            // Get the page ID and strokes before deleting
            val tasks = getTasksForPage(date, pageIndex)
            val pageId = tasks.firstOrNull()?.pageId

            // Cancel notifications for all tasks with due dates (applied once the deletion commits)
            for (task in tasks) {
                if (task.dueDay != null) {
                    afterCommit { cancelTaskNotification(task.id) }
                }
                markRecurringOccurrenceSkipped(task)
            }

            if (pageId != null) {
                // Delete strokes from this page
                val strokeRepository = StrokeRepository(context)
                val strokes = PageDataManager.getStrokesInRectangle(
                    Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                    pageId
                ) ?: strokeRepository.getAllForPage(pageId)

                if (strokes.isNotEmpty()) {
                    val strokeIds = strokes.map { it.id }
                    strokeRepository.deleteAll(strokeIds)
                    afterCommit { PageDataManager.removeStrokes(strokeIds, pageId) }
                }

                // Delete the Page entity
                pageDao.delete(pageId)
            }

            // Delete the task entities for this page
            taskDao.deleteTasksForPage(date, pageIndex)

            // Compact remaining pages: decrement indices of pages after the deleted one
            taskDao.decrementPageIndicesAfter(date, pageIndex)
        }

        // Invalidate cache
        invalidateTaskCache(date)
        _taskChanged.tryEmit(date)

        Log.i(TAG, "DELETE_PAGE: Deleted task page $pageIndex for $date and compacted remaining pages")
    }

    /**
     * Get all distinct page IDs for tasks on a given date.
     * Used for clearing all task pages when "Clear all pages" is invoked.
     */
    fun getDistinctPageIdsForDate(date: String): List<String> {
        return taskDao.getDistinctPageIdsForDate(date)
    }

    // ========== BULK ACTIONS ==========

    /**
     * Move all tasks from a specific task page to another date.
     * Moves both metadata AND strokes for each task row with content.
     *
     * GROUP-AWARE: Parent-child task groups are moved together as atomic units.
     * Groups are placed in contiguous slots on the target page.
     *
     * @param sourceDate The source date (format: "yyyy-MM-dd")
     * @param sourcePageIndex The source task page index
     * @param targetDate The target date (format: "yyyy-MM-dd")
     * @param canvasHeight The canvas height in pixels
     * @return Result with count of moved tasks
     */
    fun moveAllTasksFromPage(
        sourceDate: String,
        sourcePageIndex: Int,
        targetDate: String,
        canvasHeight: Float
    ): Result<Int> {
        // Full screen Tasks (Oct 2026): this works on pages of 10; the tools calling it leave full screen first
        if (TaskPagination.isReflowed(sourceDate) || TaskPagination.isReflowed(targetDate)) {
            return Result.failure(IllegalStateException("Tasks are on full screen pages"))
        }
        return try {
            if (sourceDate == targetDate) {
                return Result.failure(Exception("Cannot move to same date"))
            }

            val sourceTasks = getTasksForPage(sourceDate, sourcePageIndex)
            if (sourceTasks.isEmpty()) {
                return Result.success(0)
            }

            val sourcePageId = sourceTasks.firstOrNull()?.pageId
                ?: return Result.failure(Exception("No page ID found"))

            val strokeRepository = StrokeRepository(context)
            val sourceStrokes = PageDataManager.getStrokesInRectangle(
                Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                sourcePageId
            ) ?: run {
                Log.w(TAG, "BULK_MOVE: Source page $sourcePageId not in PageDataManager cache, loading from DB")
                strokeRepository.getAllForPage(sourcePageId)
            }

            // Find tasks that have content
            val tasksWithDirectContent = sourceTasks.filter { task ->
                val rowBounds = calculateRowBounds(task.rowIndex, canvasHeight, 10)
                val hasStrokes = sourceStrokes.any { stroke ->
                    val strokeCenterY = (stroke.top + stroke.bottom) / 2
                    strokeCenterY >= rowBounds.topY && strokeCenterY < rowBounds.bottomY
                }
                task.isCompleted ||
                    task.priority.isNotEmpty() ||
                    task.typedText?.isNotBlank() == true ||
                    task.convertedText?.isNotBlank() == true ||
                    task.recognizedText?.isNotBlank() == true ||
                    hasStrokes
            }

            // Include parent tasks when any of their children have content (parent row may be empty)
            val childParentIds = tasksWithDirectContent.mapNotNull { it.parentTaskId }.toSet()
            val parentsToInclude = sourceTasks.filter { task ->
                task.id in childParentIds && tasksWithDirectContent.none { it.id == task.id }
            }
            val tasksWithContent = tasksWithDirectContent + parentsToInclude

            if (tasksWithContent.isEmpty()) {
                return Result.success(0)
            }

            // GROUP-AWARE: Organize tasks into groups
            val processedTaskIds = mutableSetOf<String>()
            val taskGroups = mutableListOf<TaskGroupInfo>()

            for (task in tasksWithContent) {
                if (task.id in processedTaskIds) continue

                val group = getTaskGroup(task.id)
                // Filter to only include group members that are on this page
                val groupOnThisPage = group.filter { groupTask ->
                    tasksWithContent.any { it.id == groupTask.id }
                }

                if (groupOnThisPage.isNotEmpty()) {
                    val parent = groupOnThisPage[0]
                    val children = groupOnThisPage.drop(1)
                    taskGroups.add(TaskGroupInfo(parent, children, groupOnThisPage.size))
                    groupOnThisPage.forEach { processedTaskIds.add(it.id) }
                }
            }

            // Sort groups: larger groups first for better packing, then by original row
            taskGroups.sortWith(compareByDescending<TaskGroupInfo> { it.totalSlots }.thenBy { it.parentTask.rowIndex })

            Log.i(TAG, "BULK_MOVE: Found ${taskGroups.size} task group(s) to move")

            // TASK-OP TRANSACTION (Oct 2026): the whole move (target pages created, rows filled,
            // source rows cleared, empty source page deleted) commits together or not at all;
            // stroke-cache updates and reminder cancels are applied after the commit.
            val totalMoved = inTransaction {
                var currentTargetPageIndex = 0
                var movedCount = 0
                val pagesWithMigratedStrokes = mutableSetOf<String>()  // Track for recognition trigger

                // Track allocated rows per page to prevent overwrites (parent tasks may have no content)
                val allocatedRowsByPage = mutableMapOf<Int, MutableSet<Int>>()

                for (groupInfo in taskGroups) {
                    // Get current target page state
                    var (targetTasks, _) = getOrCreateTasksForPage(targetDate, currentTargetPageIndex)
                    var targetPageId = targetTasks.firstOrNull()?.pageId ?: continue

                    // Calculate used rows on target (content-based) + already allocated rows
                    var usedRows = calculateUsedRowsForPage(targetTasks, targetPageId, canvasHeight, strokeRepository)
                    val allocatedRows = allocatedRowsByPage.getOrPut(currentTargetPageIndex) { mutableSetOf() }
                    var combinedUsedRows = usedRows + allocatedRows

                    // Find contiguous block for this group
                    var startRow = findContiguousSlots(groupInfo.totalSlots, combinedUsedRows)

                    // If no space, try next pages until we find one
                    while (startRow == null) {
                        currentTargetPageIndex++
                        val pageResult = getOrCreateTasksForPage(targetDate, currentTargetPageIndex)
                        targetTasks = pageResult.first
                        targetPageId = targetTasks.firstOrNull()?.pageId ?: break
                        usedRows = calculateUsedRowsForPage(targetTasks, targetPageId, canvasHeight, strokeRepository)
                        val nextAllocatedRows = allocatedRowsByPage.getOrPut(currentTargetPageIndex) { mutableSetOf() }
                        combinedUsedRows = usedRows + nextAllocatedRows
                        startRow = findContiguousSlots(groupInfo.totalSlots, combinedUsedRows)
                    }

                    if (startRow == null) {
                        Log.e(TAG, "BULK_MOVE: Could not find space for group of ${groupInfo.totalSlots}")
                        continue
                    }

                    // Mark these rows as allocated BEFORE placing to prevent future overwrites
                    val currentAllocated = allocatedRowsByPage.getOrPut(currentTargetPageIndex) { mutableSetOf() }
                    for (i in 0 until groupInfo.totalSlots) {
                        currentAllocated.add(startRow + i)
                    }

                    Log.i(TAG, "BULK_MOVE: Placing group of ${groupInfo.totalSlots} at page $currentTargetPageIndex row $startRow")

                    // Move each task in the group
                    val allGroupTasks = listOf(groupInfo.parentTask) + groupInfo.children
                    for ((index, sourceTask) in allGroupTasks.withIndex()) {
                        val targetRowIndex = startRow + index
                        val sourceRowBounds = calculateRowBounds(sourceTask.rowIndex, canvasHeight, 10)
                        val targetRowBounds = calculateRowBounds(targetRowIndex, canvasHeight, 10)

                        // Migrate strokes
                        val strokesInRow = extractStrokesForRow(sourceStrokes, sourceRowBounds, canvasHeight)
                        if (strokesInRow.isNotEmpty()) {
                            val migratedStrokes = createMigratedStrokes(strokesInRow, sourceRowBounds, targetRowBounds, targetPageId)
                            val sourceStrokeIds = strokesInRow.map { it.id }
                            strokeRepository.deleteAll(sourceStrokeIds)
                            strokeRepository.create(migratedStrokes)
                            val migratedPageId = targetPageId
                            afterCommit {
                                PageDataManager.removeStrokes(sourceStrokeIds, sourcePageId)
                                PageDataManager.addStrokes(migratedStrokes, migratedPageId)
                            }
                            pagesWithMigratedStrokes.add(targetPageId)  // Track for recognition
                        }

                        // Transfer metadata
                        val targetTask = targetTasks.find { it.rowIndex == targetRowIndex }
                        if (targetTask != null) {
                            // Every content column moves; the occurrence detaches (another day)
                            transferTaskContent(sourceTask, targetTask.id, keepTemplateLink = false)
                        } else if (sourceTask.dueDay != null) {
                            afterCommit { cancelTaskNotification(sourceTask.id) }
                        }
                        markRecurringOccurrenceDetached(sourceTask, targetDate)

                        // Clear source task
                        taskDao.clearTaskMetadata(sourceTask.id)
                        movedCount++
                    }

                    // Update parentTaskId for children in new location
                    if (groupInfo.totalSlots > 1) {
                        val newParentTask = targetTasks.find { it.rowIndex == startRow }
                        if (newParentTask != null) {
                            for (i in 1 until groupInfo.totalSlots) {
                                val childTargetTask = targetTasks.find { it.rowIndex == startRow + i }
                                if (childTargetTask != null) {
                                    taskDao.setParentTask(childTargetTask.id, newParentTask.id)
                                }
                            }
                        }
                    }
                }

                // Clean up empty source page
                val maxPageIndex = getMaxTaskPageIndex(sourceDate)
                if (maxPageIndex > 0 && movedCount > 0) {
                    // T14 (Oct 2026): check what is left now (incl. ink), not the pre-move list
                    val remainingContent = !isTaskPageEmpty(sourceDate, sourcePageIndex)
                    if (!remainingContent) {
                        Log.i(TAG, "BULK_MOVE: Source page $sourcePageIndex is now empty, deleting it")
                        deleteTaskPage(sourceDate, sourcePageIndex)
                    }
                }
                movedCount
            }

            // Invalidate caches and signal changes
            invalidateTaskCache(sourceDate)
            invalidateTaskCache(targetDate)
            _taskChanged.tryEmit(sourceDate)
            _taskChanged.tryEmit(targetDate)

            // Trigger canvas refresh (non-blocking)
            TasksCanvasRefresh.request()  // coalesced (plan §13.4)

            Log.i(TAG, "BULK_MOVE: Moved $totalMoved tasks from $sourceDate page $sourcePageIndex to $targetDate")
            Result.success(totalMoved)
        } catch (e: Exception) {
            Log.e(TAG, "BULK_MOVE failed: ${e.message}", e)
            // The move was rolled back; drop task pages cached while it ran (they may not exist).
            invalidateTaskCache(sourceDate)
            invalidateTaskCache(targetDate)
            Result.failure(e)
        }
    }

    /**
     * Helper to calculate which rows are used on a target page.
     * Checks metadata, stroke content, and parent-child relationships.
     * A child task (parentTaskId != null) is considered "used".
     */
    private fun calculateUsedRowsForPage(
        tasks: List<Task>,
        pageId: String,
        canvasHeight: Float,
        strokeRepository: StrokeRepository
    ): Set<Int> {
        val strokes = PageDataManager.getStrokesInRectangle(
            Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
            pageId
        ) ?: strokeRepository.getAllForPage(pageId)

        val usedRows = mutableSetOf<Int>()
        for (rowIdx in 0 until 10) {
            val rowBounds = calculateRowBounds(rowIdx, canvasHeight, 10)
            val hasStrokes = strokes.any { stroke ->
                val strokeCenterY = (stroke.top + stroke.bottom) / 2
                strokeCenterY >= rowBounds.topY && strokeCenterY < rowBounds.bottomY
            }
            val task = tasks.find { it.rowIndex == rowIdx }
            if (hasStrokes ||
                task?.isCompleted == true ||
                task?.priority?.isNotEmpty() == true ||
                task?.typedText?.isNotBlank() == true ||
                task?.convertedText?.isNotBlank() == true ||
                task?.recognizedText?.isNotBlank() == true ||
                task?.parentTaskId != null) {  // Child tasks are NOT empty slots
                usedRows.add(rowIdx)
            }
        }
        return usedRows
    }

    /** Queues search re-indexing of every task page of [date] (plan T19). */
    private fun reindexTaskPagesForDate(date: String) {
        val indexer = TaskTextIndexer.getInstanceOrNull() ?: return
        val maxPageIndex = getMaxTaskPageIndex(date)
        for (pageIndex in 0..maxPageIndex) {
            val first = taskDao.getTasksForPage(date, pageIndex).firstOrNull() ?: continue
            indexer.onTypedTextChanged(first.id, date, pageIndex, first.pageId)
        }
    }

    /**
     * True when nothing is left on a task page (plan T14, Oct 2026): no ink in the database and
     * no row with text, a check mark, a priority, a due day, a parent or a template link.
     * Read from the database, so inside a move's transaction it sees the move's own writes.
     */
    private fun isTaskPageEmpty(date: String, pageIndex: Int): Boolean {
        val tasks = taskDao.getTasksForPage(date, pageIndex)
        val pageId = tasks.firstOrNull()?.pageId ?: return true
        if (AppDatabase.getDatabase(context).strokeDao().hasStrokesForPage(pageId)) return false
        return tasks.none { task ->
            !task.typedText.isNullOrBlank() ||
                !task.convertedText.isNullOrBlank() ||
                !task.recognizedText.isNullOrBlank() ||
                task.isCompleted ||
                task.priority.isNotBlank() ||
                task.dueDay != null ||
                task.parentTaskId != null ||
                task.templateId != null
        }
    }

    /**
     * Move all tasks from ALL pages of a date to another date.
     * Iterates through all task pages and moves content to the target.
     *
     * @param sourceDate The source date (format: "yyyy-MM-dd")
     * @param targetDate The target date (format: "yyyy-MM-dd")
     * @param canvasHeight The canvas height in pixels
     * @return Result with total count of moved tasks
     */
    fun moveAllTasksForDate(
        sourceDate: String,
        targetDate: String,
        canvasHeight: Float
    ): Result<Int> {
        return try {
            if (sourceDate == targetDate) {
                return Result.failure(Exception("Cannot move to same date"))
            }

            val maxPageIndex = getMaxTaskPageIndex(sourceDate)
            if (maxPageIndex < 0) {
                return Result.success(0)
            }

            // One transaction for every page of the day (plan §13.2-4, Oct 2026): a failure
            // part-way rolls the whole move back instead of leaving it half done
            val totalMoved = inTransaction {
                var totalMoved = 0
                var currentTargetPageIndex = 0

                // Iterate through all source pages
                for (sourcePageIdx in 0..maxPageIndex) {
                    val result = moveAllTasksFromPageInternal(
                        sourceDate = sourceDate,
                        sourcePageIndex = sourcePageIdx,
                        targetDate = targetDate,
                        targetPageIndex = currentTargetPageIndex,
                        canvasHeight = canvasHeight
                    )

                    if (result.isSuccess) {
                        val moved = result.getOrNull() ?: 0
                        totalMoved += moved

                        // Check if target page is full, create next page if needed
                        val (targetTasks, _) = getOrCreateTasksForPage(targetDate, currentTargetPageIndex)
                        val usedCount = countUsedRowsOnPage(targetTasks, targetDate, currentTargetPageIndex, canvasHeight)
                        if (usedCount >= 10) {
                            currentTargetPageIndex++
                        }
                    }
                }

                // After moving all pages, delete the source pages (except page 0) that are now empty.
                // T14 (Oct 2026): a page still holding groups that didn't fit on the target is kept —
                // it used to be deleted together with those tasks.
                if (totalMoved > 0) {
                    // Delete extra pages from highest to lowest to avoid index shifting issues
                    val newMaxPageIndex = getMaxTaskPageIndex(sourceDate)
                    for (pageIdx in newMaxPageIndex downTo 1) {
                        if (!isTaskPageEmpty(sourceDate, pageIdx)) {
                            Log.w(TAG, "BULK_MOVE_ALL: Source page $pageIdx still has tasks that didn't fit; kept")
                            continue
                        }
                        Log.i(TAG, "BULK_MOVE_ALL: Deleting now-empty source page $pageIdx")
                        deleteTaskPage(sourceDate, pageIdx)
                    }
                    // Page 0 remains but is now empty (metadata was cleared during move)
                    Log.i(TAG, "BULK_MOVE_ALL: Source date $sourceDate reset to 1 empty page")
                }
                totalMoved
            }

            // Invalidate caches and signal changes
            invalidateTaskCache(sourceDate)
            invalidateTaskCache(targetDate)
            _taskChanged.tryEmit(sourceDate)
            _taskChanged.tryEmit(targetDate)

            // Trigger canvas refresh (non-blocking)
            TasksCanvasRefresh.request()  // coalesced (plan §13.4)

            // T19 (Oct 2026): re-index both dates' pages for search
            reindexTaskPagesForDate(sourceDate)
            reindexTaskPagesForDate(targetDate)

            Log.i(TAG, "BULK_MOVE_ALL: Moved $totalMoved tasks from $sourceDate to $targetDate")
            Result.success(totalMoved)
        } catch (e: Exception) {
            Log.e(TAG, "BULK_MOVE_ALL failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Internal helper for moving tasks from one page to a specific target page.
     * GROUP-AWARE: Parent-child groups are moved together to contiguous slots.
     */
    private fun moveAllTasksFromPageInternal(
        sourceDate: String,
        sourcePageIndex: Int,
        targetDate: String,
        targetPageIndex: Int,
        canvasHeight: Float
    ): Result<Int> {
        val sourceTasks = getTasksForPage(sourceDate, sourcePageIndex)
        if (sourceTasks.isEmpty()) return Result.success(0)

        val sourcePageId = sourceTasks.firstOrNull()?.pageId ?: return Result.success(0)
        val strokeRepository = StrokeRepository(context)

        val sourceStrokes = PageDataManager.getStrokesInRectangle(
            Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
            sourcePageId
        ) ?: run {
            Log.w(TAG, "BULK_MOVE_INTERNAL: Source page $sourcePageId not in cache, loading from DB")
            strokeRepository.getAllForPage(sourcePageId)
        }

        // Find tasks with content
        val tasksWithDirectContent = sourceTasks.filter { task ->
            val rowBounds = calculateRowBounds(task.rowIndex, canvasHeight, 10)
            val hasStrokes = sourceStrokes.any { stroke ->
                val strokeCenterY = (stroke.top + stroke.bottom) / 2
                strokeCenterY >= rowBounds.topY && strokeCenterY < rowBounds.bottomY
            }
            task.isCompleted ||
                task.priority.isNotEmpty() ||
                task.typedText?.isNotBlank() == true ||
                task.convertedText?.isNotBlank() == true ||
                task.recognizedText?.isNotBlank() == true ||
                hasStrokes
        }

        // Include parent tasks when any of their children have content (parent row may be empty)
        val childParentIds = tasksWithDirectContent.mapNotNull { it.parentTaskId }.toSet()
        val parentsToInclude = sourceTasks.filter { task ->
            task.id in childParentIds && tasksWithDirectContent.none { it.id == task.id }
        }
        val tasksWithContent = tasksWithDirectContent + parentsToInclude

        if (tasksWithContent.isEmpty()) return Result.success(0)

        // GROUP-AWARE: Organize tasks into groups
        val processedTaskIds = mutableSetOf<String>()
        val taskGroups = mutableListOf<TaskGroupInfo>()

        for (task in tasksWithContent) {
            if (task.id in processedTaskIds) continue

            val group = getTaskGroup(task.id)
            val groupOnThisPage = group.filter { groupTask ->
                tasksWithContent.any { it.id == groupTask.id }
            }

            if (groupOnThisPage.isNotEmpty()) {
                val parent = groupOnThisPage[0]
                val children = groupOnThisPage.drop(1)
                taskGroups.add(TaskGroupInfo(parent, children, groupOnThisPage.size))
                groupOnThisPage.forEach { processedTaskIds.add(it.id) }
            }
        }

        // Sort groups: larger groups first for better packing
        taskGroups.sortWith(compareByDescending<TaskGroupInfo> { it.totalSlots }.thenBy { it.parentTask.rowIndex })

        // Get/create target page
        val (targetTasks, _) = getOrCreateTasksForPage(targetDate, targetPageIndex)
        val targetPageId = targetTasks.firstOrNull()?.pageId ?: return Result.success(0)

        val baseUsedRows = calculateUsedRowsForPage(targetTasks, targetPageId, canvasHeight, strokeRepository)
        val allocatedRows = mutableSetOf<Int>()  // Track allocated rows to prevent overwrites
        var movedCount = 0
        var strokesMigrated = false  // Track for recognition trigger

        for (groupInfo in taskGroups) {
            // Find contiguous block for this group (combine content-based + allocated)
            val combinedUsedRows = baseUsedRows + allocatedRows
            val startRow = findContiguousSlots(groupInfo.totalSlots, combinedUsedRows)

            if (startRow == null) {
                Log.w(TAG, "BULK_MOVE_INTERNAL: No contiguous space for group of ${groupInfo.totalSlots}, skipping")
                continue
            }

            // Mark these rows as allocated BEFORE placing
            for (i in 0 until groupInfo.totalSlots) {
                allocatedRows.add(startRow + i)
            }

            // Move each task in the group
            val allGroupTasks = listOf(groupInfo.parentTask) + groupInfo.children
            for ((index, sourceTask) in allGroupTasks.withIndex()) {
                val targetRowIndex = startRow + index
                val sourceRowBounds = calculateRowBounds(sourceTask.rowIndex, canvasHeight, 10)
                val targetRowBounds = calculateRowBounds(targetRowIndex, canvasHeight, 10)

                // Migrate strokes
                val strokesInRow = extractStrokesForRow(sourceStrokes, sourceRowBounds, canvasHeight)
                if (strokesInRow.isNotEmpty()) {
                    val migratedStrokes = createMigratedStrokes(strokesInRow, sourceRowBounds, targetRowBounds, targetPageId)
                    val sourceStrokeIds = strokesInRow.map { it.id }
                    strokeRepository.deleteByIdsOnPage(sourcePageId, sourceStrokeIds)
                    strokeRepository.create(migratedStrokes)
                    // Cache follows once the move commits (plan §13.2-4)
                    afterCommit {
                        PageDataManager.removeStrokes(sourceStrokeIds, sourcePageId)
                        PageDataManager.addStrokes(migratedStrokes, targetPageId)
                    }
                    strokesMigrated = true  // Track for recognition trigger
                }

                // Transfer metadata
                val targetTask = targetTasks.find { it.rowIndex == targetRowIndex }
                if (targetTask != null) {
                    // Every content column moves; the occurrence detaches (another day)
                    transferTaskContent(sourceTask, targetTask.id, keepTemplateLink = false)
                } else if (sourceTask.dueDay != null) {
                    afterCommit { cancelTaskNotification(sourceTask.id) }
                }
                markRecurringOccurrenceDetached(sourceTask, targetDate)

                // Clear source
                taskDao.clearTaskMetadata(sourceTask.id)
                movedCount++
            }

            // Update parentTaskId for children in new location
            if (groupInfo.totalSlots > 1) {
                val newParentTask = targetTasks.find { it.rowIndex == startRow }
                if (newParentTask != null) {
                    for (i in 1 until groupInfo.totalSlots) {
                        val childTargetTask = targetTasks.find { it.rowIndex == startRow + i }
                        if (childTargetTask != null) {
                            taskDao.setParentTask(childTargetTask.id, newParentTask.id)
                        }
                    }
                }
            }
        }

        return Result.success(movedCount)
    }

    /**
     * Helper to count how many rows are used on a task page.
     */
    private fun countUsedRowsOnPage(
        tasks: List<Task>,
        date: String,
        pageIndex: Int,
        canvasHeight: Float
    ): Int {
        val pageId = tasks.firstOrNull()?.pageId ?: return 0

        val strokes = PageDataManager.getStrokesInRectangle(
            Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
            pageId
        ) ?: emptyList()

        var usedCount = 0
        for (rowIdx in 0 until 10) {
            val rowBounds = calculateRowBounds(rowIdx, canvasHeight, 10)
            val hasStrokes = strokes.any { stroke ->
                stroke.bottom > rowBounds.topY && stroke.top < rowBounds.bottomY
            }
            val task = tasks.find { it.rowIndex == rowIdx }
            if (hasStrokes || task?.isCompleted == true || task?.priority?.isNotEmpty() == true) {
                usedCount++
            }
        }

        return usedCount
    }

    /**
     * Delete all tasks for a date, including strokes from all pages.
     * Resets the day to have just one empty task page.
     *
     * @param date The date to clear (format: "yyyy-MM-dd")
     * @return Result indicating success
     */
    fun deleteAllTasksForDate(date: String): Result<Unit> {
        return try {
            // Get all page IDs for this date
            val pageIds = getDistinctPageIdsForDate(date)

            if (pageIds.isEmpty()) {
                return Result.success(Unit)
            }

            val strokeRepository = StrokeRepository(context)

            // Cancel ALL notifications first (across all pages, before any deletion)
            val maxIndex = getMaxTaskPageIndex(date)
            for (pageIdx in 0..maxIndex) {
                val pageTasks = getTasksForPage(date, pageIdx)
                for (task in pageTasks) {
                    if (task.dueDay != null) {
                        cancelTaskNotification(task.id)
                    }
                }
            }

            // Delete all strokes from each task page
            for (pageId in pageIds) {
                // BUG FIX: Fall back to DB if page not in memory
                val strokes = PageDataManager.getStrokesInRectangle(
                    Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                    pageId
                ) ?: run {
                    Log.w(TAG, "DELETE_ALL: Page $pageId not in cache, loading from DB")
                    strokeRepository.getAllForPage(pageId)
                }

                if (strokes.isNotEmpty()) {
                    val strokeIds = strokes.map { it.id }
                    strokeRepository.deleteAll(strokeIds)
                    PageDataManager.removeStrokes(strokeIds, pageId)
                }
            }

            // Reset page 0 task metadata completely (keep the slot structure)
            val tasks = getTasksForPage(date, 0)
            for (task in tasks) {
                // Orphan children if this is a parent task
                if (taskDao.hasChildren(task.id)) {
                    taskDao.orphanChildren(task.id)
                }
                markRecurringOccurrenceSkipped(task)
                // Clear ALL metadata (notification already cancelled above)
                taskDao.clearTaskMetadata(task.id)
            }

            // Delete extra pages (keep only page 0)
            if (maxIndex > 0) {
                // Delete pages from highest to lowest to avoid index shifting issues
                for (pageIdx in maxIndex downTo 1) {
                    deleteTaskPage(date, pageIdx)
                }
                Log.i(TAG, "DELETE_ALL: Deleted $maxIndex extra pages, reset to 1 page")
            }

            // Invalidate cache and signal change
            invalidateTaskCache(date)
            _taskChanged.tryEmit(date)

            // Trigger canvas refresh (non-blocking)
            TasksCanvasRefresh.request()  // coalesced (plan §13.4)

            Log.i(TAG, "DELETE_ALL: Cleared all tasks for $date")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "DELETE_ALL failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete all tasks from a specific task page only.
     * Unlike deleteAllTasksForDate(), this only affects one page.
     * If this was the only page, the page is reset to empty state.
     * If there were multiple pages, this page is deleted and indices are compacted.
     *
     * @param date The date (format: "yyyy-MM-dd")
     * @param pageIndex The task page index to delete
     * @return Result indicating success
     */
    fun deleteTasksOnPage(date: String, pageIndex: Int): Result<Unit> {
        return try {
            val tasks = getTasksForPage(date, pageIndex)
            if (tasks.isEmpty()) {
                return Result.success(Unit)
            }

            val pageId = tasks.firstOrNull()?.pageId
            val strokeRepository = StrokeRepository(context)

            // Delete all strokes from this page
            if (pageId != null) {
                val strokes = PageDataManager.getStrokesInRectangle(
                    Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                    pageId
                ) ?: run {
                    Log.w(TAG, "DELETE_PAGE: Page $pageId not in cache, loading from DB")
                    strokeRepository.getAllForPage(pageId)
                }

                if (strokes.isNotEmpty()) {
                    val strokeIds = strokes.map { it.id }
                    strokeRepository.deleteAll(strokeIds)
                    PageDataManager.removeStrokes(strokeIds, pageId)
                }
            }

            // Cancel ALL notifications for tasks on this page FIRST
            for (task in tasks) {
                if (task.dueDay != null) {
                    cancelTaskNotification(task.id)
                }
            }

            // Check if this is the only page
            val maxPageIndex = getMaxTaskPageIndex(date)

            if (maxPageIndex == 0) {
                // This is the only page - clear ALL metadata (keep the page structure)
                for (task in tasks) {
                    // Orphan children if this is a parent task
                    if (taskDao.hasChildren(task.id)) {
                        taskDao.orphanChildren(task.id)
                    }
                    markRecurringOccurrenceSkipped(task)
                    // Clear ALL metadata (notification already cancelled above)
                    taskDao.clearTaskMetadata(task.id)
                }
                Log.i(TAG, "DELETE_PAGE: Cleared tasks on only page for $date (kept page structure)")
            } else {
                // Multiple pages exist - delete this page and compact indices
                // (notifications already cancelled above, deleteTaskPage handles entity deletion)
                deleteTaskPage(date, pageIndex)
                Log.i(TAG, "DELETE_PAGE: Deleted page $pageIndex for $date (compacted remaining pages)")
            }

            // Invalidate cache and signal change
            invalidateTaskCache(date)
            _taskChanged.tryEmit(date)

            // Trigger canvas refresh (non-blocking)
            TasksCanvasRefresh.request()  // coalesced (plan §13.4)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "DELETE_PAGE failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ========== CONVERT HANDWRITING ==========

    /**
     * Result of a convert handwriting operation.
     */
    data class ConvertHandwritingResult(
        val convertedCount: Int,
        val skippedCount: Int,
        val alreadyTypedCount: Int
    ) {
        operator fun plus(other: ConvertHandwritingResult) = ConvertHandwritingResult(
            convertedCount = convertedCount + other.convertedCount,
            skippedCount = skippedCount + other.skippedCount,
            alreadyTypedCount = alreadyTypedCount + other.alreadyTypedCount
        )
    }

    /**
     * Convert handwritten tasks on a single page to typed text.
     *
     * The background OCR (TaskTextExtractor) already recognizes all handwriting and stores
     * it in Task.recognizedText. This method simply promotes recognizedText → typedText
     * and deletes all strokes from the page.
     */
    fun convertHandwritingOnPage(
        date: String,
        pageIndex: Int,
        canvasHeight: Float
    ): ConvertHandwritingResult {
        // One transaction (plan §13.2-4, Oct 2026): text promotion and ink deletion together
        return inTransaction {
            val tasks = getTasksForPage(date, pageIndex)
            if (tasks.isEmpty()) return@inTransaction ConvertHandwritingResult(0, 0, 0)

            val pageId = tasks.firstOrNull()?.pageId ?: return@inTransaction ConvertHandwritingResult(0, 0, 0)

            var convertedCount = 0
            var alreadyTypedCount = 0
            val convertedRows = mutableListOf<Int>()

            for (task in tasks) {
                // Already has typed or AI-converted text — skip
                if (!task.typedText.isNullOrBlank() || !task.convertedText.isNullOrBlank()) {
                    alreadyTypedCount++
                    continue
                }

                // Has recognized text from background OCR — promote to typedText
                val text = task.recognizedText
                if (!text.isNullOrBlank()) {
                    taskDao.setTypedText(task.id, text)
                    convertedRows.add(task.rowIndex)
                    convertedCount++
                }
            }

            // T3 (Oct 2026): delete only the ink of the rows that converted — their text now lives
            // in typedText. Unrecognized rows and drawings keep their ink.
            if (convertedCount > 0) {
                if (canvasHeight > 0f) {
                    val strokeRepository = StrokeRepository(context)
                    val pageStrokes = PageDataManager.getStrokesInRectangle(
                        Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE), pageId
                    ) ?: strokeRepository.getAllForPage(pageId)
                    val strokesByRow = groupStrokesByRow(pageStrokes, canvasHeight)
                    val strokeIds = convertedRows.flatMap { row -> strokesByRow[row].orEmpty() }.map { it.id }
                    if (strokeIds.isNotEmpty()) {
                        strokeRepository.deleteByIdsOnPage(pageId, strokeIds)
                        afterCommit { PageDataManager.removeStrokes(strokeIds, pageId) }
                    }
                } else {
                    Log.w(TAG, "CONVERT_HANDWRITING: no canvas height, ink kept on page $pageIndex for $date")
                }

                invalidateTaskCache(date)
                _taskChanged.tryEmit(date)
            }
            Log.i(TAG, "CONVERT_HANDWRITING page $pageIndex for $date: converted=$convertedCount, alreadyTyped=$alreadyTypedCount")
            ConvertHandwritingResult(convertedCount, 0, alreadyTypedCount)
        }
    }

    /**
     * Convert handwritten tasks for all pages on a given date.
     */
    fun convertHandwritingForDay(date: String, canvasHeight: Float): ConvertHandwritingResult {
        val maxPageIndex = getMaxTaskPageIndex(date)
        var result = ConvertHandwritingResult(0, 0, 0)
        for (pageIndex in 0..maxPageIndex) {
            result = result + convertHandwritingOnPage(date, pageIndex, canvasHeight)
        }
        return result
    }

    /**
     * Convert handwritten tasks for all dates in a range (inclusive).
     */
    fun convertHandwritingForDateRange(
        startDate: String,
        endDate: String,
        canvasHeight: Float
    ): ConvertHandwritingResult {
        // Get all tasks in range to find distinct dates
        val tasks = taskDao.getTasksForDateRange(startDate, endDate)
        val distinctDates = tasks.map { it.dayDate }.distinct()

        var result = ConvertHandwritingResult(0, 0, 0)
        for (date in distinctDates) {
            result = result + convertHandwritingForDay(date, canvasHeight)
        }
        return result
    }

    // ========== COPY BULK ACTIONS ==========

    /**
     * Copy all tasks from a specific task page to another date.
     * Unlike move, this PRESERVES source data (strokes are duplicated, not moved).
     *
     * GROUP-AWARE: Parent-child task groups are copied together as atomic units.
     * Groups are placed in contiguous slots, and children get NEW parentTaskId
     * references pointing to the copied parent task (not the original).
     *
     * @param sourceDate The source date (format: "yyyy-MM-dd")
     * @param sourcePageIndex The source task page index
     * @param targetDate The target date (format: "yyyy-MM-dd")
     * @param canvasHeight The canvas height in pixels
     * @return Result with count of copied tasks
     */
    fun copyAllTasksFromPage(
        sourceDate: String,
        sourcePageIndex: Int,
        targetDate: String,
        canvasHeight: Float
    ): Result<CopyTasksResult> {
        // Full screen Tasks (Oct 2026): this works on pages of 10; the tools calling it leave full screen first
        if (TaskPagination.isReflowed(sourceDate) || TaskPagination.isReflowed(targetDate)) {
            return Result.failure(IllegalStateException("Tasks are on full screen pages"))
        }
        return try {
            if (sourceDate == targetDate) {
                return Result.failure(Exception("Cannot copy to same date"))
            }

            val sourceTasks = getTasksForPage(sourceDate, sourcePageIndex)
            if (sourceTasks.isEmpty()) {
                return Result.success(CopyTasksResult(0))
            }

            val sourcePageId = sourceTasks.firstOrNull()?.pageId
                ?: return Result.failure(Exception("No page ID found"))

            // Target day for due date comparison
            val targetDay = targetDate  // "yyyy-MM-dd" (due days compare as strings)

            val strokeRepository = StrokeRepository(context)
            val sourceStrokes = PageDataManager.getStrokesInRectangle(
                Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                sourcePageId
            ) ?: run {
                Log.w(TAG, "BULK_COPY: Source page $sourcePageId not in PageDataManager cache, loading from DB")
                strokeRepository.getAllForPage(sourcePageId)
            }

            // Find tasks that have content
            val tasksWithDirectContent = sourceTasks.filter { task ->
                val rowBounds = calculateRowBounds(task.rowIndex, canvasHeight, 10)
                val hasStrokes = sourceStrokes.any { stroke ->
                    val strokeCenterY = (stroke.top + stroke.bottom) / 2
                    strokeCenterY >= rowBounds.topY && strokeCenterY < rowBounds.bottomY
                }
                task.isCompleted ||
                    task.priority.isNotEmpty() ||
                    task.typedText?.isNotBlank() == true ||
                    task.convertedText?.isNotBlank() == true ||
                    task.recognizedText?.isNotBlank() == true ||
                    hasStrokes
            }

            // Include parent tasks when any of their children have content (parent row may be empty)
            val childParentIds = tasksWithDirectContent.mapNotNull { it.parentTaskId }.toSet()
            val parentsToInclude = sourceTasks.filter { task ->
                task.id in childParentIds && tasksWithDirectContent.none { it.id == task.id }
            }
            val tasksWithContent = tasksWithDirectContent + parentsToInclude

            if (tasksWithContent.isEmpty()) {
                return Result.success(CopyTasksResult(0))
            }

            // GROUP-AWARE: Organize tasks into groups
            val processedTaskIds = mutableSetOf<String>()
            val taskGroups = mutableListOf<TaskGroupInfo>()

            for (task in tasksWithContent) {
                if (task.id in processedTaskIds) continue

                val group = getTaskGroup(task.id)
                val groupOnThisPage = group.filter { groupTask ->
                    tasksWithContent.any { it.id == groupTask.id }
                }

                if (groupOnThisPage.isNotEmpty()) {
                    val parent = groupOnThisPage[0]
                    val children = groupOnThisPage.drop(1)
                    taskGroups.add(TaskGroupInfo(parent, children, groupOnThisPage.size))
                    groupOnThisPage.forEach { processedTaskIds.add(it.id) }
                }
            }

            // Sort groups: larger groups first for better packing
            taskGroups.sortWith(compareByDescending<TaskGroupInfo> { it.totalSlots }.thenBy { it.parentTask.rowIndex })

            Log.i(TAG, "BULK_COPY: Found ${taskGroups.size} task group(s) to copy")

            var currentTargetPageIndex = 0
            var copiedCount = 0
            var dueDatesPreserved = 0
            var dueDatesCleared = 0
            val pagesWithCopiedStrokes = mutableSetOf<String>()  // Track for recognition trigger

            // Track allocated rows per page to prevent overwrites (parent tasks may have no content)
            val allocatedRowsByPage = mutableMapOf<Int, MutableSet<Int>>()

            for (groupInfo in taskGroups) {
                // Get current target page state
                var (targetTasks, _) = getOrCreateTasksForPage(targetDate, currentTargetPageIndex)
                var targetPageId = targetTasks.firstOrNull()?.pageId ?: continue

                // Calculate used rows on target (content-based) + already allocated rows
                var usedRows = calculateUsedRowsForPage(targetTasks, targetPageId, canvasHeight, strokeRepository)
                val allocatedRows = allocatedRowsByPage.getOrPut(currentTargetPageIndex) { mutableSetOf() }
                var combinedUsedRows = usedRows + allocatedRows

                // Find contiguous block for this group
                var startRow = findContiguousSlots(groupInfo.totalSlots, combinedUsedRows)

                // If no space, try next pages
                while (startRow == null) {
                    currentTargetPageIndex++
                    val pageResult = getOrCreateTasksForPage(targetDate, currentTargetPageIndex)
                    targetTasks = pageResult.first
                    targetPageId = targetTasks.firstOrNull()?.pageId ?: break
                    usedRows = calculateUsedRowsForPage(targetTasks, targetPageId, canvasHeight, strokeRepository)
                    val nextAllocatedRows = allocatedRowsByPage.getOrPut(currentTargetPageIndex) { mutableSetOf() }
                    combinedUsedRows = usedRows + nextAllocatedRows
                    startRow = findContiguousSlots(groupInfo.totalSlots, combinedUsedRows)
                }

                if (startRow == null) {
                    Log.e(TAG, "BULK_COPY: Could not find space for group of ${groupInfo.totalSlots}")
                    continue
                }

                // Mark these rows as allocated BEFORE placing to prevent future overwrites
                val currentAllocated = allocatedRowsByPage.getOrPut(currentTargetPageIndex) { mutableSetOf() }
                for (i in 0 until groupInfo.totalSlots) {
                    currentAllocated.add(startRow + i)
                }

                Log.i(TAG, "BULK_COPY: Placing group of ${groupInfo.totalSlots} at page $currentTargetPageIndex row $startRow")

                // For parent-child groups, determine parent's effective due date first
                // (needed to enforce child due date constraint)
                val parentSourceTask = groupInfo.parentTask
                val parentEffectiveDueDate: String? = if (parentSourceTask.dueDay != null) {
                    if (targetDay <= parentSourceTask.dueDay) {
                        parentSourceTask.dueDay  // Preserve parent due date
                    } else {
                        null  // Clear parent due date (already past)
                    }
                } else {
                    null
                }

                // Copy each task in the group (do NOT delete source)
                val allGroupTasks = listOf(groupInfo.parentTask) + groupInfo.children
                for ((index, sourceTask) in allGroupTasks.withIndex()) {
                    val targetRowIndex = startRow + index
                    val sourceRowBounds = calculateRowBounds(sourceTask.rowIndex, canvasHeight, 10)
                    val targetRowBounds = calculateRowBounds(targetRowIndex, canvasHeight, 10)
                    val isParent = index == 0
                    val isChild = index > 0

                    // Copy strokes (do NOT delete source)
                    val strokesInRow = extractStrokesForRow(sourceStrokes, sourceRowBounds, canvasHeight)
                    if (strokesInRow.isNotEmpty()) {
                        val copiedStrokes = createMigratedStrokes(strokesInRow, sourceRowBounds, targetRowBounds, targetPageId)
                        strokeRepository.create(copiedStrokes)
                        PageDataManager.addStrokes(copiedStrokes, targetPageId)
                        pagesWithCopiedStrokes.add(targetPageId)  // Track for recognition
                    }

                    // Copy metadata (do NOT clear source)
                    val targetTask = targetTasks.find { it.rowIndex == targetRowIndex }
                    if (targetTask != null) {
                        taskDao.setCompleted(targetTask.id, sourceTask.isCompleted)
                        taskDao.setPriority(targetTask.id, sourceTask.priority)
                        taskDao.setTypedText(targetTask.id, sourceTask.typedText)
                        taskDao.setConvertedText(targetTask.id, sourceTask.convertedText)
                        taskDao.setTemplateId(targetTask.id, null)
                        // T2 (Oct 2026): recognizedText is not displayed (TaskDrawingAreaOverlay), so it
                        // always moves with the row (search/AI/Convert keep working on moved ink).
                        taskDao.setRecognizedTextById(targetTask.id, sourceTask.recognizedText)
                        taskDao.setReminderMinuteOfDay(targetTask.id, sourceTask.reminderMinuteOfDay)

                        // SMART DUE DATE COPY: Only preserve if target date <= due date
                        if (sourceTask.dueDay != null) {
                            val effectiveDueDate: String? = if (isParent) {
                                // Parent: use pre-calculated effective due date
                                parentEffectiveDueDate
                            } else {
                                // Child: due date cannot exceed parent's effective due date
                                if (targetDay <= sourceTask.dueDay) {
                                    // Source due date is valid for target date
                                    if (parentEffectiveDueDate != null) {
                                        // Enforce child <= parent constraint
                                        minOf(sourceTask.dueDay, parentEffectiveDueDate)
                                    } else {
                                        // No parent due date constraint
                                        sourceTask.dueDay
                                    }
                                } else {
                                    null  // Target date is past source due date
                                }
                            }

                            if (effectiveDueDate != null) {
                                taskDao.setDueDay(targetTask.id, effectiveDueDate)
                                // Schedule notification for the copied task
                                taskDao.getById(targetTask.id)?.let { copiedTask ->
                                    scheduleTaskNotificationIfEnabled(copiedTask)
                                }
                                dueDatesPreserved++
                                Log.i(TAG, "BULK_COPY: Preserved due date for task ${targetTask.id}")
                            } else {
                                dueDatesCleared++
                                Log.i(TAG, "BULK_COPY: Cleared due date for task ${targetTask.id} (target date > source due date)")
                            }
                        }
                    }

                    copiedCount++
                }

                // CRITICAL: Update parentTaskId for children to point to NEW parent ID
                if (groupInfo.totalSlots > 1) {
                    val newParentTask = targetTasks.find { it.rowIndex == startRow }
                    if (newParentTask != null) {
                        for (i in 1 until groupInfo.totalSlots) {
                            val childTargetTask = targetTasks.find { it.rowIndex == startRow + i }
                            if (childTargetTask != null) {
                                taskDao.setParentTask(childTargetTask.id, newParentTask.id)
                                Log.i(TAG, "BULK_COPY: Linked copied child at row ${startRow + i} to new parent ${newParentTask.id}")
                            }
                        }
                    }
                }
            }

            // Invalidate target cache only (source unchanged)
            invalidateTaskCache(targetDate)
            _taskChanged.tryEmit(targetDate)

            // Trigger canvas refresh (non-blocking)
            TasksCanvasRefresh.request()  // coalesced (plan §13.4)

            Log.i(TAG, "BULK_COPY: Copied $copiedCount tasks from $sourceDate page $sourcePageIndex to $targetDate (due dates: $dueDatesPreserved preserved, $dueDatesCleared cleared)")
            Result.success(CopyTasksResult(copiedCount, dueDatesPreserved, dueDatesCleared))
        } catch (e: Exception) {
            Log.e(TAG, "BULK_COPY failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Copy all tasks from ALL pages of a date to another date.
     * Iterates through all task pages and copies content to the target.
     * Source data is preserved (this is a copy, not move).
     * SMART DUE DATES: Preserves due dates only if target date <= due date.
     *
     * @param sourceDate The source date (format: "yyyy-MM-dd")
     * @param targetDate The target date (format: "yyyy-MM-dd")
     * @param canvasHeight The canvas height in pixels
     * @return Result with CopyTasksResult containing counts and due date stats
     */
    fun copyAllTasksForDate(
        sourceDate: String,
        targetDate: String,
        canvasHeight: Float
    ): Result<CopyTasksResult> {
        return try {
            if (sourceDate == targetDate) {
                return Result.failure(Exception("Cannot copy to same date"))
            }

            val maxPageIndex = getMaxTaskPageIndex(sourceDate)
            if (maxPageIndex < 0) {
                return Result.success(CopyTasksResult(0))
            }

            // Parse target date for due date comparison
            val targetDay = targetDate  // "yyyy-MM-dd" (due days compare as strings)

            var totalCopied = 0
            var totalDueDatesPreserved = 0
            var totalDueDatesCleared = 0
            var currentTargetPageIndex = 0

            // Iterate through all source pages
            for (sourcePageIdx in 0..maxPageIndex) {
                val result = copyAllTasksFromPageInternal(
                    sourceDate = sourceDate,
                    sourcePageIndex = sourcePageIdx,
                    targetDate = targetDate,
                    targetPageIndex = currentTargetPageIndex,
                    canvasHeight = canvasHeight,
                    targetDay = targetDay
                )

                if (result.isSuccess) {
                    val copyResult = result.getOrNull() ?: CopyTasksResult(0)
                    totalCopied += copyResult.copiedCount
                    totalDueDatesPreserved += copyResult.dueDatesPreserved
                    totalDueDatesCleared += copyResult.dueDatesCleared

                    // Check if target page is full, create next page if needed
                    val (targetTasks, _) = getOrCreateTasksForPage(targetDate, currentTargetPageIndex)
                    val usedCount = countUsedRowsOnPage(targetTasks, targetDate, currentTargetPageIndex, canvasHeight)
                    if (usedCount >= 10) {
                        currentTargetPageIndex++
                    }
                }
            }

            // NOTE: Unlike move, we do NOT delete source pages - data is preserved

            // Invalidate target cache only
            invalidateTaskCache(targetDate)
            _taskChanged.tryEmit(targetDate)

            // Trigger canvas refresh (non-blocking)
            TasksCanvasRefresh.request()  // coalesced (plan §13.4)

            Log.i(TAG, "BULK_COPY_ALL: Copied $totalCopied tasks from $sourceDate to $targetDate (due dates: $totalDueDatesPreserved preserved, $totalDueDatesCleared cleared)")
            Result.success(CopyTasksResult(totalCopied, totalDueDatesPreserved, totalDueDatesCleared))
        } catch (e: Exception) {
            Log.e(TAG, "BULK_COPY_ALL failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Internal helper for copying tasks from one page to a specific target page.
     * Source data is preserved (no deletions).
     * GROUP-AWARE: Parent-child groups are copied together to contiguous slots.
     * SMART DUE DATES: Preserves due dates only if target date <= due date.
     */
    private fun copyAllTasksFromPageInternal(
        sourceDate: String,
        sourcePageIndex: Int,
        targetDate: String,
        targetPageIndex: Int,
        canvasHeight: Float,
        targetDay: String
    ): Result<CopyTasksResult> {
        val sourceTasks = getTasksForPage(sourceDate, sourcePageIndex)
        if (sourceTasks.isEmpty()) return Result.success(CopyTasksResult(0))

        val sourcePageId = sourceTasks.firstOrNull()?.pageId ?: return Result.success(CopyTasksResult(0))
        val strokeRepository = StrokeRepository(context)

        val sourceStrokes = PageDataManager.getStrokesInRectangle(
            Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
            sourcePageId
        ) ?: run {
            Log.w(TAG, "BULK_COPY_INTERNAL: Source page $sourcePageId not in cache, loading from DB")
            strokeRepository.getAllForPage(sourcePageId)
        }

        // Find tasks with content
        val tasksWithDirectContent = sourceTasks.filter { task ->
            val rowBounds = calculateRowBounds(task.rowIndex, canvasHeight, 10)
            val hasStrokes = sourceStrokes.any { stroke ->
                val strokeCenterY = (stroke.top + stroke.bottom) / 2
                strokeCenterY >= rowBounds.topY && strokeCenterY < rowBounds.bottomY
            }
            task.isCompleted ||
                task.priority.isNotEmpty() ||
                task.typedText?.isNotBlank() == true ||
                task.convertedText?.isNotBlank() == true ||
                task.recognizedText?.isNotBlank() == true ||
                hasStrokes
        }

        // Include parent tasks when any of their children have content (parent row may be empty)
        val childParentIds = tasksWithDirectContent.mapNotNull { it.parentTaskId }.toSet()
        val parentsToInclude = sourceTasks.filter { task ->
            task.id in childParentIds && tasksWithDirectContent.none { it.id == task.id }
        }
        val tasksWithContent = tasksWithDirectContent + parentsToInclude

        if (tasksWithContent.isEmpty()) return Result.success(CopyTasksResult(0))

        // GROUP-AWARE: Organize tasks into groups
        val processedTaskIds = mutableSetOf<String>()
        val taskGroups = mutableListOf<TaskGroupInfo>()

        for (task in tasksWithContent) {
            if (task.id in processedTaskIds) continue

            val group = getTaskGroup(task.id)
            val groupOnThisPage = group.filter { groupTask ->
                tasksWithContent.any { it.id == groupTask.id }
            }

            if (groupOnThisPage.isNotEmpty()) {
                val parent = groupOnThisPage[0]
                val children = groupOnThisPage.drop(1)
                taskGroups.add(TaskGroupInfo(parent, children, groupOnThisPage.size))
                groupOnThisPage.forEach { processedTaskIds.add(it.id) }
            }
        }

        // Sort groups: larger groups first for better packing
        taskGroups.sortWith(compareByDescending<TaskGroupInfo> { it.totalSlots }.thenBy { it.parentTask.rowIndex })

        // Get/create target page
        val (targetTasks, _) = getOrCreateTasksForPage(targetDate, targetPageIndex)
        val targetPageId = targetTasks.firstOrNull()?.pageId ?: return Result.success(CopyTasksResult(0))

        val baseUsedRows = calculateUsedRowsForPage(targetTasks, targetPageId, canvasHeight, strokeRepository)
        val allocatedRows = mutableSetOf<Int>()  // Track allocated rows to prevent overwrites
        var copiedCount = 0
        var dueDatesPreserved = 0
        var dueDatesCleared = 0
        var strokesCopied = false  // Track for recognition trigger

        for (groupInfo in taskGroups) {
            // Find contiguous block for this group (combine content-based + allocated)
            val combinedUsedRows = baseUsedRows + allocatedRows
            val startRow = findContiguousSlots(groupInfo.totalSlots, combinedUsedRows)

            if (startRow == null) {
                Log.w(TAG, "BULK_COPY_INTERNAL: No contiguous space for group of ${groupInfo.totalSlots}, skipping")
                continue
            }

            // Mark these rows as allocated BEFORE placing
            for (i in 0 until groupInfo.totalSlots) {
                allocatedRows.add(startRow + i)
            }

            // For parent-child groups, determine parent's effective due date first
            val parentSourceTask = groupInfo.parentTask
            val parentEffectiveDueDate: String? = if (parentSourceTask.dueDay != null) {
                if (targetDay <= parentSourceTask.dueDay) {
                    parentSourceTask.dueDay
                } else {
                    null
                }
            } else {
                null
            }

            // Copy each task in the group
            val allGroupTasks = listOf(groupInfo.parentTask) + groupInfo.children
            for ((index, sourceTask) in allGroupTasks.withIndex()) {
                val targetRowIndex = startRow + index
                val sourceRowBounds = calculateRowBounds(sourceTask.rowIndex, canvasHeight, 10)
                val targetRowBounds = calculateRowBounds(targetRowIndex, canvasHeight, 10)
                val isParent = index == 0

                // Copy strokes (do NOT delete source)
                val strokesInRow = extractStrokesForRow(sourceStrokes, sourceRowBounds, canvasHeight)
                if (strokesInRow.isNotEmpty()) {
                    val copiedStrokes = createMigratedStrokes(strokesInRow, sourceRowBounds, targetRowBounds, targetPageId)
                    strokeRepository.create(copiedStrokes)
                    PageDataManager.addStrokes(copiedStrokes, targetPageId)
                    strokesCopied = true  // Track for recognition trigger
                }

                // Copy metadata (do NOT clear source)
                val targetTask = targetTasks.find { it.rowIndex == targetRowIndex }
                if (targetTask != null) {
                    taskDao.setCompleted(targetTask.id, sourceTask.isCompleted)
                    taskDao.setPriority(targetTask.id, sourceTask.priority)
                    taskDao.setTypedText(targetTask.id, sourceTask.typedText)
                    taskDao.setConvertedText(targetTask.id, sourceTask.convertedText)
                    taskDao.setTemplateId(targetTask.id, null)
                    // T2 (Oct 2026): recognizedText is not displayed (TaskDrawingAreaOverlay), so it
                    // always moves with the row (search/AI/Convert keep working on moved ink).
                    taskDao.setRecognizedTextById(targetTask.id, sourceTask.recognizedText)
                    taskDao.setReminderMinuteOfDay(targetTask.id, sourceTask.reminderMinuteOfDay)

                    // SMART DUE DATE COPY: Only preserve if target date <= due date
                    if (sourceTask.dueDay != null) {
                        val effectiveDueDate: String? = if (isParent) {
                            parentEffectiveDueDate
                        } else {
                            if (targetDay <= sourceTask.dueDay) {
                                if (parentEffectiveDueDate != null) {
                                    minOf(sourceTask.dueDay, parentEffectiveDueDate)
                                } else {
                                    sourceTask.dueDay
                                }
                            } else {
                                null
                            }
                        }

                        if (effectiveDueDate != null) {
                            taskDao.setDueDay(targetTask.id, effectiveDueDate)
                            taskDao.getById(targetTask.id)?.let { copiedTask ->
                                scheduleTaskNotificationIfEnabled(copiedTask)
                            }
                            dueDatesPreserved++
                        } else {
                            dueDatesCleared++
                        }
                    }
                }

                copiedCount++
            }

            // CRITICAL: Update parentTaskId for children to point to NEW parent ID
            if (groupInfo.totalSlots > 1) {
                val newParentTask = targetTasks.find { it.rowIndex == startRow }
                if (newParentTask != null) {
                    for (i in 1 until groupInfo.totalSlots) {
                        val childTargetTask = targetTasks.find { it.rowIndex == startRow + i }
                        if (childTargetTask != null) {
                            taskDao.setParentTask(childTargetTask.id, newParentTask.id)
                        }
                    }
                }
            }
        }

        return Result.success(CopyTasksResult(copiedCount, dueDatesPreserved, dueDatesCleared))
    }

    /**
     * Copy a single task (or task group) to a target date.
     * Source data is preserved (this is a copy, not move).
     * GROUP-AWARE: If the task is a parent, all children are copied together.
     * SMART DUE DATES: Preserves due dates only if target date <= due date.
     *
     * @param task The task to copy
     * @param targetDate The target date (format: "yyyy-MM-dd")
     * @param targetPageIndex Starting target page index (default 0, overflows to next page if full)
     * @param canvasHeight The canvas height in pixels
     * @param copyChildren If true, copies parent + all children as a group.
     *                     If false, copies only the single task as standalone (no parentTaskId).
     * @return Result with CopyTasksResult containing counts and due date stats
     */
    fun copySingleTask(
        task: Task,
        targetDate: String,
        targetPageIndex: Int = 0,
        canvasHeight: Float,
        copyChildren: Boolean = true
    ): Result<CopyTasksResult> {
        // Full screen Tasks (Oct 2026): this works on pages of 10; the tools calling it leave full screen first
        if (TaskPagination.isReflowed(task.dayDate) || TaskPagination.isReflowed(targetDate)) {
            return Result.failure(IllegalStateException("Tasks are on full screen pages"))
        }
        return try {
            val sourceDate = task.dayDate
            if (sourceDate == targetDate) {
                return Result.failure(Exception("Cannot copy to same date"))
            }

            // Determine tasks to copy
            val tasksToCopy: List<Task> = if (copyChildren) {
                // Get full group (parent + children) — getTaskGroup handles both parent and child input
                val group = getTaskGroup(task.id)
                if (group.isEmpty()) listOf(task) else group
            } else {
                // Single task only (standalone copy, no parent-child relationship at target)
                listOf(task)
            }

            val groupSize = tasksToCopy.size
            val isGroup = groupSize > 1

            Log.i(TAG, "SINGLE_COPY: Copying ${if (isGroup) "group of $groupSize" else "single task"} from $sourceDate to $targetDate")

            // Load source strokes
            val sourcePageId = task.pageId
            val strokeRepository = StrokeRepository(context)
            val sourceStrokes = PageDataManager.getStrokesInRectangle(
                Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                sourcePageId
            ) ?: run {
                Log.w(TAG, "SINGLE_COPY: Source page $sourcePageId not in cache, loading from DB")
                strokeRepository.getAllForPage(sourcePageId)
            }

            // Parse target date for due date comparison
            val targetDay = targetDate  // "yyyy-MM-dd" (due days compare as strings)

            // Find space on target page (overflow to next pages if needed)
            var currentTargetPageIndex = targetPageIndex
            var targetTasks: List<Task> = emptyList()
            var targetPageId: String = ""
            var finalStartRow: Int = -1

            var foundSpace = false
            while (!foundSpace) {
                val pageResult = getOrCreateTasksForPage(targetDate, currentTargetPageIndex)
                targetTasks = pageResult.first
                val pageId = targetTasks.firstOrNull()?.pageId
                if (pageId == null) break

                targetPageId = pageId
                val usedRows = calculateUsedRowsForPage(targetTasks, targetPageId, canvasHeight, strokeRepository)
                val slot = findContiguousSlots(groupSize, usedRows)

                if (slot != null) {
                    finalStartRow = slot
                    foundSpace = true
                } else {
                    // No space on this page, try next
                    currentTargetPageIndex++
                    if (currentTargetPageIndex > 20) {
                        return Result.failure(Exception("No space available for copy"))
                    }
                }
            }

            if (!foundSpace) {
                return Result.failure(Exception("No space available for copy"))
            }

            Log.i(TAG, "SINGLE_COPY: Placing at page $currentTargetPageIndex row $finalStartRow")

            // For groups, determine parent's effective due date (for child constraint)
            val parentTask = tasksToCopy.first()
            val parentEffectiveDueDate: String? = if (isGroup && parentTask.dueDay != null) {
                if (targetDay <= parentTask.dueDay) parentTask.dueDay else null
            } else {
                null
            }

            var copiedCount = 0
            var dueDatesPreserved = 0
            var dueDatesCleared = 0

            // Copy each task in the group
            for ((index, sourceTask) in tasksToCopy.withIndex()) {
                val targetRowIndex = finalStartRow + index
                val sourceRowBounds = calculateRowBounds(sourceTask.rowIndex, canvasHeight, 10)
                val targetRowBounds = calculateRowBounds(targetRowIndex, canvasHeight, 10)
                val isParentTask = index == 0 && isGroup

                // Copy strokes (do NOT delete source)
                val strokesInRow = extractStrokesForRow(sourceStrokes, sourceRowBounds, canvasHeight)
                if (strokesInRow.isNotEmpty()) {
                    val copiedStrokes = createMigratedStrokes(strokesInRow, sourceRowBounds, targetRowBounds, targetPageId)
                    strokeRepository.create(copiedStrokes)
                    PageDataManager.addStrokes(copiedStrokes, targetPageId)
                }

                // Copy metadata (do NOT clear source)
                val targetTask = targetTasks.find { it.rowIndex == targetRowIndex }
                if (targetTask != null) {
                    taskDao.setCompleted(targetTask.id, sourceTask.isCompleted)
                    taskDao.setPriority(targetTask.id, sourceTask.priority)
                    taskDao.setTypedText(targetTask.id, sourceTask.typedText)
                    taskDao.setConvertedText(targetTask.id, sourceTask.convertedText)
                    taskDao.setTemplateId(targetTask.id, null)
                    // T2 (Oct 2026): recognizedText is not displayed (TaskDrawingAreaOverlay), so it
                    // always moves with the row (search/AI/Convert keep working on moved ink).
                    taskDao.setRecognizedTextById(targetTask.id, sourceTask.recognizedText)
                    taskDao.setReminderMinuteOfDay(targetTask.id, sourceTask.reminderMinuteOfDay)

                    // SMART DUE DATE COPY: Only preserve if target date <= due date
                    if (sourceTask.dueDay != null) {
                        val effectiveDueDate: String? = if (isParentTask) {
                            parentEffectiveDueDate
                        } else if (isGroup) {
                            // Child: due date cannot exceed parent's effective due date
                            if (targetDay <= sourceTask.dueDay) {
                                if (parentEffectiveDueDate != null) {
                                    minOf(sourceTask.dueDay, parentEffectiveDueDate)
                                } else {
                                    sourceTask.dueDay
                                }
                            } else {
                                null
                            }
                        } else {
                            // Standalone task: simple check
                            if (targetDay <= sourceTask.dueDay) sourceTask.dueDay else null
                        }

                        if (effectiveDueDate != null) {
                            taskDao.setDueDay(targetTask.id, effectiveDueDate)
                            taskDao.getById(targetTask.id)?.let { copiedTask ->
                                scheduleTaskNotificationIfEnabled(copiedTask)
                            }
                            dueDatesPreserved++
                            Log.i(TAG, "SINGLE_COPY: Preserved due date for task ${targetTask.id}")
                        } else {
                            dueDatesCleared++
                            Log.i(TAG, "SINGLE_COPY: Cleared due date for task ${targetTask.id} (target date > source due date)")
                        }
                    }
                }

                copiedCount++
            }

            // Update parentTaskId for children to point to NEW parent ID
            if (isGroup && copyChildren) {
                val newParentTask = targetTasks.find { it.rowIndex == finalStartRow }
                if (newParentTask != null) {
                    for (i in 1 until groupSize) {
                        val childTargetTask = targetTasks.find { it.rowIndex == finalStartRow + i }
                        if (childTargetTask != null) {
                            taskDao.setParentTask(childTargetTask.id, newParentTask.id)
                            Log.i(TAG, "SINGLE_COPY: Linked child at row ${finalStartRow + i} to new parent ${newParentTask.id}")
                        }
                    }
                }
            }

            // Invalidate target cache (source unchanged)
            invalidateTaskCache(targetDate)
            _taskChanged.tryEmit(targetDate)

            // Trigger canvas refresh (non-blocking)
            TasksCanvasRefresh.request()  // coalesced (plan §13.4)

            Log.i(TAG, "SINGLE_COPY: Copied $copiedCount task(s) from $sourceDate to $targetDate (due dates: $dueDatesPreserved preserved, $dueDatesCleared cleared)")
            Result.success(CopyTasksResult(copiedCount, dueDatesPreserved, dueDatesCleared))
        } catch (e: Exception) {
            Log.e(TAG, "SINGLE_COPY failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ========== TASK EXPORT REPOSITORY METHODS (Phase 2) ==========

    /**
     * Data class for task export scope summary.
     * Used in the TaskExportDialog to show counts for each scope option.
     */
    data class ExportScopeSummary(
        val taskCount: Int,
        val pageCount: Int,
        val dayCount: Int = 0  // Only for month/all scopes
    )

    /**
     * Get all tasks for a specific day.
     * Used for "Today" scope in task export.
     */
    fun getTasksForDay(date: String): List<Task> {
        return taskDao.getTasksForDay(date)
    }

    suspend fun materializeRecurringTasksForDate(
        date: String,
        canvasHeight: Float = 0f
    ): RecurringTaskMaterializationResult {
        return recurringTaskMaterializer.materializeDate(date, canvasHeight)
    }

    internal fun emitTaskChanged(date: String) {
        _taskChanged.tryEmit(date)
    }

    /**
     * Get all tasks for a month.
     * @param monthPrefix Format: "yyyy-MM" (e.g., "2025-12")
     */
    fun getTasksForMonth(monthPrefix: String): List<Task> {
        return taskDao.getTasksForMonth(monthPrefix)
    }

    /**
     * Get ALL tasks across all dates.
     */
    fun getAllTasks(): List<Task> {
        return taskDao.getAllTasks()
    }

    /**
     * Get export scope summary for "Current Page" scope.
     * Includes stroke detection for handwriting-only tasks.
     *
     * @param date The day date
     * @param pageIndex The task page index
     * @param canvasHeight Canvas height for row bounds calculation (required for stroke detection)
     */
    fun getExportSummaryForPage(date: String, pageIndex: Int, canvasHeight: Float = 0f): ExportScopeSummary {
        val tasks = getTasksForPage(date, pageIndex)
        val taskCount = countTasksWithContent(tasks, canvasHeight)
        return ExportScopeSummary(
            taskCount = taskCount,
            pageCount = 1,
            dayCount = 0
        )
    }

    /**
     * Get export scope summary for "Today" scope.
     * Includes stroke detection for handwriting-only tasks.
     *
     * @param date The day date (format: "yyyy-MM-dd")
     * @param canvasHeight Canvas height for row bounds calculation
     */
    fun getExportSummaryForDay(date: String, canvasHeight: Float = 0f): ExportScopeSummary {
        val tasks = taskDao.getTasksForDay(date)
        val taskCount = countTasksWithContent(tasks, canvasHeight)
        val pageCount = taskDao.getTaskPageCountForDay(date)
        return ExportScopeSummary(
            taskCount = taskCount,
            pageCount = pageCount,
            dayCount = 1
        )
    }

    /**
     * Get export scope summary for "This Month" scope.
     * Includes stroke detection for handwriting-only tasks.
     *
     * @param monthPrefix Format: "yyyy-MM" (e.g., "2025-12")
     * @param canvasHeight Canvas height for row bounds calculation
     */
    fun getExportSummaryForMonth(monthPrefix: String, canvasHeight: Float = 0f): ExportScopeSummary {
        val tasks = taskDao.getTasksForMonth(monthPrefix)
        val taskCount = countTasksWithContent(tasks, canvasHeight)
        val datesWithContent = tasks.filter { taskHasContent(it, canvasHeight) }
            .map { it.dayDate }
            .distinct()
        return ExportScopeSummary(
            taskCount = taskCount,
            pageCount = 0,  // Not relevant for month scope
            dayCount = datesWithContent.size
        )
    }

    /**
     * Get export scope summary for "All Tasks" scope.
     * Includes stroke detection for handwriting-only tasks.
     *
     * @param canvasHeight Canvas height for row bounds calculation
     */
    fun getExportSummaryForAll(canvasHeight: Float = 0f): ExportScopeSummary {
        val tasks = taskDao.getAllTasks()
        val taskCount = countTasksWithContent(tasks, canvasHeight)
        val datesWithContent = tasks.filter { taskHasContent(it, canvasHeight) }
            .map { it.dayDate }
            .distinct()
        return ExportScopeSummary(
            taskCount = taskCount,
            pageCount = 0,  // Not relevant for all scope
            dayCount = datesWithContent.size
        )
    }

    /**
     * Count tasks that have any content (metadata OR strokes).
     *
     * @param tasks List of tasks to check
     * @param canvasHeight Canvas height for stroke detection (0 = skip stroke check)
     */
    private fun countTasksWithContent(tasks: List<Task>, canvasHeight: Float): Int {
        return tasks.count { task -> taskHasContent(task, canvasHeight) }
    }

    /**
     * Check if a task has any content (metadata, text, parent-child relationship, OR strokes).
     * A child task (parentTaskId != null) is considered to have content.
     *
     * @param task The task to check
     * @param canvasHeight Canvas height for stroke detection (0 = skip stroke check)
     */
    fun taskHasContent(task: Task, canvasHeight: Float): Boolean {
        // Fast check: metadata and parent-child relationship
        if (task.isCompleted ||
            task.priority.isNotEmpty() ||
            task.typedText != null ||
            task.convertedText != null ||
            task.recognizedText != null ||
            task.parentTaskId != null) {  // Child tasks have content (belong to a group)
            return true
        }

        // Slow check: strokes (only if canvasHeight provided)
        if (canvasHeight > 0) {
            return taskHasIntentionalStrokes(task, canvasHeight)
        }

        return false
    }

    /**
     * Check if a task has intentional handwriting strokes.
     * Uses the same logic as dateHasTaskContent() for consistency.
     *
     * @param task The task to check
     * @param canvasHeight Canvas height for row bounds calculation
     */
    fun taskHasIntentionalStrokes(task: Task, canvasHeight: Float): Boolean {
        // Full screen Tasks (Oct 2026): pages of more rows of the same height (tasks/TaskPagination)
        val rowCount = TaskPagination.rowsPerPage(task.dayDate)
        val rowBounds = calculateRowBounds(task.rowIndex, TaskPagination.rowsHeight(canvasHeight, rowCount, tasksTopOffset()), rowCount)
        val strokes = PageDataManager.getStrokesInRectangle(rowBounds.toRect(), task.pageId)
        return hasIntentionalStrokeContent(strokes, rowBounds)
    }

    /**
     * Filter tasks to only include those with content (metadata OR strokes).
     *
     * @param tasks List of tasks to filter
     * @param canvasHeight Canvas height for stroke detection (0 = only check metadata)
     */
    fun filterTasksWithContent(tasks: List<Task>, canvasHeight: Float = 0f): List<Task> {
        return tasks.filter { task -> taskHasContent(task, canvasHeight) }
    }

    /**
     * Check if a task has REAL content (excluding metadata fields).
     * Used for Smart Prioritization to ensure empty slots are NEVER included in sorting.
     *
     * Real content = text OR strokes OR is a child task
     * NOT real content = only has metadata (priority, dueDate, isCompleted)
     *
     * This is stricter than taskHasContent() which treats metadata as content.
     *
     * @param task Task to check
     * @param canvasHeight Canvas height for stroke detection (0 = skip stroke check)
     */
    fun taskHasRealContent(task: Task, canvasHeight: Float): Boolean {
        // Text content (use isNullOrBlank, not just != null)
        if (!task.typedText.isNullOrBlank() ||
            !task.convertedText.isNullOrBlank() ||
            !task.recognizedText.isNullOrBlank()) {
            return true
        }

        // Child task (belongs to a group, so it's meaningful content)
        if (task.parentTaskId != null) {
            return true
        }

        // Stroke content (only if canvasHeight provided for accurate detection)
        if (canvasHeight > 0) {
            return taskHasIntentionalStrokes(task, canvasHeight)
        }

        // No real content - this is an empty slot
        // (may have metadata like priority/dueDate but that's orphaned)
        return false
    }

    /**
     * Filter tasks to only include those with REAL content.
     * Used for Smart Prioritization - excludes empty slots even if they have orphaned metadata.
     *
     * @param tasks List of tasks to filter
     * @param canvasHeight Canvas height for stroke detection
     */
    fun filterTasksWithRealContent(tasks: List<Task>, canvasHeight: Float): List<Task> {
        return tasks.filter { task -> taskHasRealContent(task, canvasHeight) }
    }

    // ========== SORT BY PRIORITY - TASK REORDERING ==========

    /**
     * Represents an empty task slot available for placement.
     */
    data class EmptySlot(
        val pageIndex: Int,
        val rowIndex: Int,
        val taskId: String,
        val task: Task
    )

    enum class VoicePlacementFailure {
        EDIT_BLOCKED,
        NO_TASKS_TO_CREATE,
        PAGE_LIMIT_REACHED,
        ALL_SLOTS_OCCUPIED,
        DATABASE_ERROR
    }

    data class VoicePlacementResult(
        val placements: List<EmptySlot>,
        val createdPages: Int,
        val scannedPages: Int,
        val maxPages: Int,
        val existingPages: Int,
        val failure: VoicePlacementFailure? = null
    )

    /** Most task pages a day voice-to-task fills or adds */
    private val voicePlacementMaxPages = 5

    private fun collectEmptySlotsForPage(
        tasks: List<Task>,
        pageIndex: Int,
        canvasHeight: Float,
        target: MutableList<EmptySlot>,
        requiredCount: Int
    ) {
        if (target.size >= requiredCount) return
        val pageId = tasks.firstOrNull()?.pageId ?: return
        val pageStrokes = PageDataManager.getStrokesInRectangle(
            Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
            pageId
        ) ?: StrokeRepository(context).getAllForPage(pageId)

        for (task in tasks.sortedBy { it.rowIndex }) {
            if (target.size >= requiredCount) return

            val hasTypedText = task.typedText?.isNotBlank() == true
            val hasConvertedText = task.convertedText?.isNotBlank() == true
            val hasRecognizedText = task.recognizedText?.isNotBlank() == true
            val hasMetadata = task.isCompleted || task.priority.isNotEmpty()

            val rowBounds = calculateRowBounds(task.rowIndex, canvasHeight, 10)
            val hasStrokes = hasIntentionalStrokeContent(
                pageStrokes.filter { stroke ->
                    stroke.bottom > rowBounds.topY && stroke.top < rowBounds.bottomY
                },
                rowBounds
            )

            if (!hasTypedText && !hasConvertedText && !hasRecognizedText && !hasMetadata && !hasStrokes) {
                target.add(EmptySlot(pageIndex, task.rowIndex, task.id, task))
            }
        }
    }

    fun findPlacementsForVoiceTasks(
        date: String,
        requiredCount: Int,
        canvasHeight: Float,
        startPageIndex: Int
    ): VoicePlacementResult {
        val maxPages = voicePlacementMaxPages
        val existingPages = (getMaxTaskPageIndex(date) + 1).coerceAtLeast(0)

        if (requiredCount <= 0) {
            return VoicePlacementResult(
                placements = emptyList(),
                createdPages = 0,
                scannedPages = 0,
                maxPages = maxPages,
                existingPages = existingPages,
                failure = VoicePlacementFailure.NO_TASKS_TO_CREATE
            )
        }

        if (maxPages <= 0) {
            return VoicePlacementResult(
                placements = emptyList(),
                createdPages = 0,
                scannedPages = 0,
                maxPages = maxPages,
                existingPages = existingPages,
                failure = VoicePlacementFailure.EDIT_BLOCKED
            )
        }

        val placements = mutableListOf<EmptySlot>()
        var createdPages = 0
        var scannedPages = 0
        val cappedExistingPages = minOf(existingPages, maxPages)

        if (cappedExistingPages > 0) {
            val lastExistingIndex = cappedExistingPages - 1
            val startIndex = startPageIndex.coerceIn(0, lastExistingIndex)

            for (pageIndex in startIndex..lastExistingIndex) {
                val (tasks, _) = getOrCreateTasksForPage(date, pageIndex)
                scannedPages++
                collectEmptySlotsForPage(tasks, pageIndex, canvasHeight, placements, requiredCount)
            }

            for (pageIndex in 0 until startIndex) {
                if (placements.size >= requiredCount) break
                val (tasks, _) = getOrCreateTasksForPage(date, pageIndex)
                scannedPages++
                collectEmptySlotsForPage(tasks, pageIndex, canvasHeight, placements, requiredCount)
            }
        }

        var nextPageIndex = cappedExistingPages
        try {
            while (placements.size < requiredCount && nextPageIndex < maxPages) {
                val (tasks, _) = getOrCreateTasksForPage(date, nextPageIndex)
                createdPages++
                scannedPages++
                collectEmptySlotsForPage(tasks, nextPageIndex, canvasHeight, placements, requiredCount)
                nextPageIndex++
            }
        } catch (e: Exception) {
            Log.e(TAG, "VOICE_PLACEMENT: Failed to create or scan pages", e)
            return VoicePlacementResult(
                placements = placements,
                createdPages = createdPages,
                scannedPages = scannedPages,
                maxPages = maxPages,
                existingPages = existingPages,
                failure = VoicePlacementFailure.DATABASE_ERROR
            )
        }

        val failure = when {
            placements.size >= requiredCount -> null
            cappedExistingPages >= maxPages -> VoicePlacementFailure.PAGE_LIMIT_REACHED
            else -> VoicePlacementFailure.ALL_SLOTS_OCCUPIED
        }

        return VoicePlacementResult(
            placements = placements,
            createdPages = createdPages,
            scannedPages = scannedPages,
            maxPages = maxPages,
            existingPages = existingPages,
            failure = failure
        )
    }

    /**
     * Find empty slots for a given date, scanning across multiple pages.
     * Used by Sort by Priority to find placement locations.
     *
     * A slot is considered "empty" if ALL of these are true:
     * - No typedText, convertedText, or recognizedText
     * - Not completed (isCompleted = false)
     * - No priority set
     * - Not a child task (parentTaskId = null)
     * - No intentional handwritten strokes
     *
     * @param date The date to scan (format: "yyyy-MM-dd")
     * @param requiredCount Number of empty slots needed
     * @param canvasHeight Canvas height for stroke detection
     * @param excludeTaskIds Task IDs to treat as "empty" (tasks being moved - their slots will be cleared)
     * @param maxPages Maximum pages to scan/create (default 10)
     * @return List of empty slots, may be less than requiredCount if insufficient space
     */
    fun findEmptySlotsForDate(
        date: String,
        requiredCount: Int,
        canvasHeight: Float,
        excludeTaskIds: Set<String> = emptySet(),
        maxPages: Int = 10
    ): List<EmptySlot> {
        val emptySlots = mutableListOf<EmptySlot>()
        var pageIndex = 0

        while (emptySlots.size < requiredCount && pageIndex < maxPages) {
            val (tasks, _) = getOrCreateTasksForPage(date, pageIndex)
            val pageId = tasks.firstOrNull()?.pageId

            // Load strokes for the page
            val pageStrokes = if (pageId != null) {
                PageDataManager.getStrokesInRectangle(
                    Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                    pageId
                ) ?: run {
                    val strokeRepository = StrokeRepository(context)
                    strokeRepository.getAllForPage(pageId)
                }
            } else {
                emptyList()
            }

            // Check each row on this page
            for (task in tasks.sortedBy { it.rowIndex }) {
                if (emptySlots.size >= requiredCount) break

                // If task is in excludeTaskIds, treat as empty (it will be cleared)
                if (task.id in excludeTaskIds) {
                    emptySlots.add(EmptySlot(pageIndex, task.rowIndex, task.id, task))
                    continue
                }

                // Check if slot has any content
                val hasTypedText = task.typedText?.isNotBlank() == true
                val hasConvertedText = task.convertedText?.isNotBlank() == true
                val hasRecognizedText = task.recognizedText?.isNotBlank() == true
                val hasMetadata = task.isCompleted || task.priority.isNotEmpty()

                // A child task is NOT empty - it belongs to a parent-child group
                val isChildTask = task.parentTaskId != null

                // Check for strokes in this row
                val rowBounds = calculateRowBounds(task.rowIndex, canvasHeight, 10)
                val hasStrokes = hasIntentionalStrokeContent(
                    pageStrokes.filter { stroke ->
                        stroke.bottom > rowBounds.topY && stroke.top < rowBounds.bottomY
                    },
                    rowBounds
                )

                // Slot is empty if no content AND not a child task
                if (!hasTypedText && !hasConvertedText && !hasRecognizedText && !hasMetadata && !hasStrokes && !isChildTask) {
                    emptySlots.add(EmptySlot(pageIndex, task.rowIndex, task.id, task))
                }
            }

            pageIndex++
        }

        Log.i(TAG, "SORT_SLOTS: Found ${emptySlots.size} empty slots for $date (requested $requiredCount, scanned $pageIndex pages)")
        return emptySlots
    }

    /**
     * Represents a task reorder operation (source -> target).
     */
    data class TaskReorderItem(
        val sourceTask: Task,
        val targetPageIndex: Int,
        val targetRowIndex: Int,
        val targetTaskId: String
    )

    /**
     * Reorder tasks with full stroke migration support.
     * Used by Smart Prioritization to physically move task content to new positions.
     *
     * ROBUST STRATEGY: Read-Wipe-Write
     * 1. Read all source content (strokes + metadata) into memory.
     * 2. Wipe ALL involved slots (both source and target) to prevent collisions/ghosting.
     * 3. Write new content to target slots with auto-assigned priorities.
     *
     * @param date The date being reordered
     * @param items List of reorder operations to perform
     * @param canvasHeight Canvas height for row bounds calculation
     * @param priorityAssignments Map of taskId to priority (A/B/C) for auto-assignment.
     *        If a task is in this map, its priority is set from the map.
     *        If not, the original priority is preserved.
     * @return true if successful, false if error occurred
     */
    fun reorderTasksWithStrokes(
        date: String,
        items: List<TaskReorderItem>,
        canvasHeight: Float,
        priorityAssignments: Map<String, String> = emptyMap()
    ): Boolean {
        // Full screen Tasks (Oct 2026): this works on pages of 10; the tools calling it leave full screen first
        if (TaskPagination.isReflowed(date)) {
            Log.w(TAG, "Not reordering $date: its Tasks are on full screen pages")
            return false
        }
        if (items.isEmpty()) return true

        // T5 (Oct 2026): one transaction; every content column carried; ink deleted by ID from
        // the DB even for pages that aren't in memory (no duplicates); reminders re-scheduled;
        // no per-slot canvas refresh (the caller emits one).
        return try {
            val strokeRepository = StrokeRepository(context)

            // Pre-fetch all Tasks involved to avoid repeated queries and ensure we have Page IDs
            // Sources are already in 'items', but we need Targets
            val affectedPageIndices = (items.map { it.sourceTask.taskPageIndex } + items.map { it.targetPageIndex }).distinct()
            for (pageIdx in affectedPageIndices) {
                getOrCreateTasksForPage(date, pageIdx)
            }

            data class MetadataOp(
                val targetTaskId: String,
                val source: Task,
                val priority: String
            )

            inTransaction {
                // Fresh rows: the page cache can miss text written by background recognition
                val tasksMap = mutableMapOf<String, Task>()
                for (pageIdx in affectedPageIndices) {
                    taskDao.getTasksForPage(date, pageIdx).forEach { tasksMap[it.id] = it }
                }

                // Strokes per page: in-memory list when the page is loaded, else the DB
                val pageStrokes = mutableMapOf<String, List<Stroke>>()
                fun strokesOnPage(pageId: String): List<Stroke> = pageStrokes.getOrPut(pageId) {
                    PageDataManager.getStrokesInRectangle(
                        Rect(0, 0, Int.MAX_VALUE, Int.MAX_VALUE),
                        pageId
                    ) ?: strokeRepository.getAllForPage(pageId)
                }
                fun strokesInRowOf(task: Task): List<Stroke> = extractStrokesForRow(
                    strokesOnPage(task.pageId),
                    calculateRowBounds(task.rowIndex, canvasHeight, 10),
                    canvasHeight
                )

                val migratedStrokesToInsert = mutableListOf<Stroke>()
                val metadataUpdates = mutableListOf<MetadataOp>()
                val slotsToWipeIds = mutableSetOf<String>()

                for (item in items) {
                    val sourceTask = tasksMap[item.sourceTask.id] ?: item.sourceTask
                    val targetTask = tasksMap[item.targetTaskId]

                    // Mark both Source and Target for wiping
                    // This ensures no collision if A moves to B (B is wiped)
                    // And ensures A is cleared if it moves away
                    slotsToWipeIds.add(sourceTask.id)
                    slotsToWipeIds.add(item.targetTaskId)

                    // 1. Prepare Stroke Migration
                    val strokesInRow = strokesInRowOf(sourceTask)
                    if (strokesInRow.isNotEmpty() && targetTask != null) {
                        migratedStrokesToInsert.addAll(
                            createMigratedStrokes(
                                sourceStrokes = strokesInRow,
                                sourceRowBounds = calculateRowBounds(sourceTask.rowIndex, canvasHeight, 10),
                                targetRowBounds = calculateRowBounds(item.targetRowIndex, canvasHeight, 10),
                                targetPageId = targetTask.pageId
                            )
                        )
                    }

                    // 2. Prepare Metadata Migration
                    // Use auto-assigned priority from Smart Prioritization if available,
                    // otherwise preserve the original priority
                    metadataUpdates.add(MetadataOp(
                        targetTaskId = item.targetTaskId,
                        source = sourceTask,
                        priority = priorityAssignments[sourceTask.id] ?: sourceTask.priority
                    ))
                }

                // 3. WIPE Phase: clear ink + metadata of every involved slot
                val strokeIdsToDeleteByPage = mutableMapOf<String, MutableList<String>>()
                for (taskId in slotsToWipeIds) {
                    val task = tasksMap[taskId] ?: continue
                    strokesInRowOf(task).forEach {
                        strokeIdsToDeleteByPage.getOrPut(task.pageId) { mutableListOf() }.add(it.id)
                    }
                    if (task.dueDay != null) {
                        afterCommit { cancelTaskNotification(task.id) }
                    }
                    if (taskDao.hasChildren(task.id)) {
                        taskDao.orphanChildren(task.id)
                    }
                    taskDao.clearTaskMetadata(task.id)
                }
                for ((pageId, strokeIds) in strokeIdsToDeleteByPage) {
                    strokeRepository.deleteByIdsOnPage(pageId, strokeIds)
                    afterCommit { PageDataManager.removeStrokes(strokeIds, pageId) }
                }

                // 4. WRITE Phase: Apply new state

                // Build map of SourceID -> TargetID to resolve parent relationships
                val sourceToTargetMap = items.associate { it.sourceTask.id to it.targetTaskId }

                // Write strokes
                if (migratedStrokesToInsert.isNotEmpty()) {
                    strokeRepository.create(migratedStrokesToInsert)
                    val strokesByPage = migratedStrokesToInsert.groupBy { it.pageId }
                    afterCommit {
                        for ((pageId, strokes) in strokesByPage) {
                            PageDataManager.addStrokes(strokes, pageId)
                        }
                    }
                }

                // Write metadata (every content column moves with the task)
                for (op in metadataUpdates) {
                    val source = op.source
                    // Sources and targets overlap here; the wipe phase already released them
                    transferTaskContent(
                        source, op.targetTaskId,
                        keepTemplateLink = true, releaseSource = false, priority = op.priority
                    )

                    // Resolve Parent-Child Relationship: link to wherever the parent moved;
                    // orphan it if the parent wasn't part of this batch (its old slot is wiped)
                    val newParentId = source.parentTaskId?.let { sourceToTargetMap[it] }
                    taskDao.setParentTask(op.targetTaskId, newParentId)
                }
            }

            // Final refresh
            invalidateTaskCache(date)
            _taskChanged.tryEmit(date)
            invalidateIntelligenceCache()

            val prioritiesAssigned = if (priorityAssignments.isNotEmpty()) {
                val aCount = priorityAssignments.count { it.value == "A" }
                val bCount = priorityAssignments.count { it.value == "B" }
                val cCount = priorityAssignments.count { it.value == "C" }
                " + auto-priorities (A:$aCount, B:$bCount, C:$cCount)"
            } else ""
            Log.i(TAG, "SORT_REORDER: Reordered ${items.size} tasks$prioritiesAssigned")
            true
        } catch (e: Exception) {
            Log.e(TAG, "SORT_REORDER failed: ${e.message}", e)
            invalidateTaskCache(date)
            false
        }
    }

    /**
     * Get tasks with content for export.
     * Returns tasks that have metadata OR intentional strokes.
     *
     * @param scope Export scope
     * @param dayDate Current day date
     * @param pageIndex Current task page index
     * @param canvasHeight Canvas height for stroke detection
     */
    fun getTasksWithContentForExport(
        scope: String,
        dayDate: String,
        pageIndex: Int,
        canvasHeight: Float
    ): List<Task> {
        val tasks = when (scope) {
            "CURRENT_PAGE" -> getTasksForPage(dayDate, pageIndex)
            "TODAY" -> taskDao.getTasksForDay(java.time.LocalDate.now().toString())
            "THIS_MONTH" -> {
                val monthPrefix = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                taskDao.getTasksForMonth(monthPrefix)
            }
            "ALL_TASKS" -> taskDao.getAllTasks()
            else -> emptyList()
        }
        return filterTasksWithContent(tasks, canvasHeight)
    }

    /**
     * Add converted tasks from memo to a target date.
     * Places tasks after existing tasks, creating new pages as needed.
     *
     * Used by "Convert Memo to Tasks" feature in Memos tab.
     *
     * @param tasks List of ConvertedTask with text and priority
     * @param targetDate Target date in "yyyy-MM-dd" format
     * @param canvasHeight Canvas height for stroke content detection
     * @param dueDate Optional due date (epoch ms) to set for all added tasks
     * @return TasksAddedResult with count of added tasks and pages used
     */
    suspend fun addConvertedTasksToDate(
        tasks: List<com.boox.day.ai.ConvertedTask>,
        defaultTargetDate: String,
        canvasHeight: Float,
        defaultDueDate: Long? = null
    ): com.boox.day.ai.TasksAddedResult {
        // Full screen Tasks (Oct 2026): this works on pages of 10; the tools calling it leave full screen first
        if (TaskPagination.isReflowed(defaultTargetDate)) {
            Log.w(TAG, "Not adding tasks to $defaultTargetDate: its Tasks are on full screen pages")
            return com.boox.day.ai.TasksAddedResult(tasksAdded = 0, pagesUsed = 0, targetDates = emptySet())
        }
        val selectedTasks = tasks.filter { it.isSelected }
        if (selectedTasks.isEmpty()) {
            return com.boox.day.ai.TasksAddedResult(0, 0, emptySet(), emptyMap())
        }

        // Group tasks by their effective target date (override or default)
        val tasksByDate = selectedTasks.groupBy { task ->
            task.targetDateOverride ?: defaultTargetDate
        }

        Log.i(TAG, "MEMO_TO_TASKS: Adding ${selectedTasks.size} tasks across ${tasksByDate.size} date(s)")

        var totalTasksAdded = 0
        val pagesUsed = mutableSetOf<Int>()
        val affectedDates = mutableSetOf<String>()
        val latestPageIndexByDate = mutableMapOf<String, Int>()

        // Process each date group using centralized SlotAllocator
        // This ensures we find ALL empty slots correctly, not just start from first and increment
        // FIX: The old code would fail if there were gaps (e.g., slots 0,1 empty, 2 occupied, 3-9 empty)
        for ((targetDate, tasksForDate) in tasksByDate) {
            Log.d(TAG, "MEMO_TO_TASKS: Adding ${tasksForDate.size} tasks to $targetDate")
            affectedDates.add(targetDate)

            // Use SlotAllocator to find ALL empty slots (handles gaps correctly)
            val slots = com.boox.day.editor.utils.SlotAllocator.allocateMultiple(
                date = targetDate,
                count = tasksForDate.size,
                canvasHeight = canvasHeight,
                taskRepository = this,
                startPageIndex = 0
            )

            // Place tasks in allocated slots
            for ((index, task) in tasksForDate.withIndex()) {
                val slot = slots.getOrNull(index) ?: break  // No more slots available

                // Update task with AI-converted text and priority (dual-write)
                taskDao.setTypedText(slot.task.id, task.text)
                taskDao.setConvertedText(slot.task.id, task.text)
                TaskTextIndexer.getInstanceOrNull()?.onTypedTextChanged(
                    taskId = slot.task.id,
                    dayDate = targetDate,
                    taskPageIndex = slot.pageIndex,
                    pageId = slot.task.pageId
                )
                if (task.suggestedPriority.isNotBlank()) {
                    taskDao.setPriority(slot.task.id, task.suggestedPriority)
                }
                // Set due date: use task's override, or default if no override
                val effectiveDueDay = TaskDueDates.fromMillis(task.dueDateOverride ?: defaultDueDate)
                if (effectiveDueDay != null) {
                    taskDao.setDueDay(slot.task.id, effectiveDueDay)
                    // T12: every due-date write schedules its reminder
                    taskDao.getById(slot.task.id)?.let { scheduleTaskNotificationIfEnabled(it) }
                }
                totalTasksAdded++
                pagesUsed.add(slot.pageIndex)
                latestPageIndexByDate[targetDate] = maxOf(
                    latestPageIndexByDate[targetDate] ?: slot.pageIndex,
                    slot.pageIndex
                )
                Log.d(TAG, "MEMO_TO_TASKS: Added task at page=${slot.pageIndex} row=${slot.rowIndex}: ${task.text.take(30)}...")
            }

            // Refresh signals for this date
            invalidateTaskCache(targetDate)
            _taskChanged.tryEmit(targetDate)
        }

        invalidateIntelligenceCache()

        Log.i(TAG, "MEMO_TO_TASKS: Completed - added $totalTasksAdded tasks across ${pagesUsed.size} pages on ${affectedDates.size} date(s)")
        return com.boox.day.ai.TasksAddedResult(
            tasksAdded = totalTasksAdded,
            pagesUsed = pagesUsed.size,
            targetDates = affectedDates,
            latestPageIndexByDate = latestPageIndexByDate
        )
    }

    /**
     * Find the first empty row index in a list of tasks.
     * Checks all text fields (typedText, convertedText, recognizedText),
     * metadata (isCompleted, priority), and handwritten strokes.
     *
     * CRITICAL FIX: Uses isPageLoaded() to determine if DB fallback is needed,
     * because getStrokes() returns emptyList() for unloaded pages (not null).
     *
     * @return Row index 0-9 if empty slot found, or 10 if all rows occupied
     */
    fun findFirstEmptyRow(
        tasks: List<Task>,
        canvasHeight: Float
    ): Int {
        val pageId = tasks.firstOrNull()?.pageId ?: return 0

        // CRITICAL FIX: Check if page is actually in cache before trusting getStrokes()
        // PageDataManager.getStrokes() returns emptyList() for unloaded pages, not null!
        val strokes = if (PageDataManager.isPageLoaded(pageId)) {
            Log.d(TAG, "MEMO_TO_TASKS: Cache HIT for page $pageId")
            PageDataManager.getStrokes(pageId)
        } else {
            // Cache miss - load from DB to ensure accurate detection
            Log.d(TAG, "MEMO_TO_TASKS: Cache MISS for page $pageId, loading from DB")
            StrokeRepository(context).getAllForPage(pageId)
        }

        for (i in 0..9) {
            val task = tasks.getOrNull(i) ?: return i

            // Check all text fields
            val hasTypedText = !task.typedText.isNullOrBlank()
            val hasConvertedText = !task.convertedText.isNullOrBlank()
            val hasRecognizedText = !task.recognizedText.isNullOrBlank()

            // Check metadata
            val hasMetadata = task.isCompleted || task.priority.isNotBlank()

            // Check parent-child relationship (child tasks are NOT empty)
            val isChildTask = task.parentTaskId != null

            if (hasTypedText || hasConvertedText || hasRecognizedText || hasMetadata || isChildTask) {
                Log.d(TAG, "SLOT_CHECK: row=$i OCCUPIED (text/metadata/child)")
                continue
            }

            // Check strokes
            val rowBounds = calculateRowBounds(i, canvasHeight, 10)
            if (hasIntentionalStrokeContent(strokes, rowBounds)) {
                Log.d(TAG, "SLOT_CHECK: row=$i OCCUPIED (strokes)")
                continue
            }

            Log.d(TAG, "SLOT_CHECK: row=$i EMPTY")
            return i  // Empty row found
        }

        Log.d(TAG, "SLOT_CHECK: All 10 rows occupied")
        return 10  // All rows have content
    }

    // ========== AGENTIC PLANNING SYSTEM QUERIES (Phase 16) ==========

    /**
     * Get zombie tasks (incomplete tasks migrated 3+ times).
     * Used by the Agentic Planning System to flag deferred tasks.
     */
    fun getZombieTasks(minMigrations: Int = 3): List<Task> {
        return taskDao.getZombieTasks(minMigrations)
    }

    /**
     * Get overdue incomplete tasks (before today, not completed).
     * Used for daily planning to surface tasks needing attention.
     */
    fun getOverdueTasks(today: String): List<Task> {
        return taskDao.getOverdueTasks(today)
    }

    /**
     * Get all dates that have completed tasks, ordered desc.
     * Used for streak calculation.
     */
    fun getDatesWithCompletedTasks(): List<String> {
        return taskDao.getDatesWithCompletedTasks()
    }

    /**
     * Get tasks for a date range (inclusive).
     * Used for weekly statistics and pattern analysis.
     */
    fun getTasksForDateRange(startDate: String, endDate: String): List<Task> {
        return taskDao.getTasksForDateRange(startDate, endDate)
    }

    /**
     * Alias for getMaxTaskPageIndex for consistent naming.
     * Returns -1 if no tasks exist for the date.
     */
    fun getMaxPageIndexForDate(date: String): Int {
        return getMaxTaskPageIndex(date)
    }

    /**
     * Get total estimated minutes for incomplete tasks on a date.
     * Used for capacity analysis in agentic planning.
     */
    fun getTotalEstimatedMinutesForDate(date: String): Int {
        return taskDao.getTotalEstimatedMinutesForDate(date)
    }

    /**
     * Update migration tracking when a task is moved to a different day.
     * Increments migrationCount and sets originalDate if first migration.
     */
    fun recordTaskMigration(taskId: String, originalDate: String) {
        taskDao.incrementMigrationCount(taskId, originalDate)
        Log.d(TAG, "MIGRATION: Recorded migration for task $taskId (original: $originalDate)")
    }

    /**
     * Clear rollover tracking for a task (e.g., when completed).
     */
    fun clearTaskRolloverTracking(taskId: String) {
        taskDao.clearRolloverTracking(taskId)
    }

    /**
     * Set estimated minutes for a task (AI-assigned duration).
     */
    fun setTaskEstimatedMinutes(taskId: String, minutes: Int?) {
        taskDao.setEstimatedMinutes(taskId, minutes)
    }

    // ========== DUE DATE QUERIES (Events Panel "Due" Tab) ==========

    /**
     * Get tasks due on a specific date as a Flow.
     * @param dateString Format: "yyyy-MM-dd"
     */
    fun getTasksDueOnDateFlow(dateString: String): kotlinx.coroutines.flow.Flow<List<Task>> =
        taskDao.getTasksDueOnDateFlow(dateString)

    /**
     * Get overdue tasks (due before today) as a Flow.
     * @param todayString Format: "yyyy-MM-dd"
     */
    fun getOverdueTasksFlow(todayString: String): kotlinx.coroutines.flow.Flow<List<Task>> =
        taskDao.getOverdueTasksFlow(todayString)

    /**
     * Get count of tasks due on a specific date as a Flow.
     * @param dateString Format: "yyyy-MM-dd"
     */
    fun getTasksDueOnDateCount(dateString: String): kotlinx.coroutines.flow.Flow<Int> =
        taskDao.getTasksDueOnDateCount(dateString)

    /**
     * Get count of overdue tasks as a Flow.
     * @param todayString Format: "yyyy-MM-dd"
     */
    fun getOverdueTasksCount(todayString: String): kotlinx.coroutines.flow.Flow<Int> =
        taskDao.getOverdueTasksCount(todayString)
}
