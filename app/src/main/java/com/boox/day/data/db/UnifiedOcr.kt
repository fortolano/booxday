package com.boox.day.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.model.UnifiedOcrStatus

/**
 * Tracks OCR processing status for unified page indexing.
 *
 * Supports all three source types:
 * - BOOX_NOTE: Boox Notes app pages
 * - MEMO: Day memo pages
 * - TASK: Day task pages
 *
 * This entity enables:
 * - Incremental indexing (only process changed pages)
 * - Crash recovery (IN_PROGRESS status with stale timestamp = retry)
 * - Version tracking (re-index when OCR algorithm improves)
 * - Failure tracking (max retries before marking FAILED)
 */
@Entity(
    tableName = "unified_ocr_page",
    indices = [
        Index(value = ["sourceType"]),
        Index(value = ["sourceId"]),
        Index(value = ["status"]),
        Index(value = ["ocrAt"]),
        Index(value = ["status", "failCount"]),  // For efficient retry queries
        Index(value = ["sourceType", "status"])   // For per-type statistics
    ]
)
data class UnifiedOcrPage(
    /**
     * Composite key: "{sourceType}_{sourceId}_{pageId}"
     * Example: "BOOX_NOTE_abc123_page1", "MEMO_2026-01-01_page1"
     */
    @PrimaryKey
    val id: String,

    /**
     * Source type (BOOX_NOTE, MEMO, TASK)
     */
    val sourceType: String,  // Stored as string for Room compatibility

    /**
     * Source identifier:
     * - BOOX_NOTE: noteId (unique note identifier)
     * - MEMO: dayDate (yyyy-MM-dd)
     * - TASK: dayDate (yyyy-MM-dd)
     */
    val sourceId: String,

    /**
     * Page identifier within the source
     */
    val pageId: String,

    /**
     * When the source was created (epoch ms)
     */
    val sourceCreatedAt: Long,

    /**
     * When the source was last modified (epoch ms)
     * Used for change detection
     */
    val sourceModifiedAt: Long,

    /**
     * OCR algorithm version (for re-indexing on upgrade)
     */
    val ocrVersion: Int = 1,

    /**
     * When OCR was performed (epoch ms)
     */
    val ocrAt: Long,

    /**
     * Current processing status
     */
    val status: String = UnifiedOcrStatus.PENDING.name,  // Stored as string

    /**
     * Number of failed OCR attempts
     */
    val failCount: Int = 0,

    /**
     * Last error message (for debugging)
     */
    val lastError: String? = null,

    /**
     * Hash of extracted text (for change detection)
     */
    val textHash: String? = null,

    /**
     * Hash of semantic bundle (for change detection)
     */
    val semanticHash: String? = null,

    /**
     * When this page was FIRST indexed (epoch ms).
     * Used to distinguish new pages (costs budget) vs re-indexed pages (free).
     * 0 = never indexed before, >0 = has been indexed.
     */
    val firstIndexedAt: Long = 0L
) {
    /**
     * Get the OcrSource enum value
     */
    fun getSourceType(): OcrSource = OcrSource.fromString(sourceType) ?: OcrSource.BOOX_NOTE

    /**
     * Get the UnifiedOcrStatus enum value
     */
    fun getStatus(): UnifiedOcrStatus = UnifiedOcrStatus.fromString(status) ?: UnifiedOcrStatus.PENDING

    companion object {
        /**
         * Generate composite ID from components
         */
        fun generateId(sourceType: OcrSource, sourceId: String, pageId: String): String {
            return "${sourceType.name}_${sourceId}_${pageId}"
        }

        /**
         * Create new page entry
         */
        fun create(
            sourceType: OcrSource,
            sourceId: String,
            pageId: String,
            sourceCreatedAt: Long,
            sourceModifiedAt: Long
        ): UnifiedOcrPage {
            return UnifiedOcrPage(
                id = generateId(sourceType, sourceId, pageId),
                sourceType = sourceType.name,
                sourceId = sourceId,
                pageId = pageId,
                sourceCreatedAt = sourceCreatedAt,
                sourceModifiedAt = sourceModifiedAt,
                ocrAt = System.currentTimeMillis(),
                status = UnifiedOcrStatus.PENDING.name
            )
        }
    }
}

/**
 * Stores extracted OCR text and semantic bundle for a page.
 *
 * Separated from UnifiedOcrPage to:
 * - Keep status tracking lightweight
 * - Enable efficient text search via FTS4 virtual table
 * - Allow text updates without touching status
 */
