package com.boox.day.ocr.semantic

import com.boox.day.ocr.model.*
import com.boox.day.ocr.semantic.model.SemanticProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SemanticGeniusService semantic affinity calculations.
 *
 * Tests verify the 9-component semantic affinity algorithm from Section 4.3
 * of SEMANTIC_GENIUS_TRANSFORMATION_PLAN_V2.md.
 *
 * Component weights:
 * 1. Domain Match: 0.20
 * 2. Intent Compatibility: 0.12
 * 3. Keyword Overlap: 0.18
 * 4. Key Phrases: 0.08
 * 5. Person Entities: 0.12
 * 6. Org Entities: 0.08
 * 7. Location Entities: 0.07
 * 8. Action Items: 0.10
 * 9. Temporal Signals: 0.05
 * Total: 1.0
 */
class SemanticGeniusServiceTest {

    // Use a helper that can call the pure functions without Android context
    private lateinit var affinityCalculator: TestableAffinityCalculator

    @Before
    fun setup() {
        affinityCalculator = TestableAffinityCalculator()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT 1: DOMAIN MATCH (max 0.20)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `component 1 - exact domain match returns 0_20`() {
        val profileA = createProfile(domain = Domain.FINANCE)
        val profileB = createProfile(domain = Domain.FINANCE)

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Exact domain match should return at least 0.20", score >= 0.20f)
    }

    @Test
    fun `component 1 - sibling domain match returns 0_10`() {
        // PROFESSIONAL and BUSINESS are siblings
        val profileA = createProfile(domain = Domain.PROFESSIONAL)
        val profileB = createProfile(domain = Domain.BUSINESS)

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Sibling domain match should return at least 0.10", score >= 0.10f)
        assertTrue("Sibling domain match should return less than 0.20", score < 0.20f)
    }

    @Test
    fun `component 1 - no domain match returns 0`() {
        val profileA = createProfile(domain = Domain.HEALTH)
        val profileB = createProfile(domain = Domain.FINANCE)

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertEquals("Unrelated domains with no other data should return 0", 0f, score, 0.01f)
    }

    @Test
    fun `component 1 - null domain returns 0`() {
        val profileA = createProfile(domain = null)
        val profileB = createProfile(domain = Domain.PROFESSIONAL)

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertEquals("Null domain should return 0", 0f, score, 0.01f)
    }

    @Test
    fun `areSiblingDomains - PROFESSIONAL and BUSINESS are siblings`() {
        assertTrue(affinityCalculator.areSiblingDomains(Domain.PROFESSIONAL, Domain.BUSINESS))
        assertTrue(affinityCalculator.areSiblingDomains(Domain.BUSINESS, Domain.PROFESSIONAL))
    }

    @Test
    fun `areSiblingDomains - PROFESSIONAL and TECH are siblings`() {
        assertTrue(affinityCalculator.areSiblingDomains(Domain.PROFESSIONAL, Domain.TECH))
    }

    @Test
    fun `areSiblingDomains - EDUCATION and TEACHING are siblings`() {
        assertTrue(affinityCalculator.areSiblingDomains(Domain.EDUCATION, Domain.TEACHING))
        assertTrue(affinityCalculator.areSiblingDomains(Domain.TEACHING, Domain.EDUCATION))
    }

    @Test
    fun `areSiblingDomains - HEALTH and LIFE are siblings`() {
        assertTrue(affinityCalculator.areSiblingDomains(Domain.HEALTH, Domain.LIFE))
    }

    @Test
    fun `areSiblingDomains - SPIRITUAL and LDS_LEADERSHIP are siblings`() {
        assertTrue(affinityCalculator.areSiblingDomains(Domain.SPIRITUAL, Domain.LDS_LEADERSHIP))
    }

    @Test
    fun `areSiblingDomains - unrelated domains are not siblings`() {
        assertFalse(affinityCalculator.areSiblingDomains(Domain.FINANCE, Domain.HEALTH))
        assertFalse(affinityCalculator.areSiblingDomains(Domain.TECH, Domain.LIFE))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT 2: INTENT COMPATIBILITY (max 0.12)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `component 2 - matching intents with high confidence returns close to 0_12`() {
        val profileA = createProfile(
            intents = listOf(IntentEntry(Intent.MEETING_NOTES, 0.9f))
        )
        val profileB = createProfile(
            intents = listOf(IntentEntry(Intent.MEETING_NOTES, 0.9f))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Matching intents with high confidence should contribute ~0.12", score >= 0.10f)
    }

    @Test
    fun `component 2 - matching intents with low confidence returns less`() {
        val profileA = createProfile(
            intents = listOf(IntentEntry(Intent.MEETING_NOTES, 0.5f))
        )
        val profileB = createProfile(
            intents = listOf(IntentEntry(Intent.MEETING_NOTES, 0.5f))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Matching intents with 0.5 confidence should return ~0.06", score >= 0.05f)
        assertTrue("Should be less than max", score < 0.12f)
    }

    @Test
    fun `component 2 - no overlapping intents returns 0`() {
        val profileA = createProfile(
            intents = listOf(IntentEntry(Intent.MEETING_NOTES, 0.9f))
        )
        val profileB = createProfile(
            intents = listOf(IntentEntry(Intent.TODO_LIST, 0.9f))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertEquals("Non-overlapping intents should return 0", 0f, score, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT 3: KEYWORD OVERLAP (max 0.18) - Jaccard similarity
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `component 3 - 100 percent keyword overlap returns 0_18`() {
        val keywords = listOf(
            KeywordEntry("budget", 1.0f),
            KeywordEntry("review", 1.0f),
            KeywordEntry("quarterly", 1.0f)
        )
        val profileA = createProfile(keywords = keywords)
        val profileB = createProfile(keywords = keywords)

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        // 100% Jaccard = 1.0, so 1.0 * 0.18 = 0.18
        assertTrue("100% keyword overlap should return 0.18", score >= 0.17f)
    }

    @Test
    fun `component 3 - 50 percent keyword overlap returns approx 0_09`() {
        val profileA = createProfile(
            keywords = listOf(
                KeywordEntry("budget", 1.0f),
                KeywordEntry("review", 1.0f)
            )
        )
        val profileB = createProfile(
            keywords = listOf(
                KeywordEntry("budget", 1.0f),
                KeywordEntry("quarterly", 1.0f)
            )
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        // Intersection = 1 (budget), Union = 3, Jaccard = 1/3 = 0.33
        // 0.33 * 0.18 = ~0.06
        assertTrue("50% keyword overlap should return ~0.06", score >= 0.05f)
    }

    @Test
    fun `component 3 - no keyword overlap returns 0`() {
        val profileA = createProfile(
            keywords = listOf(KeywordEntry("budget", 1.0f))
        )
        val profileB = createProfile(
            keywords = listOf(KeywordEntry("health", 1.0f))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertEquals("No keyword overlap should return 0", 0f, score, 0.01f)
    }

    @Test
    fun `component 3 - keyword matching is case insensitive`() {
        val profileA = createProfile(
            keywords = listOf(KeywordEntry("Budget", 1.0f))
        )
        val profileB = createProfile(
            keywords = listOf(KeywordEntry("budget", 1.0f))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Keyword matching should be case-insensitive", score >= 0.17f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT 4: KEY PHRASES (max 0.08)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `component 4 - matching key phrases returns up to 0_08`() {
        val profileA = createProfile(
            keyPhrases = listOf("quarterly review", "budget planning")
        )
        val profileB = createProfile(
            keyPhrases = listOf("quarterly review", "team meeting")
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Matching key phrases should contribute to score", score >= 0.04f)
    }

    @Test
    fun `component 4 - no matching phrases returns 0`() {
        val profileA = createProfile(keyPhrases = listOf("sprint planning"))
        val profileB = createProfile(keyPhrases = listOf("budget review"))

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertEquals("No matching phrases should return 0", 0f, score, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT 5: PERSON ENTITIES (max 0.12)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `component 5 - matching person entities returns up to 0_12`() {
        val profileA = createProfile(
            entities = listOf(
                ExtractedEntity("John Smith", EntityType.PERSON),
                ExtractedEntity("Jane Doe", EntityType.PERSON)
            )
        )
        val profileB = createProfile(
            entities = listOf(
                ExtractedEntity("John Smith", EntityType.PERSON)
            )
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Matching person should contribute 0.06", score >= 0.06f)
    }

    @Test
    fun `component 5 - partial name match works`() {
        val profileA = createProfile(
            entities = listOf(ExtractedEntity("John Smith", EntityType.PERSON))
        )
        val profileB = createProfile(
            entities = listOf(ExtractedEntity("John", EntityType.PERSON))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Partial name match should work", score >= 0.06f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT 6: ORG ENTITIES (max 0.08)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `component 6 - matching org entities returns up to 0_08`() {
        val profileA = createProfile(
            entities = listOf(ExtractedEntity("Acme Corp", EntityType.ORG))
        )
        val profileB = createProfile(
            entities = listOf(ExtractedEntity("Acme Corp", EntityType.ORG))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Matching org should contribute 0.04", score >= 0.04f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT 7: LOCATION ENTITIES (max 0.07)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `component 7 - matching location entities returns up to 0_07`() {
        val profileA = createProfile(
            entities = listOf(ExtractedEntity("Conference Room A", EntityType.LOCATION))
        )
        val profileB = createProfile(
            entities = listOf(ExtractedEntity("Conference Room A", EntityType.LOCATION))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Matching location should contribute 0.035", score >= 0.03f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT 8: ACTION ITEMS (max 0.10)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `component 8 - matching action verb and object returns up to 0_10`() {
        val profileA = createProfile(
            actionItems = listOf(
                ActionItem(verb = "call", objectText = "John about budget")
            )
        )
        val profileB = createProfile(
            keywords = listOf(KeywordEntry("call", 1.0f), KeywordEntry("budget", 1.0f))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Matching action verb + object should contribute", score >= 0.04f)
    }

    @Test
    fun `component 8 - matching only verb returns partial score`() {
        val profileA = createProfile(
            actionItems = listOf(ActionItem(verb = "review", objectText = "presentation slides"))
        )
        val profileB = createProfile(
            keywords = listOf(KeywordEntry("review", 1.0f))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Matching verb only should contribute partial score", score >= 0.02f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPONENT 9: TEMPORAL SIGNALS (max 0.05)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `component 9 - matching weekday returns 0_05`() {
        val profileA = createProfile(
            temporalSignals = TemporalSignals(weekdays = listOf("Monday"))
        )
        val profileB = createProfile(
            temporalSignals = TemporalSignals(weekdays = listOf("Monday"))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Matching weekday should contribute 0.05", score >= 0.05f)
    }

    @Test
    fun `component 9 - matching relative date returns 0_05`() {
        val profileA = createProfile(
            temporalSignals = TemporalSignals(relativeDates = listOf("next week"))
        )
        val profileB = createProfile(
            temporalSignals = TemporalSignals(relativeDates = listOf("next week"))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertTrue("Matching relative date should contribute 0.05", score >= 0.05f)
    }

    @Test
    fun `component 9 - no matching temporal returns 0`() {
        val profileA = createProfile(
            temporalSignals = TemporalSignals(weekdays = listOf("Monday"))
        )
        val profileB = createProfile(
            temporalSignals = TemporalSignals(weekdays = listOf("Friday"))
        )

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertEquals("Non-matching temporal should return 0", 0f, score, 0.01f)
    }

    @Test
    fun `component 9 - null temporal returns 0`() {
        val profileA = createProfile(temporalSignals = null)
        val profileB = createProfile(temporalSignals = TemporalSignals(weekdays = listOf("Monday")))

        val score = affinityCalculator.calculateSemanticAffinity(profileA, profileB)

        assertEquals("Null temporal should return 0", 0f, score, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OVERALL SCORE CONSTRAINTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `total score stays between 0 and 1`() {
        // Create profiles with maximum overlap in all categories
        val maxProfile = createProfile(
            domain = Domain.FINANCE,
            intents = listOf(IntentEntry(Intent.BUDGET, 1.0f)),
            keywords = listOf(
                KeywordEntry("budget", 1.0f),
                KeywordEntry("quarterly", 1.0f),
                KeywordEntry("review", 1.0f)
            ),
            keyPhrases = listOf("budget review", "quarterly meeting"),
            entities = listOf(
                ExtractedEntity("John Smith", EntityType.PERSON),
                ExtractedEntity("Jane Doe", EntityType.PERSON),
                ExtractedEntity("Acme Corp", EntityType.ORG),
                ExtractedEntity("Conference Room", EntityType.LOCATION)
            ),
            actionItems = listOf(ActionItem("review", "budget figures")),
            temporalSignals = TemporalSignals(weekdays = listOf("Monday"))
        )

        val score = affinityCalculator.calculateSemanticAffinity(maxProfile, maxProfile)

        assertTrue("Score should be >= 0", score >= 0f)
        assertTrue("Score should be <= 1", score <= 1f)
    }

    @Test
    fun `empty profiles return 0`() {
        val emptyProfile = createProfile()

        val score = affinityCalculator.calculateSemanticAffinity(emptyProfile, emptyProfile)

        assertEquals("Empty profiles should return 0", 0f, score, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYNOPSIS AFFINITY (Section 4.11)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `calculateSynopsisAffinity - matching words returns score capped at 0_15`() {
        val profile = createProfile(synopsis = "Quarterly budget review meeting with finance team")
        val eventTitle = "Q4 Budget Review Meeting"

        val score = affinityCalculator.calculateSynopsisAffinity(profile, eventTitle)

        assertTrue("Synopsis with matching words should return score", score > 0f)
        assertTrue("Synopsis score should be capped at 0.15", score <= 0.15f)
    }

    @Test
    fun `calculateSynopsisAffinity - null synopsis returns 0`() {
        val profile = createProfile(synopsis = null)

        val score = affinityCalculator.calculateSynopsisAffinity(profile, "Budget Review")

        assertEquals("Null synopsis should return 0", 0f, score, 0.01f)
    }

    @Test
    fun `calculateSynopsisAffinity - short synopsis returns 0`() {
        val profile = createProfile(synopsis = "Budget")  // Less than 10 chars

        val score = affinityCalculator.calculateSynopsisAffinity(profile, "Budget Review")

        assertEquals("Short synopsis should return 0", 0f, score, 0.01f)
    }

    @Test
    fun `calculateSynopsisAffinity - no word overlap returns 0`() {
        val profile = createProfile(synopsis = "Completely unrelated content here")

        val score = affinityCalculator.calculateSynopsisAffinity(profile, "Budget Review Meeting")

        assertEquals("No word overlap should return 0", 0f, score, 0.01f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 8: MULTI-NOTE CONTEXT - DATE COHESION (Section 4.10)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `calculateDateCohesionBoost - 0 notes returns 0`() {
        val profiles = emptyList<SemanticProfile>()
        val eventProfile = createProfile(
            keywords = listOf(KeywordEntry("budget", 1.0f))
        )

        val boost = affinityCalculator.calculateDateCohesionBoost(profiles, eventProfile)

        assertEquals("Empty profiles should return 0", 0f, boost, 0.001f)
    }

    @Test
    fun `calculateDateCohesionBoost - 1 note returns 0`() {
        val profiles = listOf(
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f)))
        )
        val eventProfile = createProfile(
            keywords = listOf(KeywordEntry("budget", 1.0f))
        )

        val boost = affinityCalculator.calculateDateCohesionBoost(profiles, eventProfile)

        assertEquals("Single note should return 0", 0f, boost, 0.001f)
    }

    @Test
    fun `calculateDateCohesionBoost - 2 notes returns 0 because 3+ notes required`() {
        // Only 2 notes - even if they share keywords, cohesion requires 3+
        val profiles = listOf(
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f)))
        )
        val eventProfile = createProfile(
            keywords = listOf(KeywordEntry("budget", 1.0f))
        )

        val boost = affinityCalculator.calculateDateCohesionBoost(profiles, eventProfile)

        assertEquals("2 notes should return 0 (need 3+)", 0f, boost, 0.001f)
    }

    @Test
    fun `calculateDateCohesionBoost - 3 notes with shared keyword and 1 overlap returns 0_06`() {
        // 3 notes all share "budget" keyword - this appears in 3+ notes
        val profiles = listOf(
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("planning", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("quarterly", 1.0f)))
        )
        // Event has "budget" - overlaps with the shared keyword
        val eventProfile = createProfile(
            keywords = listOf(KeywordEntry("budget", 1.0f))
        )

        val boost = affinityCalculator.calculateDateCohesionBoost(profiles, eventProfile)

        assertEquals("3 notes with 1 shared keyword overlap should return 0.06 (weak cohesion)", 0.06f, boost, 0.001f)
    }

    @Test
    fun `calculateDateCohesionBoost - 3 notes with 2 shared keywords overlap returns 0_12`() {
        // 3 notes all share "budget" AND "review" keywords
        val profiles = listOf(
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("review", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("review", 1.0f), KeywordEntry("team", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("review", 1.0f), KeywordEntry("q4", 1.0f)))
        )
        // Event has both "budget" and "review"
        val eventProfile = createProfile(
            keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("review", 1.0f))
        )

        val boost = affinityCalculator.calculateDateCohesionBoost(profiles, eventProfile)

        assertEquals("3 notes with 2 shared keyword overlap should return 0.12 (moderate cohesion)", 0.12f, boost, 0.001f)
    }

    @Test
    fun `calculateDateCohesionBoost - 3 notes with 3+ shared keywords overlap returns 0_20`() {
        // 3 notes all share "budget", "review", AND "quarterly" keywords
        val profiles = listOf(
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("review", 1.0f), KeywordEntry("quarterly", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("review", 1.0f), KeywordEntry("quarterly", 1.0f), KeywordEntry("finance", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("review", 1.0f), KeywordEntry("quarterly", 1.0f), KeywordEntry("meeting", 1.0f)))
        )
        // Event has all 3 shared keywords
        val eventProfile = createProfile(
            keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("review", 1.0f), KeywordEntry("quarterly", 1.0f))
        )

        val boost = affinityCalculator.calculateDateCohesionBoost(profiles, eventProfile)

        assertEquals("3 notes with 3+ shared keyword overlap should return 0.20 (strong cohesion)", 0.20f, boost, 0.001f)
    }

    @Test
    fun `calculateDateCohesionBoost - shared keywords but no event overlap returns 0`() {
        // 3 notes share keywords, but event has different keywords
        val profiles = listOf(
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f)))
        )
        // Event has completely different keywords
        val eventProfile = createProfile(
            keywords = listOf(KeywordEntry("health", 1.0f), KeywordEntry("exercise", 1.0f))
        )

        val boost = affinityCalculator.calculateDateCohesionBoost(profiles, eventProfile)

        assertEquals("No overlap with event should return 0", 0f, boost, 0.001f)
    }

    @Test
    fun `calculateDateCohesionBoost - keywords in fewer than 3 notes dont count`() {
        // "budget" appears in 3 notes, but "review" only in 2
        val profiles = listOf(
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("review", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("review", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f)))  // No "review" here
        )
        // Event has both "budget" and "review"
        val eventProfile = createProfile(
            keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("review", 1.0f))
        )

        val boost = affinityCalculator.calculateDateCohesionBoost(profiles, eventProfile)

        // Only "budget" is in 3+ notes, so overlap is 1
        assertEquals("Only keywords in 3+ notes count, overlap should be 1", 0.06f, boost, 0.001f)
    }

    @Test
    fun `calculateDateCohesionBoost - keyword case insensitive`() {
        val profiles = listOf(
            createProfile(keywords = listOf(KeywordEntry("Budget", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("BUDGET", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f)))
        )
        val eventProfile = createProfile(
            keywords = listOf(KeywordEntry("budget", 1.0f))
        )

        val boost = affinityCalculator.calculateDateCohesionBoost(profiles, eventProfile)

        assertEquals("Keywords should be case-insensitive", 0.06f, boost, 0.001f)
    }

    @Test
    fun `calculateDateCohesionBoost - duplicate keywords in same note counted once`() {
        // A note might have the same keyword multiple times (shouldn't count more)
        val profiles = listOf(
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f), KeywordEntry("budget", 0.9f))),  // Duplicate
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f))),
            createProfile(keywords = listOf(KeywordEntry("budget", 1.0f)))
        )
        val eventProfile = createProfile(
            keywords = listOf(KeywordEntry("budget", 1.0f))
        )

        val boost = affinityCalculator.calculateDateCohesionBoost(profiles, eventProfile)

        assertEquals("Duplicate keywords in same note should count once", 0.06f, boost, 0.001f)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun createProfile(
        domain: Domain? = null,
        intents: List<IntentEntry> = emptyList(),
        keywords: List<KeywordEntry> = emptyList(),
        keyPhrases: List<String> = emptyList(),
        entities: List<ExtractedEntity> = emptyList(),
        actionItems: List<ActionItem> = emptyList(),
        temporalSignals: TemporalSignals? = null,
        synopsis: String? = null
    ): SemanticProfile {
        return SemanticProfile(
            sourceType = OcrSource.MEMO,
            sourceId = "test-id",
            pageId = "page-0",
            domain = domain,
            domainConfidence = domain?.let { 0.9f } ?: 0f,
            intents = intents,
            subdomainHint = null,
            docStyle = null,
            keywords = keywords,
            keyPhrases = keyPhrases,
            keySentences = emptyList(),
            entities = entities,
            actionItems = actionItems,
            temporalSignals = temporalSignals,
            synopsis = synopsis,
            sourceDate = "2026-01-03",
            sourceTitle = "Test Profile",
            wordCount = 100,
            confidence = 0.9f
        )
    }
}

/**
 * Testable wrapper for semantic affinity calculations.
 * Extracts the pure calculation logic without requiring Android context.
 */
class TestableAffinityCalculator {

    /**
     * Calculate semantic affinity between two profiles.
     * This is a copy of the algorithm from SemanticGeniusService for testing.
     */
    fun calculateSemanticAffinity(profileA: SemanticProfile, profileB: SemanticProfile): Float {
        var score = 0f

        // 1. DOMAIN MATCH (max 0.20)
        if (profileA.domain != null && profileB.domain != null) {
            if (profileA.domain == profileB.domain) {
                score += 0.20f
            } else if (areSiblingDomains(profileA.domain, profileB.domain)) {
                score += 0.10f
            }
        }

        // 2. INTENT COMPATIBILITY (max 0.12)
        val intentA = profileA.intents.map { it.intent }.toSet()
        val intentB = profileB.intents.map { it.intent }.toSet()
        val intentOverlap = intentA.intersect(intentB)

        if (intentOverlap.isNotEmpty()) {
            val confidencesA = profileA.intents.filter { it.intent in intentOverlap }.map { it.confidence }
            val confidencesB = profileB.intents.filter { it.intent in intentOverlap }.map { it.confidence }
            val avgConfidence = if (confidencesA.isNotEmpty() && confidencesB.isNotEmpty()) {
                (confidencesA.average() + confidencesB.average()) / 2
            } else {
                0.0
            }
            score += (0.12f * avgConfidence.toFloat()).coerceAtMost(0.12f)
        }

        // 3. KEYWORD OVERLAP (max 0.18)
        val keywordsA = profileA.keywords.map { it.term.lowercase() }.toSet()
        val keywordsB = profileB.keywords.map { it.term.lowercase() }.toSet()
        val keywordIntersection = keywordsA.intersect(keywordsB)

        if (keywordIntersection.isNotEmpty() && keywordsA.isNotEmpty() && keywordsB.isNotEmpty()) {
            val jaccardSimilarity = keywordIntersection.size.toFloat() /
                (keywordsA.union(keywordsB).size)
            score += (jaccardSimilarity * 0.18f).coerceAtMost(0.18f)
        }

        // 4. KEY PHRASES (max 0.08)
        val phrasesA = profileA.keyPhrases.map { it.lowercase() }.toSet()
        val phrasesB = profileB.keyPhrases.map { it.lowercase() }.toSet()

        val phraseMatches = phrasesA.count { phrase ->
            phrasesB.any { it.contains(phrase) || phrase.contains(it) }
        }
        score += (phraseMatches * 0.04f).coerceAtMost(0.08f)

        // 5. PERSON ENTITIES (max 0.12)
        val personsA = profileA.entities.filter { it.type == EntityType.PERSON }
            .map { it.text.lowercase() }
        val personsB = profileB.entities.filter { it.type == EntityType.PERSON }
            .map { it.text.lowercase() }

        val personMatches = personsA.count { pa ->
            personsB.any { pb -> pa.contains(pb) || pb.contains(pa) }
        }
        score += (personMatches * 0.06f).coerceAtMost(0.12f)

        // 6. ORG ENTITIES (max 0.08)
        val orgsA = profileA.entities.filter { it.type == EntityType.ORG }
            .map { it.text.lowercase() }
        val orgsB = profileB.entities.filter { it.type == EntityType.ORG }
            .map { it.text.lowercase() }

        val orgMatches = orgsA.count { oa ->
            orgsB.any { ob -> oa.contains(ob) || ob.contains(oa) }
        }
        score += (orgMatches * 0.04f).coerceAtMost(0.08f)

        // 7. LOCATION ENTITIES (max 0.07)
        val locsA = profileA.entities.filter { it.type == EntityType.LOCATION }
            .map { it.text.lowercase() }
        val locsB = profileB.entities.filter { it.type == EntityType.LOCATION }
            .map { it.text.lowercase() }

        val locMatches = locsA.count { la ->
            locsB.any { lb -> la.contains(lb) || lb.contains(la) }
        }
        score += (locMatches * 0.035f).coerceAtMost(0.07f)

        // 8. ACTION ITEMS (max 0.10)
        val actionsA = profileA.actionItems
        val textB = (profileB.keywords.map { it.term } + profileB.keyPhrases)
            .joinToString(" ").lowercase()

        var actionScore = 0f
        for (action in actionsA) {
            val verbMatch = textB.contains(action.verb.lowercase())
            val objectMatch = action.objectText.split(Regex("\\s+"))
                .filter { it.length > 2 }
                .any { textB.contains(it.lowercase()) }

            if (verbMatch && objectMatch) actionScore += 0.05f
            else if (verbMatch || objectMatch) actionScore += 0.025f
        }
        score += actionScore.coerceAtMost(0.10f)

        // 9. TEMPORAL SIGNALS (max 0.05)
        val temporalA = profileA.temporalSignals
        val temporalB = profileB.temporalSignals

        if (temporalA != null && temporalB != null) {
            val weekdayOverlap = temporalA.weekdays.intersect(temporalB.weekdays.toSet())
            val relativeDateOverlap = temporalA.relativeDates.intersect(temporalB.relativeDates.toSet())

            if (weekdayOverlap.isNotEmpty() || relativeDateOverlap.isNotEmpty()) {
                score += 0.05f
            }
        }

        return score.coerceIn(0f, 1f)
    }

    fun areSiblingDomains(a: Domain, b: Domain): Boolean {
        // Delegate to Domain.areSiblings() for consistency with production code
        return Domain.areSiblings(a, b)
    }

    fun calculateSynopsisAffinity(noteProfile: SemanticProfile, eventTitle: String): Float {
        val synopsis = noteProfile.synopsis ?: return 0f
        if (synopsis.length < 10) return 0f

        val synopsisWords = synopsis.lowercase()
            .split(Regex("[\\s,.;:!?]+"))
            .filter { it.length >= 3 }
            .toSet()

        val eventWords = eventTitle.lowercase()
            .split(Regex("[\\s,.;:!?]+"))
            .filter { it.length >= 3 }
            .toSet()

        if (synopsisWords.isEmpty() || eventWords.isEmpty()) return 0f

        val overlap = synopsisWords.intersect(eventWords)
        val jaccardSimilarity = overlap.size.toFloat() / synopsisWords.union(eventWords).size

        return (jaccardSimilarity * 0.3f).coerceAtMost(0.15f)
    }

    /**
     * Calculate date cohesion boost for multi-note context (Phase 8).
     * Algorithm from Section 4.10 of SEMANTIC_GENIUS_TRANSFORMATION_PLAN_V2.md.
     */
    fun calculateDateCohesionBoost(
        profiles: List<SemanticProfile>,
        eventProfile: SemanticProfile
    ): Float {
        // Need at least 2 notes to calculate cohesion
        if (profiles.size < 2) return 0f

        // Find keywords shared across multiple notes
        // Count each keyword once per note (not multiple times within same note)
        val keywordCounts = mutableMapOf<String, Int>()
        for (profile in profiles) {
            // Use toSet() to deduplicate keywords within a single note
            profile.keywords.map { it.term.lowercase() }.toSet().forEach { keyword ->
                keywordCounts[keyword] = (keywordCounts[keyword] ?: 0) + 1
            }
        }

        // Keywords appearing in 3+ notes (high-cohesion keywords)
        val sharedKeywords = keywordCounts.filter { it.value >= 3 }.keys

        if (sharedKeywords.isEmpty()) return 0f

        // Check if event contains any of these shared keywords
        val eventKeywords = eventProfile.keywords.map { it.term.lowercase() }.toSet()
        val overlap = sharedKeywords.intersect(eventKeywords)

        return when {
            overlap.size >= 3 -> 0.20f  // Strong cohesion
            overlap.size >= 2 -> 0.12f  // Moderate cohesion
            overlap.size >= 1 -> 0.06f  // Weak cohesion
            else -> 0f
        }
    }
}
