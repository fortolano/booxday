package com.boox.day.data.db

import android.content.Context
import com.boox.day.ocr.OcrQuotaManager
import java.time.LocalDate

internal fun mergeSmartLinkEvents(
    cloudEvents: List<CalendarEvent>,
    localEvents: List<CalendarEvent>
): List<CalendarEvent> {
    val mergedById = LinkedHashMap<String, CalendarEvent>()

    cloudEvents.forEach { event ->
        mergedById.putIfAbsent(event.id, event)
    }
    localEvents.forEach { event ->
        mergedById.putIfAbsent(event.id, event)
    }

    return mergedById.values.sortedWith(
        compareByDescending<CalendarEvent> { it.isAllDay }
            .thenBy { it.startTime }
    )
}

enum class SmartLinkPreflightState {
    SERVER_SMART_LINK_DISABLED,
    SERVER_OCR_DISABLED,
    FILTERS_HIDE_ALL_ELIGIBLE_EVENTS,
    NO_ELIGIBLE_TIMED_EVENTS,
    READY,
    LOCAL_ONLY_READY,
    NO_ACCOUNTS_NO_EVENTS,
    ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS
}

data class SmartLinkSourceVisibility(
    val showLocal: Boolean = true,
    val showGoogle: Boolean = true,
    val showOutlook: Boolean = true
)

data class SmartLinkPreflightStatus(
    val state: SmartLinkPreflightState,
    val connectedAccountCount: Int,
    val cloudEventCount: Long,
    val googleEventCount: Long,
    val outlookEventCount: Long,
    val localOnlyEventCount: Long,
    val localVisibleEventCount: Long,
    val googleTimedEventCount: Long,
    val outlookTimedEventCount: Long,
    val localOnlyTimedEventCount: Long,
    val localVisibleTimedEventCount: Long,
    val visibleEventCount: Long,
    val totalEventCount: Long,
    val visibleTimedEventCount: Long,
    val totalTimedEventCount: Long,
    val isDeterministicOnly: Boolean
)

internal fun deriveSmartLinkPreflightStatus(
    smartLinkEnabled: Boolean,
    ocrEnabled: Boolean,
    semanticExtractionEnabled: Boolean,
    connectedAccountCount: Int,
    googleEventCount: Long,
    outlookEventCount: Long,
    localOnlyEventCount: Long,
    localVisibleEventCount: Long,
    googleTimedEventCount: Long,
    outlookTimedEventCount: Long,
    localOnlyTimedEventCount: Long,
    localVisibleTimedEventCount: Long,
    sourceVisibility: SmartLinkSourceVisibility
): SmartLinkPreflightStatus {
    val cloudEventCount = googleEventCount + outlookEventCount
    val cloudTimedEventCount = googleTimedEventCount + outlookTimedEventCount
    val visibleEventCount =
        (if (sourceVisibility.showLocal) localVisibleEventCount else 0L) +
            (if (sourceVisibility.showGoogle) googleEventCount else 0L) +
            (if (sourceVisibility.showOutlook) outlookEventCount else 0L)
    val visibleTimedEventCount =
        (if (sourceVisibility.showLocal) localVisibleTimedEventCount else 0L) +
            (if (sourceVisibility.showGoogle) googleTimedEventCount else 0L) +
            (if (sourceVisibility.showOutlook) outlookTimedEventCount else 0L)
    val totalEventCount = cloudEventCount + localVisibleEventCount
    val totalTimedEventCount = cloudTimedEventCount + localVisibleTimedEventCount
    val isDeterministicOnly = smartLinkEnabled && ocrEnabled && !semanticExtractionEnabled
    val state = when {
        !smartLinkEnabled ->
            SmartLinkPreflightState.SERVER_SMART_LINK_DISABLED
        !ocrEnabled ->
            SmartLinkPreflightState.SERVER_OCR_DISABLED
        totalTimedEventCount > 0L && visibleTimedEventCount == 0L ->
            SmartLinkPreflightState.FILTERS_HIDE_ALL_ELIGIBLE_EVENTS
        totalEventCount > 0L && totalTimedEventCount == 0L ->
            SmartLinkPreflightState.NO_ELIGIBLE_TIMED_EVENTS
        totalTimedEventCount > 0L && cloudTimedEventCount == 0L && localOnlyTimedEventCount > 0L ->
            SmartLinkPreflightState.LOCAL_ONLY_READY
        totalTimedEventCount > 0L ->
            SmartLinkPreflightState.READY
        connectedAccountCount > 0 ->
            SmartLinkPreflightState.ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS
        else ->
            SmartLinkPreflightState.NO_ACCOUNTS_NO_EVENTS
    }

    return SmartLinkPreflightStatus(
        state = state,
        connectedAccountCount = connectedAccountCount,
        cloudEventCount = cloudEventCount,
        googleEventCount = googleEventCount,
        outlookEventCount = outlookEventCount,
        localOnlyEventCount = localOnlyEventCount,
        localVisibleEventCount = localVisibleEventCount,
        googleTimedEventCount = googleTimedEventCount,
        outlookTimedEventCount = outlookTimedEventCount,
        localOnlyTimedEventCount = localOnlyTimedEventCount,
        localVisibleTimedEventCount = localVisibleTimedEventCount,
        visibleEventCount = visibleEventCount,
        totalEventCount = totalEventCount,
        visibleTimedEventCount = visibleTimedEventCount,
        totalTimedEventCount = totalTimedEventCount,
        isDeterministicOnly = isDeterministicOnly
    )
}