@Entity(
    tableName = "unified_ocr_text",
    indices = [
        Index(value = ["sourceType"]),
        Index(value = ["sourceId"]),
        Index(value = ["pageId"]),
        Index(value = ["domain"])  // For domain-based filtering (WORK, SCHOOL, etc.)
    ]
)
data class UnifiedOcrText(
    /**
     * Composite key: matches UnifiedOcrPage.id
     * "{sourceType}_{sourceId}_{pageId}"
     */
    @PrimaryKey
    val id: String,

    /**
     * Source type (BOOX_NOTE, MEMO, TASK)
     */
    val sourceType: String,

    /**
     * Source identifier
     */
    val sourceId: String,

    /**
     * Page identifier
     */
    val pageId: String,

    // ═══════════════════════════════════════════════════════════════
    // Raw OCR Output
    // ═══════════════════════════════════════════════════════════════

    /**
     * Raw extracted text from Cloud Vision
     */
    val text: String,

    /**
     * Normalized text for search (lowercase, NFKC, whitespace collapsed)
     */
    val textNormalized: String,

    /**
     * Clean text after Step 1 processing (garbage removed, lines deduped)
     */
    val cleanText: String,

    /**
     * Detected language code (e.g., "en", "pt") - null if unknown
     */
    val language: String? = null,

    /**
     * Word count for statistics
     */
    val wordCount: Int,

    /**
     * OCR confidence (0.0-1.0)
     */
    val confidence: Float,

    // ═══════════════════════════════════════════════════════════════
    // Semantic Bundle (JSON serialized)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Document style (MEETING_NOTES, CHECKLIST, etc.)
     */
    val docStyle: String? = null,

    /**
     * Keywords with scores [{term, score}] JSON
     */
    val keywordsJson: String? = null,

    /**
     * Key phrases [String] JSON
     */
    val keyPhrasesJson: String? = null,

    /**
     * Key sentences [String] JSON
     */
    val keySentencesJson: String? = null,

    /**
     * Entities [{text, type, confidence}] JSON
     */
    val entitiesJson: String? = null,

    /**
     * Action items [{verb, object, dueDate, confidence}] JSON
     */
    val actionItemsJson: String? = null,

    /**
     * Temporal signals {dates, times, relative, weekdays} JSON
     */
    val temporalSignalsJson: String? = null,

    /**
     * Topics [{topic, score}] JSON
     */
    val topicsJson: String? = null,

    /**
     * Structured synopsis for Smart Link (max 900 chars)
     */
    val synopsis: String? = null,

    // ═══════════════════════════════════════════════════════════════
    // Domain + Intent Classification (SemanticGenius v1)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Primary domain classification (e.g., "WORK", "SCHOOL", "LDS_LEADERSHIP")
     * Matches Domain enum in DomainIntent.kt
     */
    val domain: String? = null,

    /**
     * Domain classification confidence (0.0-1.0)
     */
    val domainConfidence: Float = 0f,

    /**
     * Intent classifications [{intent, confidence}] JSON array
     * Up to 4 intents sorted by confidence
     */
    val intentsJson: String? = null,

    /**
     * Subdomain hint for SPIRITUAL domain (e.g., "lds", "christian", "islamic")
     */
    val subdomainHint: String? = null,

    /**
     * Full classification explanation JSON for debugging/transparency
     * Contains scoring breakdown, evidence, and decision trace
     */
    val classificationExplainJson: String? = null,

    // ═══════════════════════════════════════════════════════════════
    // Source Linkage
    // ═══════════════════════════════════════════════════════════════

    /**
     * Source title (note title, day name, etc.)
     */
    val sourceTitle: String? = null,

    /**
     * Source path (for Boox Notes)
     */
    val sourcePath: String? = null,

    /**
     * Source date (yyyy-MM-dd format)
     */
    val sourceDate: String? = null,

    /**
     * Source timestamp (epoch ms) - used for events to store start time.
     * Enables temporal queries and duplicate detection (same title within 5 minutes).
     * Null for bitmap sources (notes/memos/tasks).
     */
    val sourceTimestamp: Long? = null,

    // ═══════════════════════════════════════════════════════════════
    // Versioning
    // ═══════════════════════════════════════════════════════════════

    /**
     * Semantic extraction version
     */
    val semanticVersion: Int = 1,

    /**
     * When this text was last updated (epoch ms)
     */
    val updatedAt: Long
) {
    companion object {
        /**
         * Generate composite ID from components
         */
        fun generateId(sourceType: OcrSource, sourceId: String, pageId: String): String {
            return "${sourceType.name}_${sourceId}_${pageId}"
        }
    }
}

/**
 * Search result from FTS4 full-text search.
 */
data class UnifiedOcrSearchResult(
    val id: String,
    val sourceType: String,
    val sourceId: String,
    val pageId: String,
    val sourceTitle: String?,
    val sourceDate: String?,
    val snippet: String,
    val score: Double,
    val domain: String? = null,
    val wordCount: Int = 0,
    val confidence: Float = 0f
)

/**
 * OCR statistics for UI display.
 *
 * Note: Event stats are tracked separately since events don't use Cloud Vision OCR.
 * Events are text-only indexed directly from their title/description.
 */
data class UnifiedOcrStatistics(
    // OCR-based sources (use Cloud Vision)
    val booxNotesScanned: Int = 0,
    val booxNotesPending: Int = 0,
    val memosScanned: Int = 0,
    val memosPending: Int = 0,
    val tasksScanned: Int = 0,
    val tasksPending: Int = 0,
    val totalFailed: Int = 0,

    // Event sources (text-only, no OCR)
    val calendarEventsIndexed: Int = 0,
    val localEventsIndexed: Int = 0,
    val lastEventIndexingTime: Long = 0L
) {
    /** Total OCR-processed pages (excludes events) */
    val totalIndexed: Int get() = booxNotesScanned + memosScanned + tasksScanned

    /** Total pending OCR pages (excludes events - they're indexed immediately) */
    val totalPending: Int get() = booxNotesPending + memosPending + tasksPending

    /** Total events indexed (calendar + local) */
    val totalEventsIndexed: Int get() = calendarEventsIndexed + localEventsIndexed
}

/**
 * Per-type statistics result from DAO query.
 */
data class OcrTypeStats(
    val sourceType: String,
    val scanned: Int,
    val pending: Int,
    val failed: Int
)
