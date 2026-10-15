package com.boox.day.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.RecurringTaskException
import com.boox.day.data.db.RecurringTaskTemplate
import com.boox.day.data.db.Task
import com.boox.day.sync.ExportService
import com.boox.day.sync.ImportService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Plan 2026-10-01, A0: restore is all-or-nothing and never empties tables the file doesn't cover.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreSafetyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var device: AppDatabase
    private val filesToDelete = mutableListOf<File>()

    @Before
    fun setUp() {
        device = TestDb.inMemory(context)
        seedDevice(device)
    }

    @After
    fun tearDown() {
        device.close()
        filesToDelete.forEach { it.delete() }
    }

    @Test
    fun backupReferringToMissingPage_isRejected_andNothingChanges() = runBlocking {
        val before = snapshot(device)
        val json = v3Backup(
            pages = listOf(page("backup-page")),
            strokes = listOf(stroke("backup-stroke", pageId = "page-not-in-file"))
        )

        val result = ImportService(context) { device }.restoreBackup(json)

        assertTrue("expected rejection, got $result", result is ImportService.ImportResult.Error)
        assertEquals(before, snapshot(device))
    }

    @Test
    fun damagedBackupFile_isRejected_andNothingChanges() = runBlocking {
        val before = snapshot(device)
        val complete = v3Backup(pages = listOf(page("backup-page")), strokes = listOf(stroke("s1", "backup-page")))
        val truncated = complete.substring(0, complete.length * 2 / 3)

        val result = ImportService(context) { device }.restoreBackup(truncated)

        assertTrue("expected rejection, got $result", result is ImportService.ImportResult.Error)
        assertEquals(before, snapshot(device))
    }

    @Test
    fun strokeWithUnreadablePoints_isRejected_andNothingChanges() = runBlocking {
        val before = snapshot(device)
        val json = v3Backup(
            pages = listOf(page("backup-page")),
            strokes = listOf(stroke("s1", "backup-page", pointsJson = "[]"))
        )

        val result = ImportService(context) { device }.restoreBackup(json)

        assertTrue("expected rejection, got $result", result is ImportService.ImportResult.Error)
        assertEquals(before, snapshot(device))
    }

    @Test
    fun restoringV2File_keepsRepeatingTasks_andReplacesTheRest() = runBlocking {
        val v2 = """
            {
              "metadata": { "version": 2, "appVersion": "1.0.30-personal" },
              "pages": [ ${page("v2-page")} ],
              "tasks": [
                {"id":"v2-task","dayDate":"2026-10-05","taskPageIndex":0,"rowIndex":0,"pageId":"v2-page",
                 "isCompleted":false,"priority":"","typedText":"From v2","convertedText":null,
                 "createdAt":1759000000000,"updatedAt":1759000000000}
              ],
              "strokes": [], "dayPages": [], "textAnnotations": [], "images": [], "localEvents": [],
              "localCalendars": [], "notebooks": [], "folders": [], "attachments": [], "settings": [],
              "ocrPages": [], "ocrTexts": [], "summaryCache": []
            }
        """.trimIndent()

        val result = ImportService(context) { device }.restoreBackup(v2)

        assertTrue("restore failed: $result", result is ImportService.ImportResult.Success)
        assertEquals(listOf("device-template"), device.recurringTaskTemplateDao().getAll().map { it.id })
        assertEquals(listOf("device-exception"), device.recurringTaskExceptionDao().getAll().map { it.id })
        assertEquals(listOf("v2-task"), device.taskDao().getAllTasks().map { it.id })
        assertEquals(emptyList<String>(), device.strokeDao().getAllStrokes().map { it.id })
    }

    @Test
    fun exportWithManyStrokes_restoresEveryStroke() = runBlocking {
        val manyStrokes = (0 until 950).map { TestDb.stroke("device-page", id = "bulk-%04d".format(it), dx = it % 50f) }
        device.strokeDao().create(manyStrokes)
        val expected = device.strokeDao().getAllStrokes().sortedBy { it.id }

        val export = ExportService(context) { device }.createBackup()
        assertTrue("export failed: $export", export is ExportService.ExportResult.Success)
        val file = (export as ExportService.ExportResult.Success).backupFile
        filesToDelete += file

        val target = TestDb.inMemory(context)
        try {
            val result = ImportService(context) { target }.restoreBackup({ file.inputStream() })
            assertTrue("restore failed: $result", result is ImportService.ImportResult.Success)
            assertEquals(expected, target.strokeDao().getAllStrokes().sortedBy { it.id })
            val preview = ImportService(context) { target }.previewBackup { file.inputStream() }.getOrThrow()
            assertEquals(expected.size, preview.strokeCount)
        } finally {
            target.close()
        }
    }

    private fun seedDevice(db: AppDatabase) {
        db.pageDao().create(TestDb.page("device-page"))
        db.strokeDao().create(listOf(TestDb.stroke("device-page", id = "device-stroke-1"), TestDb.stroke("device-page", id = "device-stroke-2", dx = 30f)))
        db.taskDao().insert(Task(id = "device-task", dayDate = "2026-10-01", taskPageIndex = 0, rowIndex = 0, pageId = "device-page", typedText = "Keep me"))
        db.recurringTaskTemplateDao().insert(
            RecurringTaskTemplate(id = "device-template", title = "Weekly", recurrenceType = "WEEKLY", anchorDate = "2026-09-01")
        )
        db.recurringTaskExceptionDao().insert(
            RecurringTaskException(id = "device-exception", templateId = "device-template", occurrenceDate = "2026-10-08", exceptionType = "SKIP")
        )
    }

    /** Everything a failed restore must leave as it was. */
    private fun snapshot(db: AppDatabase): List<Any> = listOf(
        db.pageDao().getAllPages().sortedBy { it.id },
        db.strokeDao().getAllStrokes().sortedBy { it.id },
        db.taskDao().getAllTasks().sortedBy { it.id },
        db.recurringTaskTemplateDao().getAll(),
        db.recurringTaskExceptionDao().getAll()
    )

    private fun page(id: String) =
        """{"id":"$id","notebookId":null,"scroll":0,"background":"blank","backgroundType":"native","parentFolderId":null,"linkedEventId":null,"createdAt":1759000000000,"updatedAt":1759000000000}"""

    private fun stroke(id: String, pageId: String, pointsJson: String = """[{"x":1.0,"y":2.0},{"x":3.0,"y":4.0}]""") =
        """{"id":"$id","pageId":"$pageId","pen":"BALLPEN","color":-16777216,"size":3.0,"maxPressure":4096,"top":2.0,"bottom":4.0,"left":1.0,"right":3.0,"pointsJson":${org.json.JSONObject.quote(pointsJson)},"createdAt":1759000000000,"updatedAt":1759000000000}"""

    private fun v3Backup(pages: List<String>, strokes: List<String>) = """
        {
          "folders": [], "notebooks": [],
          "pages": [ ${pages.joinToString(",")} ],
          "dayPages": [], "recurringTaskTemplates": [], "recurringTaskExceptions": [], "tasks": [],
          "strokes": [ ${strokes.joinToString(",")} ],
          "textAnnotations": [], "images": [], "attachments": [], "localCalendars": [], "localEvents": [],
          "settings": [], "ocrPages": [], "ocrTexts": [], "summaryCache": [],
          "metadata": { "version": 3, "appVersion": "test" }
        }
    """.trimIndent()
}
