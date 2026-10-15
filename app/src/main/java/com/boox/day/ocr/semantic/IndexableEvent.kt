package com.boox.day.ocr.semantic

import com.boox.day.data.db.CalendarEvent
import com.boox.day.data.db.LocalEvent
import com.boox.day.ocr.model.OcrSource

/**
 * Common interface for events that can be semantically indexed.
 *
 * Both CalendarEvent (synced from Google/Outlook) and LocalEvent (created in-app)
 * implement this interface through extension functions, enabling unified semantic
 * classification without code duplication.
 *
 * Events are TEXT sources - they don't require bitmap/OCR processing.
 * The text content (title + description) is directly classified by DomainIntentClassifier.
 *
 * @see CalendarEvent.toIndexable
 * @see LocalEvent.toIndexable
 */
interface IndexableEvent {
    /** Unique identifier for the event (provider UUID or app-generated UUID) */
    val id: String

    /** Event title (e.g., "Budget Review Meeting") */
    val title: String

    /** Event description (may contain meeting links, boilerplate to be stripped) */
    val description: String?

    /** Event location (physical address or meeting room) */
    val location: String?

    /** Event start time in epoch milliseconds */
    val startTime: Long

    /** Event end time in epoch milliseconds (null for all-day events without end) */
    val endTime: Long?

    /** Whether this is an all-day event */
    val isAllDay: Boolean

    /** Comma-separated list of attendee emails (optional) */
    val attendees: String?

    /**
     * Returns the OcrSource type for this event.
     * CALENDAR_EVENT for synced events, LOCAL_EVENT for app-created events.
     */
    fun getOcrSourceType(): OcrSource
}

/**
 * Convert CalendarEvent to IndexableEvent for semantic indexing.
 *
 * CalendarEvent represents events synced from Google or Outlook.
 * These events use OcrSource.CALENDAR_EVENT.
 */
fun CalendarEvent.toIndexable(): IndexableEvent = object : IndexableEvent {
    override val id: String = this@toIndexable.id
    override val title: String = this@toIndexable.title
    override val description: String? = this@toIndexable.description
    override val location: String? = this@toIndexable.location
    override val startTime: Long = this@toIndexable.startTime
    override val endTime: Long? = this@toIndexable.endTime
    override val isAllDay: Boolean = this@toIndexable.isAllDay
    override val attendees: String? = this@toIndexable.attendees
    override fun getOcrSourceType(): OcrSource = OcrSource.CALENDAR_EVENT
}

/**
 * Convert LocalEvent to IndexableEvent for semantic indexing.
 *
 * LocalEvent represents events created directly in the app.
 * These events use OcrSource.LOCAL_EVENT.
 */
fun LocalEvent.toIndexable(): IndexableEvent = object : IndexableEvent {
    override val id: String = this@toIndexable.id
    override val title: String = this@toIndexable.title
    override val description: String? = this@toIndexable.description
    override val location: String? = this@toIndexable.location
    override val startTime: Long = this@toIndexable.startTime
    override val endTime: Long? = this@toIndexable.endTime
    override val isAllDay: Boolean = this@toIndexable.isAllDay
    override val attendees: String? = this@toIndexable.attendees
    override fun getOcrSourceType(): OcrSource = OcrSource.LOCAL_EVENT
}
