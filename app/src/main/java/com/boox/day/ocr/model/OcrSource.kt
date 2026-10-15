package com.boox.day.ocr.model

import kotlinx.serialization.Serializable

/**
 * Source type for unified semantic indexing.
 *
 * Bitmap sources (OCR via Cloud Vision):
 * - BOOX_NOTE, MEMO, TASK
 *
 * Text sources (direct classification, no OCR):
 * - CALENDAR_EVENT, LOCAL_EVENT, TEXT_ANNOTATION
 */
@Serializable
enum class OcrSource {
    /** Boox Notes app content - rendered via JNI */
    BOOX_NOTE,

    /** Day memo pages - loaded from persisted bitmap */
    MEMO,

    /** Day task pages - loaded from persisted bitmap */
    TASK,

    /** Calendar events synced from Google/Outlook - text only, no OCR */
    CALENDAR_EVENT,

    /** Local events created in-app - text only, no OCR */
    LOCAL_EVENT,

    /** Text annotations on memo pages (voice, handwriting, typed) - text only, no OCR */
    TEXT_ANNOTATION;

    companion object {
        fun fromString(value: String): OcrSource? = entries.find { it.name == value }

        /**
         * Bitmap sources that require Cloud Vision OCR processing.
         */
        val BITMAP_SOURCES = setOf(BOOX_NOTE, MEMO, TASK)

        /**
         * Text sources that don't require OCR (events, text annotations).
         */
        val TEXT_SOURCES = setOf(CALENDAR_EVENT, LOCAL_EVENT, TEXT_ANNOTATION)
    }

    /**
     * Check if this source requires bitmap/OCR processing.
     */
    fun isBitmapSource(): Boolean = this in BITMAP_SOURCES

    /**
     * Check if this source is text-only (no OCR needed).
     */
    fun isTextSource(): Boolean = this in TEXT_SOURCES

    /**
     * Check if this source represents an event (calendar or local).
     */
    fun isEventSource(): Boolean = this == CALENDAR_EVENT || this == LOCAL_EVENT
}

/**
 * Processing status for OCR pages.
 */
@Serializable
enum class UnifiedOcrStatus {
    /** Page queued for OCR processing */
    PENDING,

    /** OCR currently in progress (crash recovery marker) */
    IN_PROGRESS,

    /** OCR completed successfully with text extracted */
    SUCCESS,

    /** OCR failed after max retries */
    FAILED,

    /** OCR completed but no text found (blank/drawing-only page) */
    EMPTY,

    /** Page explicitly excluded from indexing */
    EXCLUDED,

    /** Low signal - text too short for meaningful extraction */
    LOW_SIGNAL,

    /** Permanently failed after 3+ attempts - do not retry */
    QUARANTINED;

    companion object {
        fun fromString(value: String): UnifiedOcrStatus? = entries.find { it.name == value }
    }
}

/**
 * Document style classification for semantic extraction.
 */
@Serializable
enum class DocumentStyle {
    /** Meeting notes with timestamps and attendees */
    MEETING_NOTES,

    /** Bulleted/numbered checklist */
    CHECKLIST,

    /** Freeform journal or diary entry */
    FREEFORM_JOURNAL,

    /** Project plan with tasks and milestones */
    PROJECT_PLAN,

    /** Form-like structure with fields and values */
    FORM_LIKE,

    /** Mixed content that doesn't fit other categories */
    MIXED;

    companion object {
        fun fromString(value: String): DocumentStyle? = entries.find { it.name == value }
    }
}

/**
 * Topic classification taxonomy for semantic extraction.
 */
@Serializable
enum class Topic {
    MEETINGS,
    CALLS,
    TRAVEL,
    FINANCE,
    HEALTH,
    FAMILY,
    WORK,
    SHOPPING,
    PROJECTS,
    REMINDERS,
    MISC;

    companion object {
        fun fromString(value: String): Topic? = entries.find { it.name == value }
    }
}

/**
 * Entity type for named entity recognition.
 */
@Serializable
enum class EntityType {
    DATE,
    TIME,
    EMAIL,
    PHONE,
    URL,
    MONEY,
    PERCENT,
    PERSON,
    ORG,
    LOCATION;

    companion object {
        fun fromString(value: String): EntityType? = entries.find { it.name == value }
    }
}

/**
 * Queue item for OCR processing.
 *
 * @property sourceType Source type (BOOX_NOTE, MEMO, TASK)
 * @property sourceId Source identifier (noteId, dayDate)
 * @property pageId Page ID within the source
 * @property sourceCreatedAt When the source was created
 * @property sourceModifiedAt When the source was last modified
 * @property priority Processing priority (higher = sooner)
 * @property sourceTitle Optional title for the source (e.g., Boox Note title)
 * @property sourceDate Optional date for the source (e.g., Boox Note original date)
 */
@Serializable
data class OcrQueueItem(
    val sourceType: OcrSource,
    val sourceId: String,
    val pageId: String,
    val sourceCreatedAt: Long,
    val sourceModifiedAt: Long,
    val priority: Int = 0,
    val sourceTitle: String? = null,
    val sourceDate: String? = null
) {
    fun stableQueueId(): String = "${sourceType.name}_${sourceId}_${pageId}"
}
