package com.boox.day.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.DayPage
import com.boox.day.data.db.Folder
import com.boox.day.data.db.Image
import com.boox.day.data.db.Kv
import com.boox.day.data.db.LocalCalendar
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.Notebook
import com.boox.day.data.db.Page
import com.boox.day.data.db.RecurringTaskException
import com.boox.day.data.db.RecurringTaskTemplate
import com.boox.day.data.db.Stroke
import com.boox.day.data.db.StrokePoint
import com.boox.day.data.db.Task
import com.boox.day.data.db.TextAnnotation
import com.boox.day.editor.utils.Pen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Date

/**
 * JVM/Robolectric port of the core BackupRestoreSafetyTest and BackupRestoreRoundTripTest checks
 * (Phase 2 M8) so backup/restore integrity runs on the JVM without requiring connectedAndroidTest
 * (which wipes tablet data).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupRestoreSafetyJvmTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var device: AppDatabase
    private val filesToDelete = mutableListOf<File>()

    @Before
    fun setUp() {
        device = inMemoryDb()
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
            pages = listOf(pageJson("backup-page")),
            strokes = listOf(strokeJson("backup-stroke", pageId = "page-not-in-file"))
        )

        val result = ImportService(context) { device }.restoreBackup(json)

        assertTrue("expected rejection, got $result", result is ImportService.ImportResult.Error)
        assertEquals(before, snapshot(device))
    }

    @Test
    fun damagedBackupFile_isRejected_andNothingChanges() = runBlocking {
        val before = snapshot(device)
        val complete = v3Backup(pages = listOf(pageJson("backup-page")), strokes = listOf(strokeJson("s1", "backup-page")))
        val truncated = complete.substring(0, complete.length * 2 / 3)

        val result = ImportService(context) { device }.restoreBackup(truncated)

        assertTrue("expected rejection, got $result", result is ImportService.ImportResult.Error)
        assertEquals(before, snapshot(device))
    }

    @Test
    fun strokeWithUnreadablePoints_isRejected_andNothingChanges() = runBlocking {
        val before = snapshot(device)
        val json = v3Backup(
            pages = listOf(pageJson("backup-page")),
            strokes = listOf(strokeJson("s1", "backup-page", pointsJson = "[]"))
        )

        val result = ImportService(context) { device }.restoreBackup(json)

        assertTrue("expected rejection, got $result", result is ImportService.ImportResult.Error)
        assertEquals(before, snapshot(device))
    }

    @Test
    fun restoringV2File_keepsRepeatingTasks_andMakesEventsVisibleInDayQueries() = runBlocking {
        val v2 = """
            {
              "metadata": { "version": 2, "appVersion": "1.0.30-personal" },
              "pages": [ ${pageJson("v2-page")} ],
              "tasks": [
                {"id":"v2-task","dayDate":"2026-10-05","taskPageIndex":0,"rowIndex":0,"pageId":"v2-page",
                 "isCompleted":false,"priority":"","typedText":"From v2","convertedText":null,
                 "createdAt":1759000000000,"updatedAt":1759000000000}
              ],
              "localEvents": [
                {
                  "id": "v2-event-1", "title": "Stake council", "dayId": "2026-09-30",
                  "startTime": 1759255200000, "endTime": 1759262400000, "isAllDay": false,
                  "location": "Hall", "description": null, "colorHex": null, "attendees": null,
                  "localCalendarId": null, "linkedMemoPageId": null,
                  "createdAt": 1759000000000, "updatedAt": 1759000000000
                }
              ],
              "strokes": [], "dayPages": [], "textAnnotations": [], "images": [],
              "localCalendars": [], "notebooks": [], "folders": [], "attachments": [], "settings": [],
              "ocrPages": [], "ocrTexts": [], "summaryCache": []
            }
        """.trimIndent()

        val result = ImportService(context) { device }.restoreBackup(v2)

        assertTrue("restore failed: $result", result is ImportService.ImportResult.Success)
        assertEquals(listOf("device-template"), device.recurringTaskTemplateDao().getAll().map { it.id })
        assertEquals(listOf("device-exception"), device.recurringTaskExceptionDao().getAll().map { it.id })
        assertEquals(listOf("v2-task"), device.taskDao().getAllTasks().map { it.id })
        assertTrue("v2-event-1" in device.localEventDao().getEventsForDay("2026-09-30").map { it.id })
    }

    @Test
    fun fullExportThenRestoreRoundTripPreservesCoreTablesAndBulkStrokes() = runBlocking {
        device.folderDao().create(Folder(id = "f1", title = "Work", createdAt = Date(1_759_000_000_000), updatedAt = Date(1_759_000_000_500)))
        device.notebookDao().create(
            Notebook(
                id = "nb1", title = "Notes", openPageId = "device-page",
                pageIds = listOf("device-page"), parentFolderId = "f1",
                createdAt = Date(1_759_000_001_000), updatedAt = Date(1_759_000_002_000)
            )
        )
        device.dayPageDao().insert(
            DayPage(date = "2026-10-01", pageIds = listOf("device-page"), openPageId = "device-page",
                createdAt = Date(1_759_000_003_000), updatedAt = Date(1_759_000_004_000))
        )
        device.textAnnotationDao().insert(
            TextAnnotation(
                id = "txt-1", content = "Agenda", x = 10f, y = 20f, width = 200f, height = 50f,
                pageId = "device-page", createdAt = Date(1_759_000_005_000), updatedAt = Date(1_759_000_005_000)
            )
        )
        device.ImageDao().create(
            Image(id = "img-1", x = 5, y = 6, height = 40, width = 50, uri = "file:///images/img-1.png",
                pageId = "device-page", createdAt = Date(1_759_000_006_000), updatedAt = Date(1_759_000_006_000))
        )
        device.attachmentDao().create(
            Attachment(id = "att-1", x = 7, y = 8, filePath = "/sdcard/doc.pdf", displayName = "doc.pdf",
                fileType = "pdf", pageId = "device-page", createdAt = Date(1_759_000_007_000), updatedAt = Date(1_759_000_007_000))
        )
        device.localCalendarDao().insert(LocalCalendar(id = "lc-1", name = "Personal", sortOrder = 0, createdAt = 1_759_000_008_000))
        device.localEventDao().insert(
            LocalEvent(id = "le-1", title = "Dentist", dayId = "2026-10-01", endDayId = "2026-10-01",
                startTime = 1_759_320_000_000, endTime = 1_759_323_600_000, localCalendarId = "lc-1",
                createdAt = 1_759_000_009_000, updatedAt = 1_759_000_009_000)
        )
        device.kvDao().set(Kv(key = "test-setting", value = """{"a":1}"""))
        val bulkStrokes = (0 until 300).map { stroke("device-page", id = "bulk-%04d".format(it), dx = (it % 40).toFloat()) }
        device.strokeDao().create(bulkStrokes)

        val export = ExportService(context) { device }.createBackup()
        assertTrue("export failed: $export", export is ExportService.ExportResult.Success)
        val file = (export as ExportService.ExportResult.Success).backupFile
        filesToDelete += file

        val target = inMemoryDb()
        try {
            val restore = ImportService(context) { target }.restoreBackup({ file.inputStream() })
            assertTrue("restore failed: $restore", restore is ImportService.ImportResult.Success)
            assertEquals(device.pageDao().getAllPages().sortedBy { it.id }, target.pageDao().getAllPages().sortedBy { it.id })
            assertEquals(device.strokeDao().getAllStrokes().sortedBy { it.id }, target.strokeDao().getAllStrokes().sortedBy { it.id })
            assertEquals(device.taskDao().getAllTasks().sortedBy { it.id }, target.taskDao().getAllTasks().sortedBy { it.id })
            assertEquals(device.recurringTaskTemplateDao().getAll(), target.recurringTaskTemplateDao().getAll())
            assertEquals(device.recurringTaskExceptionDao().getAll(), target.recurringTaskExceptionDao().getAll())
            assertEquals(device.textAnnotationDao().getAllAnnotations(), target.textAnnotationDao().getAllAnnotations())
            assertEquals(device.ImageDao().getAllImages(), target.ImageDao().getAllImages())
            assertEquals(device.attachmentDao().getAllAttachments(), target.attachmentDao().getAllAttachments())
            assertEquals(device.localEventDao().getAllEvents(), target.localEventDao().getAllEvents())
            assertEquals(device.kvDao().getAll(), target.kvDao().getAll())
            val preview = ImportService(context) { target }.previewBackup { file.inputStream() }.getOrThrow()
            assertEquals(device.strokeDao().getAllStrokes().size, preview.strokeCount)
        } finally {
            target.close()
        }
    }

    @Test
    fun zipArchiveBundlesOwnedMemoImagesRestoresBytesAndWarnsOnExternalLinks(): Unit = runBlocking {
        val imagesDir = com.boox.day.data.ensureImagesFolder(context)
        val ownedFile = java.io.File(imagesDir, "memo-owned-${System.nanoTime()}.png")
        val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4, 5)
        ownedFile.writeBytes(imageBytes)

        val target = inMemoryDb()
        try {
            device.pageDao().create(page("media-page"))
            device.ImageDao().create(
                Image(
                    id = "img-owned",
                    x = 10, y = 20, width = 100, height = 80,
                    uri = android.net.Uri.fromFile(ownedFile).toString(),
                    pageId = "media-page"
                )
            )
            device.ImageDao().create(
                Image(
                    id = "img-external",
                    x = 30, y = 40, width = 50, height = 50,
                    uri = "content://com.example.provider/photo/42",
                    pageId = "media-page"
                )
            )
            device.attachmentDao().create(
                Attachment(
                    id = "att-ext",
                    x = 5, y = 5, width = 60, height = 60,
                    filePath = "/sdcard/Books/handbook.pdf",
                    displayName = "handbook.pdf",
                    fileType = "pdf",
                    pageId = "media-page"
                )
            )

            val exported = ExportService(context) { device }.createBackup() as ExportService.ExportResult.Success
            assertTrue("export must be a ZIP archive", BackupArchive.isZipFile(exported.backupFile))
            assertTrue(
                "export must surface warning for external links",
                exported.warnings.any { it.contains("external link", ignoreCase = true) }
            )

            // Delete the owned image file to prove restore genuinely recovers its bytes from the archive
            assertTrue(ownedFile.delete())
            assertFalse(ownedFile.exists())

            // Single-spool proof: source stream lambda must be opened strictly ONCE
            var openCount = 0
            val restored = ImportService(context) { target }.restoreBackup(
                source = {
                    openCount++
                    if (openCount > 1) throw IllegalStateException("source reopened!")
                    exported.backupFile.inputStream()
                }
            )
            assertTrue("restore must succeed: $restored", restored is ImportService.ImportResult.Success)
            assertEquals("restore must spool input stream strictly once", 1, openCount)
            assertTrue("owned memo image file must be restored to disk", ownedFile.exists())
            assertTrue("restored bytes must match original", imageBytes.contentEquals(ownedFile.readBytes()))

            val preview = ImportService(context) { target }.previewBackup { exported.backupFile.inputStream() }.getOrThrow()
            assertEquals(1, preview.bundledMediaCount)
            assertTrue(preview.warnings.isNotEmpty())
        } finally {
            ownedFile.delete()
            target.close()
        }
    }

    @Test
    fun zipSlipMediaEntryIsRejectedWithoutTouchingDatabase(): Unit = runBlocking {
        val before = snapshot(device)

        val evilZip = java.io.File(context.cacheDir, "evil-backup-${System.nanoTime()}.zip")
        try {
            java.util.zip.ZipOutputStream(evilZip.outputStream()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry(BackupArchive.BACKUP_JSON_ENTRY))
                zip.write(v3Backup(pages = listOf(pageJson("p1")), strokes = emptyList()).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(java.util.zip.ZipEntry("media/images/../../escape.txt"))
                zip.write("boom".toByteArray())
                zip.closeEntry()
            }

            val result = ImportService(context) { device }.restoreBackup(evilZip)
            assertTrue("zip-slip archive must be rejected", result is ImportService.ImportResult.Error)
            assertEquals("existing database must stay untouched", before, snapshot(device))
        } finally {
            evilZip.delete()
        }
    }

    private fun inMemoryDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    private fun page(id: String) = Page(id = id, createdAt = Date(1_759_000_000_000), updatedAt = Date(1_759_000_100_000))

    private fun stroke(pageId: String, id: String, dx: Float = 0f) = Stroke(
        id = id,
        size = 3f,
        pen = Pen.BALLPEN,
        top = 10f,
        bottom = 20f,
        left = 10f + dx,
        right = 30f + dx,
        points = listOf(
            StrokePoint(x = 10f + dx, y = 10f, pressure = 1200f),
            StrokePoint(x = 20f + dx, y = 15f, pressure = 1300f),
            StrokePoint(x = 30f + dx, y = 20f, pressure = 1400f),
        ),
        pageId = pageId,
        createdAt = Date(1_759_000_200_000),
        updatedAt = Date(1_759_000_300_000),
    )

    private fun seedDevice(db: AppDatabase) {
        db.pageDao().create(page("device-page"))
        db.strokeDao().create(listOf(stroke("device-page", id = "device-stroke-1"), stroke("device-page", id = "device-stroke-2", dx = 30f)))
        db.taskDao().insert(Task(id = "device-task", dayDate = "2026-10-01", taskPageIndex = 0, rowIndex = 0, pageId = "device-page", typedText = "Keep me"))
        db.recurringTaskTemplateDao().insert(
            RecurringTaskTemplate(id = "device-template", title = "Weekly", recurrenceType = "WEEKLY", anchorDate = "2026-09-01")
        )
        db.recurringTaskExceptionDao().insert(
            RecurringTaskException(id = "device-exception", templateId = "device-template", occurrenceDate = "2026-10-08", exceptionType = "SKIP")
        )
    }

    private fun snapshot(db: AppDatabase): List<Any> = listOf(
        db.pageDao().getAllPages().sortedBy { it.id },
        db.strokeDao().getAllStrokes().sortedBy { it.id },
        db.taskDao().getAllTasks().sortedBy { it.id },
        db.recurringTaskTemplateDao().getAll(),
        db.recurringTaskExceptionDao().getAll()
    )

    private fun pageJson(id: String) =
        """{"id":"$id","notebookId":null,"scroll":0,"background":"blank","backgroundType":"native","parentFolderId":null,"linkedEventId":null,"createdAt":1759000000000,"updatedAt":1759000000000}"""

    private fun strokeJson(id: String, pageId: String, pointsJson: String = """[{"x":1.0,"y":2.0},{"x":3.0,"y":4.0}]""") =
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
