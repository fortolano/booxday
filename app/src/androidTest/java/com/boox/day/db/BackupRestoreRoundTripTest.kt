package com.boox.day.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.DayPage
import com.boox.day.data.db.Folder
import com.boox.day.data.db.Kv
import com.boox.day.data.db.LocalCalendar
import com.boox.day.data.db.LocalEvent
import com.boox.day.data.db.Notebook
import com.boox.day.data.db.RecurringTaskException
import com.boox.day.data.db.RecurringTaskTemplate
import com.boox.day.data.db.SummaryCache
import com.boox.day.data.db.SyncStatus
import com.boox.day.data.db.Task
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.UnifiedOcrPage
import com.boox.day.data.db.UnifiedOcrText
import com.boox.day.sync.ExportService
import com.boox.day.sync.ImportService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date

/**
 * Plan 2026-10-01, A0 (Phase 0 test a): a backup followed by a restore must bring back every
 * row and column the app relies on, and restored events must be visible in day queries.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase
    private val filesToDelete = mutableListOf<File>()

    @Before
    fun setUp() {
        source = TestDb.inMemory(context)
        target = TestDb.inMemory(context)
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
        filesToDelete.forEach { it.delete() }
    }

    @Test
    fun backupThenRestore_keepsEveryRowAndColumn() = runBlocking {
        seed(source)

        val export = ExportService(context) { source }.createBackup()
        assertTrue("export failed: $export", export is ExportService.ExportResult.Success)
        val backupFile = (export as ExportService.ExportResult.Success).backupFile
        filesToDelete += backupFile

        val restore = ImportService(context) { target }.restoreBackup(backupFile.readText())
        assertTrue("restore failed: $restore", restore is ImportService.ImportResult.Success)

        val problems = mutableListOf<String>()
        problems += TestDb.diff("task", source.taskDao().getAllTasks(), target.taskDao().getAllTasks()) { it.id }
        problems += TestDb.diff(
            "recurring_task_template",
            source.recurringTaskTemplateDao().getAll(),
            target.recurringTaskTemplateDao().getAll()
        ) { it.id }
        problems += TestDb.diff("recurring_task_exception", exceptions(source), exceptions(target)) { it.id }
        problems += TestDb.diff(
            "local_events",
            source.localEventDao().getAllEvents(),
            target.localEventDao().getAllEvents()
        ) { it.id }
        for ((day, eventIds) in EVENTS_VISIBLE_ON) {
            val visible = target.localEventDao().getEventsForDay(day).map { it.id }.toSet()
            eventIds.filterNot { it in visible }
                .forEach { problems += "local_events[$it]: not returned by getEventsForDay($day)" }
        }
        problems += TestDb.diff("Page", source.pageDao().getAllPages(), target.pageDao().getAllPages()) { it.id }
        problems += TestDb.diff("DayPage", source.dayPageDao().getAllDayPages(), target.dayPageDao().getAllDayPages()) { it.date }
        problems += TestDb.diff("Stroke", source.strokeDao().getAllStrokes(), target.strokeDao().getAllStrokes()) { it.id }
        problems += TestDb.diff(
            "text_annotations",
            source.textAnnotationDao().getAllAnnotations(),
            target.textAnnotationDao().getAllAnnotations()
        ) { it.id }
        problems += TestDb.diff("Image", source.ImageDao().getAllImages(), target.ImageDao().getAllImages()) { it.id }
        problems += TestDb.diff(
            "Attachment",
            source.attachmentDao().getAllAttachments(),
            target.attachmentDao().getAllAttachments()
        ) { it.id }
        problems += TestDb.diff("Notebook", source.notebookDao().getAllNotebooks(), target.notebookDao().getAllNotebooks()) { it.id }
        problems += TestDb.diff("Folder", source.folderDao().getAllFolders(), target.folderDao().getAllFolders()) { it.id }
        problems += TestDb.diff("Kv", source.kvDao().getAll(), target.kvDao().getAll()) { it.key }
        problems += TestDb.diff("local_calendars", source.localCalendarDao().getAll(), target.localCalendarDao().getAll()) { it.id }
        problems += TestDb.diff("unified_ocr_page", source.unifiedOcrDao().getAllPages(), target.unifiedOcrDao().getAllPages()) { it.id }
        problems += TestDb.diff("unified_ocr_text", source.unifiedOcrDao().getAllTexts(), target.unifiedOcrDao().getAllTexts()) { it.id }
        problems += TestDb.diff("summary_cache", source.summaryCacheDao().getAll(), target.summaryCacheDao().getAll()) { it.id }

        assertTrue(
            problems.joinToString("\n", prefix = "${problems.size} round-trip problem(s):\n"),
            problems.isEmpty()
        )
    }

    /**
     * Backups already on disk were written by the v2 exporter, which has no endDayId on events.
     * Restoring such a file must still show the events on their day.
     */
    @Test
    fun restoreV2File_eventsVisibleInDayQueries() = runBlocking {
        val v2Json = """
            {
              "metadata": { "version": 2, "createdAt": 1759300000000, "appVersion": "1.0.30-personal" },
              "localEvents": [
                {
                  "id": "v2-event-1", "title": "Stake council", "dayId": "2026-09-30",
                  "startTime": 1759255200000, "endTime": 1759262400000, "isAllDay": false,
                  "location": "Stake center", "description": null, "colorHex": null, "attendees": null,
                  "localCalendarId": null, "linkedMemoPageId": null,
                  "createdAt": 1759000000000, "updatedAt": 1759000000000
                },
                {
                  "id": "v2-event-2", "title": "Temple day", "dayId": "2026-10-01",
                  "startTime": 1759302000000, "endTime": null, "isAllDay": true,
                  "location": null, "description": "All day", "colorHex": "#000000", "attendees": null,
                  "localCalendarId": null, "linkedMemoPageId": null,
                  "createdAt": 1759000000000, "updatedAt": 1759000000000
                }
              ]
            }
        """.trimIndent()

        val restore = ImportService(context) { target }.restoreBackup(v2Json)
        assertTrue("restore failed: $restore", restore is ImportService.ImportResult.Success)

        val sep30 = target.localEventDao().getEventsForDay("2026-09-30").map { it.id }
        val oct1 = target.localEventDao().getEventsForDay("2026-10-01").map { it.id }
        assertTrue("v2-event-1 invisible on 2026-09-30, got $sep30", "v2-event-1" in sep30)
        assertTrue("v2-event-2 invisible on 2026-10-01, got $oct1", "v2-event-2" in oct1)
    }

    private fun exceptions(db: AppDatabase): List<RecurringTaskException> =
        EXCEPTION_DATES.flatMap { db.recurringTaskExceptionDao().getForDate(it) }

    private suspend fun seed(db: AppDatabase) {
        db.folderDao().create(Folder(id = "folder-1", title = "Church", createdAt = Date(1_759_000_000_000), updatedAt = Date(1_759_000_000_500)))
        db.notebookDao().create(
            Notebook(
                id = "notebook-1", title = "Council notes", openPageId = "page-notebook",
                pageIds = listOf("page-notebook"), parentFolderId = "folder-1",
                defaultBackground = "lined", defaultBackgroundType = "native",
                linkedExternalUri = "content://notes/1",
                createdAt = Date(1_759_000_001_000), updatedAt = Date(1_759_000_002_000)
            )
        )
        db.pageDao().create(TestDb.page("page-notebook").copy(notebookId = "notebook-1", parentFolderId = "folder-1", scroll = 12))
        db.pageDao().create(TestDb.page("page-memo").copy(background = "dotted", linkedEventId = "event-1"))
        db.pageDao().create(TestDb.page("page-tasks"))
        db.dayPageDao().insert(
            DayPage(
                date = "2026-09-30", pageIds = listOf("page-memo"), openPageId = "page-memo",
                createdAt = Date(1_759_000_003_000), updatedAt = Date(1_759_000_004_000)
            )
        )

        db.recurringTaskTemplateDao().insert(
            RecurringTaskTemplate(
                id = "template-1", title = "Weekly interviews", defaultPriority = "A",
                defaultEstimatedMinutes = 45, recurrenceType = "WEEKLY", daysOfWeek = "2,4",
                dayOfMonth = null, intervalDays = 7, anchorDate = "2026-09-01", endDate = "2026-12-31",
                isActive = true, isHabit = true, createdAt = 1_759_000_005_000, updatedAt = 1_759_000_006_000
            )
        )
        db.recurringTaskExceptionDao().insert(
            RecurringTaskException(
                id = "exception-skip", templateId = "template-1", occurrenceDate = EXCEPTION_DATES[0],
                exceptionType = "SKIP", createdAt = 1_759_000_007_000, updatedAt = 1_759_000_008_000
            )
        )
        db.recurringTaskExceptionDao().insert(
            RecurringTaskException(
                id = "exception-detached", templateId = "template-1", occurrenceDate = EXCEPTION_DATES[1],
                exceptionType = "DETACHED", createdAt = 1_759_000_009_000, updatedAt = 1_759_000_010_000
            )
        )

        // Every Task column set to a non-default value somewhere in this list.
        db.taskDao().insertAll(
            listOf(
                Task(
                    id = "task-parent", dayDate = "2026-09-30", taskPageIndex = 0, rowIndex = 0,
                    pageId = "page-tasks", isCompleted = false, priority = "A",
                    typedText = null, recognizedText = "call bishop", convertedText = "Call the bishop",
                    parentTaskId = null, migrationCount = 2, originalDate = "2026-09-28",
                    lastMigratedAt = 1_759_200_000_000, estimatedMinutes = 30,
                    dueDay = "2026-10-02", templateId = "template-1",
                    createdAt = Date(1_759_000_011_000), updatedAt = Date(1_759_000_012_000)
                ),
                Task(
                    id = "task-child", dayDate = "2026-09-30", taskPageIndex = 0, rowIndex = 1,
                    pageId = "page-tasks", isCompleted = true, priority = "B",
                    typedText = "Prepare agenda", recognizedText = null, convertedText = null,
                    parentTaskId = "task-parent", migrationCount = 1, originalDate = "2026-09-29",
                    lastMigratedAt = 1_759_210_000_000, estimatedMinutes = 15,
                    dueDay = "2026-10-01", templateId = null,
                    createdAt = Date(1_759_000_013_000), updatedAt = Date(1_759_000_014_000)
                ),
                Task(
                    id = "task-empty", dayDate = "2026-09-30", taskPageIndex = 0, rowIndex = 2,
                    pageId = "page-tasks",
                    createdAt = Date(1_759_000_015_000), updatedAt = Date(1_759_000_016_000)
                )
            )
        )

        db.strokeDao().create(
            listOf(
                TestDb.stroke("page-memo", id = "stroke-memo-1"),
                TestDb.stroke("page-memo", id = "stroke-memo-2", dx = 40f),
                TestDb.stroke("page-tasks", id = "stroke-task-1")
            )
        )
        db.textAnnotationDao().insert(
            TextAnnotation(
                id = "text-1", content = "Ministering", x = 1f, y = 2f, width = 300f, height = 40f,
                fontSize = 20f, fontFamily = "serif", fontWeight = 700, fontStyle = "italic",
                textColor = 0xFF111111.toInt(), backgroundColor = 0x11000000, textAlign = "center",
                padding = 4f, spans = "[]", sourceFormat = "rich", pageId = "page-memo",
                sourceType = "voice", sourceStrokeIds = "stroke-memo-1", recognitionLanguage = "en-US",
                recognitionConfidence = 0.9f,
                createdAt = Date(1_759_000_017_000), updatedAt = Date(1_759_000_018_000)
            )
        )
        db.ImageDao().create(TestDb.image("page-memo", id = "image-1"))
        db.attachmentDao().create(TestDb.attachment("page-memo", id = "attachment-1"))
        db.kvDao().set(Kv(key = "test-setting", value = """{"a":1}"""))

        db.localCalendarDao().insert(
            LocalCalendar(id = "calendar-1", name = "Stake", colorHex = "#123456", sortOrder = 1, isDefault = false, createdAt = 1_759_000_019_000)
        )
        db.localEventDao().insertAll(
            listOf(
                LocalEvent(
                    id = "event-1", title = "Stake council", dayId = "2026-09-30", endDayId = "2026-09-30",
                    startTime = 1_759_255_200_000, endTime = 1_759_262_400_000, isAllDay = false,
                    location = "Stake center", description = "Agenda", colorHex = "#000000",
                    attendees = "a@example.com,b@example.com",
                    syncEnabled = true, syncStatus = SyncStatus.SYNCED,
                    googleEventId = "g-event-1", googleCalendarId = "g-cal", googleCalendarName = "Stake",
                    providerId = "GOOGLE", accountId = "account-1", accountEmail = "me@example.com",
                    providerEventId = "g-event-1", providerCalendarId = "g-cal", providerCalendarName = "Stake",
                    lastSyncedAt = 1_759_000_020_000, serverEtag = "etag-1", isReadOnly = false,
                    createdAt = 1_759_000_021_000, updatedAt = 1_759_000_022_000,
                    linkedMemoPageId = "page-memo", localCalendarId = "calendar-1"
                ),
                LocalEvent(
                    id = "event-2", title = "Conference", dayId = "2026-09-30", endDayId = "2026-10-01",
                    startTime = 1_759_240_000_000, endTime = 1_759_400_000_000, isAllDay = true,
                    localCalendarId = "calendar-1",
                    createdAt = 1_759_000_023_000, updatedAt = 1_759_000_024_000
                )
            )
        )

        db.unifiedOcrDao().upsertOcrPage(
            UnifiedOcrPage(
                id = "ocr-page-1", sourceType = "MEMO", sourceId = "2026-09-30", pageId = "page-memo",
                sourceCreatedAt = 1_759_000_025_000, sourceModifiedAt = 1_759_000_026_000, ocrVersion = 3,
                ocrAt = 1_759_000_027_000, status = "COMPLETED", failCount = 1, lastError = "timeout",
                textHash = "t-hash", semanticHash = "s-hash", firstIndexedAt = 1_759_000_028_000
            )
        )
        db.unifiedOcrDao().upsertOcrText(
            UnifiedOcrText(
                id = "ocr-text-1", sourceType = "MEMO", sourceId = "2026-09-30", pageId = "page-memo",
                text = "Call bishop", textNormalized = "call bishop", cleanText = "Call bishop",
                language = "en", wordCount = 2, confidence = 0.8f, docStyle = "list",
                keywordsJson = "[]", keyPhrasesJson = "[]", keySentencesJson = "[]", entitiesJson = "[]",
                actionItemsJson = "[]", temporalSignalsJson = "[]", topicsJson = "[]", synopsis = "s",
                domain = "church", domainConfidence = 0.7f, intentsJson = "[]", subdomainHint = "council",
                classificationExplainJson = "{}", sourceTitle = "Sep 30", sourcePath = "/x",
                sourceDate = "2026-09-30", sourceTimestamp = 1_759_000_029_000, semanticVersion = 4,
                updatedAt = 1_759_000_030_000
            )
        )
        db.summaryCacheDao().save(
            SummaryCache(
                id = "daily_2026-09-30", type = "daily", dateKey = "2026-09-30", rangeStart = null, rangeEnd = null,
                taskCount = 3, completedCount = 1, lastUpdatedAt = 1_759_000_031_000, contentHash = "c-hash",
                resultJson = "{}", generatedAt = 1_759_000_032_000, validHours = 4
            )
        )
    }

    private companion object {
        val EXCEPTION_DATES = listOf("2026-10-02", "2026-10-07")
        val EVENTS_VISIBLE_ON = mapOf(
            "2026-09-30" to listOf("event-1", "event-2"),
            "2026-10-01" to listOf("event-2")
        )
    }
}
