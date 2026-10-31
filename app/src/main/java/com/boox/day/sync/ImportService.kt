package com.boox.day.sync

import android.content.Context
import androidx.room.withTransaction
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.Attachment
import com.boox.day.data.db.DayPage
import com.boox.day.data.db.TaskDueDates
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
import com.boox.day.data.db.SummaryCache
import com.boox.day.data.db.SyncStatus
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.UnifiedOcrPage
import com.boox.day.data.db.UnifiedOcrText
import com.boox.day.data.db.encodeStrokePoints
import com.boox.day.editor.utils.Pen
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Date

private const val TAG = "ImportService"

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

// JSON decoder for stroke points
private val strokePointsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private const val STROKE_INSERT_BATCH = 200

/** Section key in the backup file -> table it restores. */
private val SECTION_TABLES = linkedMapOf(
    "folders" to "Folder",
    "notebooks" to "Notebook",
    "pages" to "Page",
    "dayPages" to "DayPage",
    "recurringTaskTemplates" to "recurring_task_template",
    "recurringTaskExceptions" to "recurring_task_exception",
    "tasks" to "task",
    "taskSyncLinks" to "task_sync_link",
    "strokes" to "Stroke",
    "textAnnotations" to "text_annotations",
    "images" to "Image",
    "attachments" to "Attachment",
    "localCalendars" to "local_calendars",
    "localEvents" to "local_events",
    "settings" to "Kv",
    "ocrPages" to "unified_ocr_page",
    "ocrTexts" to "unified_ocr_text",
    "summaryCache" to "summary_cache"
)

/**
 * Children are emptied before their parents. Task sync links are not listed: a restore that brings
 * back tasks or links empties them itself, before any task row goes (see restoreBackup).
 */
private val CLEAR_ORDER = listOf(
    "recurringTaskExceptions", "tasks", "strokes", "textAnnotations", "images", "attachments",
    "dayPages", "pages", "notebooks", "folders", "recurringTaskTemplates",
    "localEvents", "localCalendars", "settings", "ocrTexts", "ocrPages", "summaryCache"
)

/** ON DELETE CASCADE links: emptying the parent table also deletes these child rows. */
private val CASCADES = listOf(
    Triple("folders", "notebooks", "parentFolderId"),
    Triple("folders", "pages", "parentFolderId"),
    Triple("notebooks", "pages", "notebookId"),
    Triple("pages", "tasks", "pageId"),
    Triple("pages", "strokes", "pageId"),
    Triple("pages", "textAnnotations", "pageId"),
    Triple("pages", "images", "pageId"),
    Triple("pages", "attachments", "pageId"),
    Triple("recurringTaskTemplates", "recurringTaskExceptions", "templateId")
)

private val SECTION_LABELS = mapOf(
    "folders" to "folder",
    "notebooks" to "notebook",
    "pages" to "page",
    "dayPages" to "day page",
    "recurringTaskTemplates" to "repeating task",
    "recurringTaskExceptions" to "repeating task exception",
    "tasks" to "task",
    "taskSyncLinks" to "task sync link",
    "strokes" to "stroke",
    "textAnnotations" to "text box",
    "images" to "image",
    "attachments" to "attachment",
    "localCalendars" to "calendar",
    "localEvents" to "event",
    "settings" to "setting",
    "ocrPages" to "OCR page",
    "ocrTexts" to "OCR text",
    "summaryCache" to "summary"
)

private fun label(section: String) = SECTION_LABELS[section] ?: section

/** Anything in the file that would stop it from restoring completely. */
private class BackupRejected(message: String) : Exception(message)

/**
 * Import Service for BooxDay backup restore (formats v1-v3, see BackupSchema.kt).
 *
 * Restore is all-or-nothing:
 * 1. The whole file is read and checked first: every row decodes, every stroke's points can be
 *    stored, no key appears twice, and every reference to a folder/notebook/page/repeating task
 *    resolves. Nothing is written if anything is wrong.
 * 2. Then, in ONE transaction, the tables that have a section in the file are emptied and every
 *    row is inserted. The first error rolls everything back, leaving the database unchanged.
 * Tables without a section in the file are never emptied (e.g. repeating tasks when restoring a
 * v2 file). The file is read as a stream (twice), so it never has to fit in memory.
 *
 * v3 files restore event sync metadata; v1/v2 events come back local-only, with endDayId = dayId.
 * Callers must restart the app after a successful restore (see PostRestore) - every in-memory
 * cache and the page previews on disk still describe the old data.
 */
