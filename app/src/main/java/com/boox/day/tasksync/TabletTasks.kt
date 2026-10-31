package com.boox.day.tasksync

import android.content.Context
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.DueDateResult
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskRepository
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.utils.SlotAllocator
import com.boox.day.sync.workerAppSettings
import com.boox.day.tasks.TaskPagination

/**
 * The tablet side of a merge (task sync, Oct 2026). Plan:
 * C:\Users\fort\.claude\plans\reactive-wiggling-micali.md, steps 2.3-2.4. A change the task service
 * brings is made through BooxDay's own task operations, so reminders, subtasks and ink behave as
 * when the user makes it. What can't be done now says so, and the merge tries again at the next
 * sync: rows of unknown size (Tasks never shown), a day on full screen pages, the page being
 * written on, a day with all 10 pages full.
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
     * A new typed task, unticked and with no due day of its own (owner decisions 27, 28), on
     * [day]'s first free row; [link] writes its link in the same transaction, so the change watcher
     * never sends it back as new. False: not now.
     */
    suspend fun add(day: String, words: String, link: (Task) -> Unit): Boolean
}

/**
 * The Tasks rows' size for sync's writes. With Tasks on screen, what MainScreen measured. With
 * BooxDay closed (a background run), the size the stored ink is laid out for, which TasksInkLayout
 * saved; it also becomes the session's top offset, where BooxDay's row code reads it
 * (tasksTopOffset). 0 when Tasks has never been shown: then rows can't be found and writes wait.
 */
internal fun syncCanvasHeight(context: Context): Float {
    GlobalAppSettings.tasksCanvasHeight.takeIf { it > 0f }?.let { return it }
    val saved = workerAppSettings(context)
    if (saved.tasksInkCanvasHeight <= saved.tasksInkTopOffset + 10f) return 0f
    if (GlobalAppSettings.tasksTopOffsetPx <= 0f) GlobalAppSettings.setTasksTopOffsetPx(saved.tasksInkTopOffset)
    return saved.tasksInkCanvasHeight
}

/** [TabletTasks] through TaskRepository. */
internal class RepositoryTabletTasks(
    private val context: Context,
    private val repo: TaskRepository = TaskRepository(context),
    /** The normal view's Tasks canvas height, 0 when not known (rows can't be found then). */
    private val canvasHeight: () -> Float = { syncCanvasHeight(context) },
    /** The pen is writing, or was a moment ago, on one of these pages. */
    private val penOn: (Collection<String>) -> Boolean = { pages ->
        DrawCanvas.isStylusActiveWithin(PEN_QUIET_MS) && DrawCanvas.activeCanvasPageId.value in pages
    }
) : TabletTasks {

    private val tasks = AppDatabase.getDatabase(context).taskDao()

    /** Days written to by the transaction running now; MainScreen hears of them after the commit. */
    private val changedDays = ThreadLocal<MutableSet<String>?>()

    init {
        // With BooxDay closed nothing has loaded the settings: reminders follow them (TaskRepository)
        if (!GlobalAppSettings.loaded) GlobalAppSettings.update(workerAppSettings(context))
    }

    override fun <T> inTransaction(block: () -> T): T {
        val days = mutableSetOf<String>()
        changedDays.set(days)
        val result = try {
            repo.inSyncTransaction(block)
        } finally {
            changedDays.remove()
        }
        days.forEach(TaskRepository::notifyDayChangedBySync)
        return result
    }

    override fun setWords(task: Task, words: String) {
        if (task.typedText.isNullOrBlank() && !task.convertedText.isNullOrBlank()) {
            // A task from Convert Memo to Tasks keeps its words where they are shown
            tasks.setConvertedText(task.id, words)
            repo.invalidateTaskCache(task.dayDate)
        } else {
            repo.setTypedText(task.id, words)
        }
        changed(task.dayDate)
    }

    override fun setCompleted(taskId: String, completed: Boolean) {
        repo.setCompleted(taskId, completed)
        tasks.getDayDateById(taskId)?.let(::changed)
    }

    override fun setDueDay(taskId: String, dueDay: String?): Boolean {
        val done = repo.setDueDay(taskId, dueDay, canvasHeight()) is DueDateResult.Success
        tasks.getDayDateById(taskId)?.let(::changed)
        return done
    }

    override fun moveToDay(task: Task, day: String): Boolean {
        val height = canvasHeight()
        // Moves work on pages of 10: not while a day is on full screen pages (TaskPagination)
        if (height <= 0f || TaskPagination.isReflowed(task.dayDate) || TaskPagination.isReflowed(day)) return false
        if (penOn(listOf(task.pageId) + tasks.getTasksForDay(day).map { it.pageId })) return false
        if (!repo.moveTaskWithStrokes(task, day, 0, height, context).success) return false
        changed(task.dayDate)
        changed(day)
        return true
    }

    override fun clear(task: Task): Boolean {
        val height = canvasHeight()
        if (height <= 0f || penOn(listOf(task.pageId))) return false
        repo.clearTaskSlot(task, height)
        changed(task.dayDate)
        return true
    }

    override suspend fun add(day: String, words: String, link: (Task) -> Unit): Boolean {
        val height = canvasHeight()
        if (height <= 0f || TaskPagination.isReflowed(day)) return false
        val slot = SlotAllocator.allocateSingle(day, height, repo)
        if (slot == null) {
            TaskSync.setDayFull(context, day, true)  // all 10 pages full: the card says so
            return false
        }
        // Never onto the page being written on; nor a row taken since it was found
        if (penOn(listOf(slot.task.pageId)) || !SlotAllocator.validateBeforeWrite(slot.task, height, repo)) return false
        if (!repo.writeSyncedTask(slot.task.id, words, link)) return false
        TaskSync.setDayFull(context, day, false)
        TaskRepository.notifyDayChangedBySync(day)
        return true
    }

    private fun changed(day: String) {
        changedDays.get()?.add(day) ?: TaskRepository.notifyDayChangedBySync(day)
    }

    private companion object {
        /** Like the change watcher's wait while the pen is active (TaskSyncWatcher). */
        const val PEN_QUIET_MS = 5_000L
    }
}
