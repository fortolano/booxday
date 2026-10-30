package com.boox.day.ai.intelligence

import java.util.concurrent.ConcurrentHashMap

/**
 * Whole-word keyword matching for the task scorers (Oct 2026). Plain `contains` matched inside
 * words: "meet" in every "meeting", "art" in "start", "read" in "bread", "book" in "notebook",
 * which put wrong "Matches HIGH priority" hints in AI prompts and skewed Sort by Priority.
 * A keyword matches as a whole word or phrase, plus a plain plural (s / es).
 */
object KeywordMatch {
    private val patterns = ConcurrentHashMap<String, Regex>()

    private fun pattern(keyword: String): Regex = patterns.getOrPut(keyword) {
        Regex("\\b${Regex.escape(keyword.lowercase())}(?:s|es)?\\b", RegexOption.IGNORE_CASE)
    }

    fun contains(text: String, keyword: String): Boolean = pattern(keyword).containsMatchIn(text)

    fun containsAny(text: String, keywords: Collection<String>): Boolean = keywords.any { contains(text, it) }
}
