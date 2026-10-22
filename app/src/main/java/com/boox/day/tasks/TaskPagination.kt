package com.boox.day.tasks

import android.content.Context
import android.util.Log
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.PageDataManager
import com.boox.day.data.PagePersistenceWriter
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Kv
import com.boox.day.data.db.KvProxy
import com.boox.day.data.db.Page
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskRepository
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.UNDECODABLE_STROKE_POINTS
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.data.ensurePreviewsFullFolder
import com.boox.day.editor.utils.getDominantRowIndex
import com.boox.day.ocr.TaskTextIndexer
import com.boox.day.ocr.model.OcrSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Date

/**
 * Tasks in full screen (Oct 2026): rows keep the normal view's height and more of them fit, so a day's
 * tasks go on pages of ~17 rows instead of 10. A day's tasks are one ordered list of slots
 * (page × rows per page + row); [reflowDay] puts every slot — the task row, its ink and any text box
 * pasted on it — at the same place in that list on pages of the other size. Blank rows and order are kept; only the page breaks
 * move. Task IDs stay (reminders, subtasks, the tasks feed) and so do stroke IDs; ink moves by whole
 * rows, never scaled. The day's drawing pages are used again in the same order, so full screen and
 * back gives exactly the pages it started with.
 *
 * At rest every day has pages of 10 rows. While full screen shows a day's Tasks, that one day is
 * reflowed; a record in the KV table, written in the same transaction, lets the next start put it back
 * if the app is killed meanwhile ([restoreAtStartup]). Reflows run on [PagePersistenceWriter], after
 * every pen write queued before them.
 *
 * Code that assumes 10 rows refuses a reflowed day ([isReflowed]); the tools using it leave full screen
 * first (ui/FullScreenState.exitThen).
 */
object TaskPagination {
    private const val TAG = "TaskPagination"
    const val NORMAL_ROWS = 10

    /** More than this would come from a wrong measurement (full screen is ~17 rows on both tablets). */
    private const val MAX_ROWS = 40
    private const val KV_KEY = "tasks_full_screen_day"
    private const val PREFS = "tasks_pagination"
    private const val PREF_PENDING = "reflow_pending"

    /** The reflowed day. [pageIds]: its drawing pages in order before full screen (used again in this order). */
    @Serializable
    data class Reflowed(
        val date: String,
        val rows: Int,
        val rowHeight: Float,
        val topOffset: Float,
        val pageIds: List<String>
    )

