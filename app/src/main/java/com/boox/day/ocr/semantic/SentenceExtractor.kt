package com.boox.day.ocr.semantic

import com.boox.day.ocr.model.ExtractedEntity
import com.boox.day.ocr.model.KeywordEntry

/**
 * Step 6: Key sentence extraction.
 *
 * Selects the most informative sentences based on:
 * - Entity presence
 * - Action verb presence
 * - Keyword density
 * - Bullet points
 * - Sentence length (prefer shorter, more specific)
 *
 * Returns top 6-12 sentences.
 */
object SentenceExtractor {

    /**
     * Maximum sentence length in characters
     */
    private const val MAX_SENTENCE_LENGTH = 240

    /**
     * Maximum sentences to return
     */
    private const val MAX_SENTENCES = 12

    /**
     * Minimum sentences to return
     */
    private const val MIN_SENTENCES = 6

    /**
     * Action verbs that make sentences more valuable
     */
    private val ACTION_VERBS = setOf(
        "call", "email", "send", "submit", "review", "complete", "finish",
        "buy", "schedule", "book", "prepare", "create", "update", "fix",
        "check", "confirm", "contact", "follow", "meet", "discuss", "plan",
        "write", "read", "organize", "clean", "pay", "order", "cancel",
        "start", "begin", "end", "stop", "continue", "report", "analyze",
        "develop", "design", "implement", "test", "deploy", "launch",
        "hire", "fire", "train", "teach", "learn", "study"
    )

    /**
     * Extract key sentences.
     *
     * @param sentences All sentences from text
     * @param keywords Extracted keywords
     * @param entities Extracted entities
     * @return List of key sentences
     */
    fun extract(
        sentences: List<String>,
        keywords: List<KeywordEntry>,
        entities: List<ExtractedEntity>
    ): List<String> {
        if (sentences.isEmpty()) return emptyList()

        val keywordSet = keywords.map { it.term.lowercase() }.toSet()
        val entityTexts = entities.map { it.text.lowercase() }.toSet()

        val scored = sentences.mapNotNull { sentence ->
            // Skip sentences that are too long or too short
            if (sentence.length > MAX_SENTENCE_LENGTH || sentence.length < 10) {
                return@mapNotNull null
            }

            val score = scoreSentence(sentence, keywordSet, entityTexts)
            if (score > 0) ScoredSentence(sentence, score) else null
        }

        // Sort and deduplicate
        val deduped = deduplicateSentences(scored.sortedByDescending { it.score })

        // Return top sentences
        return deduped
            .take(MAX_SENTENCES)
            .map { it.sentence }
            .let { result ->
                if (result.size < MIN_SENTENCES && deduped.size > result.size) {
                    deduped.take(MIN_SENTENCES).map { it.sentence }
                } else {
                    result
                }
            }
    }

    /**
     * Score a sentence based on multiple factors.
     */
    private fun scoreSentence(
        sentence: String,
        keywords: Set<String>,
        entities: Set<String>
    ): Float {
        val lowerSentence = sentence.lowercase()
        val words = lowerSentence.split(Regex("\\s+"))

        var score = 0f

        // Factor 1: Entity presence (high value)
        val entityCount = entities.count { entity -> entity in lowerSentence }
        score += entityCount * 3.0f

        // Factor 2: Action verb presence
        val hasActionVerb = words.any { it in ACTION_VERBS }
        if (hasActionVerb) {
            score += 2.5f
        }

        // Factor 3: Keyword density
        val keywordCount = words.count { it in keywords }
        score += keywordCount * 1.5f

        // Factor 4: Bullet point bonus
        if (sentence.trimStart().let {
            it.startsWith("•") || it.startsWith("-") ||
            it.startsWith("*") || it.matches(Regex("^\\d+[.):].*")) ||
            it.startsWith("[")
        }) {
            score += 1.5f
        }

        // Factor 5: Length preference (prefer medium length)
        when {
            sentence.length in 30..120 -> score *= 1.2f  // Ideal length
            sentence.length in 121..180 -> score *= 1.0f  // Acceptable
            sentence.length > 180 -> score *= 0.8f  // Penalty for too long
        }

        // Factor 6: Contains date/time reference
        if (containsDateOrTime(lowerSentence)) {
            score += 1.5f
        }

        // Factor 7: Starts with uppercase (likely proper sentence start)
        if (sentence.first().isUpperCase()) {
            score += 0.5f
        }

        return score
    }

    /**
     * Check if sentence contains date or time reference.
     */
    private fun containsDateOrTime(text: String): Boolean {
        val datePatterns = listOf(
            Regex("\\d{1,2}/\\d{1,2}"),
            Regex("\\d{1,2}:\\d{2}"),
            Regex("(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)"),
            Regex("(monday|tuesday|wednesday|thursday|friday|saturday|sunday)"),
            Regex("(today|tomorrow|yesterday|next week|last week)")
        )

        return datePatterns.any { it.containsMatchIn(text) }
    }

    /**
     * Deduplicate similar sentences.
     */
    private fun deduplicateSentences(sentences: List<ScoredSentence>): List<ScoredSentence> {
        val result = mutableListOf<ScoredSentence>()

        for (sentence in sentences) {
            val normalized = sentence.sentence.lowercase().trim()

            // Check if this sentence is too similar to existing ones
            val isSimilar = result.any { existing ->
                val existingNorm = existing.sentence.lowercase().trim()

                // Check for substring relationship
                existingNorm.contains(normalized) || normalized.contains(existingNorm) ||

                // Check for high overlap (Jaccard similarity)
                calculateJaccard(existingNorm, normalized) > 0.7
            }

            if (!isSimilar) {
                result.add(sentence)
            }
        }

        return result
    }

    /**
     * Calculate Jaccard similarity between two strings.
     */
    private fun calculateJaccard(s1: String, s2: String): Float {
        val words1 = s1.split(Regex("\\s+")).toSet()
        val words2 = s2.split(Regex("\\s+")).toSet()

        val intersection = words1.intersect(words2).size.toFloat()
        val union = words1.union(words2).size.toFloat()

        return if (union > 0) intersection / union else 0f
    }

    private data class ScoredSentence(val sentence: String, val score: Float)
}
