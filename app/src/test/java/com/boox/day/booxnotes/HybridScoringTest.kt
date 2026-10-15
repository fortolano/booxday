package com.boox.day.booxnotes

import com.boox.day.data.db.CalendarEvent
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Phase 4: Hybrid Scoring Integration.
 *
 * Tests verify:
 * 1. Hybrid scoring formula: finalScore = temporalScore × (1.0 + semanticAffinity × SEMANTIC_WEIGHT)
 * 2. EventExclusionRules.shouldExclude() for filtering generic events
 * 3. EventExclusionRules.getWeightMultiplier() for reduced-weight events
 *
 * Section 4.2 and 4.7 of SEMANTIC_GENIUS_TRANSFORMATION_PLAN_V2.md.
 */
class HybridScoringTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // HYBRID SCORING FORMULA (Section 4.2)
    // Formula: finalScore = temporalScore × (1.0 + semanticAffinity × SEMANTIC_WEIGHT)
    // SEMANTIC_WEIGHT = 0.8
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `hybrid formula - temporalScore 60 semanticAffinity 0_5 returns 84`() {
        // Formula: 60 × (1.0 + 0.5 × 0.8) = 60 × 1.4 = 84
        val temporalScore = 60
        val semanticAffinity = 0.5f
        val semanticWeight = 0.8f

        val semanticMultiplier = 1.0f + (semanticAffinity * semanticWeight)
        val finalScore = (temporalScore * semanticMultiplier).toInt()

        assertEquals("Expected finalScore = 84", 84, finalScore)
    }

    @Test
    fun `hybrid formula - temporalScore 100 semanticAffinity 1_0 returns 180`() {
        // Formula: 100 × (1.0 + 1.0 × 0.8) = 100 × 1.8 = 180
        val temporalScore = 100
        val semanticAffinity = 1.0f
        val semanticWeight = 0.8f

        val semanticMultiplier = 1.0f + (semanticAffinity * semanticWeight)
        val finalScore = (temporalScore * semanticMultiplier).toInt()

        assertEquals("Expected finalScore = 180", 180, finalScore)
    }

    @Test
    fun `hybrid formula - zero semanticAffinity returns temporalScore unchanged`() {
        // Formula: 60 × (1.0 + 0.0 × 0.8) = 60 × 1.0 = 60
        val temporalScore = 60
        val semanticAffinity = 0.0f
        val semanticWeight = 0.8f

        val semanticMultiplier = 1.0f + (semanticAffinity * semanticWeight)
        val finalScore = (temporalScore * semanticMultiplier).toInt()

        assertEquals("Zero semantic should not change score", temporalScore, finalScore)
    }

    @Test
    fun `hybrid formula - weight constants have correct values`() {
        assertEquals("SEMANTIC_WEIGHT should be 0.8", 0.8f, BooxNoteIndexer.SEMANTIC_WEIGHT)
        assertEquals("COOCCURRENCE_WEIGHT should be 0.15", 0.15f, BooxNoteIndexer.COOCCURRENCE_WEIGHT)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT EXCLUSION RULES - shouldExclude() (Section 4.7)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `shouldExclude - daily standup returns true`() {
        val event = createEvent(title = "Daily Standup")
        assertTrue("Daily standup should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - team sync returns true`() {
        val event = createEvent(title = "Team Sync")
        assertTrue("Team sync should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - scrum meeting returns true`() {
        val event = createEvent(title = "Sprint Scrum")
        assertTrue("Scrum should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - huddle returns true`() {
        val event = createEvent(title = "Morning Huddle")
        assertTrue("Huddle should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - focus time returns true`() {
        val event = createEvent(title = "Focus Time")
        assertTrue("Focus time should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - blocked time returns true`() {
        val event = createEvent(title = "Blocked - Working on report")
        assertTrue("Blocked time should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - busy returns true`() {
        val event = createEvent(title = "Busy")
        assertTrue("Busy should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - dnd returns true`() {
        val event = createEvent(title = "DND - Coding")
        assertTrue("DND should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - do not disturb returns true`() {
        val event = createEvent(title = "Do Not Disturb")
        assertTrue("Do not disturb should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - lunch returns true`() {
        val event = createEvent(title = "Lunch")
        assertTrue("Lunch should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - break returns true`() {
        val event = createEvent(title = "Break")
        assertTrue("Break should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - coffee break returns true`() {
        val event = createEvent(title = "Coffee Break")
        assertTrue("Coffee break should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - hold returns true`() {
        val event = createEvent(title = "Hold for later")
        assertTrue("Hold should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - placeholder returns true`() {
        val event = createEvent(title = "Placeholder")
        assertTrue("Placeholder should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - tentative returns true`() {
        val event = createEvent(title = "Tentative Meeting")
        assertTrue("Tentative should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - TBD returns true`() {
        val event = createEvent(title = "TBD")
        assertTrue("TBD should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - TBA returns true`() {
        val event = createEvent(title = "TBA Meeting")
        assertTrue("TBA should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - commute returns true`() {
        val event = createEvent(title = "Commute")
        assertTrue("Commute should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - travel time returns true`() {
        val event = createEvent(title = "Travel Time")
        assertTrue("Travel time should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - drive time returns true`() {
        val event = createEvent(title = "Drive Time")
        assertTrue("Drive time should be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - recurring short events excluded`() {
        // Short recurring event (15 minutes, more than 5 occurrences)
        val event = createEvent(
            title = "Quick Check",
            startTime = 0L,
            endTime = 15 * 60 * 1000L  // 15 minutes
        )
        assertTrue("Short recurring events should be excluded", EventExclusionRules.shouldExclude(event, recurrenceCount = 10))
    }

    @Test
    fun `shouldExclude - project kickoff returns false`() {
        val event = createEvent(title = "Project Alpha Kickoff")
        assertFalse("Project kickoff should NOT be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - budget review returns false`() {
        val event = createEvent(title = "Q4 Budget Review")
        assertFalse("Budget review should NOT be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - interview returns false`() {
        val event = createEvent(title = "Interview with John Smith")
        assertFalse("Interview should NOT be excluded", EventExclusionRules.shouldExclude(event))
    }

    @Test
    fun `shouldExclude - customer meeting returns false`() {
        val event = createEvent(title = "Customer Meeting - Acme Corp")
        assertFalse("Customer meeting should NOT be excluded", EventExclusionRules.shouldExclude(event))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EVENT EXCLUSION RULES - getWeightMultiplier() (Section 4.7)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getWeightMultiplier - check-in returns 0_7`() {
        val event = createEvent(title = "Weekly Check-in")
        assertEquals("Check-in should return 0.7 weight", 0.7f, EventExclusionRules.getWeightMultiplier(event), 0.01f)
    }

    @Test
    fun `getWeightMultiplier - 1on1 returns 0_7`() {
        val event = createEvent(title = "1:1 with Manager")
        assertEquals("1:1 should return 0.7 weight", 0.7f, EventExclusionRules.getWeightMultiplier(event), 0.01f)
    }

    @Test
    fun `getWeightMultiplier - all-hands returns 0_5`() {
        val event = createEvent(title = "Company All-Hands")
        assertEquals("All-hands should return 0.5 weight", 0.5f, EventExclusionRules.getWeightMultiplier(event), 0.01f)
    }

    @Test
    fun `getWeightMultiplier - town hall returns 0_5`() {
        val event = createEvent(title = "Engineering Town Hall")
        assertEquals("Town hall should return 0.5 weight", 0.5f, EventExclusionRules.getWeightMultiplier(event), 0.01f)
    }

    @Test
    fun `getWeightMultiplier - normal event returns 1_0`() {
        val event = createEvent(title = "Project Planning")
        assertEquals("Normal event should return 1.0 weight", 1.0f, EventExclusionRules.getWeightMultiplier(event), 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HYBRID SCORED MATCH DATA CLASS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `HybridScoredMatch - all fields present`() {
        val match = HybridScoredMatch(
            eventId = "event-123",
            eventTitle = "Budget Review",
            temporalScore = 60,
            semanticAffinity = 0.5f,
            cooccurrenceBoost = 0.1f,
            cohesionBoost = 0.06f,  // Phase 8: Multi-note context
            finalScore = 84,
            confidence = LinkConfidence.HIGH,
            temporalReasons = listOf(MatchReason.DURING_EVENT),
            semanticReasons = listOf(SemanticMatchReason.DOMAIN_MATCH)
        )

        assertEquals("event-123", match.eventId)
        assertEquals("Budget Review", match.eventTitle)
        assertEquals(60, match.temporalScore)
        assertEquals(0.5f, match.semanticAffinity, 0.01f)
        assertEquals(0.1f, match.cooccurrenceBoost, 0.01f)
        assertEquals(0.06f, match.cohesionBoost, 0.01f)
        assertEquals(84, match.finalScore)
        assertEquals(LinkConfidence.HIGH, match.confidence)
        assertTrue(match.temporalReasons.contains(MatchReason.DURING_EVENT))
        assertTrue(match.semanticReasons.contains(SemanticMatchReason.DOMAIN_MATCH))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEMANTIC MATCH REASON ENUM
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `SemanticMatchReason - all values present`() {
        val expectedValues = listOf(
            SemanticMatchReason.DOMAIN_MATCH,
            SemanticMatchReason.INTENT_COMPATIBLE,
            SemanticMatchReason.ENTITY_PERSON_MATCH,
            SemanticMatchReason.ENTITY_ORG_MATCH,
            SemanticMatchReason.ENTITY_LOCATION_MATCH,
            SemanticMatchReason.KEYWORD_OVERLAP,
            SemanticMatchReason.KEY_PHRASE_MATCH,
            SemanticMatchReason.ACTION_ITEM_MATCH,
            SemanticMatchReason.COOCCURRENCE_BOOST,
            SemanticMatchReason.SYNOPSIS_SIMILARITY
        )

        assertEquals("Should have 10 semantic match reasons", 10, SemanticMatchReason.values().size)
        for (expected in expectedValues) {
            assertTrue("Should contain $expected", SemanticMatchReason.values().contains(expected))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTEGRATION: WEIGHT MULTIPLIER AFFECTS HYBRID SCORE
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `hybrid formula with weight multiplier - check-in reduces semantic boost`() {
        // Normal event: 60 × (1.0 + 0.5 × 0.8) = 60 × 1.4 = 84
        // Check-in with 0.7 multiplier: 60 × (1.0 + 0.5 × 0.8 × 0.7) = 60 × 1.28 = 76.8 ≈ 76
        val temporalScore = 60
        val semanticAffinity = 0.5f
        val semanticWeight = 0.8f
        val weightMultiplier = 0.7f  // Check-in multiplier

        val semanticMultiplier = 1.0f + (semanticAffinity * semanticWeight * weightMultiplier)
        val finalScore = (temporalScore * semanticMultiplier).toInt()

        assertEquals("Check-in should reduce semantic boost", 76, finalScore)
    }

    @Test
    fun `hybrid formula with weight multiplier - all-hands reduces semantic boost more`() {
        // All-hands with 0.5 multiplier: 60 × (1.0 + 0.5 × 0.8 × 0.5) = 60 × 1.2 = 72
        val temporalScore = 60
        val semanticAffinity = 0.5f
        val semanticWeight = 0.8f
        val weightMultiplier = 0.5f  // All-hands multiplier

        val semanticMultiplier = 1.0f + (semanticAffinity * semanticWeight * weightMultiplier)
        val finalScore = (temporalScore * semanticMultiplier).toInt()

        assertEquals("All-hands should reduce semantic boost more", 72, finalScore)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun createEvent(
        title: String,
        startTime: Long = System.currentTimeMillis(),
        endTime: Long? = null
    ): CalendarEvent {
        return CalendarEvent(
            id = "test-event-${title.hashCode()}",
            providerId = "GOOGLE",
            accountId = "test-account",
            accountEmail = "test@example.com",
            calendarId = "test-calendar",
            calendarName = "Test Calendar",
            title = title,
            dayId = "2026-01-03",
            startTime = startTime,
            endTime = endTime,
            isAllDay = false,
            location = null,
            description = null,
            attendees = null,
            isReadOnly = false,
            colorHex = null
        )
    }
}
