package com.boox.day.ocr

import android.content.Context
import android.util.Log

/**
 * Splits merged words using dictionary lookup and dynamic programming.
 *
 * Example: "ADDINGA" → "ADDING A"
 *
 * Algorithm:
 * 1. For each word, try all possible split points
 * 2. Score splits by: both parts are valid words, longer words preferred
 * 3. Use memoization for efficiency
 */
class WordSegmenter(private val dictionary: Set<String>) {

    companion object {
        private const val TAG = "WordSegmenter"

        // Minimum word length to attempt segmentation (avoid splitting "at" into "a t")
        private const val MIN_SEGMENT_LENGTH = 4

        // Minimum length for each split part
        private const val MIN_PART_LENGTH = 1
    }

    /**
     * Segment a text string, splitting any merged words.
     *
     * @param text Input text (may contain multiple words)
     * @return Text with merged words split
     */
    fun segment(text: String): String {
        if (text.isBlank()) return text

        val words = text.split(Regex("\\s+"))
        val result = words.map { word ->
            segmentWord(word)
        }

        return result.joinToString(" ")
    }

    /**
     * Attempt to segment a single word.
     *
     * @param word Single word to potentially split
     * @return Original word or split version
     */
    private fun segmentWord(word: String): String {
        // Too short to split meaningfully
        if (word.length < MIN_SEGMENT_LENGTH) return word

        // If word is already valid, no need to split
        val lowerWord = word.lowercase()
        if (lowerWord in dictionary) return word

        // Try to find the best split
        val bestSplit = findBestSplit(lowerWord)

        return if (bestSplit != null) {
            // Preserve original casing pattern as much as possible
            applySplitWithCasing(word, bestSplit)
        } else {
            word
        }
    }

    /**
     * Find the best way to split a word into two valid words.
     *
     * @param word Lowercase word to split
     * @return Pair of (firstPart, secondPart) or null if no valid split found
     */
    private fun findBestSplit(word: String): Pair<String, String>? {
        var bestSplit: Pair<String, String>? = null
        var bestScore = 0

        // Try all possible split points
        for (i in MIN_PART_LENGTH until word.length - MIN_PART_LENGTH + 1) {
            val first = word.substring(0, i)
            val second = word.substring(i)

            // Both parts must be valid words
            if (first in dictionary && second in dictionary) {
                // Score: prefer longer first part (more natural splits)
                // "ADDING A" (6+1) scores higher than "ADD INGA" (3+4)
                val score = first.length * 2 + second.length

                if (score > bestScore) {
                    bestScore = score
                    bestSplit = Pair(first, second)
                }
            }
        }

        return bestSplit
    }

    /**
     * Apply a split while trying to preserve original casing.
     */
    private fun applySplitWithCasing(original: String, split: Pair<String, String>): String {
        val (first, second) = split
        val splitPoint = first.length

        // Extract original casing
        val originalFirst = original.substring(0, splitPoint)
        val originalSecond = original.substring(splitPoint)

        // If original was all uppercase, keep parts uppercase
        return if (original.all { it.isUpperCase() || !it.isLetter() }) {
            "${originalFirst.uppercase()} ${originalSecond.uppercase()}"
        } else {
            // Try to preserve original casing
            "$originalFirst $originalSecond"
        }
    }

    /**
     * Segment with support for multi-word splits (recursive).
     * For words that might be 3+ words merged.
     *
     * Note: This is more expensive, use sparingly.
     */
    fun segmentRecursive(text: String, maxDepth: Int = 2): String {
        if (text.isBlank() || maxDepth <= 0) return text

        val words = text.split(Regex("\\s+"))
        val result = words.map { word ->
            val segmented = segmentWord(word)
            if (segmented != word && maxDepth > 1) {
                // Recursively try to segment the result
                segmentRecursive(segmented, maxDepth - 1)
            } else {
                segmented
            }
        }

        return result.joinToString(" ")
    }
}
