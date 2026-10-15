package com.boox.day.ocr

import android.util.Log
import kotlin.math.min

/**
 * Spell corrector using Levenshtein distance and word frequency.
 *
 * Algorithm:
 * 1. If word is in dictionary, return as-is
 * 2. Generate candidates within edit distance 1-2
 * 3. Filter to valid dictionary words
 * 4. Rank by edit distance (lower = better) and frequency (higher = better)
 * 5. Return best match if confidence > threshold
 */
class SpellCorrector(
    private val dictionary: Set<String>,
    private val frequencies: Map<String, Int> = emptyMap(),
    private val confidenceThreshold: Float = 0.70f,
    private val alphabet: String = LATIN_ALPHABET
) {

    companion object {
        private const val TAG = "SpellCorrector"

        // Maximum edit distance to consider
        private const val MAX_EDIT_DISTANCE = 2

        // Minimum word length to attempt correction
        private const val MIN_WORD_LENGTH = 3

        // Default frequency for unknown words
        private const val DEFAULT_FREQUENCY = 1

        // Language-specific alphabets for edit generation
        const val LATIN_ALPHABET = "abcdefghijklmnopqrstuvwxyz"
        const val CYRILLIC_ALPHABET = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
        const val ARABIC_ALPHABET = "ابتثجحخدذرزسشصضطظعغفقكلمنهويءآأؤإئة"

        /**
         * Get the appropriate alphabet for a language tag.
         */
        fun getAlphabetForLanguage(languageTag: String): String {
            return when {
                languageTag.startsWith("ru") -> CYRILLIC_ALPHABET
                languageTag.startsWith("ar") -> ARABIC_ALPHABET
                languageTag.startsWith("uk") -> CYRILLIC_ALPHABET  // Ukrainian
                languageTag.startsWith("bg") -> CYRILLIC_ALPHABET  // Bulgarian
                languageTag.startsWith("sr") -> CYRILLIC_ALPHABET  // Serbian
                else -> LATIN_ALPHABET  // Default to Latin for en, es, fr, de, it, etc.
            }
        }
    }

    /**
     * Correct spelling of a single word.
     *
     * @param word Word to correct
     * @return Corrected word or original if no confident correction found
     */
    fun correct(word: String): String {
        // Too short to correct meaningfully
        if (word.length < MIN_WORD_LENGTH) return word

        val lowerWord = word.lowercase()

        // Already valid
        if (lowerWord in dictionary) return word

        // Find best correction
        val correction = findBestCorrection(lowerWord)

        return if (correction != null) {
            // Preserve original casing pattern
            applyCasingPattern(word, correction)
        } else {
            word
        }
    }

    /**
     * Correct all words in a text string.
     *
     * @param text Input text
     * @return Text with spelling corrections applied
     */
    fun correctText(text: String): String {
        if (text.isBlank()) return text

        val words = text.split(Regex("\\s+"))
        val corrected = words.map { correct(it) }
        return corrected.joinToString(" ")
    }

    /**
     * Find the best correction for a word.
     *
     * @param word Lowercase word to correct
     * @return Best correction or null if none found with sufficient confidence
     */
    private fun findBestCorrection(word: String): String? {
        // Generate candidates at edit distance 1
        val candidates1 = generateEdits(word)
            .filter { it in dictionary }
            .map { CorrectionCandidate(it, 1, getFrequency(it)) }

        // If we have good candidates at distance 1, prefer them
        val bestAtDistance1 = candidates1.maxByOrNull { it.score }

        // Generate candidates at edit distance 2 (only if needed)
        val candidates2 = if (candidates1.isEmpty() || (bestAtDistance1?.confidence ?: 0f) < confidenceThreshold) {
            generateEditsDistance2(word)
                .filter { it in dictionary }
                .map { CorrectionCandidate(it, 2, getFrequency(it)) }
        } else {
            emptyList()
        }

        // Combine and find best
        val allCandidates = candidates1 + candidates2
        val best = allCandidates.maxByOrNull { it.score }

        // Only return if confidence is high enough
        return if (best != null && best.confidence >= confidenceThreshold) {
            Log.d(TAG, "Correcting '$word' → '${best.word}' (distance=${best.editDistance}, freq=${best.frequency}, conf=${best.confidence})")
            best.word
        } else {
            null
        }
    }

    /**
     * Generate all words at edit distance 1 from the input.
     *
     * Edits include:
     * - Deletions: remove one character
     * - Insertions: add one character
     * - Substitutions: replace one character
     * - Transpositions: swap adjacent characters
     */
    private fun generateEdits(word: String): Set<String> {
        val edits = mutableSetOf<String>()
        val letters = alphabet  // Use language-specific alphabet

        // Deletions
        for (i in word.indices) {
            edits.add(word.removeRange(i, i + 1))
        }

        // Insertions
        for (i in 0..word.length) {
            for (c in letters) {
                edits.add(word.substring(0, i) + c + word.substring(i))
            }
        }

        // Substitutions
        for (i in word.indices) {
            for (c in letters) {
                if (word[i] != c) {
                    edits.add(word.substring(0, i) + c + word.substring(i + 1))
                }
            }
        }

        // Transpositions
        for (i in 0 until word.length - 1) {
            val chars = word.toCharArray()
            val temp = chars[i]
            chars[i] = chars[i + 1]
            chars[i + 1] = temp
            edits.add(String(chars))
        }

        return edits
    }

    /**
     * Generate all words at edit distance 2 from the input.
     * This is edits of edits, filtered to valid words only.
     */
    private fun generateEditsDistance2(word: String): Set<String> {
        val edits1 = generateEdits(word)
        val edits2 = mutableSetOf<String>()

        for (edit in edits1) {
            // Only expand edits that are close to valid words
            // (optimization: skip very long or very short edits)
            if (edit.length in (word.length - 2)..(word.length + 2)) {
                edits2.addAll(generateEdits(edit))
            }
        }

        return edits2
    }

    /**
     * Calculate Levenshtein distance between two strings.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        // Edge cases
        if (m == 0) return n
        if (n == 0) return m

        // Create distance matrix
        val dp = Array(m + 1) { IntArray(n + 1) }

        // Initialize first row and column
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        // Fill in the rest
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[m][n]
    }

    /**
     * Get frequency score for a word.
     */
    private fun getFrequency(word: String): Int {
        return frequencies[word] ?: DEFAULT_FREQUENCY
    }

    /**
     * Apply original casing pattern to corrected word.
     */
    private fun applyCasingPattern(original: String, corrected: String): String {
        return when {
            // All uppercase
            original.all { it.isUpperCase() || !it.isLetter() } -> corrected.uppercase()
            // Title case (first letter uppercase)
            original.firstOrNull()?.isUpperCase() == true -> corrected.replaceFirstChar { it.uppercase() }
            // Lowercase
            else -> corrected.lowercase()
        }
    }

    /**
     * Candidate correction with scoring.
     */
    private data class CorrectionCandidate(
        val word: String,
        val editDistance: Int,
        val frequency: Int
    ) {
        /**
         * Combined score favoring:
         * 1. Lower edit distance (more important)
         * 2. Higher frequency (secondary)
         */
        val score: Float
            get() {
                // Edit distance penalty (distance 1 = 0.9, distance 2 = 0.7)
                val distanceFactor = when (editDistance) {
                    1 -> 0.9f
                    2 -> 0.7f
                    else -> 0.5f
                }

                // Frequency boost (log scale)
                val frequencyFactor = kotlin.math.ln(frequency.toFloat() + 1) / 10f

                return distanceFactor + frequencyFactor
            }

        /**
         * Confidence based on edit distance and word length.
         * Longer words with small edits = higher confidence.
         */
        val confidence: Float
            get() {
                val lengthFactor = word.length.toFloat()
                val distancePenalty = editDistance.toFloat()
                return (lengthFactor - distancePenalty) / lengthFactor
            }
    }
}
