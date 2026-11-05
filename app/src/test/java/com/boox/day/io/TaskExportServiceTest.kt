package com.boox.day.io

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Page
import com.boox.day.data.db.Task
import com.boox.day.editor.ui.TaskExportAction
import com.boox.day.editor.ui.TaskExportFormat
import com.boox.day.editor.ui.TaskExportScope
import com.boox.day.tasks.TaskPaginationTest.Companion.ROW_HEIGHT
import com.boox.day.tasks.TaskPaginationTest.Companion.TOP
import com.boox.day.tasks.TaskPaginationTest.Companion.strokeInRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Handwriting not read yet belongs in a Tasks export (Nov 2026): the check saw ink only on pages open
 * this session, so such tasks dropped out of the longer scopes without a word.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskExportServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val today = LocalDate.now().toString()
    private val settingsBefore = GlobalAppSettings.current
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = AppDatabase.getDatabase(context)
        GlobalAppSettings.update(settingsBefore.copy(tasksInkCanvasHeight = TOP + 10 * ROW_HEIGHT, tasksInkTopOffset = TOP))
    }

    @After
    fun tearDown() {
        GlobalAppSettings.update(settingsBefore)
        AppDatabase.closeDatabase()
    }

    @Test
    fun `handwriting on a page not open this session is exported`() {
        val page = Page().also { db.pageDao().create(it) }
        db.taskDao().insertAll((0 until 10).map { row ->
            Task(dayDate = today, taskPageIndex = 0, rowIndex = row, pageId = page.id, typedText = if (row == 0) "Typed" else if (row == 2) "" else null)
        })
        db.strokeDao().create(strokeInRow(page.id, row = 1, from = 0.2f, to = 0.6f))
        db.strokeDao().create(strokeInRow(page.id, row = 1, from = 0.3f, to = 0.7f))
        db.strokeDao().create(strokeInRow(page.id, row = 2, from = 0.2f, to = 0.6f))
        db.strokeDao().create(strokeInRow(page.id, row = 2, from = 0.3f, to = 0.7f))

        val result = TaskExportService(context).export(
            scope = TaskExportScope.TODAY,
            format = TaskExportFormat.TEXT,
            action = TaskExportAction.CLIPBOARD,
            dayDate = today,
            currentTaskPageIndex = 0,
            canvasHeight = TOP + 10 * ROW_HEIGHT
        )

        assertEquals(3, result.taskCount)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(text, text.contains("Typed"))
        // An empty typed text is no text: the row still says what it is
        assertEquals(text, 2, Regex("""\[Handwritten task]""").findAll(text).count())
    }
}