    /** Result of one reflow, for the in-memory pages and caches after the commit. */
    internal class Outcome(
        val pageIds: List<String>,
        val deletedPageIds: List<String>,
        val removedStrokeIds: Map<String, List<String>>,
        val addedStrokes: Map<String, List<Stroke>>,
        /** Text boxes (pasted on Tasks rows) before and after their move. */
        val movedTextBoxes: List<Pair<TextAnnotation, TextAnnotation>> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var appContext: Context

    /** The day on pages of more than 10 rows, or null (one day at a time). */
    @Volatile
    var current: Reflowed? = null
        private set

    /** The day being reflowed right now: its pages may be either size until [current] says. */
    @Volatile
    private var changingDate: String? = null

    // Tasks rows on screen now, set by the canvas host for the drawing code (completed-row grey,
    // eraser block): 0 height = the view's height, i.e. 10 rows filling the canvas (normal view).
    @Volatile
    var screenRowCount = NORMAL_ROWS
    @Volatile
    var screenRowsHeightPx = 0f

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isReflowed(date: String): Boolean = current?.date == date || changingDate == date

    fun rowsPerPage(date: String): Int = current?.takeIf { it.date == date }?.rows ?: NORMAL_ROWS

    /** Height of the Tasks rows on screen, for a view of [viewHeight] px. */
    fun screenRowsHeight(viewHeight: Float): Float = if (screenRowsHeightPx > 0f) screenRowsHeightPx else viewHeight

    /** Rows of the normal view's row height that fit in a full screen canvas of [fullScreenHeight] px. */
    fun fullScreenRows(fullScreenHeight: Float, normalHeight: Float, topOffset: Float): Int {
        val rowHeight = (normalHeight - topOffset) / NORMAL_ROWS
        if (rowHeight <= 0f) return NORMAL_ROWS
        return ((fullScreenHeight - topOffset) / rowHeight).toInt().coerceIn(NORMAL_ROWS, MAX_ROWS)
    }

    /**
     * The height [rows] rows take with the normal view's row height ([normalHeight] holds 10), the
     * canvas height to pair with [rows] in calculateRowBounds / getDominantRowIndex.
     */
    fun rowsHeight(normalHeight: Float, rows: Int, topOffset: Float): Float =
        if (rows == NORMAL_ROWS) normalHeight else topOffset + rows * (normalHeight - topOffset) / NORMAL_ROWS

    /** Full screen shows [date]'s Tasks: pages of [rows] rows. False when nothing changed. */
    suspend fun expand(date: String, rows: Int, normalHeight: Float, topOffset: Float): Boolean {
        current?.let { return it.date == date && it.rows == rows }
        if (rows <= NORMAL_ROWS || rows > MAX_ROWS) return false
        val rowHeight = (normalHeight - topOffset) / NORMAL_ROWS
        if (rowHeight <= 0f) return false
        // Before the reflow: if the app dies right after it, the next start knows to look
        prefs().edit().putBoolean(PREF_PENDING, true).commit()
        changingDate = date
        val result = onWriteQueue("expand $date") { db ->
            val pageIds = db.taskDao().getTasksForDay(date).map { it.pageId }.distinct()
            val outcome = reflowDay(db, date, NORMAL_ROWS, rows, rowHeight, topOffset, pageIds, minPages = 1, deleteUnused = false)
                ?: return@onWriteQueue null
            val record = Reflowed(date, rows, rowHeight, topOffset, pageIds)
            db.kvDao().set(Kv(KV_KEY, json.encodeToString(Reflowed.serializer(), record)))
            record to outcome
        }
        if (result == null) {
            changingDate = null
            prefs().edit().remove(PREF_PENDING).apply()
            return false
        }
        current = result.first
        changingDate = null
        Log.i(TAG, "Tasks of $date on pages of $rows rows")
        afterReflow(date, result.second)
        return true
    }

    /** Back to pages of 10 rows (normal view). True when no day is reflowed any more. */
    suspend fun collapse(): Boolean {
        val record = current ?: return true
        changingDate = record.date
        val outcome = onWriteQueue("collapse ${record.date}") { db ->
            val outcome = reflowDay(
                db, record.date, record.rows, NORMAL_ROWS, record.rowHeight, record.topOffset,
                preferredPageIds = record.pageIds, minPages = record.pageIds.size, deleteUnused = true
            ) ?: return@onWriteQueue null
            db.kvDao().delete(KV_KEY)
            outcome
        }
        if (outcome == null) {
            changingDate = null
            return false
        }
        current = null
        changingDate = null
        prefs().edit().remove(PREF_PENDING).apply()
        Log.i(TAG, "Tasks of ${record.date} back on pages of 10 rows")
        afterReflow(record.date, outcome)
        // Text typed in full screen was indexed with the full screen pages: index the day's pages again
        TaskTextIndexer.getInstanceOrNull()?.let { indexer ->
            outcome.pageIds.forEachIndexed { index, pageId -> indexer.indexNow(record.date, index, pageId) }
        }
        return true
    }

    /**
     * App start (BooxDayApp, before any screen): a day left on full screen pages because the app was
     * killed in full screen goes back to pages of 10 rows. Blocks only when that happened.
     */
    fun restoreAtStartup() {
        if (!prefs().getBoolean(PREF_PENDING, false)) return
        runBlocking(Dispatchers.IO) {
            val record = AppDatabase.getDatabase(appContext).kvDao().get(KV_KEY)?.let {
                runCatching { json.decodeFromString(Reflowed.serializer(), it.value) }.getOrNull()
            }
            if (record == null) {
                prefs().edit().remove(PREF_PENDING).apply()
                return@runBlocking
            }
            current = record
            val restored = collapse()
            Log.i(TAG, "Start: Tasks of ${record.date} back on pages of 10 rows: $restored")
        }
    }

    /**
     * Safety net (background, at start): a day with pages of more than 10 rows and no record — e.g. from
     * a backup made while full screen showed it — goes back to pages of 10, with the stored ink geometry.
     */
    suspend fun normalizeStrayDays() {
        val db = AppDatabase.getDatabase(appContext)
        val days = db.taskDao().getDaysWithRowsFrom(NORMAL_ROWS).filter { !isReflowed(it) }
        if (days.isEmpty()) return
        val settings = KvProxy(appContext).get(APP_SETTINGS_KEY, AppSettings.serializer())
        val inkHeight = settings?.tasksInkCanvasHeight ?: 0f
        val inkTop = settings?.tasksInkTopOffset ?: 0f
        if (inkHeight <= inkTop) {
            Log.w(TAG, "Days with long Tasks pages ($days) but no ink geometry yet; left as they are")
            return
        }
        val rowHeight = (inkHeight - inkTop) / NORMAL_ROWS
        for (day in days) {
            val rows = (db.taskDao().getMaxRowIndex(day) ?: continue) + 1
            val pageIds = db.taskDao().getTasksForDay(day).map { it.pageId }.distinct()
            val outcome = onWriteQueue("normalize $day") {
                // Full screen may have started on this day since the look above
                if (isReflowed(day)) null
                else reflowDay(it, day, rows, NORMAL_ROWS, rowHeight, inkTop, pageIds, minPages = 1, deleteUnused = true)
            } ?: continue
            Log.i(TAG, "Tasks of $day ($rows rows per page, no record) back on pages of 10 rows")
            afterReflow(day, outcome)
        }
    }

    /**
     * Moves every slot of [date] from pages of [fromRows] rows to pages of [toRows] rows. Pages:
     * [preferredPageIds] first (those that still exist), then the day's other pages, then new ones.
     * Pages no longer needed are kept for later ([deleteUnused] false: full screen) or deleted when empty.
     * Trailing empty slots are dropped, keeping at least [minPages] pages. Null, with nothing written,
     * when the day's rows don't fit [fromRows]. Runs inside the caller's transaction.
     */
    internal fun reflowDay(
        db: AppDatabase,
        date: String,
        fromRows: Int,
        toRows: Int,
        rowHeight: Float,
        topOffset: Float,
        preferredPageIds: List<String>,
        minPages: Int,
        deleteUnused: Boolean
    ): Outcome? {
        val taskDao = db.taskDao()
        val strokeDao = db.strokeDao()
        val pageDao = db.pageDao()
        val tasks = taskDao.getTasksForDay(date)
        if (tasks.isEmpty()) return Outcome(emptyList(), emptyList(), emptyMap(), emptyMap())

        // Each page index is one drawing page; rows must fit the page size we start from
        val pageOfIndex = sortedMapOf<Int, String>()
        for (task in tasks) {
            if (task.taskPageIndex < 0 || task.rowIndex !in 0 until fromRows) {
                Log.w(TAG, "Reflow of $date refused: row ${task.taskPageIndex}/${task.rowIndex} on pages of $fromRows")
                return null
            }
            val known = pageOfIndex.putIfAbsent(task.taskPageIndex, task.pageId)
            if (known != null && known != task.pageId) {
                Log.w(TAG, "Reflow of $date refused: page ${task.taskPageIndex} has two drawing pages")
                return null
            }
        }

        // Ink by slot (its dominant row); undecodable strokes can't be rewritten and stay where they are
        val fromHeight = topOffset + fromRows * rowHeight
        val inkOfSlot = HashMap<Int, MutableList<Stroke>>()
        for ((pageIndex, pageId) in pageOfIndex) {
            for (stroke in strokeDao.getAllForPage(pageId)) {
                if (stroke.points === UNDECODABLE_STROKE_POINTS) continue
                val row = getDominantRowIndex(stroke, fromHeight, fromRows, topOffset)
                inkOfSlot.getOrPut(pageIndex * fromRows + row) { mutableListOf() }.add(stroke)
            }
        }
        // Text boxes pasted on rows: by the row their middle is in
        val textDao = db.textAnnotationDao()
        val textOfSlot = HashMap<Int, MutableList<TextAnnotation>>()
        for ((pageIndex, pageId) in pageOfIndex) {
            for (box in textDao.getForPageNow(pageId)) {
                val row = ((box.y + box.height / 2f - topOffset) / rowHeight).toInt().coerceIn(0, fromRows - 1)
                textOfSlot.getOrPut(pageIndex * fromRows + row) { mutableListOf() }.add(box)
            }
        }
        val taskOfSlot = tasks.associateBy { it.taskPageIndex * fromRows + it.rowIndex }

        // Slots up to the last one in use; trailing empty ones are dropped
        val parentIds = tasks.mapNotNullTo(HashSet()) { it.parentTaskId }
        val lastUsed = maxOf(
            taskOfSlot.filterValues { it.holdsContent(parentIds) }.keys.maxOrNull() ?: -1,
            inkOfSlot.keys.maxOrNull() ?: -1,
            textOfSlot.keys.maxOrNull() ?: -1
        )
        val pageCount = maxOf(minPages, (lastUsed + toRows) / toRows, 1)
        val slotCount = pageCount * toRows

        // Drawing pages: the preferred order, the day's other pages, then new ones
        val existing = (preferredPageIds + pageOfIndex.values).distinct().filter { pageDao.getById(it) != null }
        val targetPageIds = (0 until pageCount).map { index ->
            existing.getOrNull(index) ?: Page().also { pageDao.create(it) }.id
        }
        val unusedPageIds = existing.drop(pageCount)

        // Task rows: drop empty slots past the end, move the others aside (unique day/page/row), then
        // into place; fill slots that had no row
        taskOfSlot.filterKeys { it >= slotCount }.values.forEach { taskDao.delete(it) }
        val moving = taskOfSlot.filter { (slot, task) ->
            slot < slotCount && (task.taskPageIndex != slot / toRows || task.rowIndex != slot % toRows ||
                task.pageId != targetPageIds[slot / toRows])
        }
        moving.values.forEachIndexed { index, task -> taskDao.moveTaskToSlot(task.id, -1 - index, task.rowIndex, task.pageId) }
        moving.forEach { (slot, task) -> taskDao.moveTaskToSlot(task.id, slot / toRows, slot % toRows, targetPageIds[slot / toRows]) }
        val newRows = (0 until slotCount).filter { it !in taskOfSlot }.map { slot ->
            Task(dayDate = date, taskPageIndex = slot / toRows, rowIndex = slot % toRows, pageId = targetPageIds[slot / toRows])
        }
        if (newRows.isNotEmpty()) taskDao.insertAll(newRows)

        // Ink follows its slot: same stroke, moved by whole rows, maybe onto another page
        val removed = HashMap<String, MutableList<String>>()
        val added = HashMap<String, MutableList<Stroke>>()
        val now = Date()
        for ((slot, strokes) in inkOfSlot) {
            val dy = rowShift(slot % toRows - slot % fromRows, rowHeight)
            val pageId = targetPageIds[slot / toRows]
            for (stroke in strokes) {
                if (dy == 0f && stroke.pageId == pageId) continue
                val moved = stroke.copy(
                    points = stroke.points.map { it.copy(y = storedPointY(it.y + dy)) },
                    top = stroke.top + dy,
                    bottom = stroke.bottom + dy,
                    pageId = pageId,
                    updatedAt = now
                )
                strokeDao.update(moved)
                removed.getOrPut(stroke.pageId) { mutableListOf() }.add(stroke.id)
                added.getOrPut(pageId) { mutableListOf() }.add(moved)
            }
        }
        val movedTextBoxes = mutableListOf<Pair<TextAnnotation, TextAnnotation>>()
        for ((slot, boxes) in textOfSlot) {
            val dy = rowShift(slot % toRows - slot % fromRows, rowHeight)
            val pageId = targetPageIds[slot / toRows]
            for (box in boxes) {
                if (dy == 0f && box.pageId == pageId) continue
                textDao.moveTo(box.id, pageId, box.y + dy)
                movedTextBoxes += box to box.copy(pageId = pageId, y = box.y + dy)
            }
        }

        // Pages no longer used: kept for the way back (full screen), or deleted when nothing is left on them
        val deleted = if (deleteUnused) {
            unusedPageIds.filter { pageId ->
                val empty = taskDao.getTasksForPageId(pageId).isEmpty() && !strokeDao.hasStrokesForPage(pageId) &&
                    textDao.getForPageNow(pageId).isEmpty()
                if (empty) pageDao.delete(pageId) else Log.w(TAG, "Unused page $pageId of $date still has content; kept")
                empty
            }
        } else {
            emptyList()
        }
        // The rows of these pages changed: their recognized text is looked at again (OCR staleness)
        (targetPageIds + pageOfIndex.values).distinct().forEach { pageDao.updateUpdatedAt(it, now) }
        return Outcome(targetPageIds, deleted, removed, added, movedTextBoxes)
    }

    /**
     * Distance of a move by [rows] rows, in whole hundredths of a pixel: stroke points are stored to
     * 0.01 px (StrokePointConverter), so moving there and back gives exactly the stored points.
     */
    private fun rowShift(rows: Int, rowHeight: Float): Float {
        // Rounded by size, then signed: the way back is exactly the way there, reversed
        val hundredths = Math.round(kotlin.math.abs(rows) * rowHeight * 100f)
        return (if (rows < 0) -hundredths else hundredths) / 100f
    }

    /**
     * A moved point's y for storage: stroke points are stored as whole hundredths of a pixel, cut (not
     * rounded) by EncodePolyline, so 30.07 held as a float (30.0699997…) would be stored as 30.06. The
     * exact hundredth plus 0.004 is always stored as that hundredth, so moves never drift.
     */
    private fun storedPointY(y: Float): Float = ((Math.round(y * 100.0) + 0.4) / 100.0).toFloat()

    /** Any content a slot can hold besides ink (an empty slot is a placeholder row). */
    private fun Task.holdsContent(parentIds: Set<String>): Boolean =
        !typedText.isNullOrBlank() || !convertedText.isNullOrBlank() || !recognizedText.isNullOrBlank() ||
            isCompleted || priority.isNotEmpty() || parentTaskId != null || id in parentIds ||
            dueDay != null || legacyDueDateMs != null || reminderMinuteOfDay != null || templateId != null ||
            estimatedMinutes != null || migrationCount > 0 || originalDate != null || lastMigratedAt != null

    /** After the commit: loaded pages, caches and saved previews follow (like TasksInkLayout). */
    private suspend fun afterReflow(date: String, outcome: Outcome) {
        for ((pageId, ids) in outcome.removedStrokeIds) {
            if (PageDataManager.isPageLoaded(pageId)) PageDataManager.removeStrokes(ids, pageId)
        }
        for ((pageId, strokes) in outcome.addedStrokes) {
            if (PageDataManager.isPageLoaded(pageId)) PageDataManager.addStrokes(strokes, pageId)
        }
        for ((before, after) in outcome.movedTextBoxes) {
            if (PageDataManager.isPageLoaded(before.pageId)) PageDataManager.removeTextAnnotation(before.pageId, before.id)
            if (PageDataManager.isPageLoaded(after.pageId)) PageDataManager.addTextAnnotation(after.pageId, after)
        }
        val touched = outcome.pageIds + outcome.removedStrokeIds.keys + outcome.addedStrokes.keys +
            outcome.movedTextBoxes.flatMap { (before, after) -> listOf(before.pageId, after.pageId) }
        val previews = ensurePreviewsFullFolder(appContext)
        for (pageId in touched.toSet()) {
            PageDataManager.invalidatePreviewCache(pageId)
            previews.listFiles { file -> file.name.startsWith(pageId) }?.forEach { it.delete() }
        }
        TaskRepository(appContext).invalidateTaskCache(date)
        if (outcome.deletedPageIds.isNotEmpty()) {
            val ocr = UnifiedOcrRepository(appContext)
            outcome.deletedPageIds.forEach { ocr.deleteOcrForPage(OcrSource.TASK, date, it) }
        }
    }

    /** Runs [block] in one transaction on the page write queue, after every write queued before it. */
    private suspend fun <T> onWriteQueue(what: String, block: (AppDatabase) -> T?): T? {
        val result = CompletableDeferred<T?>()
        PagePersistenceWriter.submit("Tasks pages: $what") {
            result.complete(
                try {
                    val db = AppDatabase.getDatabase(appContext)
                    db.runInTransaction<T?> { block(db) }
                } catch (e: Exception) {
                    Log.e(TAG, "Tasks pages $what failed: ${e.message}", e)
                    null
                }
            )
        }
        return result.await()
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
