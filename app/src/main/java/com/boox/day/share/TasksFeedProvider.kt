package com.boox.day.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import com.boox.day.data.db.AppDatabase
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
        val args: Array<Any> = if (match == OPEN) emptyArray() else arrayOf(today)
        val sql = """
            SELECT id, typedText, convertedText, recognizedText, dayDate, dueDay,
                   isCompleted, priority, parentTaskId, updatedAt
            FROM task
            WHERE $where AND $HAS_CONTENT
            ORDER BY $order
            LIMIT $limit
        """.trimIndent()

        val out = MatrixCursor(TasksFeedContract.COLUMNS)
        val db = AppDatabase.getDatabase(context).openHelper.readableDatabase
        db.query(SimpleSQLiteQuery(sql, args)).use { c ->
            var row = 0L
            while (c.moveToNext()) {
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
