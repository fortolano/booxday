package com.boox.day.sync

import android.os.Build
import kotlinx.serialization.Serializable

/**
 * Backup schema for BooxDay app data.
 *
 * Version history:
 * - v1: Initial backup format (Dec 2025)
 * - v2: Extended coverage (Jan 2026)
 *   - Added: notebooks, folders, attachments, settings, summaryCache
 *   - Added: FULL OCR data (ocrPages + ocrTexts) to avoid expensive re-OCR via Cloud Vision API
 * - v3: Every column of the backed-up tables (Oct 2026)
 *   - Tasks: due date, subtask parent, repeat template, recognized text, rollover fields, estimate
 *   - Events: end day (multi-day) and sync metadata, so restored events show and don't re-sync as duplicates
 *   - Day pages: timestamps. Summary cache: lastUpdatedAt
 *   - Added: recurringTaskTemplates + recurringTaskExceptions
 *   - Written and read as a stream (ExportService/ImportService), never as one in-memory object
 *
 * Restore replaces only the tables whose section is present in the file. Device-only data is not
 * backed up and is left untouched by a restore: connected accounts + calendar selections (sign-in
 * is per device), the provider calendar cache (re-synced), Boox Notes index/links/aggregates
 * (rebuilt from the Boox notes), learned analytics (entity co-occurrence, activity patterns) and
 * What's New state.
 *
 * Older files decode with the defaults below (v2 events get endDayId = dayId on import).
 */
@Serializable
data class BackupData(
    val metadata: BackupMetadata,
    val localCalendars: List<BackupLocalCalendar> = emptyList(),
    val localEvents: List<BackupLocalEvent> = emptyList(),
    val tasks: List<BackupTask> = emptyList(),
    val dayPages: List<BackupDayPage> = emptyList(),
    val pages: List<BackupPage> = emptyList(),
    val strokes: List<BackupStroke> = emptyList(),
    val textAnnotations: List<BackupTextAnnotation> = emptyList(),
    val images: List<BackupImage> = emptyList(),
    // v2 additions
    val notebooks: List<BackupNotebook> = emptyList(),
    val folders: List<BackupFolder> = emptyList(),
    val attachments: List<BackupAttachment> = emptyList(),
    val settings: List<BackupSetting> = emptyList(),
    val ocrPages: List<BackupOcrPage> = emptyList(),
    val ocrTexts: List<BackupOcrText> = emptyList(),
    val summaryCache: List<BackupSummary> = emptyList(),
    // v3 additions
    val recurringTaskTemplates: List<BackupRecurringTaskTemplate> = emptyList(),
    val recurringTaskExceptions: List<BackupRecurringTaskException> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 5  // v5: Task.reminderMinuteOfDay; v4: Task.dueDay replaces epoch-ms dueDate
    }
}

@Serializable
data class BackupMetadata(
    val version: Int = BackupData.CURRENT_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersion: String,
    val deviceModel: String = Build.MODEL,
    val deviceManufacturer: String = Build.MANUFACTURER,
    val androidVersion: Int = Build.VERSION.SDK_INT,
    val totalEvents: Int = 0,
    val totalTasks: Int = 0,
    val totalPages: Int = 0,
    val totalStrokes: Int = 0,
    // v2 additions
    val totalNotebooks: Int = 0,
    val totalFolders: Int = 0,
    val totalAttachments: Int = 0,
    val totalOcrPages: Int = 0,
    val totalOcrTexts: Int = 0,
    val totalSummaries: Int = 0,
    // v3 additions
    val totalRecurringTaskTemplates: Int = 0,
    val totalRecurringTaskExceptions: Int = 0
)

// ========== Local Calendars ==========

@Serializable
data class BackupLocalCalendar(
    val id: String,
    val name: String,
    val colorHex: String?,
    val sortOrder: Int,
    val isDefault: Boolean,
    val createdAt: Long
)

// ========== Local Events ==========

@Serializable
data class BackupLocalEvent(
    val id: String,
    val title: String,
    val dayId: String,
    val startTime: Long,
    val endTime: Long?,
    val isAllDay: Boolean,
    val location: String?,
    val description: String?,
    val colorHex: String?,
    val attendees: String?,
    val localCalendarId: String?,
    val linkedMemoPageId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    // v3: end day and sync metadata. v1/v2 files have none of these: those events come back
    // local-only, as before, and endDayId falls back to dayId on import.
    val endDayId: String? = null,
    val syncEnabled: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val googleEventId: String? = null,
    val googleCalendarId: String? = null,
    val googleCalendarName: String? = null,
    val providerId: String = "LOCAL",
    val accountId: String? = null,
    val accountEmail: String? = null,
    val providerEventId: String? = null,
    val providerCalendarId: String? = null,
    val providerCalendarName: String? = null,
    val lastSyncedAt: Long? = null,
    val serverEtag: String? = null,
    val isReadOnly: Boolean = false
)

// ========== Tasks ==========

@Serializable
data class BackupTask(
    val id: String,
    val dayDate: String,
    val taskPageIndex: Int,
    val rowIndex: Int,
    val pageId: String,
    val isCompleted: Boolean,
    val priority: String,
    val typedText: String?,
    val convertedText: String?,
    val createdAt: Long,
    val updatedAt: Long,
    // v3 additions
    val recognizedText: String? = null,
    val parentTaskId: String? = null,
    val migrationCount: Int = 0,
    val originalDate: String? = null,
    val lastMigratedAt: Long? = null,
    val estimatedMinutes: Int? = null,
    val dueDate: Long? = null,  // <= v3 (epoch ms); read on import, no longer written
    val templateId: String? = null,
    // v4 addition: due day "yyyy-MM-dd"
    val dueDay: String? = null,
    // v5 addition: own reminder time, minutes after midnight (null = default)
    val reminderMinuteOfDay: Int? = null
)

