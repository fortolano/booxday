package com.boox.day.ocr.semantic

import com.boox.day.ocr.model.ExtractedEntity
import com.boox.day.ocr.model.KeywordEntry

/**
 * Step 5: Key phrase extraction.
 *
 * Generates n-grams (2-6 words) and scores them based on:
 * - Keyword presence
 * - Entity presence
 * - Proximity to dates
 * - Returns top 10-20 phrases
 */
object PhraseExtractor {

    /**
     * Minimum n-gram size
     */
    private const val MIN_NGRAM = 2

    /**
     * Maximum n-gram size
     */
    private const val MAX_NGRAM = 6

    /**
     * Maximum phrases to return
     */
    private const val MAX_PHRASES = 20

    /**
     * Minimum phrases to return
     */
    private const val MIN_PHRASES = 10

    /**
     * Extract key phrases.
     *
     * @param sentences List of sentences
     * @param keywords Extracted keywords
     * @param entities Extracted entities
     * @return List of key phrases
     */
    fun extract(
        sentences: List<String>,
        keywords: List<KeywordEntry>,
        entities: List<ExtractedEntity>
    ): List<String> {
        if (sentences.isEmpty()) return emptyList()

        val keywordSet = keywords.map { it.term.lowercase() }.toSet()
        val entityTexts = entities.map { it.text.lowercase() }.toSet()

        val candidates = mutableListOf<ScoredPhrase>()

        for (sentence in sentences) {
            // Clean sentence
            val cleanSentence = sentence
                .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val words = cleanSentence.split(" ").filter { it.isNotBlank() }

            // Generate n-grams
            for (n in MIN_NGRAM..MAX_NGRAM) {
                for (i in 0..words.size - n) {
                    val ngram = words.subList(i, i + n).joinToString(" ")

                    // Score the n-gram
                    val score = scorePhrase(ngram, keywordSet, entityTexts)
                    if (score > 0) {
                        candidates.add(ScoredPhrase(ngram, score))
                    }
                }
            }
        }

        // Deduplicate and sort
        val deduped = deduplicatePhrases(candidates)

        // Return top phrases
        return deduped
            .sortedByDescending { it.score }
            .take(MAX_PHRASES)
            .map { it.phrase }
            .let { phrases ->
                if (phrases.size < MIN_PHRASES && candidates.size > phrases.size) {
                    deduped.sortedByDescending { it.score }
                        .take(MIN_PHRASES)
                        .map { it.phrase }
                } else {
                    phrases
                }
            }
    }

    /**
     * Score a phrase based on keyword and entity presence.
     */
    private fun scorePhrase(
        phrase: String,
        keywords: Set<String>,
        entities: Set<String>
    ): Float {
        val lowerPhrase = phrase.lowercase()
        val words = lowerPhrase.split(" ")

        var score = 0f

        // Score for each keyword in phrase
        val keywordCount = words.count { it in keywords }
        score += keywordCount * 1.5f

        // Score for entity presence
        for (entity in entities) {
            if (entity in lowerPhrase) {
                score += 2.0f
            }
        }

        // Bonus for optimal phrase length (3-4 words)
        if (words.size in 3..4) {
            score *= 1.2f
        }

        // Penalty for very common words dominating
        val commonWords = setOf("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for")
        val commonWordCount = words.count { it in commonWords }
        if (commonWordCount > words.size / 2) {
            score *= 0.5f
        }

        // Only return if we have at least one keyword or entity
        return if (keywordCount > 0 || entities.any { it in lowerPhrase }) score else 0f
    }

    /**
     * Deduplicate phrases, preferring longer phrases that contain shorter ones.
     */
    private fun deduplicatePhrases(phrases: List<ScoredPhrase>): List<ScoredPhrase> {
        val sorted = phrases.sortedByDescending { it.phrase.length }
        val result = mutableListOf<ScoredPhrase>()

        for (phrase in sorted) {
            val isSubset = result.any { existing ->
                existing.phrase.lowercase().contains(phrase.phrase.lowercase())
            }
            if (!isSubset) {
                result.add(phrase)
            }
        }

        return result
    }

    private data class ScoredPhrase(val phrase: String, val score: Float)
}