class ImportService(
    private val context: Context,
    // Test seam: instrumented tests pass an in-memory database. The app always uses the singleton.
    private val databaseProvider: () -> AppDatabase = { AppDatabase.getDatabase(context) }
) {

    /**
     * Result of import operation.
     */
    sealed class ImportResult {
        data class Success(
            val eventsRestored: Int,
            val tasksRestored: Int,
            val pagesRestored: Int,
            val strokesRestored: Int,
            // v2 counts
            val notebooksRestored: Int = 0,
            val foldersRestored: Int = 0,
            val attachmentsRestored: Int = 0,
            val settingsRestored: Int = 0,
            val ocrPagesRestored: Int = 0,
            val ocrTextsRestored: Int = 0,
            val summariesRestored: Int = 0
        ) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    /**
     * Preview of backup contents before importing.
     */
    data class BackupPreview(
        val metadata: BackupMetadata,
        val eventCount: Int,
        val taskCount: Int,
        val pageCount: Int,
        val strokeCount: Int,
        val calendarCount: Int,
        // v2 counts
        val notebookCount: Int = 0,
        val folderCount: Int = 0,
        val attachmentCount: Int = 0,
        val settingCount: Int = 0,
        val ocrPageCount: Int = 0,
        val ocrTextCount: Int = 0,
        val summaryCount: Int = 0
    )

    /**
     * Count the backup's contents without importing (rows are skipped, not decoded).
     */
    suspend fun previewBackup(jsonContent: String): Result<BackupPreview> =
        previewBackup { jsonContent.byteInputStream() }

    suspend fun previewBackup(source: () -> InputStream): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            var metadata: BackupMetadata? = null
            val counts = HashMap<String, Int>()
            readBackupFile(source) { key, reader ->
                when (key) {
                    "metadata" -> metadata = decodeValue(reader, BackupMetadata.serializer())
                    in SECTION_TABLES -> counts[key] = (counts[key] ?: 0) + countElements(reader)
                    else -> reader.skipValue()
                }
            }
            val meta = metadata ?: throw BackupRejected("This file is not a BooxDay backup.")
            if (meta.version > BackupData.CURRENT_VERSION) {
                throw BackupRejected("Backup version ${meta.version} is newer than supported version ${BackupData.CURRENT_VERSION}")
            }
            Result.success(
                BackupPreview(
                    metadata = meta,
                    eventCount = counts["localEvents"] ?: 0,
                    taskCount = counts["tasks"] ?: 0,
                    pageCount = counts["pages"] ?: 0,
                    strokeCount = counts["strokes"] ?: 0,
                    calendarCount = counts["localCalendars"] ?: 0,
                    notebookCount = counts["notebooks"] ?: 0,
                    folderCount = counts["folders"] ?: 0,
                    attachmentCount = counts["attachments"] ?: 0,
                    settingCount = counts["settings"] ?: 0,
                    ocrPageCount = counts["ocrPages"] ?: 0,
                    ocrTextCount = counts["ocrTexts"] ?: 0,
                    summaryCount = counts["summaryCache"] ?: 0
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to preview backup: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Restore data from backup JSON held in memory (Google Drive download).
     */
    suspend fun restoreBackup(
        jsonContent: String,
        clearExisting: Boolean = true
    ): ImportResult = restoreBackup({ jsonContent.byteInputStream() }, clearExisting)

    /**
     * Restore data from a backup file.
     *
     * @param source Opens the backup file; called twice (check pass, then import pass)
     * @param clearExisting If true (default), the tables the backup covers are emptied first
     * @return ImportResult indicating success or failure. On failure nothing was changed.
     */
    suspend fun restoreBackup(
        source: () -> InputStream,
        clearExisting: Boolean = true
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting backup restore...")
            val db = databaseProvider()

            // 1. Read and check the whole file before touching the database.
            val check = BackupCheck(db, clearExisting)
            try {
                readBackupFile(source) { key, reader -> check.read(key, reader) }
            } catch (e: IOException) {
                throw BackupRejected("The backup file is damaged or incomplete (${e.message})")
            }
            check.finish()
            Log.i(TAG, "Backup checked: version ${check.version}, sections ${check.present}")

            // 2. One transaction: empty the tables the file covers, then insert every row.
            //    Foreign keys are checked once all rows are in (v2 files list tasks before
            //    pages), and checked explicitly so a violation rolls back instead of failing COMMIT.
            val counts = db.withTransaction {
                val sql = db.openHelper.writableDatabase
                sql.execSQL("PRAGMA defer_foreign_keys = ON")
                // Task sync: restored tasks get only the links in the file. Emptied before any task
                // row goes, so the row-deleted trigger queues no remote deletes; the outbox described
                // the old rows; the device's pull cursors are newer than the restored tasks.
                if ("tasks" in check.present || "taskSyncLinks" in check.present) {
                    val taskSync = db.taskSyncDao()
                    taskSync.clearLinks()
                    taskSync.clearOutbox()
                    taskSync.resetPullCursors()
                }
                if (clearExisting) {
                    for (section in CLEAR_ORDER) {
                        if (section in check.present) {
                            sql.execSQL("DELETE FROM `${SECTION_TABLES.getValue(section)}`")
                        }
                    }
                }
                val writer = BackupWriter(db)
                readBackupFile(source) { key, reader -> writer.read(key, reader) }
                for (section in check.present) {
                    sql.query("PRAGMA foreign_key_check(`${SECTION_TABLES.getValue(section)}`)").use { cursor ->
                        if (cursor.moveToFirst()) {
                            throw IllegalStateException(
                                "Restored ${cursor.getString(0)} rows reference missing ${cursor.getString(2)} rows"
                            )
                        }
                    }
                }
                writer.counts
            }

            Log.i(TAG, "Backup restore complete: $counts")
            ImportResult.Success(
                eventsRestored = counts["localEvents"] ?: 0,
                tasksRestored = counts["tasks"] ?: 0,
                pagesRestored = counts["pages"] ?: 0,
                strokesRestored = counts["strokes"] ?: 0,
                notebooksRestored = counts["notebooks"] ?: 0,
                foldersRestored = counts["folders"] ?: 0,
                attachmentsRestored = counts["attachments"] ?: 0,
                settingsRestored = counts["settings"] ?: 0,
                ocrPagesRestored = counts["ocrPages"] ?: 0,
                ocrTextsRestored = counts["ocrTexts"] ?: 0,
                summariesRestored = counts["summaryCache"] ?: 0
            )
        } catch (e: BackupRejected) {
            Log.e(TAG, "Backup not restored: ${e.message}")
            ImportResult.Error(e.message ?: "The backup can't be restored")
        } catch (e: Exception) {
            Log.e(TAG, "Backup restore failed: ${e.message}", e)
            ImportResult.Error(e.message ?: "Unknown error during restore")
        }
    }

    /**
     * Pass 1. Decodes every row and records what the import pass will need, without writing.
     */
    private class BackupCheck(private val db: AppDatabase, private val clearExisting: Boolean) {
        val present = LinkedHashSet<String>()
        var version = 0
            private set
        private var sawMetadata = false
        private val ids = HashMap<String, HashSet<String>>()
        private val references = HashMap<String, HashSet<String>>()
        private val taskSlots = HashSet<String>()
        private val exceptionSlots = HashSet<String>()
        private val googleEventIds = HashSet<String>()
        private val remoteTaskIds = HashSet<String>()

        fun read(key: String, reader: JsonReader) {
            if (key == "metadata") {
                version = decodeValue(reader, BackupMetadata.serializer()).version
                sawMetadata = true
                return
            }
            if (key !in SECTION_TABLES) {
                reader.skipValue()
                return
            }
            present += key
            when (key) {
                "folders" -> forEachElement(reader, key, BackupFolder.serializer()) {
                    id(key, it.id); ref("folders", it.parentFolderId)
                }
                "notebooks" -> forEachElement(reader, key, BackupNotebook.serializer()) {
                    id(key, it.id); ref("folders", it.parentFolderId)
                }
                "pages" -> forEachElement(reader, key, BackupPage.serializer()) {
                    id(key, it.id); ref("notebooks", it.notebookId); ref("folders", it.parentFolderId)
                }
                "dayPages" -> forEachElement(reader, key, BackupDayPage.serializer()) { id(key, it.date) }
                "recurringTaskTemplates" -> forEachElement(reader, key, BackupRecurringTaskTemplate.serializer()) {
                    id(key, it.id)
                }
                "recurringTaskExceptions" -> forEachElement(reader, key, BackupRecurringTaskException.serializer()) {
                    id(key, it.id)
                    ref("recurringTaskTemplates", it.templateId)
                    if (!exceptionSlots.add("${it.templateId}|${it.occurrenceDate}")) {
                        throw BackupRejected("The backup has two exceptions for repeating task '${it.templateId}' on ${it.occurrenceDate}.")
                    }
                }
                "tasks" -> forEachElement(reader, key, BackupTask.serializer()) {
                    id(key, it.id)
                    ref("pages", it.pageId)
                    if (!taskSlots.add("${it.dayDate}|${it.taskPageIndex}|${it.rowIndex}")) {
                        throw BackupRejected("The backup has two tasks in row ${it.rowIndex + 1} of ${it.dayDate} page ${it.taskPageIndex + 1}.")
                    }
                }
                "taskSyncLinks" -> forEachElement(reader, key, BackupTaskSyncLink.serializer()) {
                    id(key, it.taskId)
                    ref("tasks", it.taskId)
                    if (!remoteTaskIds.add("${it.provider}|${it.accountEmail}|${it.serverUrl}|${it.listId}|${it.remoteId}")) {
                        throw BackupRejected("The backup links two tasks to the same synced task '${it.remoteId}'.")
                    }
                }
                "strokes" -> forEachElement(reader, key, BackupStroke.serializer()) {
                    id(key, it.id)
                    ref("pages", it.pageId)
                    val points = decodePoints(it)
                    try {
                        encodeStrokePoints(points)
                    } catch (e: Exception) {
                        throw BackupRejected("Stroke '${it.id}' can't be stored: ${e.message}")
                    }
                }
                "textAnnotations" -> forEachElement(reader, key, BackupTextAnnotation.serializer()) {
                    id(key, it.id); ref("pages", it.pageId)
                }
                "images" -> forEachElement(reader, key, BackupImage.serializer()) {
                    id(key, it.id); ref("pages", it.pageId)
                }
                "attachments" -> forEachElement(reader, key, BackupAttachment.serializer()) {
                    id(key, it.id); ref("pages", it.pageId)
                }
                "localCalendars" -> forEachElement(reader, key, BackupLocalCalendar.serializer()) { id(key, it.id) }
                "localEvents" -> forEachElement(reader, key, BackupLocalEvent.serializer()) {
                    id(key, it.id)
                    if (SyncStatus.entries.none { status -> status.name == it.syncStatus }) {
                        throw BackupRejected("Event '${it.id}' has an unknown sync status '${it.syncStatus}'.")
                    }
                    val googleId = it.googleEventId
                    if (googleId != null && !googleEventIds.add(googleId)) {
                        throw BackupRejected("The backup has two events linked to Google event '$googleId'.")
                    }
                }
                "settings" -> forEachElement(reader, key, BackupSetting.serializer()) { id(key, it.key) }
                "ocrPages" -> forEachElement(reader, key, BackupOcrPage.serializer()) { id(key, it.id) }
                "ocrTexts" -> forEachElement(reader, key, BackupOcrText.serializer()) { id(key, it.id) }
                "summaryCache" -> forEachElement(reader, key, BackupSummary.serializer()) { id(key, it.id) }
            }
        }

        fun finish() {
            if (!sawMetadata) throw BackupRejected("This file is not a BooxDay backup.")
            if (version > BackupData.CURRENT_VERSION) {
                throw BackupRejected(
                    "Backup version $version is newer than this app supports (${BackupData.CURRENT_VERSION}). Please update BooxDay."
                )
            }
            for ((parent, referenced) in references) {
                val inFile = ids[parent].orEmpty()
                // Rows of a table the restore doesn't replace stay in the database.
                val tableIsKept = !clearExisting || parent !in present
                for (id in referenced) {
                    if (id in inFile) continue
                    if (tableIsKept && existsInDatabase(parent, id)) continue
                    throw BackupRejected("The backup is incomplete: it refers to ${label(parent)} '$id', which isn't in the file.")
                }
            }
            if (clearExisting) {
                for ((parent, child, column) in CASCADES) {
                    if (parent in present && child !in present && hasRows(child, column)) {
                        throw BackupRejected(
                            "This backup has no ${label(child)} data; restoring it would delete the ${label(child)}s on this device."
                        )
                    }
                }
            }
        }

        private fun id(section: String, value: String) {
            if (!ids.getOrPut(section) { HashSet() }.add(value)) {
                throw BackupRejected("The backup lists ${label(section)} '$value' twice.")
            }
        }

        private fun ref(parentSection: String, value: String?) {
            if (value != null) references.getOrPut(parentSection) { HashSet() }.add(value)
        }

        private fun existsInDatabase(section: String, id: String): Boolean =
            db.query("SELECT EXISTS(SELECT 1 FROM `${SECTION_TABLES.getValue(section)}` WHERE id = ?)", arrayOf<Any?>(id))
                .use { it.moveToFirst() && it.getInt(0) == 1 }

        private fun hasRows(section: String, column: String): Boolean =
            db.query("SELECT EXISTS(SELECT 1 FROM `${SECTION_TABLES.getValue(section)}` WHERE `$column` IS NOT NULL)", null)
                .use { it.moveToFirst() && it.getInt(0) == 1 }
    }

    /**
     * Pass 2. Inserts every row; runs inside the restore transaction.
     */
    private class BackupWriter(private val db: AppDatabase) {
        val counts = HashMap<String, Int>()
        private val strokeBatch = ArrayList<Stroke>(STROKE_INSERT_BATCH)

        suspend fun read(key: String, reader: JsonReader) {
            if (key !in SECTION_TABLES) {
                reader.skipValue()  // metadata, unknown keys
                return
            }
            val inserted = when (key) {
                "folders" -> forEachElement(reader, key, BackupFolder.serializer()) { db.folderDao().create(it.toEntity()) }
                "notebooks" -> forEachElement(reader, key, BackupNotebook.serializer()) { db.notebookDao().create(it.toEntity()) }
                "pages" -> forEachElement(reader, key, BackupPage.serializer()) { db.pageDao().create(it.toEntity()) }
                "dayPages" -> forEachElement(reader, key, BackupDayPage.serializer()) { db.dayPageDao().insert(it.toEntity()) }
                "recurringTaskTemplates" -> forEachElement(reader, key, BackupRecurringTaskTemplate.serializer()) {
                    db.recurringTaskTemplateDao().insert(it.toEntity())
                }
                "recurringTaskExceptions" -> forEachElement(reader, key, BackupRecurringTaskException.serializer()) {
                    db.recurringTaskExceptionDao().insert(it.toEntity())
                }
                "tasks" -> forEachElement(reader, key, BackupTask.serializer()) { db.taskDao().insert(it.toEntity()) }
                "taskSyncLinks" -> forEachElement(reader, key, BackupTaskSyncLink.serializer()) {
                    db.taskSyncDao().insertLink(it.toEntity())
                }
                "strokes" -> {
                    val n = forEachElement(reader, key, BackupStroke.serializer()) {
                        strokeBatch += it.toEntity()
                        if (strokeBatch.size >= STROKE_INSERT_BATCH) flushStrokes()
                    }
                    flushStrokes()
                    n
                }
                "textAnnotations" -> forEachElement(reader, key, BackupTextAnnotation.serializer()) {
                    db.textAnnotationDao().insert(it.toEntity())
                }
                "images" -> forEachElement(reader, key, BackupImage.serializer()) { db.ImageDao().create(it.toEntity()) }
                "attachments" -> forEachElement(reader, key, BackupAttachment.serializer()) { db.attachmentDao().create(it.toEntity()) }
                "localCalendars" -> forEachElement(reader, key, BackupLocalCalendar.serializer()) {
                    db.localCalendarDao().insert(it.toEntity())
                }
                "localEvents" -> forEachElement(reader, key, BackupLocalEvent.serializer()) { db.localEventDao().insert(it.toEntity()) }
                "settings" -> forEachElement(reader, key, BackupSetting.serializer()) { db.kvDao().set(Kv(key = it.key, value = it.value)) }
                "ocrPages" -> forEachElement(reader, key, BackupOcrPage.serializer()) { db.unifiedOcrDao().upsertOcrPage(it.toEntity()) }
                "ocrTexts" -> forEachElement(reader, key, BackupOcrText.serializer()) { db.unifiedOcrDao().upsertOcrText(it.toEntity()) }
                "summaryCache" -> forEachElement(reader, key, BackupSummary.serializer()) { db.summaryCacheDao().save(it.toEntity()) }
                else -> error("No importer for section $key")
            }
            counts[key] = (counts[key] ?: 0) + inserted
        }

        private fun flushStrokes() {
            if (strokeBatch.isEmpty()) return
            db.strokeDao().create(strokeBatch.toList())
            strokeBatch.clear()
        }
    }
}

// ========== Streaming JSON helpers ==========

/** Reads the top-level object of a backup file, handing each member to [onEntry], which must consume it. */
private suspend fun readBackupFile(
    source: () -> InputStream,
    onEntry: suspend (key: String, reader: JsonReader) -> Unit
) {
    source().use { input ->
        val reader = JsonReader(InputStreamReader(input.buffered(), Charsets.UTF_8))
        reader.isLenient = true
        reader.beginObject()
        while (reader.hasNext()) {
            onEntry(reader.nextName(), reader)
        }
        reader.endObject()
    }
}

/** Decodes the elements of one section array one at a time. Returns how many there were. */
private inline fun <T> forEachElement(
    reader: JsonReader,
    section: String,
    serializer: KSerializer<T>,
    action: (T) -> Unit
): Int {
    var count = 0
    reader.beginArray()
    while (reader.hasNext()) {
        val element = readJsonElement(reader)
        val item = try {
            json.decodeFromJsonElement(serializer, element)
        } catch (e: SerializationException) {
            throw BackupRejected("${label(section).replaceFirstChar { it.uppercase() }} #${count + 1} can't be read: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw BackupRejected("${label(section).replaceFirstChar { it.uppercase() }} #${count + 1} can't be read: ${e.message}")
        }
        action(item)
        count++
    }
    reader.endArray()
    return count
}

private fun <T> decodeValue(reader: JsonReader, serializer: KSerializer<T>): T = try {
    json.decodeFromJsonElement(serializer, readJsonElement(reader))
} catch (e: SerializationException) {
    throw BackupRejected("The backup header can't be read: ${e.message}")
}

private fun countElements(reader: JsonReader): Int {
    var count = 0
    reader.beginArray()
    while (reader.hasNext()) {
        reader.skipValue()
        count++
    }
    reader.endArray()
    return count
}

/** One JSON value from the stream as a kotlinx JsonElement (numbers keep their exact text). */
private fun readJsonElement(reader: JsonReader): JsonElement = when (reader.peek()) {
    JsonToken.BEGIN_OBJECT -> {
        val members = LinkedHashMap<String, JsonElement>()
        reader.beginObject()
        while (reader.hasNext()) members[reader.nextName()] = readJsonElement(reader)
        reader.endObject()
        JsonObject(members)
    }
    JsonToken.BEGIN_ARRAY -> {
        val items = ArrayList<JsonElement>()
        reader.beginArray()
        while (reader.hasNext()) items += readJsonElement(reader)
        reader.endArray()
        JsonArray(items)
    }
    JsonToken.STRING -> JsonPrimitive(reader.nextString())
    JsonToken.NUMBER -> JsonPrimitive(reader.nextString().toBigDecimal())
    JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
    JsonToken.NULL -> {
        reader.nextNull()
        JsonNull
    }
    else -> throw BackupRejected("Unexpected ${reader.peek()} at ${reader.path}")
}

private fun decodePoints(stroke: BackupStroke): List<StrokePoint> {
    val points = try {
        strokePointsJson.decodeFromString(ListSerializer(StrokePoint.serializer()), stroke.pointsJson)
    } catch (e: Exception) {
        throw BackupRejected("Stroke '${stroke.id}' has unreadable points: ${e.message}")
    }
    if (points.isEmpty()) throw BackupRejected("Stroke '${stroke.id}' has no points.")
    return points
}

// ========== Row mapping (every column) ==========

private fun BackupFolder.toEntity() = Folder(
    id = id,
    title = title,
    parentFolderId = parentFolderId,
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
)

private fun BackupNotebook.toEntity() = Notebook(
    id = id,
    title = title,
    openPageId = openPageId,
    pageIds = pageIds,
    parentFolderId = parentFolderId,
    defaultBackground = defaultBackground,
    defaultBackgroundType = defaultBackgroundType,
    linkedExternalUri = linkedExternalUri,
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
)

private fun BackupPage.toEntity() = Page(
    id = id,
    notebookId = notebookId,
    scroll = scroll,
    background = background,
    backgroundType = backgroundType,
    parentFolderId = parentFolderId,
    linkedEventId = linkedEventId,
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
)

private fun BackupDayPage.toEntity(): DayPage {
    val restoredAt = Date()
    return DayPage(
        date = date,
        pageIds = pageIds,
        openPageId = openPageId,
        createdAt = createdAt?.let { Date(it) } ?: restoredAt,
        updatedAt = updatedAt?.let { Date(it) } ?: restoredAt
    )
}

private fun BackupRecurringTaskTemplate.toEntity() = RecurringTaskTemplate(
    id = id,
    title = title,
    defaultPriority = defaultPriority,
    defaultEstimatedMinutes = defaultEstimatedMinutes,
    recurrenceType = recurrenceType,
    daysOfWeek = daysOfWeek,
    dayOfMonth = dayOfMonth,
    intervalDays = intervalDays,
    anchorDate = anchorDate,
    endDate = endDate,
    isActive = isActive,
    isHabit = isHabit,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun BackupRecurringTaskException.toEntity() = RecurringTaskException(
    id = id,
    templateId = templateId,
    occurrenceDate = occurrenceDate,
    exceptionType = exceptionType,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun BackupTask.toEntity() = Task(
    id = id,
    dayDate = dayDate,
    taskPageIndex = taskPageIndex,
    rowIndex = rowIndex,
    pageId = pageId,
    isCompleted = isCompleted,
    priority = priority,
    typedText = typedText,
    recognizedText = recognizedText,
    convertedText = convertedText,
    parentTaskId = parentTaskId,
    migrationCount = migrationCount,
    originalDate = originalDate,
    lastMigratedAt = lastMigratedAt,
    estimatedMinutes = estimatedMinutes,
    // v4 files carry dueDay; <= v3 files carry epoch-ms dueDate (converted in this zone)
    dueDay = dueDay ?: TaskDueDates.fromMillis(dueDate),
    reminderMinuteOfDay = reminderMinuteOfDay,
    templateId = templateId,
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
)

private fun BackupTaskSyncLink.toEntity() = TaskSyncLink(
    taskId = taskId,
    provider = provider,
    accountEmail = accountEmail,
    serverUrl = serverUrl,
    listId = listId,
    remoteId = remoteId,
    remoteVersion = remoteVersion,
    syncedTitle = syncedTitle,
    syncedCompleted = syncedCompleted,
    syncedDueDay = syncedDueDay,
    syncedParentRemoteId = syncedParentRemoteId,
    contentHash = contentHash,
    remoteUndated = remoteUndated,
    orphanedAt = orphanedAt,
    syncedAt = syncedAt,
    // A v30 backup has no remoteParentId: the two were the same then (MIGRATION_30_31)
    remoteParentId = remoteParentId ?: syncedParentRemoteId,
    keptOnTablet = keptOnTablet,
    keptReason = keptReason
)

private fun BackupStroke.toEntity() = Stroke(
    id = id,
    pageId = pageId,
    // Unknown pen names (e.g. from a newer app) fall back to the ballpoint, as before.
    pen = runCatching { Pen.valueOf(pen) }.getOrDefault(Pen.BALLPEN),
    color = color,
    size = size,
    maxPressure = maxPressure,
    top = top,
    bottom = bottom,
    left = left,
    right = right,
    points = decodePoints(this),
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
)

private fun BackupTextAnnotation.toEntity() = TextAnnotation(
    id = id,
    pageId = pageId,
    content = content,
    spans = spans,
    sourceFormat = sourceFormat,
    sourceType = sourceType,
    sourceStrokeIds = sourceStrokeIds,
    recognitionLanguage = recognitionLanguage,
    recognitionConfidence = recognitionConfidence,
    x = x,
    y = y,
    width = width,
    height = height,
    fontSize = fontSize,
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontStyle = fontStyle,
    textColor = textColor,
    backgroundColor = backgroundColor,
    textAlign = textAlign,
    padding = padding,
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
)

private fun BackupImage.toEntity() = Image(
    id = id,
    pageId = pageId,
    uri = uri,
    x = x,
    y = y,
    width = width,
    height = height,
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
)

private fun BackupAttachment.toEntity() = Attachment(
    id = id,
    pageId = pageId,
    x = x,
    y = y,
    width = width,
    height = height,
    filePath = filePath,
    displayName = displayName,
    fileType = fileType,
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt)
)

private fun BackupLocalCalendar.toEntity() = LocalCalendar(
    id = id,
    name = name,
    colorHex = colorHex,
    sortOrder = sortOrder,
    isDefault = isDefault,
    createdAt = createdAt
)

private fun BackupLocalEvent.toEntity() = LocalEvent(
    id = id,
    title = title,
    dayId = dayId,
    // v1/v2 files have no end day; "" means "same as dayId" elsewhere in the app too.
    endDayId = endDayId?.takeIf { it.isNotEmpty() } ?: dayId,
    startTime = startTime,
    endTime = endTime,
    isAllDay = isAllDay,
    location = location,
    description = description,
    colorHex = colorHex,
    attendees = attendees,
    syncEnabled = syncEnabled,
    syncStatus = SyncStatus.valueOf(syncStatus),
    googleEventId = googleEventId,
    googleCalendarId = googleCalendarId,
    googleCalendarName = googleCalendarName,
    providerId = providerId,
    accountId = accountId,
    accountEmail = accountEmail,
    providerEventId = providerEventId,
    providerCalendarId = providerCalendarId,
    providerCalendarName = providerCalendarName,
    lastSyncedAt = lastSyncedAt,
    serverEtag = serverEtag,
    isReadOnly = isReadOnly,
    createdAt = createdAt,
    updatedAt = updatedAt,
    linkedMemoPageId = linkedMemoPageId,
    localCalendarId = localCalendarId
)

private fun BackupOcrPage.toEntity() = UnifiedOcrPage(
    id = id,
    sourceType = sourceType,
    sourceId = sourceId,
    pageId = pageId,
    sourceCreatedAt = sourceCreatedAt,
    sourceModifiedAt = sourceModifiedAt,
    ocrVersion = ocrVersion,
    ocrAt = ocrAt,
    status = status,
    failCount = failCount,
    lastError = lastError,
    textHash = textHash,
    semanticHash = semanticHash,
    firstIndexedAt = firstIndexedAt
)

private fun BackupOcrText.toEntity() = UnifiedOcrText(
    id = id,
    sourceType = sourceType,
    sourceId = sourceId,
    pageId = pageId,
    // Raw OCR Output
    text = text,
    textNormalized = textNormalized,
    cleanText = cleanText,
    language = language,
    wordCount = wordCount,
    confidence = confidence,
    // Semantic Bundle
    docStyle = docStyle,
    keywordsJson = keywordsJson,
    keyPhrasesJson = keyPhrasesJson,
    keySentencesJson = keySentencesJson,
    entitiesJson = entitiesJson,
    actionItemsJson = actionItemsJson,
    temporalSignalsJson = temporalSignalsJson,
    topicsJson = topicsJson,
    synopsis = synopsis,
    // Domain + Intent Classification
    domain = domain,
    domainConfidence = domainConfidence,
    intentsJson = intentsJson,
    subdomainHint = subdomainHint,
    classificationExplainJson = classificationExplainJson,
    // Source Linkage
    sourceTitle = sourceTitle,
    sourcePath = sourcePath,
    sourceDate = sourceDate,
    sourceTimestamp = sourceTimestamp,
    // Versioning
    semanticVersion = semanticVersion,
    updatedAt = updatedAt
)

private fun BackupSummary.toEntity() = SummaryCache(
    id = id,
    type = type,
    dateKey = dateKey,
    rangeStart = rangeStart,
    rangeEnd = rangeEnd,
    taskCount = taskCount,
    completedCount = completedCount,
    lastUpdatedAt = lastUpdatedAt,
    contentHash = contentHash,
    resultJson = resultJson,
    generatedAt = generatedAt,
    validHours = validHours
)
