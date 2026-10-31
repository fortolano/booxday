package com.boox.day.tasksync

import android.content.Context
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.DueDateResult
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskRepository
import com.boox.day.tasks.TaskPagination

/**
 * The tablet side of a merge (task sync, Oct 2026). Plan:
 * C:\Users\fort\.claude\plans\reactive-wiggling-micali.md, step 2.3. A change the task service
 * brings is made through BooxDay's own task operations, so reminders, subtasks and ink behave as
 * when the user makes it. What can't be done now (a move onto a full-screen day, no row size yet)
 * says so, and the merge tries again at the next sync.
 */
internal interface TabletTasks {
    /** Every write of one merge in one transaction; screen and reminder updates follow the commit. */
    fun <T> inTransaction(block: () -> T): T

    /** New words for a typed or converted task (handwriting keeps its ink: owner decision 2). */
    fun setWords(task: Task, words: String)

    /** Ticks or unticks it, with BooxDay's subtask rules (a parent ticks its subtasks, and back). */
    fun setCompleted(taskId: String, completed: Boolean)

    /** Its own due day; false when BooxDay refuses it (a subtask due after its parent). */
    fun setDueDay(taskId: String, dueDay: String?): Boolean

    /** Moves it, with its subtasks and ink, to [day] (its link goes along); false: not now. */
    fun moveToDay(task: Task, day: String): Boolean

    /** Clears its row: words, ink, reminder (its link must already be off); false: not now. */
    fun clear(task: Task): Boolean

    /**
     * A new typed task on [day] (its due day is the day it sits on); [link] writes its link in the
     * same transaction, so the change watcher never sends it back as new. False: not now.
     */
    suspend fun add(day: String, words: String, completed: Boolean, link: (Task) -> Unit): Boolean
}

/** [TabletTasks] through TaskRepository. Placing new tasks comes with plan step 2.4. */
internal class RepositoryTabletTasks(
    private val context: Context,
    private val repo: TaskRepository = TaskRepository(context),
    /** The normal view's Tasks canvas height, 0 when not measured yet (rows can't be found then). */
    private val canvasHeight: () -> Float = { GlobalAppSettings.tasksCanvasHeight }
) : TabletTasks {

    override fun <T> inTransaction(block: () -> T): T = repo.inSyncTransaction(block)

    override fun setWords(task: Task, words: String) {
        if (task.typedText.isNullOrBlank() && !task.convertedText.isNullOrBlank()) {
            // A task from Convert Memo to Tasks keeps its words where they are shown
            AppDatabase.getDatabase(context).taskDao().setConvertedText(task.id, words)
            repo.invalidateTaskCache(task.dayDate)
        } else {
            repo.setTypedText(task.id, words)
        }
    }

    override fun setCompleted(taskId: String, completed: Boolean) = repo.setCompleted(taskId, completed)

    override fun setDueDay(taskId: String, dueDay: String?): Boolean =
        repo.setDueDay(taskId, dueDay, canvasHeight()) is DueDateResult.Success

    override fun moveToDay(task: Task, day: String): Boolean {
        val height = canvasHeight()
        // Moves work on pages of 10: not while a day is on full screen pages (TaskPagination)
        if (height <= 0f || TaskPagination.isReflowed(task.dayDate) || TaskPagination.isReflowed(day)) return false
        return repo.moveTaskWithStrokes(task, day, 0, height, context).success
    }

    override fun clear(task: Task): Boolean {
        val height = canvasHeight()
        if (height <= 0f) return false
        repo.clearTaskSlot(task, height)
        return true
    }

    override suspend fun add(day: String, words: String, completed: Boolean, link: (Task) -> Unit): Boolean =
        false  // plan step 2.4 (TaskRepository.addSyncedTask): until then a new task waits
}
