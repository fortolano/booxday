package com.boox.day.ocr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Complete semantic extraction result from the 11-step pipeline.
 *
 * This bundle contains all extracted semantic information used for
 * Smart Link matching and intelligent search.
 *
 * All fields are serialized to JSON for database storage.
 */
@Serializable
data class SemanticBundle(
    /**
     * Document style classification (Step 2)
     */
    val docStyle: DocumentStyle = DocumentStyle.MIXED,

    // ═══════════════════════════════════════════════════════════════════════════
    // NEW: Two-tier Domain + Intent classification (v2)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Primary domain classification (v2).
     * What area of life is this note about?
     */
    val domain: Domain? = null,

    /**
     * Domain classification confidence (0.0 - 1.0).
     */
    val domainConfidence: Float = 0f,

    /**
     * Secondary intent classifications (v2).
     * What is the purpose of this note? (max 4, sorted by confidence)
     */
    val intents: List<IntentEntry> = emptyList(),

    /**
     * Optional subdomain hint (e.g., "christian", "islamic" for SPIRITUAL).
     */
    val subdomainHint: String? = null,

    /**
     * Full classification explanation JSON (for debugging).
     * Contains all signal traces, negative evidence, disambiguation, etc.
     */
    val classificationExplainJson: String? = null,

    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Extracted keywords with TF-IDF scores (Steps 3-4)
     * Top 12-25 keywords by relevance
     */
    val keywords: List<KeywordEntry> = emptyList(),

    /**
     * Key phrases (2-6 word n-grams) (Step 5)
     * Top 10-20 phrases containing keywords or entities
     */
    val keyPhrases: List<String> = emptyList(),

    /**
     * Key sentences for Smart Link context (Step 6)
     * Top 6-12 high-signal sentences
     */
    val keySentences: List<String> = emptyList(),

    /**
     * Named entities extracted via regex (Step 7)
     * DATE, TIME, EMAIL, PHONE, URL, MONEY, PERCENT, PERSON, ORG, LOCATION
     */
    val entities: List<ExtractedEntity> = emptyList(),

    /**
     * Action items with verb-object structure (Step 8)
     * Up to 15 action items
     */
    val actionItems: List<ActionItem> = emptyList(),

    /**
     * Temporal signals extracted from text (Step 9)
     */
    val temporalSignals: TemporalSignals = TemporalSignals(),

    /**
     * Topic classifications with scores (Step 10)
     * Top 3 topics
     */
    val topics: List<TopicEntry> = emptyList(),

    /**
     * Structured synopsis for Smart Link LLM (Step 11)
     * Max 900 characters
     */
    val synopsis: String = "",

    /**
     * Whether this is a low-signal extraction (text < 20 chars)
     */
    val isLowSignal: Boolean = false,

    /**
     * Semantic extraction version for re-indexing on algorithm upgrade.
     * v1 = Original 11-step pipeline
     * v2 = Added Domain + Intent classification
     */
    val semanticVersion: Int = 2
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Create a minimal bundle for low-signal text (< 20 chars)
         */
        fun lowSignal(): SemanticBundle = SemanticBundle(
            isLowSignal = true,
            docStyle = DocumentStyle.MIXED
        )

        /**
         * Deserialize from JSON string
         */
        fun fromJson(jsonString: String): SemanticBundle? {
            return try {
                json.decodeFromString<SemanticBundle>(jsonString)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Serialize to JSON string
     */
    fun toJson(): String = json.encodeToString(this)

    /**
     * Check if quality gates pass
     */
    fun passesQualityGates(): Boolean {
        if (isLowSignal) return true

        // keywordList not empty (unless low_signal)
        if (keywords.isEmpty()) return false

        // At least one of: entities, actionItems, or keySentences found
        if (entities.isEmpty() && actionItems.isEmpty() && keySentences.isEmpty()) return false

        // Synopsis ≤ 900 chars
        if (synopsis.length > 900) return false

        // No duplicates check (handled by extractors)
        return true
    }
}

/**
 * Keyword entry with TF-IDF score and position boosts
 */
@Serializable
data class KeywordEntry(
    val term: String,
    val score: Float,
    val frequency: Int = 1,
    val boostedBy: List<String> = emptyList()  // e.g., "title", "date_proximity", "bullet"
)

/**
 * Extracted named entity
 */
@Serializable
data class ExtractedEntity(
    val text: String,
    val type: EntityType,
    val confidence: Float = 1.0f,
    val startIndex: Int = -1,
    val endIndex: Int = -1
)

/**
 * Action item with verb-object structure
 */
@Serializable
data class ActionItem(
    val verb: String,
    val objectText: String,
    val dueDateCandidate: String? = null,
    val timeCandidate: String? = null,
    val assigneeCandidate: String? = null,
    val confidence: Float = 0.5f,
    val rawText: String = ""
)

/**
 * Temporal signals extracted from text
 */
@Serializable
data class TemporalSignals(
    val explicitDates: List<String> = emptyList(),
    val explicitTimes: List<String> = emptyList(),
    val relativeDates: List<String> = emptyList(),  // "tomorrow", "next week"
    val weekdays: List<String> = emptyList(),       // "Monday", "Friday"
    val dateRanges: List<String> = emptyList(),     // "Jan 5-10"
    val dominantDateCandidate: String? = null
)

/**
 * Topic entry with classification score (legacy, v1)
 */
@Serializable
data class TopicEntry(
    val topic: Topic,
    val score: Float
)

/**
 * Intent entry with classification confidence (v2)
 */
@Serializable
data class IntentEntry(
    val intent: Intent,
    val confidence: Float
)
