package com.boox.day.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSyncOutcomeTest {

    @Test
    fun `buildCalendarFetchSyncResult returns success when all calendars succeed`() {
        val result = buildCalendarFetchSyncResult(
            successfulCalendarCount = 2,
            fetchFailures = emptyList(),
            newEventsCount = 3,
            updatedEventsCount = 4
        )

        assertTrue(result is SyncResult.Success)
        result as SyncResult.Success
        assertEquals(7, result.eventCount)
        assertEquals(3, result.newEvents)
        assertEquals(4, result.updatedEvents)
    }

    @Test
    fun `buildCalendarFetchSyncResult returns partial success when some calendars fail`() {
        val result = buildCalendarFetchSyncResult(
            successfulCalendarCount = 1,
            fetchFailures = listOf(
                CalendarFetchFailure(
                    calendarId = "calendar-b",
                    errorType = SyncErrorType.NETWORK,
                    message = "Unable to resolve host"
                )
            ),
            newEventsCount = 2,
            updatedEventsCount = 1
        )

        assertTrue(result is SyncResult.PartialSuccess)
        result as SyncResult.PartialSuccess
        assertEquals(3, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(listOf("calendar-b: Unable to resolve host"), result.errors)
    }

    @Test
    fun `buildCalendarFetchSyncResult returns no network when all calendars fail with network errors`() {
        val result = buildCalendarFetchSyncResult(
            successfulCalendarCount = 0,
            fetchFailures = listOf(
                CalendarFetchFailure("calendar-a", SyncErrorType.NETWORK, "timeout"),
                CalendarFetchFailure("calendar-b", SyncErrorType.NETWORK, "dns")
            ),
            newEventsCount = 0,
            updatedEventsCount = 0
        )

        assertTrue(result is SyncResult.NoNetwork)
    }

    @Test
    fun `buildCalendarFetchSyncResult returns error when all calendars fail with non network errors`() {
        val result = buildCalendarFetchSyncResult(
            successfulCalendarCount = 0,
            fetchFailures = listOf(
                CalendarFetchFailure(
                    calendarId = "calendar-a",
                    errorType = SyncErrorType.AUTHENTICATION,
                    message = "401 unauthorized"
                )
            ),
            newEventsCount = 0,
            updatedEventsCount = 0
        )

        assertTrue(result is SyncResult.Error)
        result as SyncResult.Error
        assertEquals(SyncErrorType.AUTHENTICATION, result.errorType)
        assertEquals("Authentication failed while fetching calendars", result.message)
    }
}
