package com.boox.day.ai.intelligence.scorers

import com.boox.day.data.ai.*
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.semantic.model.DateSemanticContext

/**
 * Scores items based on semantic memory:
 * - Domain match (matches recently active domains)
 * - Intent match (matches detected intents)
 * - Entity relevance (mentions known people, orgs, locations)
 * - Action item alignment (relates to pending action items)
 */
class SemanticScorer : Scorer {
    override val id = ScorerIds.SEMANTIC

    companion object {
        // Domain match scoring
        private const val DOMAIN_MATCH_HIGH = 0.60f   // Matches top domain
        private const val DOMAIN_MATCH_MEDIUM = 0.40f // Matches secondary domain
        private const val DOMAIN_MATCH_LOW = 0.20f    // Matches tertiary domain

        // Entity match scoring
        private const val ENTITY_PERSON_MATCH = 0.50f
        private const val ENTITY_ORG_MATCH = 0.40f
        private const val ENTITY_LOCATION_MATCH = 0.30f

        // Action item alignment
        private const val ACTION_ITEM_MATCH = 0.55f

        // Intent match
        private const val INTENT_MATCH = 0.35f
    }

    override suspend fun score(input: ScoringInput): ScorerResult {
        val semanticContexts = input.semanticContexts
        if (semanticContexts.isNullOrEmpty()) return emptyResult(input)

        val scores = mutableMapOf<String, Float>()
        val signals = mutableMapOf<String, MutableList<String>>()

        // Aggregate semantic data across days
        val allDomains = semanticContexts.flatMap { it.dominantDomains }
        val allEntities = semanticContexts.flatMap { it.allEntities }
        val allKeywords = semanticContexts.flatMap { it.allKeywords }
        val allActionItems = semanticContexts.flatMap { it.pendingActionItems }

        // Build lookup structures
        val topDomains = allDomains
            .groupBy { it.domain }
            .mapValues { it.value.sumOf { ds -> ds.count } }
            .entries.sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        val entityNames = allEntities.map { it.text.lowercase() }.toSet()
        val keywordTerms = allKeywords.map { it.term.lowercase() }.toSet()
        val actionItemTexts = allActionItems.map {
            "${it.actionItem.verb} ${it.actionItem.objectText}".lowercase().trim()
        }

        input.itemsToScore.forEach { item ->
            val itemSignals = mutableListOf<String>()
            var maxScore = 0f

            val itemTextLower = item.text.lowercase()
            val itemWords = itemTextLower.split(Regex("\\s+")).filter { it.length >= 3 }

            // 1. Domain matching
            topDomains.forEachIndexed { index, domain ->
                if (itemMatchesDomain(itemTextLower, domain)) {
                    val domainScore = when (index) {
                        0 -> DOMAIN_MATCH_HIGH
                        1, 2 -> DOMAIN_MATCH_MEDIUM
                        else -> DOMAIN_MATCH_LOW
                    }
                    if (domainScore > maxScore) {
                        maxScore = domainScore
                        itemSignals.add("Matches active domain: ${domain.name}")
                    }
                }
            }

            // 2. Entity matching
            entityNames.forEach { entityName ->
                if (itemTextLower.contains(entityName) && entityName.length >= 3) {
                    val entityScore = ENTITY_PERSON_MATCH  // Simplified - treat all as person
                    if (entityScore > maxScore) {
                        maxScore = entityScore
                        itemSignals.add("Mentions known entity: $entityName")
                    }
                }
            }

            // 3. Keyword matching
            val matchedKeywords = keywordTerms.filter { keyword ->
                itemWords.any { it.contains(keyword) || keyword.contains(it) }
            }
            if (matchedKeywords.isNotEmpty()) {
                val keywordBonus = (matchedKeywords.size * 0.10f).coerceAtMost(0.30f)
                if (keywordBonus > 0) {
                    maxScore = (maxScore + keywordBonus).coerceAtMost(1f)
                    itemSignals.add("Matches ${matchedKeywords.size} semantic keywords")
                }
            }

            // 4. Action item matching
            actionItemTexts.forEach { actionText ->
                if (itemTextLower.contains(actionText) ||
                    actionText.split(" ").all { itemTextLower.contains(it) }) {
                    if (ACTION_ITEM_MATCH > maxScore) {
                        maxScore = ACTION_ITEM_MATCH
                        itemSignals.add("Aligns with action item from notes")
                    }
                }
            }

            scores[item.id] = maxScore.coerceIn(0f, 1f)
            signals[item.id] = itemSignals
        }

        return ScorerResult(
            scorerId = id,
            scores = scores,
            signals = signals
        )
    }

    private fun itemMatchesDomain(itemText: String, domain: Domain): Boolean {
        // Domain-specific keywords for matching
        // Uses actual Domain enum values from the codebase
        val domainKeywords = when (domain) {
            Domain.PROFESSIONAL -> listOf("work", "meeting", "project", "deadline", "client", "report", "office")
            Domain.LIFE -> listOf("personal", "home", "family", "errands", "social", "friend")
            Domain.HEALTH -> listOf("exercise", "gym", "health", "doctor", "workout", "fitness", "yoga")
            Domain.EDUCATION -> listOf("study", "learn", "course", "read", "book", "class", "research")
            Domain.FINANCE -> listOf("budget", "pay", "bill", "money", "bank", "invest", "tax")
            Domain.TRAVEL -> listOf("travel", "trip", "flight", "hotel", "vacation", "booking")
            Domain.TECH -> listOf("tech", "software", "code", "app", "computer", "programming", "api")
            Domain.CREATIVE -> listOf("create", "design", "write", "art", "music", "craft", "draw")
            Domain.SHOPPING -> listOf("buy", "shop", "order", "amazon", "store", "purchase")
            Domain.HOME -> listOf("house", "clean", "repair", "maintenance", "chores", "garden")
            Domain.FOOD -> listOf("cook", "recipe", "meal", "grocery", "restaurant", "dinner")
            Domain.TEACHING -> listOf("lesson", "student", "grade", "curriculum", "teach", "class")
            Domain.BUSINESS -> listOf("business", "revenue", "sales", "customer", "operations")
            Domain.STRATEGY -> listOf("strategy", "planning", "roadmap", "goals", "vision")
            Domain.LEGAL -> listOf("contract", "legal", "compliance", "lawyer", "agreement")
            Domain.SPIRITUAL -> listOf("church", "worship", "prayer", "meditation", "faith")
            Domain.COMMUNITY -> listOf("volunteer", "community", "neighborhood", "local")
            Domain.PET -> listOf("pet", "dog", "cat", "vet", "walk", "feed")
            Domain.SOCIAL_MEDIA -> listOf("post", "social", "content", "followers", "engagement")
            Domain.ENTREPRENEURSHIP -> listOf("startup", "venture", "founder", "pitch", "funding")
            Domain.LDS_LEADERSHIP -> listOf("bishop", "stake", "calling", "ward", "ministering")
            Domain.MISC -> emptyList()
        }
        return domainKeywords.any { itemText.contains(it) }
    }

    private fun emptyResult(input: ScoringInput): ScorerResult {
        return ScorerResult(
            scorerId = id,
            scores = input.itemsToScore.associate { it.id to 0f },
            signals = input.itemsToScore.associate { it.id to emptyList() }
        )
    }

    override fun isApplicable(input: ScoringInput): Boolean {
        return !input.semanticContexts.isNullOrEmpty()
    }
}