/**
 * Smart Link event source that merges cloud cache events with local-only events.
 *
 * This is intentionally scoped to Smart Link and Boox Notes flows so we can fix
 * matching correctness without doing a wider calendar architecture refactor yet.
 */
class SmartLinkEventRepository(
    context: Context,
    private val calendarEventRepository: CalendarEventRepository,
    private val localEventRepository: LocalEventRepository = LocalEventRepository(context.applicationContext)
) {
    private val connectedAccountDao = AppDatabase.getDatabase(context.applicationContext).connectedAccountDao()
    private val quotaManager = OcrQuotaManager(context.applicationContext)

    suspend fun getEventsForDateSync(dayId: String): List<CalendarEvent> {
        val cloudEvents = calendarEventRepository.getEventsForDateSync(dayId)
        val localEvents = localEventRepository.getEventsForDay(dayId).map { it.toCalendarEvent() }
        return mergeSmartLinkEvents(cloudEvents, localEvents)
    }

    suspend fun getEventsInRange(startDate: String, endDate: String): List<CalendarEvent> {
        val cloudEvents = calendarEventRepository.getEventsInRange(startDate, endDate)
        val localEvents = localEventRepository.getEventsForRange(
            LocalDate.parse(startDate),
            LocalDate.parse(endDate)
        ).map { it.toCalendarEvent() }
        return mergeSmartLinkEvents(cloudEvents, localEvents)
    }

    suspend fun getEventById(eventId: String): CalendarEvent? {
        return calendarEventRepository.getEventById(eventId)
            ?: localEventRepository.getEventBySmartLinkId(eventId)
    }

    suspend fun hasAnyEvents(): Boolean {
        return calendarEventRepository.hasCachedEvents() || localEventRepository.hasAnyEvents()
    }

    suspend fun getGoogleEventCount(): Long = calendarEventRepository.getEventCountByProvider("GOOGLE")

    suspend fun getOutlookEventCount(): Long = calendarEventRepository.getEventCountByProvider("OUTLOOK")

    suspend fun getGoogleTimedEventCount(): Long = calendarEventRepository.getTimedEventCountByProvider("GOOGLE")

    suspend fun getOutlookTimedEventCount(): Long = calendarEventRepository.getTimedEventCountByProvider("OUTLOOK")

    suspend fun getCloudEventCount(): Long = getGoogleEventCount() + getOutlookEventCount()

    suspend fun getLocalOnlyEventCount(): Long = localEventRepository.getLocalOnlyEventCount().toLong()

    suspend fun getLocalOnlyTimedEventCount(): Long = localEventRepository.getLocalOnlyTimedEventCount().toLong()

    suspend fun getConnectedAccountCount(): Int = connectedAccountDao.countActiveAccounts()

    suspend fun getLocalVisibleEventCount(): Long {
        val uniqueIds = calendarEventRepository.getAllEventIds().toMutableSet()
        return localEventRepository.getAllActiveEventsForSmartLink()
            .count { event -> event.id !in uniqueIds }
            .toLong()
    }

    suspend fun getLocalVisibleTimedEventCount(): Long {
        val uniqueIds = calendarEventRepository.getAllEventIds().toMutableSet()
        return localEventRepository.getAllActiveEventsForSmartLink()
            .count { event -> !event.isAllDay && event.id !in uniqueIds }
            .toLong()
    }

    suspend fun getEventCount(): Long = getCloudEventCount() + getLocalVisibleEventCount()

    suspend fun getPreflightStatus(
        sourceVisibility: SmartLinkSourceVisibility = SmartLinkSourceVisibility()
    ): SmartLinkPreflightStatus {
        val googleEventCount = getGoogleEventCount()
        val outlookEventCount = getOutlookEventCount()
        val localOnlyEventCount = getLocalOnlyEventCount()
        val localVisibleEventCount = getLocalVisibleEventCount()
        val googleTimedEventCount = getGoogleTimedEventCount()
        val outlookTimedEventCount = getOutlookTimedEventCount()
        val localOnlyTimedEventCount = getLocalOnlyTimedEventCount()
        val localVisibleTimedEventCount = getLocalVisibleTimedEventCount()
        val connectedAccountCount = getConnectedAccountCount()
        return deriveSmartLinkPreflightStatus(
            smartLinkEnabled = quotaManager.isSmartLinkEnabled,
            ocrEnabled = quotaManager.isOcrFeatureEnabled,
            semanticExtractionEnabled = quotaManager.isSemanticExtractionEnabled,
            connectedAccountCount = connectedAccountCount,
            googleEventCount = googleEventCount,
            outlookEventCount = outlookEventCount,
            localOnlyEventCount = localOnlyEventCount,
            localVisibleEventCount = localVisibleEventCount,
            googleTimedEventCount = googleTimedEventCount,
            outlookTimedEventCount = outlookTimedEventCount,
            localOnlyTimedEventCount = localOnlyTimedEventCount,
            localVisibleTimedEventCount = localVisibleTimedEventCount,
            sourceVisibility = sourceVisibility
        )
    }
}
