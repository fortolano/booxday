package com.boox.day.sync

import com.boox.day.data.db.KvProxy
import android.content.Context
import androidx.room.withTransaction
import com.boox.day.BuildConfig
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
import com.boox.day.data.db.SummaryCache
import com.boox.day.data.db.SyncStatus
import com.boox.day.data.db.Task
import com.boox.day.data.db.TaskSyncLink
import com.boox.day.data.db.TextAnnotation
import com.boox.day.data.db.UnifiedOcrPage
import com.boox.day.data.db.UnifiedOcrText
import com.boox.day.data.db.isUndecodable
import com.boox.day.data.getDbDir
import com.google.gson.stream.JsonWriter
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export Service for BooxDay backup (format v3, see BackupSchema.kt).
 *
 * Streams every backed-up table into the JSON file one row at a time (strokes and OCR text in
 * batches), so a long handwriting history never has to fit in memory.
 * The export runs inside one database transaction: a consistent snapshot, so no stroke or task
 * can end up in the file without its page. Writes from the rest of the app wait until it ends.
 * Any failure fails the whole export and removes the partial file - a section is never silently
 * left out.
 */
class ExportService(
    private val context: Context,
    // Test seam: instrumented tests pass an in-memory database. The app always uses the singleton.
    private val databaseProvider: () -> AppDatabase = { AppDatabase.getDatabase(context) }
) {

    companion object {
        private const val TAG = "ExportService"
        private val json = Json {
            encodeDefaults = true
        }

        // JSON encoder for stroke points serialization
        private val strokePointsJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        private const val STROKE_BATCH = 300
        private const val OCR_TEXT_BATCH = 200
    }

    /**
     * Result of export operation.
     */
    sealed class ExportResult {
        data class Success(val backupFile: File, val metadata: BackupMetadata) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    /**
     * Create a full backup of user data in the local backups folder.
     * Returns the backup file and its metadata (row counts).
     */
    suspend fun createBackup(): ExportResult = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val backupDir = File(getDbDir(context), "backups").apply { mkdirs() }
        val backupFile = File(backupDir, "booxday_backup_$timestamp.json")
        val partFile = File(backupDir, "${backupFile.name}.part")
        try {
            Log.i(TAG, "Starting backup export...")
            val db = databaseProvider()

            val metadata = db.withTransaction {
                partFile.bufferedWriter(Charsets.UTF_8).use { out ->
                    JsonWriter(out).use { writer -> writeBackup(db, writer) }
                }
            }
            if (!partFile.renameTo(backupFile)) {
                partFile.copyTo(backupFile, overwrite = true)
            }

            Log.i(TAG, "Backup created: ${backupFile.absolutePath} (${backupFile.length()} bytes)")
            Log.i(
                TAG,
                "  Events: ${metadata.totalEvents}, Tasks: ${metadata.totalTasks}, " +
                    "Pages: ${metadata.totalPages}, Strokes: ${metadata.totalStrokes}, " +
                    "Repeating: ${metadata.totalRecurringTaskTemplates}"
            )
            ExportResult.Success(backupFile, metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Backup export failed: ${e.message}", e)
            ExportResult.Error(e.message ?: "Unknown error during backup")
        } finally {
            if (partFile.exists()) partFile.delete()
        }
    }

    /**
     * Writes the backup object. Parent tables come before their children, so the file can also be
     * read top to bottom; the metadata (row counts) is written last, once the counts are known.
     */
    private suspend fun writeBackup(db: AppDatabase, writer: JsonWriter): BackupMetadata {
        writer.beginObject()

        val folders = writeSection(writer, "folders", BackupFolder.serializer(),
            db.folderDao().getAllFolders().map { it.toBackup() })
        val notebooks = writeSection(writer, "notebooks", BackupNotebook.serializer(),
            db.notebookDao().getAllNotebooks().map { it.toBackup() })
        val pages = writeSection(writer, "pages", BackupPage.serializer(),
            db.pageDao().getAllPages().map { it.toBackup() })
        writeSection(writer, "dayPages", BackupDayPage.serializer(),
            db.dayPageDao().getAllDayPages().map { it.toBackup() })
        val templates = writeSection(writer, "recurringTaskTemplates", BackupRecurringTaskTemplate.serializer(),
            db.recurringTaskTemplateDao().getAll().map { it.toBackup() })
        val exceptions = writeSection(writer, "recurringTaskExceptions", BackupRecurringTaskException.serializer(),
            db.recurringTaskExceptionDao().getAll().map { it.toBackup() })
        val tasks = writeSection(writer, "tasks", BackupTask.serializer(),
            db.taskDao().getAllTasks().map { it.toBackup() })
        writeSection(writer, "taskSyncLinks", BackupTaskSyncLink.serializer(),
            db.taskSyncDao().getAllLinks().map { it.toBackup() })
        val strokes = writeStrokes(db, writer)
        writeSection(writer, "textAnnotations", BackupTextAnnotation.serializer(),
            db.textAnnotationDao().getAllAnnotations().map { it.toBackup() })
        writeSection(writer, "images", BackupImage.serializer(),
            db.ImageDao().getAllImages().map { it.toBackup() })
        val attachments = writeSection(writer, "attachments", BackupAttachment.serializer(),
            db.attachmentDao().getAllAttachments().map { it.toBackup() })
        writeSection(writer, "localCalendars", BackupLocalCalendar.serializer(),
            db.localCalendarDao().getAll().map { it.toBackup() })
        // Events deleted on this device (waiting to be removed from the provider) are not backed up.
        val events = writeSection(writer, "localEvents", BackupLocalEvent.serializer(),
            db.localEventDao().getAllEvents()
                .filter { it.syncStatus != SyncStatus.PENDING_DELETE }
                .map { it.toBackup() })
        writeSection(writer, "settings", BackupSetting.serializer(),
            db.kvDao().getAll().map { KvProxy.withoutSecrets(it).toBackup() })
        val ocrPages = writeSection(writer, "ocrPages", BackupOcrPage.serializer(),
            db.unifiedOcrDao().getAllPages().map { it.toBackup() })
        val ocrTexts = writeOcrTexts(db, writer)
        val summaries = writeSection(writer, "summaryCache", BackupSummary.serializer(),
            db.summaryCacheDao().getAll().map { it.toBackup() })

        val metadata = BackupMetadata(
            version = BackupData.CURRENT_VERSION,
            createdAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            totalEvents = events,
            totalTasks = tasks,
            totalPages = pages,
            totalStrokes = strokes,
            totalNotebooks = notebooks,
            totalFolders = folders,
            totalAttachments = attachments,
            totalOcrPages = ocrPages,
            totalOcrTexts = ocrTexts,
            totalSummaries = summaries,
            totalRecurringTaskTemplates = templates,
            totalRecurringTaskExceptions = exceptions
        )
        writer.name("metadata").jsonValue(json.encodeToString(BackupMetadata.serializer(), metadata))
        writer.endObject()
        return metadata
    }

    private fun <T> writeSection(writer: JsonWriter, name: String, serializer: KSerializer<T>, rows: List<T>): Int {
        writer.name(name).beginArray()
        for (row in rows) {
            writer.jsonValue(json.encodeToString(serializer, row))
        }
        writer.endArray()
        return rows.size
    }

    private fun writeStrokes(db: AppDatabase, writer: JsonWriter): Int {
        writer.name("strokes").beginArray()
        var written = 0
        val undecodable = mutableListOf<String>()
        var afterId = ""
        while (true) {
            val batch = db.strokeDao().getBatchAfter(afterId, STROKE_BATCH)
            if (batch.isEmpty()) break
            for (stroke in batch) {
                // A stroke whose stored points can't be decoded has nothing to write; skip it
                // (its row stays in the database) instead of failing the whole backup.
                if (stroke.isUndecodable) {
                    undecodable += stroke.id
                    continue
                }
                writer.jsonValue(json.encodeToString(BackupStroke.serializer(), stroke.toBackup()))
                written++
            }
            afterId = batch.last().id
        }
        writer.endArray()
        if (undecodable.isNotEmpty()) {
            Log.e(TAG, "Backup skipped ${undecodable.size} undecodable stroke(s): $undecodable")
        }
        return written
    }

    private fun writeOcrTexts(db: AppDatabase, writer: JsonWriter): Int {
        writer.name("ocrTexts").beginArray()
        var written = 0
        var afterId = ""
        while (true) {
            val batch = db.unifiedOcrDao().getTextBatchAfter(afterId, OCR_TEXT_BATCH)
            if (batch.isEmpty()) break
            for (text in batch) {
                writer.jsonValue(json.encodeToString(BackupOcrText.serializer(), text.toBackup()))
                written++
            }
            afterId = batch.last().id
        }
        writer.endArray()
        return written
    }

    // ========== Row mapping (every column) ==========

    private fun Folder.toBackup() = BackupFolder(
        id = id,
        title = title,
        parentFolderId = parentFolderId,
        createdAt = createdAt.time,
        updatedAt = updatedAt.time
    )

    private fun Notebook.toBackup() = BackupNotebook(
        id = id,
        title = title,
        openPageId = openPageId,
        pageIds = pageIds,
        parentFolderId = parentFolderId,
        defaultBackground = defaultBackground,
        defaultBackgroundType = defaultBackgroundType,
        linkedExternalUri = linkedExternalUri,
        createdAt = createdAt.time,
        updatedAt = updatedAt.time
    )

    private fun Page.toBackup() = BackupPage(
        id = id,
        notebookId = notebookId,
        scroll = scroll,
        background = background,
        backgroundType = backgroundType,
        parentFolderId = parentFolderId,
        linkedEventId = linkedEventId,
        createdAt = createdAt.time,
        updatedAt = updatedAt.time
    )

    private fun DayPage.toBackup() = BackupDayPage(
        date = date,
        pageIds = pageIds,
        openPageId = openPageId,
        createdAt = createdAt.time,
        updatedAt = updatedAt.time
    )

    private fun RecurringTaskTemplate.toBackup() = BackupRecurringTaskTemplate(
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

    private fun RecurringTaskException.toBackup() = BackupRecurringTaskException(
        id = id,
        templateId = templateId,
        occurrenceDate = occurrenceDate,
        exceptionType = exceptionType,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Task.toBackup() = BackupTask(
        id = id,
        dayDate = dayDate,
        taskPageIndex = taskPageIndex,
        rowIndex = rowIndex,
        pageId = pageId,
        isCompleted = isCompleted,
        priority = priority,
        typedText = typedText,
        convertedText = convertedText,
        createdAt = createdAt.time,
        updatedAt = updatedAt.time,
        recognizedText = recognizedText,
        parentTaskId = parentTaskId,
        migrationCount = migrationCount,
        originalDate = originalDate,
        lastMigratedAt = lastMigratedAt,
        estimatedMinutes = estimatedMinutes,
        templateId = templateId,
        dueDay = dueDay,
        reminderMinuteOfDay = reminderMinuteOfDay
    )

    private fun TaskSyncLink.toBackup() = BackupTaskSyncLink(
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
        remoteParentId = remoteParentId,
        keptOnTablet = keptOnTablet,
        keptReason = keptReason
    )

    private fun Stroke.toBackup() = BackupStroke(
        id = id,
        pageId = pageId,
        pen = pen.name,  // Convert Pen enum to String
        color = color,
        size = size,
        maxPressure = maxPressure,
        top = top,
        bottom = bottom,
        left = left,
        right = right,
        pointsJson = strokePointsJson.encodeToString(points),
        createdAt = createdAt.time,
        updatedAt = updatedAt.time
    )

    private fun TextAnnotation.toBackup() = BackupTextAnnotation(
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
        createdAt = createdAt.time,
        updatedAt = updatedAt.time
    )

    private fun Image.toBackup() = BackupImage(
        id = id,
        pageId = pageId,
        uri = uri,
        x = x,
        y = y,
        width = width,
        height = height,
        createdAt = createdAt.time,
        updatedAt = updatedAt.time
    )

    private fun Attachment.toBackup() = BackupAttachment(
        id = id,
        pageId = pageId,
        x = x,
        y = y,
        width = width,
        height = height,
        filePath = filePath,
        displayName = displayName,
        fileType = fileType,
        createdAt = createdAt.time,
        updatedAt = updatedAt.time
    )

    private fun LocalCalendar.toBackup() = BackupLocalCalendar(
        id = id,
        name = name,
        colorHex = colorHex,
        sortOrder = sortOrder,
        isDefault = isDefault,
        createdAt = createdAt
    )

    private fun LocalEvent.toBackup() = BackupLocalEvent(
        id = id,
        title = title,
        dayId = dayId,
        startTime = startTime,
        endTime = endTime,
        isAllDay = isAllDay,
        location = location,
        description = description,
        colorHex = colorHex,
        attendees = attendees,
        localCalendarId = localCalendarId,
        linkedMemoPageId = linkedMemoPageId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        endDayId = endDayId,
        syncEnabled = syncEnabled,
        syncStatus = syncStatus.name,
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
        isReadOnly = isReadOnly
    )

    private fun Kv.toBackup() = BackupSetting(
        key = key,
        value = value
    )

    private fun UnifiedOcrPage.toBackup() = BackupOcrPage(
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

    private fun UnifiedOcrText.toBackup() = BackupOcrText(
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

    private fun SummaryCache.toBackup() = BackupSummary(
        id = id,
        type = type,
        dateKey = dateKey,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        taskCount = taskCount,
        completedCount = completedCount,
        contentHash = contentHash,
        resultJson = resultJson,
        generatedAt = generatedAt,
        validHours = validHours,
        lastUpdatedAt = lastUpdatedAt
    )
}
