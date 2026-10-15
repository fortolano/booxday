package com.boox.day.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLinkEventRepositoryTest {

    @Test
    fun deriveSmartLinkPreflightStatus_returnsNoAccountsStateWhenNothingIsAvailable() {
        val status = deriveSmartLinkPreflightStatus(
            smartLinkEnabled = true,
            ocrEnabled = true,
            semanticExtractionEnabled = true,
            connectedAccountCount = 0,
            googleEventCount = 0L,
            outlookEventCount = 0L,
            localOnlyEventCount = 0L,
            localVisibleEventCount = 0L,
            googleTimedEventCount = 0L,
            outlookTimedEventCount = 0L,
            localOnlyTimedEventCount = 0L,
            localVisibleTimedEventCount = 0L,
            sourceVisibility = SmartLinkSourceVisibility()
        )

        assertEquals(SmartLinkPreflightState.NO_ACCOUNTS_NO_EVENTS, status.state)
    }

    @Test
    fun deriveSmartLinkPreflightStatus_returnsConnectedButUnsyncedState() {
        val status = deriveSmartLinkPreflightStatus(
            smartLinkEnabled = true,
            ocrEnabled = true,
            semanticExtractionEnabled = true,
            connectedAccountCount = 2,
            googleEventCount = 0L,
            outlookEventCount = 0L,
            localOnlyEventCount = 0L,
            localVisibleEventCount = 0L,
            googleTimedEventCount = 0L,
            outlookTimedEventCount = 0L,
            localOnlyTimedEventCount = 0L,
            localVisibleTimedEventCount = 0L,
            sourceVisibility = SmartLinkSourceVisibility()
        )

        assertEquals(SmartLinkPreflightState.ACCOUNTS_CONNECTED_BUT_NO_SYNCED_EVENTS, status.state)
    }

    @Test
    fun deriveSmartLinkPreflightStatus_returnsLocalOnlyReadyWhenOnlyLocalEventsExist() {
        val status = deriveSmartLinkPreflightStatus(
            smartLinkEnabled = true,
            ocrEnabled = true,
            semanticExtractionEnabled = true,
            connectedAccountCount = 0,
            googleEventCount = 0L,
            outlookEventCount = 0L,
            localOnlyEventCount = 3L,
            localVisibleEventCount = 3L,
            googleTimedEventCount = 0L,
            outlookTimedEventCount = 0L,
            localOnlyTimedEventCount = 3L,
            localVisibleTimedEventCount = 3L,
            sourceVisibility = SmartLinkSourceVisibility()
        )

        assertEquals(SmartLinkPreflightState.LOCAL_ONLY_READY, status.state)
    }

    @Test
    fun deriveSmartLinkPreflightStatus_returnsReadyWhenAnyUsableEventsExist() {
        val status = deriveSmartLinkPreflightStatus(
            smartLinkEnabled = true,
            ocrEnabled = true,
            semanticExtractionEnabled = true,
            connectedAccountCount = 1,
            googleEventCount = 4L,
            outlookEventCount = 0L,
            localOnlyEventCount = 0L,
            localVisibleEventCount = 0L,
            googleTimedEventCount = 4L,
            outlookTimedEventCount = 0L,
            localOnlyTimedEventCount = 0L,
            localVisibleTimedEventCount = 0L,
            sourceVisibility = SmartLinkSourceVisibility()
        )

        assertEquals(SmartLinkPreflightState.READY, status.state)
    }

    @Test
    fun deriveSmartLinkPreflightStatus_returnsServerDisabledStateWhenSmartLinkIsOff() {
        val status = deriveSmartLinkPreflightStatus(
            smartLinkEnabled = false,
            ocrEnabled = true,
            semanticExtractionEnabled = true,
            connectedAccountCount = 1,
            googleEventCount = 2L,
            outlookEventCount = 0L,
            localOnlyEventCount = 0L,
            localVisibleEventCount = 0L,
            googleTimedEventCount = 2L,
            outlookTimedEventCount = 0L,
            localOnlyTimedEventCount = 0L,
            localVisibleTimedEventCount = 0L,
            sourceVisibility = SmartLinkSourceVisibility()
        )

        assertEquals(SmartLinkPreflightState.SERVER_SMART_LINK_DISABLED, status.state)
    }

    @Test
    fun deriveSmartLinkPreflightStatus_returnsOcrDisabledStateWhenOcrIsOff() {
        val status = deriveSmartLinkPreflightStatus(
            smartLinkEnabled = true,
            ocrEnabled = false,
            semanticExtractionEnabled = true,
            connectedAccountCount = 1,
            googleEventCount = 2,
            outlookEventCount = 0,
            localOnlyEventCount = 0,
            localVisibleEventCount = 0,
            googleTimedEventCount = 2,
            outlookTimedEventCount = 0,
            localOnlyTimedEventCount = 0,
            localVisibleTimedEventCount = 0,
            sourceVisibility = SmartLinkSourceVisibility()
        )

        assertEquals(SmartLinkPreflightState.SERVER_OCR_DISABLED, status.state)
        assertEquals(false, status.isDeterministicOnly)
    }

    @Test
    fun deriveSmartLinkPreflightStatus_returnsFilterHiddenStateWhenVisibleSourcesAreOff() {
        val status = deriveSmartLinkPreflightStatus(
            smartLinkEnabled = true,
            ocrEnabled = true,
            semanticExtractionEnabled = true,
            connectedAccountCount = 1,
            googleEventCount = 2L,
            outlookEventCount = 0L,
            localOnlyEventCount = 0L,
            localVisibleEventCount = 0L,
            googleTimedEventCount = 2L,
            outlookTimedEventCount = 0L,
            localOnlyTimedEventCount = 0L,
            localVisibleTimedEventCount = 0L,
            sourceVisibility = SmartLinkSourceVisibility(
                showLocal = false,
                showGoogle = false,
                showOutlook = false
            )
        )

        assertEquals(SmartLinkPreflightState.FILTERS_HIDE_ALL_ELIGIBLE_EVENTS, status.state)
    }

    @Test
    fun deriveSmartLinkPreflightStatus_returnsNoEligibleTimedEventsWhenOnlyAllDayEventsExist() {
        val status = deriveSmartLinkPreflightStatus(
            smartLinkEnabled = true,
            ocrEnabled = true,
            semanticExtractionEnabled = true,
            connectedAccountCount = 1,
            googleEventCount = 3L,
            outlookEventCount = 0L,
            localOnlyEventCount = 0L,
            localVisibleEventCount = 0L,
            googleTimedEventCount = 0L,
            outlookTimedEventCount = 0L,
            localOnlyTimedEventCount = 0L,
            localVisibleTimedEventCount = 0L,
            sourceVisibility = SmartLinkSourceVisibility()
        )

        assertEquals(SmartLinkPreflightState.NO_ELIGIBLE_TIMED_EVENTS, status.state)
        assertEquals(false, status.isDeterministicOnly)
    }

    @Test
    fun deriveSmartLinkPreflightStatus_marksDeterministicOnlyWhenSemanticSignalsAreUnavailable() {
        val status = deriveSmartLinkPreflightStatus(
            smartLinkEnabled = true,
            ocrEnabled = true,
            semanticExtractionEnabled = false,
            connectedAccountCount = 1,
            googleEventCount = 1L,
            outlookEventCount = 0L,
            localOnlyEventCount = 0L,
            localVisibleEventCount = 0L,
            googleTimedEventCount = 1L,
            outlookTimedEventCount = 0L,
            localOnlyTimedEventCount = 0L,
            localVisibleTimedEventCount = 0L,
            sourceVisibility = SmartLinkSourceVisibility()
        )

        assertEquals(SmartLinkPreflightState.READY, status.state)
        assertEquals(true, status.isDeterministicOnly)
    }

    @Test
    fun mergeSmartLinkEvents_prefersCloudEventWhenIdsMatch() {
        val cloud = listOf(
            event(id = "shared", title = "Cloud Title", startTime = 200L)
        )
        val local = listOf(
            event(id = "shared", title = "Local Title", startTime = 100L),
            event(id = "local-only", title = "Local Only", startTime = 300L)
        )

        val merged = mergeSmartLinkEvents(cloud, local)

        assertEquals(listOf("shared", "local-only"), merged.map { it.id })
        assertEquals("Cloud Title", merged.first().title)
    }

    @Test
    fun mergeSmartLinkEvents_sortsAllDayBeforeTimedThenByStartTime() {
        val cloud = listOf(
            event(id = "timed-late", title = "Late", startTime = 900L),
            event(id = "all-day", title = "All Day", startTime = 0L, isAllDay = true)
        )
        val local = listOf(
            event(id = "timed-early", title = "Early", startTime = 100L)
        )

        val merged = mergeSmartLinkEvents(cloud, local)

        assertEquals(listOf("all-day", "timed-early", "timed-late"), merged.map { it.id })
        assertTrue(merged.first().isAllDay)
    }

    private fun event(
        id: String,
        title: String,
        startTime: Long,
        isAllDay: Boolean = false
    ): CalendarEvent {
        return CalendarEvent(
            id = id,
            providerId = "GOOGLE",
            accountId = "acct",
            accountEmail = "user@example.com",
            calendarId = "cal",
            calendarName = "Calendar",
            title = title,
            dayId = "2026-03-06",
            endDayId = "2026-03-06",
            startTime = startTime,
            endTime = if (isAllDay) null else startTime + 60L,
            isAllDay = isAllDay
        )
    }
}
