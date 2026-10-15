package com.boox.day.booxnotes

import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLinkDecisionSupportTest {

    @Test
    fun `parseDismissedEventIds returns ids from json array`() {
        val dismissed = SmartLinkDecisionSupport.parseDismissedEventIds("[\"evt-1\", \"evt-2\"]")

        assertEquals(setOf("evt-1", "evt-2"), dismissed)
    }

    @Test
    fun `filterEligibleEvents removes all-day dismissed generic and out-of-window events`() {
        val note = createNote(date = "2026-03-06")
        val eligible = createEvent(id = "eligible", title = "Project Kickoff", dayId = "2026-03-06")
        val dismissed = createEvent(id = "dismissed", title = "Budget Review", dayId = "2026-03-06")
        val generic = createEvent(id = "generic", title = "Daily Standup", dayId = "2026-03-06")
        val allDay = createEvent(id = "all-day", title = "Workshop", dayId = "2026-03-06", isAllDay = true)
        val outsideWindow = createEvent(id = "outside", title = "Planning", dayId = "2026-03-12")

        val filtered = SmartLinkDecisionSupport.filterEligibleEvents(
            note = note,
            events = listOf(eligible, dismissed, generic, allDay, outsideWindow),
            dismissedEventIds = setOf("dismissed"),
            searchDays = 2
        )

        assertEquals(listOf("eligible"), filtered.map { it.id })
    }

    @Test
    fun `filterEligibleEvents keeps in-window cross-day event`() {
        val note = createNote(date = "2026-03-06")
        val yesterday = createEvent(id = "yesterday", title = "Design Review", dayId = "2026-03-05")

        val filtered = SmartLinkDecisionSupport.filterEligibleEvents(
            note = note,
            events = listOf(yesterday),
            searchDays = 1
        )

        assertEquals(1, filtered.size)
        assertTrue(filtered.any { it.id == "yesterday" })
    }

    @Test
    fun `timing-only strong suggestion is shown as possible`() {
        val timingOnly = listOf(MatchReason.DURING_EVENT, MatchReason.ONLY_EVENT_OF_DAY, MatchReason.LONG_EVENT)

        assertEquals(LinkConfidence.MEDIUM, SmartLinkDecisionSupport.suggestionConfidence(LinkConfidence.HIGH, timingOnly))
    }

    @Test
    fun `strong suggestion with a content signal stays strong`() {
        listOf(
            MatchReason.TITLE_MATCH,
            MatchReason.SEMANTIC_GROUP_MATCH,
            MatchReason.SEMANTIC_CONTEXT_MATCH,
            MatchReason.AI_SUGGESTED_MATCH
        ).forEach { content ->
            assertEquals(
                LinkConfidence.HIGH,
                SmartLinkDecisionSupport.suggestionConfidence(LinkConfidence.HIGH, listOf(MatchReason.DURING_EVENT, content))
            )
        }
    }

    @Test
    fun `possible and weak suggestions are unchanged`() {
        assertEquals(LinkConfidence.MEDIUM, SmartLinkDecisionSupport.suggestionConfidence(LinkConfidence.MEDIUM, emptyList()))
        assertEquals(LinkConfidence.LOW, SmartLinkDecisionSupport.suggestionConfidence(LinkConfidence.LOW, listOf(MatchReason.TITLE_MATCH)))
    }

    @Test
    fun `mergeMatchReasons adds semantic and ai reasons without duplicates`() {
        val merged = SmartLinkDecisionSupport.mergeMatchReasons(
            reasons = listOf(MatchReason.TITLE_MATCH, MatchReason.AI_SUGGESTED_MATCH),
            addSemanticContext = true,
            addAiSuggested = true
        )

        assertEquals(
            listOf(
                MatchReason.TITLE_MATCH,
                MatchReason.AI_SUGGESTED_MATCH,
                MatchReason.SEMANTIC_CONTEXT_MATCH
            ),
            merged
        )
    }

    @Test
    fun `mergeDataQualityFlags deduplicates while preserving order`() {
        val merged = SmartLinkDecisionSupport.mergeDataQualityFlags(
            listOf(
                SmartLinkDataQualityFlag.SEMANTIC_RUNTIME_DISABLED,
                SmartLinkDataQualityFlag.NO_NOTE_SEMANTICS
            ),
            listOf(
                SmartLinkDataQualityFlag.NO_NOTE_SEMANTICS,
                SmartLinkDataQualityFlag.AI_GENERATED
            )
        )

        assertEquals(
            listOf(
                SmartLinkDataQualityFlag.SEMANTIC_RUNTIME_DISABLED,
                SmartLinkDataQualityFlag.NO_NOTE_SEMANTICS,
                SmartLinkDataQualityFlag.AI_GENERATED
            ),
            merged
        )
    }

    private fun createNote(date: String): BooxNoteIndex {
        return BooxNoteIndex(
            uniqueId = "note-$date",
            title = "Project Alpha Notes",
            originalDate = date,
            originalTimestamp = 1_700_000_000_000L,
            pageCount = 2,
            pageIdsJson = "[\"page-1\", \"page-2\"]",
            shapeFileModified = 1_700_000_000_000L,
            indexedAt = 1_700_000_000_000L
        )
    }

    private fun createEvent(
        id: String,
        title: String,
        dayId: String,
        isAllDay: Boolean = false
    ): CalendarEvent {
        return CalendarEvent(
            id = id,
            providerId = "GOOGLE",
            accountId = "acct",
            accountEmail = "acct@example.com",
            calendarId = "cal",
            calendarName = "Calendar",
            title = title,
            dayId = dayId,
            startTime = 1_700_000_000_000L,
            endTime = 1_700_000_360_000L,
            isAllDay = isAllDay,
            location = null,
            description = null,
            attendees = null,
            isReadOnly = false,
            colorHex = null
        )
    }
}