// ========== Recurring Tasks (v3) ==========

@Serializable
data class BackupRecurringTaskTemplate(
    val id: String,
    val title: String,
    val defaultPriority: String,
    val defaultEstimatedMinutes: Int?,
    val recurrenceType: String,
    val daysOfWeek: String?,
    val dayOfMonth: Int?,
    val intervalDays: Int,
    val anchorDate: String,
    val endDate: String?,
    val isActive: Boolean,
    val isHabit: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupRecurringTaskException(
    val id: String,
    val templateId: String,
    val occurrenceDate: String,
    val exceptionType: String,
    val createdAt: Long,
    val updatedAt: Long
)

// ========== Day Pages ==========

@Serializable
data class BackupDayPage(
    val date: String,
    val pageIds: List<String>,
    val openPageId: String?,
    // v3 additions (older files: restore time)
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

// ========== Pages ==========

@Serializable
data class BackupPage(
    val id: String,
    val notebookId: String?,
    val scroll: Int,
    val background: String,
    val backgroundType: String,
    val parentFolderId: String?,
    val linkedEventId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

// ========== Strokes ==========

@Serializable
data class BackupStroke(
    val id: String,
    val pageId: String,
    val pen: String,  // Pen enum name
    val color: Int,
    val size: Float,
    val maxPressure: Int,
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
    val pointsJson: String,  // JSON serialized List<StrokePoint>
    val createdAt: Long,
    val updatedAt: Long
)

// ========== Text Annotations ==========

@Serializable
data class BackupTextAnnotation(
    val id: String,
    val pageId: String,
    val content: String,
    val spans: String?,
    val sourceFormat: String,
    val sourceType: String,
    val sourceStrokeIds: String?,
    val recognitionLanguage: String?,
    val recognitionConfidence: Float?,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fontSize: Float,
    val fontFamily: String,
    val fontWeight: Int,
    val fontStyle: String,
    val textColor: Int,
    val backgroundColor: Int,
    val textAlign: String,
    val padding: Float,
    val createdAt: Long,
    val updatedAt: Long
)

// ========== Images ==========

@Serializable
data class BackupImage(
    val id: String,
    val pageId: String,
    val uri: String?,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val updatedAt: Long
)

// ========== Notebooks (v2) ==========

@Serializable
data class BackupNotebook(
    val id: String,
    val title: String,
    val openPageId: String?,
    val pageIds: List<String>,
    val parentFolderId: String?,
    val defaultBackground: String,
    val defaultBackgroundType: String,
    val linkedExternalUri: String?,
    val createdAt: Long,
    val updatedAt: Long
)

// ========== Folders (v2) ==========

@Serializable
data class BackupFolder(
    val id: String,
    val title: String,
    val parentFolderId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

// ========== Attachments (v2) ==========

@Serializable
data class BackupAttachment(
    val id: String,
    val pageId: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val filePath: String,
    val displayName: String,
    val fileType: String,
    val createdAt: Long,
    val updatedAt: Long
)

// ========== Settings (v2) ==========

@Serializable
data class BackupSetting(
    val key: String,
    val value: String
)

// ========== OCR Page Status (v2) ==========
// Full UnifiedOcrPage backup - preserves processing status to avoid re-OCR

@Serializable
data class BackupOcrPage(
    val id: String,
    val sourceType: String,
    val sourceId: String,
    val pageId: String,
    val sourceCreatedAt: Long,
    val sourceModifiedAt: Long,
    val ocrVersion: Int,
    val ocrAt: Long,
    val status: String,
    val failCount: Int,
    val lastError: String?,
    val textHash: String?,
    val semanticHash: String?,
    val firstIndexedAt: Long
)

// ========== OCR Text Data (v2) ==========
// Full UnifiedOcrText backup - ALL fields to avoid expensive re-OCR via Cloud Vision API

@Serializable
data class BackupOcrText(
    val id: String,
    val sourceType: String,
    val sourceId: String,
    val pageId: String,
    // Raw OCR Output
    val text: String,
    val textNormalized: String,
    val cleanText: String,
    val language: String?,
    val wordCount: Int,
    val confidence: Float,
    // Semantic Bundle (JSON serialized)
    val docStyle: String?,
    val keywordsJson: String?,
    val keyPhrasesJson: String?,
    val keySentencesJson: String?,
    val entitiesJson: String?,
    val actionItemsJson: String?,
    val temporalSignalsJson: String?,
    val topicsJson: String?,
    val synopsis: String?,
    // Domain + Intent Classification
    val domain: String?,
    val domainConfidence: Float,
    val intentsJson: String?,
    val subdomainHint: String?,
    val classificationExplainJson: String?,
    // Source Linkage
    val sourceTitle: String?,
    val sourcePath: String?,
    val sourceDate: String?,
    val sourceTimestamp: Long?,
    // Versioning
    val semanticVersion: Int,
    val updatedAt: Long
)

// ========== Summary Cache (v2) ==========

@Serializable
data class BackupSummary(
    val id: String,
    val type: String,
    val dateKey: String,
    val rangeStart: String?,
    val rangeEnd: String?,
    val taskCount: Int?,
    val completedCount: Int?,
    val contentHash: String,
    val resultJson: String,
    val generatedAt: Long,
    val validHours: Int,
    // v3 addition
    val lastUpdatedAt: Long? = null
)
