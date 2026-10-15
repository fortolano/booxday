package com.boox.day.ocr.semantic

import com.boox.day.data.db.UnifiedOcrText
import com.boox.day.ocr.model.OcrSource

/**
 * Storage contract for event semantic profiles in unified_ocr_text.
 *
 * Events (CALENDAR_EVENT, LOCAL_EVENT) are stored differently than notes/memos/tasks:
 * - Events are TEXT sources - no bitmap, no Cloud Vision OCR
 * - Events use pageId="0" (sentinel) since they don't have pages
 * - Events store directly in unified_ocr_text WITHOUT creating UnifiedOcrPage rows
 *
 * Field mapping for events:
 * - sourceType: CALENDAR_EVENT or LOCAL_EVENT
 * - sourceId: Event's unique ID (provider UUID or app-generated UUID)
 * - pageId: "0" (PAGE_ID_SENTINEL)
 * - sourceDate: Event's date (yyyy-MM-dd)
 * - sourceTitle: Event title
 * - sourceTimestamp: Event start time (for temporal queries)
 * - rawText: Normalized title + description (after EventTextNormalizer)
 * - domain: Classification result from DomainIntentClassifier
 * - intentsJson: Classification result (JSON array of intents)
 * - entitiesJson: Extracted entities (includes attendees)
 * - keywordsJson: Extracted keywords
 */
object EventProfileContract {

    /**
     * Sentinel value for pageId when storing event profiles.
     *
     * Events don't have pages (unlike notes/memos/tasks which have multiple pages),
     * so we use "0" as a placeholder to satisfy the composite key requirement.
     *
     * Composite key format: "{sourceType}_{sourceId}_{pageId}"
     * For events: "CALENDAR_EVENT_google_abc123_0" or "LOCAL_EVENT_uuid-here_0"
     */
    const val PAGE_ID_SENTINEL = "0"

    /**
     * Check if a profile is for an event (calendar or local).
     *
     * @param ocrText The unified OCR text entry
     * @return true if this is an event profile (either CALENDAR_EVENT or LOCAL_EVENT)
     */
    fun isEventProfile(ocrText: UnifiedOcrText): Boolean {
        val sourceType = OcrSource.fromString(ocrText.sourceType)
        return sourceType == OcrSource.CALENDAR_EVENT || sourceType == OcrSource.LOCAL_EVENT
    }

    /**
     * Check if a profile is for a synced calendar event (from Google/Outlook).
     *
     * @param ocrText The unified OCR text entry
     * @return true if this is a CALENDAR_EVENT profile
     */
    fun isSyncedEventProfile(ocrText: UnifiedOcrText): Boolean {
        return OcrSource.fromString(ocrText.sourceType) == OcrSource.CALENDAR_EVENT
    }

    /**
     * Check if a profile is for a local event (created in-app).
     *
     * @param ocrText The unified OCR text entry
     * @return true if this is a LOCAL_EVENT profile
     */
    fun isLocalEventProfile(ocrText: UnifiedOcrText): Boolean {
        return OcrSource.fromString(ocrText.sourceType) == OcrSource.LOCAL_EVENT
    }

    /**
     * Generate the composite ID for an event profile.
     *
     * @param sourceType The OcrSource type (CALENDAR_EVENT or LOCAL_EVENT)
     * @param eventId The event's unique ID
     * @return Composite ID in format "{sourceType}_{eventId}_{PAGE_ID_SENTINEL}"
     */
    fun generateEventProfileId(sourceType: OcrSource, eventId: String): String {
        require(sourceType.isEventSource()) {
            "sourceType must be CALENDAR_EVENT or LOCAL_EVENT, got: $sourceType"
        }
        return "${sourceType.name}_${eventId}_$PAGE_ID_SENTINEL"
    }

    /**
     * Generate the composite ID for an IndexableEvent.
     *
     * @param event The event to generate ID for
     * @return Composite ID for storage in unified_ocr_text
     */
    fun generateEventProfileId(event: IndexableEvent): String {
        return generateEventProfileId(event.getOcrSourceType(), event.id)
    }
}
