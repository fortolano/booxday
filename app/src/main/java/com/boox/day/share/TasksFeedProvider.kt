package com.boox.day.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.boox.day.APP_SETTINGS_KEY
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.KvProxy
import com.boox.day.editor.utils.calculateRowBounds
import com.boox.day.editor.utils.hasIntentionalInk
import com.boox.day.tasks.TaskPagination
import java.time.LocalDate

/**
 * Read-only feed of BooxDay tasks for other apps on the device (launchers, widgets,
 * assistants). It exposes task text and dates only: no strokes, notes, pages,
 * accounts or settings, and every write is refused.
 *
 *   content://com.boox.day.tasks/due    open tasks due today or overdue
 *   content://com.boox.day.tasks/today  every task with content on today's page
 *   content://com.boox.day.tasks/open   all open tasks with content (newest day first)
 *
 * Optional query parameter `limit` (1..200, default 50). Columns: see [TasksFeedContract].
 */
class TasksFeedProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(TasksFeedContract.AUTHORITY, TasksFeedContract.PATH_DUE, DUE)
        addURI(TasksFeedContract.AUTHORITY, TasksFeedContract.PATH_TODAY, TODAY)
        addURI(TasksFeedContract.AUTHORITY, TasksFeedContract.PATH_OPEN, OPEN)
    }

    // Opening the database here would slow app start-up; it is opened on first query.
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val match = matcher.match(uri)
        if (match == UriMatcher.NO_MATCH) return null
        val context = context ?: return null
        val limit = uri.getQueryParameter("limit")?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val today = LocalDate.now().toString()
        // /today?day=yyyy-MM-dd: another day's tasks (the Markdown daily note writes yesterday's)
        val day = uri.getQueryParameter("day")?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess } ?: today

        val where = when (match) {
            DUE -> "isCompleted = 0 AND dueDay IS NOT NULL AND dueDay <= ?"
            TODAY -> "dayDate = ?"
            else -> "isCompleted = 0"
        }
        val order = when (match) {
            DUE -> "dueDay ASC, dayDate ASC, taskPageIndex ASC, rowIndex ASC"
            TODAY -> "taskPageIndex ASC, rowIndex ASC"
            else -> "dayDate DESC, taskPageIndex ASC, rowIndex ASC"
        }
        val args: Array<Any> = when (match) {
            OPEN -> emptyArray()
            TODAY -> arrayOf(day)
            else -> arrayOf(today)
        }
        // Rows with text or task data, and rows on pages with ink: a task can be handwriting only
        // (no text until handwriting recognition reads it, or ever if it can't). Those are checked
        // below with the app's own rule for intentional ink in a row.
        val sql = """
            SELECT id, typedText, convertedText, recognizedText, dayDate, dueDay,
                   isCompleted, priority, parentTaskId, updatedAt,
                   $HAS_CONTENT AS hasContent, pageId, rowIndex
            FROM task
            WHERE $where AND ($HAS_CONTENT OR EXISTS (SELECT 1 FROM stroke WHERE stroke.pageId = task.pageId))
            ORDER BY $order
        """.trimIndent()

        val out = MatrixCursor(TasksFeedContract.COLUMNS)
        val db = AppDatabase.getDatabase(context).openHelper.readableDatabase
        val handwriting = HandwrittenRows(context, db)
        db.query(SimpleSQLiteQuery(sql, args)).use { c ->
            var row = 0L
            while (row < limit && c.moveToNext()) {
                if (c.getInt(10) == 0 && !handwriting.inRow(c.getString(11), c.getString(4), c.getInt(12))) continue
                val title = listOf(c.getString(2), c.getString(1), c.getString(3))
                    .firstOrNull { !it.isNullOrBlank() }?.trim()?.lineSequence()?.first()?.take(300)
                    ?: HANDWRITTEN
                val dueDay = c.getString(5)
                val completed = c.getInt(6) != 0
                out.addRow(arrayOf<Any?>(
                    row++,
                    c.getString(0),
                    title,
                    if (title == HANDWRITTEN) 1 else 0,
                    c.getString(4),
                    dueDay,
                    if (completed) 1 else 0,
                    c.getString(7).orEmpty(),
                    if (!completed && dueDay != null && dueDay < today) 1 else 0,
                    c.getString(8),
                    if (c.isNull(9)) null else c.getLong(9)
                ))
            }
        }
        out.setNotificationUri(context.contentResolver, TasksFeedContract.BASE_URI)
        return out
    }

    override fun getType(uri: Uri): String? =
        if (matcher.match(uri) == UriMatcher.NO_MATCH) null
        else "vnd.android.cursor.dir/vnd.com.boox.day.task"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw readOnly()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw readOnly()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw readOnly()

    private fun readOnly() = UnsupportedOperationException("The BooxDay tasks feed is read-only")

    private companion object {
        const val DUE = 1
        const val TODAY = 2
        const val OPEN = 3
        const val HANDWRITTEN = "Handwritten task"
        // Same "slot has content" rule BooxDay uses for its own task counts.
        const val HAS_CONTENT = "(isCompleted = 1 OR priority != '' OR typedText IS NOT NULL OR convertedText IS NOT NULL " +
            "OR (recognizedText IS NOT NULL AND recognizedText != '') OR parentTaskId IS NOT NULL OR dueDay IS NOT NULL)"
    }
}

/**
 * Tasks rows holding intentional handwriting (hasIntentionalInk, the rule the app uses for a row's
 * content), on the row geometry the stored Tasks ink belongs to (TasksInkLayout). Reads each page's
 * stroke bounds once, never the points. No geometry yet (Tasks never shown): no row counts as handwritten.
 * Also used by task sync (tasksync/TaskSyncScanner), which checks [known] first.
 */
internal class HandwrittenRows(context: Context, private val db: SupportSQLiteDatabase) {
    private val settings = GlobalAppSettings.current.takeIf { it.tasksInkCanvasHeight > 0f }
        ?: runCatching { KvProxy(context).get(APP_SETTINGS_KEY, AppSettings.serializer()) }.getOrNull()
    private val canvasHeight = settings?.tasksInkCanvasHeight ?: 0f
    private val topOffset = settings?.tasksInkTopOffset ?: 0f
    private val inkByPage = HashMap<String, List<FloatArray>>()

    /** False until Tasks has been shown once: then [inRow] can't tell and says false. */
    val known: Boolean get() = canvasHeight > topOffset

    fun inRow(pageId: String, day: String, rowIndex: Int): Boolean {
        if (canvasHeight <= topOffset) return false
        // Full screen Tasks (Oct 2026): the day full screen shows is on pages of more rows of the same height
        val rows = TaskPagination.rowsPerPage(day)
        if (rowIndex !in 0 until rows) return false
        val bounds = calculateRowBounds(rowIndex, TaskPagination.rowsHeight(canvasHeight, rows, topOffset), rows, topOffset)
        val ink = inkByPage.getOrPut(pageId) {
            db.query("SELECT top, bottom FROM stroke WHERE pageId = ?", arrayOf(pageId)).use { c ->
                List(c.count) { c.moveToNext(); floatArrayOf(c.getFloat(0), c.getFloat(1)) }
            }
        }
        return hasIntentionalInk(ink, bounds, { it[0] }, { it[1] })
    }
}
