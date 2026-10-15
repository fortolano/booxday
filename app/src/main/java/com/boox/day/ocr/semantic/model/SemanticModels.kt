package com.boox.day.ocr.semantic.model

import com.boox.day.data.db.UnifiedOcrSearchResult
import com.boox.day.ocr.model.*

/**
 * Semantic profile for a specific source (note/memo/task).
 * Parsed from UnifiedOcrText entity.
 */
data class SemanticProfile(
    val sourceType: OcrSource,
    val sourceId: String,
    val pageId: String,

    // Classification
    val domain: Domain?,
    val domainConfidence: Float,  // NOT nullable, defaults to 0f
    val intents: List<IntentEntry>,
    val subdomainHint: String?,

    // Semantic data
    val docStyle: DocumentStyle?,
    val keywords: List<KeywordEntry>,
    val keyPhrases: List<String>,
    val keySentences: List<String>,
    val entities: List<ExtractedEntity>,
    val actionItems: List<ActionItem>,
    val temporalSignals: TemporalSignals?,
    val synopsis: String?,

    // Metadata
    val sourceDate: String?,
    val sourceTitle: String?,
    val wordCount: Int,
    val confidence: Float
) {
    val hasSemanticData: Boolean
        get() = domain != null || keywords.isNotEmpty() || entities.isNotEmpty()

    val primaryIntents: List<IntentEntry>
        get() = intents.filter { it.confidence >= 0.5f }
}

/**
 * Aggregated semantic context for a specific date.
 */
data class DateSemanticContext(
    val date: String,  // yyyy-MM-dd
    val dominantDomains: List<DomainScore>,
    val dominantIntents: List<IntentScore>,
    val allKeywords: List<KeywordEntry>,
    val allEntities: List<ExtractedEntity>,
    val pendingActionItems: List<ActionItemWithSource>,
    val memoCount: Int,
    val taskCount: Int,
    val booxNoteCount: Int,
    val coveragePercent: Float
) {
    companion object {
        fun empty(date: String) = DateSemanticContext(
            date = date,
            dominantDomains = emptyList(),
            dominantIntents = emptyList(),
            allKeywords = emptyList(),
            allEntities = emptyList(),
            pendingActionItems = emptyList(),
            memoCount = 0,
            taskCount = 0,
            booxNoteCount = 0,
            coveragePercent = 0f
        )
    }
}

data class DomainScore(
    val domain: Domain,
    val count: Int,
    val avgConfidence: Float
)

data class IntentScore(
    val intent: Intent,
    val count: Int,
    val avgConfidence: Float
)

data class ActionItemWithSource(
    val actionItem: ActionItem,
    val sourceType: OcrSource,
    val sourceId: String,
    val sourceDate: String?
)

/**
 * Match result for Smart Link enhancement.
 */
data class SemanticMatchResult(
    val noteId: String,
    val eventTitle: String,
    val score: Float,  // 0.0 - 1.0
    val matchReasons: List<SemanticMatchReason>,
    val profile: SemanticProfile?
)

enum class SemanticMatchReason {
    KEYWORD_MATCH,
    ENTITY_PERSON_MATCH,
    ENTITY_ORG_MATCH,
    DOMAIN_RELEVANCE,
    INTENT_MATCH,
    SYNOPSIS_MATCH
}

/**
 * Enhanced search result with semantic ranking.
 */
data class SemanticSearchResult(
    val ocrResult: UnifiedOcrSearchResult,
    val semanticScore: Float,
    val matchedDomains: List<Domain>,
    val matchedIntents: List<Intent>,
    val combinedScore: Double  // Double to match ocrResult.score type
)

/**
 * Domain distribution for insights/analytics.
 */
data class DomainDistribution(
    val totalPages: Int,
    val byDomain: Map<Domain, Int>,
    val bySourceType: Map<OcrSource, Map<Domain, Int>>,
    val topKeywords: List<KeywordEntry>,
    val periodStart: String?,
    val periodEnd: String?
)
