package com.boox.day.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BooxNoteLinkLogicTest {

    @Test
    fun buildDismissedSuggestionLink_createsPlaceholderWhenNoExistingLink() {
        val timestamp = 1234L

        val link = buildDismissedSuggestionLink(
            existingLink = null,
            noteId = "note-1",
            linkedDayDate = "2026-03-06",
            eventIdToDismiss = "event-1",
            timestamp = timestamp
        )

        assertEquals("note-1", link.noteUniqueId)
        assertNull(link.linkedEventId)
        assertEquals("2026-03-06", link.linkedDayDate)
        assertEquals("NONE", link.confidence)
        assertEquals("[\"event-1\"]", link.dismissedEventIds)
        assertNull(link.suggestedEventIds)
        assertFalse(link.isManualLink)
        assertFalse(link.needsAttention)
        assertEquals(timestamp, link.createdAt)
        assertEquals(timestamp, link.updatedAt)
    }

    @Test
    fun applyDeletedEventToLink_clearsActiveLinkAndMarksNeedsAttention() {
        val existing = BooxNoteLink(
            id = "link-1",
            noteUniqueId = "note-1",
            linkedEventId = "event-1",
            linkedDayDate = "2026-03-06",
            isManualLink = true,
            confidence = "HIGH",
            confidenceScore = 97,
            matchReasons = "[\"TITLE_MATCH\"]",
            suggestedEventIds = "[\"event-1\",\"event-2\"]",
            dismissedEventIds = "[\"event-9\"]",
            needsAttention = false,
            createdAt = 10L,
            updatedAt = 20L,
            lastReviewedAt = 30L
        )

        val updated = applyDeletedEventToLink(
            existingLink = existing,
            deletedEventId = "event-1",
            timestamp = 99L
        )

        requireNotNull(updated)
        assertNull(updated.linkedEventId)
        assertFalse(updated.isManualLink)
        assertEquals("NONE", updated.confidence)
        assertEquals(0, updated.confidenceScore)
        assertNull(updated.matchReasons)
        assertEquals("[\"event-2\"]", updated.suggestedEventIds)
        assertEquals("[\"event-9\"]", updated.dismissedEventIds)
        assertTrue(updated.needsAttention)
        assertEquals(99L, updated.updatedAt)
    }

    @Test
    fun applyDeletedEventToLink_removesDeletedSuggestionButKeepsActiveLink() {
        val existing = BooxNoteLink(
            id = "link-1",
            noteUniqueId = "note-1",
            linkedEventId = "event-live",
            linkedDayDate = "2026-03-06",
            isManualLink = false,
            confidence = "MEDIUM",
            confidenceScore = 55,
            matchReasons = "[\"DURING_EVENT\"]",
            suggestedEventIds = "[\"event-deleted\",\"event-other\"]",
            dismissedEventIds = null,
            needsAttention = true,
            createdAt = 10L,
            updatedAt = 20L,
            lastReviewedAt = null
        )

        val updated = applyDeletedEventToLink(
            existingLink = existing,
            deletedEventId = "event-deleted",
            timestamp = 99L
        )

        requireNotNull(updated)
        assertEquals("event-live", updated.linkedEventId)
        assertEquals("MEDIUM", updated.confidence)
        assertEquals(55, updated.confidenceScore)
        assertEquals("[\"event-other\"]", updated.suggestedEventIds)
        assertTrue(updated.needsAttention)
    }

    @Test
    fun buildEventBadgeData_prefersManualThenConfidenceThenRecency() {
        val badgeData = buildEventBadgeData(
            listOf(
                badgeLink(
                    noteUniqueId = "note-low",
                    linkedEventId = "event-1",
                    isManualLink = false,
                    confidence = "LOW",
                    updatedAt = 100L,
                    noteTimestamp = 1000L
                ),
                badgeLink(
                    noteUniqueId = "note-high",
                    linkedEventId = "event-1",
                    isManualLink = false,
                    confidence = "HIGH",
                    updatedAt = 200L,
                    noteTimestamp = 2000L
                ),
                badgeLink(
                    noteUniqueId = "note-manual",
                    linkedEventId = "event-1",
                    isManualLink = true,
                    confidence = "LOW",
                    updatedAt = 50L,
                    noteTimestamp = 500L,
                    needsAttention = true
                )
            )
        )

        assertEquals(3, badgeData.noteCounts["event-1"])
        assertEquals(setOf("event-1"), badgeData.eventsWithAttention)
        assertEquals("note-manual", badgeData.eventToNoteLinks["event-1"])
    }

    @Test
    fun buildEventBadgeData_reportsSingleAutoLinkedNote() {
        val badgeData = buildEventBadgeData(
            listOf(
                badgeLink(
                    noteUniqueId = "note-auto",
                    linkedEventId = "event-1",
                    isManualLink = false,
                    confidence = "HIGH",
                    updatedAt = 100L,
                    noteTimestamp = 1000L
                )
            )
        )

        assertEquals(1, badgeData.noteCounts["event-1"])
        assertEquals(emptySet<String>(), badgeData.eventsWithAttention)
        assertEquals("note-auto", badgeData.eventToNoteLinks["event-1"])
    }

    @Test
    fun buildEventBadgeData_movesBadgeAfterRelink() {
        val beforeRelink = buildEventBadgeData(
            listOf(
                badgeLink(
                    noteUniqueId = "note-1",
                    linkedEventId = "event-1",
                    isManualLink = false,
                    confidence = "MEDIUM",
                    updatedAt = 100L,
                    noteTimestamp = 1000L
                )
            )
        )
        val afterRelink = buildEventBadgeData(
            listOf(
                badgeLink(
                    noteUniqueId = "note-1",
                    linkedEventId = "event-2",
                    isManualLink = true,
                    confidence = "HIGH",
                    updatedAt = 200L,
                    noteTimestamp = 1000L
                )
            )
        )

        assertEquals("note-1", beforeRelink.eventToNoteLinks["event-1"])
        assertNull(afterRelink.eventToNoteLinks["event-1"])
        assertEquals(1, afterRelink.noteCounts["event-2"])
        assertEquals("note-1", afterRelink.eventToNoteLinks["event-2"])
    }

    @Test
    fun buildEventBadgeData_clearsBadgeAfterUnlink() {
        val badgeData = buildEventBadgeData(emptyList())

        assertTrue(badgeData.noteCounts.isEmpty())
        assertTrue(badgeData.eventsWithAttention.isEmpty())
        assertTrue(badgeData.eventToNoteLinks.isEmpty())
    }

    @Test
    fun buildSmartLinkEventAliasResolution_includesLegacyLocalIdForSyncedEvent() {
        val syncedLocalEvent = LocalEvent(
            id = "local-1",
            title = "Project Sync",
            dayId = "2026-03-06",
            startTime = 1000L,
            endTime = 2000L,
            syncEnabled = true,
            syncStatus = SyncStatus.SYNCED,
            googleEventId = "provider-1",
            providerId = "GOOGLE",
            providerEventId = "provider-1"
        )

        val resolution = buildSmartLinkEventAliasResolution(
            displayEventIds = listOf("provider-1"),
            matchingLocalEvents = listOf(syncedLocalEvent)
        )

        assertEquals(setOf("provider-1", "local-1"), resolution.queryEventIds.toSet())
        assertEquals("provider-1", resolution.aliasToDisplayEventId["provider-1"])
        assertEquals("provider-1", resolution.aliasToDisplayEventId["local-1"])
    }

    @Test
    fun buildSmartLinkEventAliasResolution_mapsSyncedProviderAliasBackToDisplayedLocalId() {
        val syncedLocalEvent = LocalEvent(
            id = "local-1",
            title = "Project Sync",
            dayId = "2026-03-06",
            startTime = 1000L,
            endTime = 2000L,
            syncEnabled = true,
            syncStatus = SyncStatus.SYNCED,
            googleEventId = "provider-1",
            providerId = "GOOGLE",
            providerEventId = "provider-1"
        )

        val resolution = buildSmartLinkEventAliasResolution(
            displayEventIds = listOf("local-1"),
            matchingLocalEvents = listOf(syncedLocalEvent)
        )

        assertEquals(setOf("local-1", "provider-1"), resolution.queryEventIds.toSet())
        assertEquals("local-1", resolution.aliasToDisplayEventId["local-1"])
        assertEquals("local-1", resolution.aliasToDisplayEventId["provider-1"])
    }

    @Test
    fun buildEventBadgeData_mapsLegacyLocalLinksToDisplayedProviderEventId() {
        val syncedLocalEvent = LocalEvent(
            id = "local-1",
            title = "Project Sync",
            dayId = "2026-03-06",
            startTime = 1000L,
            endTime = 2000L,
            syncEnabled = true,
            syncStatus = SyncStatus.SYNCED,
            googleEventId = "provider-1",
            providerId = "GOOGLE",
            providerEventId = "provider-1"
        )
        val resolution = buildSmartLinkEventAliasResolution(
            displayEventIds = listOf("provider-1"),
            matchingLocalEvents = listOf(syncedLocalEvent)
        )

        val badgeData = buildEventBadgeData(
            links = listOf(
                badgeLink(
                    noteUniqueId = "note-legacy",
                    linkedEventId = "local-1",
                    isManualLink = false,
                    confidence = "MEDIUM",
                    updatedAt = 100L,
                    noteTimestamp = 1000L
                ),
                badgeLink(
                    noteUniqueId = "note-current",
                    linkedEventId = "provider-1",
                    isManualLink = true,
                    confidence = "LOW",
                    updatedAt = 50L,
                    noteTimestamp = 500L,
                    needsAttention = true
                )
            ),
            aliasToDisplayEventId = resolution.aliasToDisplayEventId
        )

        assertEquals(2, badgeData.noteCounts["provider-1"])
        assertEquals(setOf("provider-1"), badgeData.eventsWithAttention)
        assertEquals("note-current", badgeData.eventToNoteLinks["provider-1"])
        assertNull(badgeData.noteCounts["local-1"])
    }

    @Test
    fun rebindEventReferencesInLink_promotesActiveAndSecondaryIds() {
        val existing = BooxNoteLink(
            id = "link-1",
            noteUniqueId = "note-1",
            linkedEventId = "local-1",
            linkedDayDate = "2026-03-06",
            isManualLink = true,
            confidence = "HIGH",
            confidenceScore = 99,
            matchReasons = "[\"AI_SUGGESTED_MATCH\"]",
            suggestedEventIds = "[\"local-1\",\"provider-1\",\"event-2\"]",
            dismissedEventIds = "[\"local-1\",\"event-9\"]",
            needsAttention = false,
            createdAt = 10L,
            updatedAt = 20L,
            lastReviewedAt = 30L
        )

        val updated = rebindEventReferencesInLink(
            existingLink = existing,
            eventIdReplacements = mapOf("local-1" to "provider-1"),
            timestamp = 99L
        )

        requireNotNull(updated)
        assertEquals("provider-1", updated.linkedEventId)
        assertEquals("[\"provider-1\",\"event-2\"]", updated.suggestedEventIds)
        assertEquals("[\"provider-1\",\"event-9\"]", updated.dismissedEventIds)
        assertEquals(99L, updated.updatedAt)
        assertTrue(updated.isManualLink)
        assertEquals("HIGH", updated.confidence)
    }

    @Test
    fun rebindEventReferencesInLink_returnsNullWhenNothingMatches() {
        val existing = BooxNoteLink(
            id = "link-1",
            noteUniqueId = "note-1",
            linkedEventId = "provider-1",
            linkedDayDate = "2026-03-06",
            isManualLink = false,
            confidence = "MEDIUM",
            confidenceScore = 55,
            matchReasons = null,
            suggestedEventIds = "[\"event-2\"]",
            dismissedEventIds = "[\"event-9\"]",
            needsAttention = true,
            createdAt = 10L,
            updatedAt = 20L,
            lastReviewedAt = null
        )

        val updated = rebindEventReferencesInLink(
            existingLink = existing,
            eventIdReplacements = mapOf("local-1" to "provider-1"),
            timestamp = 99L
        )

        assertNull(updated)
    }

    private fun badgeLink(
        noteUniqueId: String,
        linkedEventId: String,
        isManualLink: Boolean,
        confidence: String,
        updatedAt: Long,
        noteTimestamp: Long,
        needsAttention: Boolean = false
    ): BooxNoteLinkWithTitle {
        return BooxNoteLinkWithTitle(
            id = "link-$noteUniqueId",
            noteUniqueId = noteUniqueId,
            linkedEventId = linkedEventId,
            linkedDayDate = "2026-03-06",
            isManualLink = isManualLink,
            confidence = confidence,
            confidenceScore = 0,
            matchReasons = null,
            suggestedEventIds = null,
            dismissedEventIds = null,
            needsAttention = needsAttention,
            createdAt = 1L,
            updatedAt = updatedAt,
            lastReviewedAt = null,
            decisionMode = "LEGACY",
            engineVersion = null,
            dataQualityFlagsJson = null,
            noteTitle = noteUniqueId,
            noteTimestamp = noteTimestamp,
            notePageCount = 2
        )
    }
}
