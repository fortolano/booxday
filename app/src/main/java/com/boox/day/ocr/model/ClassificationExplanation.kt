package com.boox.day.ocr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Complete explainability output for classification debugging and tuning.
 *
 * Every classification decision is traceable to weighted signals.
 */
@Serializable
data class ClassificationExplanation(
    // ═══════════════════════════════════════════════════════════════════════════
    // INPUT (Phase 9b - Debug Observability)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Original raw input text (before any processing) */
    val inputText: String = "",

    /** Normalized text after OCR correction and cleaning */
    val normalizedText: String = "",

    // ═══════════════════════════════════════════════════════════════════════════
    // CORE RESULTS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Detected language ("en", "es", "unknown", etc.) */
    val languageHint: String,

    /** Classification mode used ("A" = full, "B" = structure-only, "C" = extended) */
    val modeUsed: String,

    /** Primary domain classification */
    val domain: Domain,

    /** Domain confidence (0.0 - 1.0) */
    val domainConfidence: Float,

    /** Detected intents, sorted by confidence (max 4) */
    val intents: List<IntentScore>,

    /** Optional subdomain hint (e.g., "christian", "islamic" for SPIRITUAL) */
    val subdomainHint: String? = null,

    // ═══════════════════════════════════════════════════════════════════════════
    // SIGNAL BREAKDOWN
    // ═══════════════════════════════════════════════════════════════════════════

    /** Structure signals (checkboxes, bullets, numbered lists, code blocks) */
    val structureSignals: List<StructureSignal> = emptyList(),

    /** Pattern signals (regex matches) */
    val patternSignals: List<PatternSignal> = emptyList(),

    /** Token signals (keyword/phrase matches with tiers) */
    val tokenSignals: List<TokenSignal> = emptyList(),

    // ═══════════════════════════════════════════════════════════════════════════
    // OVERRIDE TRACES (CRITICAL FOR DEBUGGING)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Dominant evidence rules that fired (e.g., "TECH_CODEBLOCK", "SPIRITUAL_SCRIPTURE") */
    val dominantEvidence: List<String> = emptyList(),

    /** Negative signal rules applied */
    val negativeEvidenceApplied: List<NegativeEvidenceTrace> = emptyList(),

    /** Ambiguous terms and how they were resolved */
    val ambiguousTermsResolved: List<AmbiguousTermResolution> = emptyList(),

    // ═══════════════════════════════════════════════════════════════════════════
    // PERSONALIZATION TRACES
    // ═══════════════════════════════════════════════════════════════════════════

    /** Per-user prior boosts applied (null if not used) */
    val priorBoosts: Map<String, Float>? = null,

    /** Why priors were used ("shortText", "lowConfidence", null if not used) */
    val priorReason: String? = null,

    // ═══════════════════════════════════════════════════════════════════════════
    // RAW SCORES (FOR TUNING)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Raw domain scores before priors/overrides */
    val rawScores: Map<String, Float> = emptyMap(),

    /** Final domain scores after all adjustments */
    val finalScores: Map<String, Float> = emptyMap(),

    // ═══════════════════════════════════════════════════════════════════════════
    // CANDIDATE RANKINGS (Phase 9b - Debug Observability)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Top domain candidates with scores (sorted by score descending) */
    val topDomainCandidates: List<DomainCandidate> = emptyList(),

    /** Top intent candidates with scores (sorted by score descending) */
    val topIntentCandidates: List<IntentCandidate> = emptyList(),

    /** Whether context-aware disambiguation was applied during classification */
    val disambiguationApplied: Boolean = false,

    // ═══════════════════════════════════════════════════════════════════════════
    // METADATA
    // ═══════════════════════════════════════════════════════════════════════════

    /** Classification engine version */
    val engineVersion: Int = 2,

    /** Processing time in milliseconds */
    val processingTimeMs: Long = 0
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonString: String): ClassificationExplanation? {
            return try {
                json.decodeFromString<ClassificationExplanation>(jsonString)
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Serialize to compact JSON for storage */
    fun toJson(): String = json.encodeToString(this)

    /** Generate human-readable summary for debugging */
    fun toDebugSummary(): String = buildString {
        appendLine("=== Classification: ${domain.name} (${(domainConfidence * 100).toInt()}%) ===")
        appendLine("Language: $languageHint | Mode: $modeUsed")

        if (dominantEvidence.isNotEmpty()) {
            appendLine("DOMINANT: ${dominantEvidence.joinToString()}")
        }

        appendLine("Intents: ${intents.take(3).joinToString { "${it.intent.name}(${(it.confidence * 100).toInt()}%)" }}")

        if (structureSignals.isNotEmpty()) {
            appendLine("Structure: ${structureSignals.joinToString { "${it.type}(${it.count})" }}")
        }

        if (tokenSignals.isNotEmpty()) {
            val grouped = tokenSignals.groupBy { it.tier }
            appendLine("Tokens: A=${grouped["A"]?.size ?: 0} B=${grouped["B"]?.size ?: 0} C=${grouped["C"]?.size ?: 0}")
        }

        if (negativeEvidenceApplied.isNotEmpty()) {
            appendLine("Suppressed: ${negativeEvidenceApplied.take(3).joinToString { it.targetTerm }}")
        }

        if (ambiguousTermsResolved.isNotEmpty()) {
            appendLine("Disambiguated: ${ambiguousTermsResolved.take(3).joinToString { "${it.term}->${it.chosenDomain.name}" }}")
        }

        priorBoosts?.let {
            appendLine("Priors: ${it.entries.take(2).joinToString { (k, v) -> "$k:${(v * 100).toInt()}%" }}")
        }

        appendLine("Time: ${processingTimeMs}ms")
    }

    /**
     * Generate formatted log string for debug logging.
     * Matches the format specified in Section 4.18 of the SemanticGenius Transformation Plan.
     *
     * Output format:
     * ═══════════════════════════════════════════
     * Input: [truncated input text]
     * Normalized: [truncated normalized text]
     * Language: [lang] | Mode: [mode]
     * Structure: [signals]
     * Dominant: [evidence]
     * Top tokens: [(term, weight), ...]
     * Domain candidates: [(DOMAIN, score), ...]
     * Intent candidates: [(INTENT, score), ...]
     * Disambiguation: [true/false]
     * RESULT: Domain=DOMAIN, Intents=[INTENT1, INTENT2]
     * Time: Xms
     * ═══════════════════════════════════════════
     */
    fun toLogString(): String = buildString {
        appendLine("═══════════════════════════════════════════")
        appendLine("Input: ${inputText.take(100)}${if (inputText.length > 100) "..." else ""}")
        appendLine("Normalized: ${normalizedText.take(100)}${if (normalizedText.length > 100) "..." else ""}")
        appendLine("Language: $languageHint | Mode: $modeUsed")

        if (structureSignals.isNotEmpty()) {
            appendLine("Structure: ${structureSignals.joinToString { "${it.type}(${it.count})" }}")
        }

        if (dominantEvidence.isNotEmpty()) {
            appendLine("Dominant: ${dominantEvidence.joinToString()}")
        }

        // Top tokens - show top 5 by weight
        if (tokenSignals.isNotEmpty()) {
            val topTokens = tokenSignals
                .sortedByDescending { it.weight }
                .take(5)
                .map { "${it.term}=${String.format("%.2f", it.weight)}" }
            appendLine("Top tokens: $topTokens")
        }

        // Domain candidates
        if (topDomainCandidates.isNotEmpty()) {
            val domainCandidates = topDomainCandidates.take(5)
                .map { "(${it.domain}, ${String.format("%.2f", it.score)})" }
            appendLine("Domain candidates: $domainCandidates")
        } else if (finalScores.isNotEmpty()) {
            // Fallback to finalScores if topDomainCandidates not populated
            val domainCandidates = finalScores.entries
                .sortedByDescending { it.value }
                .take(5)
                .filter { it.value > 0 }
                .map { "(${it.key}, ${String.format("%.2f", it.value)})" }
            appendLine("Domain candidates: $domainCandidates")
        }

        // Intent candidates
        if (topIntentCandidates.isNotEmpty()) {
            val intentCandidates = topIntentCandidates.take(5)
                .map { "(${it.intent}, ${String.format("%.2f", it.score)})" }
            appendLine("Intent candidates: $intentCandidates")
        } else if (intents.isNotEmpty()) {
            // Fallback to intents if topIntentCandidates not populated
            val intentCandidates = intents.take(5)
                .map { "(${it.intent.name}, ${String.format("%.2f", it.confidence)})" }
            appendLine("Intent candidates: $intentCandidates")
        }

        appendLine("Disambiguation: $disambiguationApplied")
        appendLine("RESULT: Domain=${domain.name}, Intents=${intents.map { it.intent.name }}")
        appendLine("Time: ${processingTimeMs}ms")
        appendLine("═══════════════════════════════════════════")
    }
}

/**
 * Intent classification result with confidence score.
 */
@Serializable
data class IntentScore(
    val intent: Intent,
    val confidence: Float,
    /** Brief signal descriptions that contributed to this intent */
    val signals: List<String> = emptyList()
)

/**
 * Structure signal detected in text (universal, language-independent).
 */
@Serializable
data class StructureSignal(
    /** Type: "CHECKBOX_UNCHECKED", "CHECKBOX_CHECKED", "BULLET_LIST", "NUMBERED_LIST", "CODE_BLOCK" */
    val type: String,
    /** How many times this structure was found */
    val count: Int,
    /** Intent inferred from this structure (e.g., CHECKBOX -> TODO_LIST) */
    val inferredIntent: Intent? = null,
    /** Weight contribution */
    val weight: Float
)

/**
 * Pattern signal from regex match.
 */
@Serializable
data class PatternSignal(
    /** Pattern ID: "SCRIPTURE_REF", "STACK_TRACE", "CURRENCY_HEAVY", etc. */
    val patternId: String,
    /** Number of matches */
    val matches: Int,
    /** Domain this pattern indicates */
    val domain: Domain,
    /** Weight contribution */
    val weight: Float,
    /** First match for debugging (truncated) */
    val sampleMatch: String? = null
)

/**
 * Token/keyword signal from dictionary match.
 */
@Serializable
data class TokenSignal(
    /** The matched term */
    val term: String,
    /** Tier: "A" (high precision), "B" (strong), "C" (weak) */
    val tier: String,
    /** Domain this token indicates */
    val domain: Domain,
    /** Weight contribution */
    val weight: Float,
    /** Match type: "EXACT", "FUZZY", "OCR_CORRECTED" */
    val matchType: String,
    /** Original token if OCR corrected */
    val originalToken: String? = null
)

/**
 * Trace of negative evidence suppression.
 */
@Serializable
data class NegativeEvidenceTrace(
    /** The term that was suppressed */
    val targetTerm: String,
    /** Which dominant domain caused suppression */
    val suppressedBy: Domain,
    /** Original weight before suppression */
    val originalWeight: Float,
    /** Weight after suppression */
    val finalWeight: Float,
    /** Reason: "TECH_CODEBLOCK_DOMINANT", etc. */
    val reason: String
)

/**
 * Resolution of an ambiguous term.
 */
@Serializable
data class AmbiguousTermResolution(
    /** The ambiguous term */
    val term: String,
    /** Domain it was assigned to */
    val chosenDomain: Domain,
    /** Confidence in this resolution */
    val confidence: Float,
    /** Reason: "collocation(api,endpoint)" or "priorContext" */
    val reason: String,
    /** Other domains that could have matched */
    val competingDomains: List<String> = emptyList()
)

/**
 * Domain candidate with score for debug observability.
 * Phase 9b - SemanticGenius Transformation Plan.
 */
@Serializable
data class DomainCandidate(
    /** The domain */
    val domain: String,
    /** Score for this domain (higher = more likely) */
    val score: Float
)

/**
 * Intent candidate with score for debug observability.
 * Phase 9b - SemanticGenius Transformation Plan.
 */
@Serializable
data class IntentCandidate(
    /** The intent */
    val intent: String,
    /** Score for this intent (higher = more likely) */
    val score: Float
)
