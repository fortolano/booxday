package com.boox.day.booxnotes

import com.boox.day.ocr.semantic.classification.OcrCorrector
import kotlin.math.abs

/**
 * Title matching for Smart Link: does a note's title name the event (title, or its description)?
 * Worth +100 points, the strongest Smart Link signal, so it must only fire on a real shared word.
 *
 * Oct 2026: whole words only. It used to accept any 3-letter piece of the event text found inside the
 * note title, and dates counted, so "Notebook-27" matched every event whose description said "note"
 * and a date title like "Oct 12, 2026" matched any event mentioning "October" or "2026" — most Smart
 * Link suggestions came from that. Dates, numbers and default note names now never match.
 *
 * A word matches when it is the same word, a known abbreviation of it, the same stem
 * (meetings / meeting), or a likely handwriting/OCR misreading (see [fuzzyMatch]).
 */
internal object TitleMatcher {

    /** Common words that say nothing about which event a note belongs to */
    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
        "be", "have", "has", "had", "do", "does", "did", "will", "would",
        "could", "should", "may", "might", "must", "shall", "can", "need",
        "meeting", "call", "sync", "chat", "discussion", "session", "notes"
    )

    /** Words of default note titles ("Notebook-27", "Note 3", "Untitled page") */
    private val GENERIC_NOTE_WORDS = setOf(
        "notebook", "notebooks", "note", "notes", "page", "pages", "untitled", "new", "draft", "copy"
    )

    /** Dates are when, not what: Boox titles notes with dates, and invites are full of them */
    private val DATE_WORDS = setOf(
        "january", "february", "march", "april", "may", "june", "july", "august",
        "september", "october", "november", "december",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "mon", "tue", "tues", "wed", "thu", "thur", "thurs", "fri", "sat", "sun",
        "today", "tomorrow", "yesterday"
    )

    private val WORD_SPLIT = Regex("[^\\p{L}\\p{Nd}']+")
    private val HAS_DIGIT = Regex("\\d")

    /** The words of [text] that can identify an event: no stop words, dates, numbers or default names */
    fun meaningfulWords(text: String): Set<String> =
        text.lowercase()
            .split(WORD_SPLIT)
            .map { it.trim('\'') }
            .filter { word ->
                word.length >= 3 &&
                    !HAS_DIGIT.containsMatchIn(word) &&
                    word !in STOP_WORDS &&
                    word !in GENERIC_NOTE_WORDS &&
                    word !in DATE_WORDS
            }
            .toSet()

    /**
     * The event word that the note title names, or null.
     *
     * @param expandAbbreviation the abbreviation dictionary ("mtg" -> "meeting"), null when unknown
     */
    fun matchTerm(
        noteTitle: String?,
        eventText: String,
        expandAbbreviation: (String) -> String?
    ): String? {
        if (noteTitle.isNullOrBlank()) return null
        val noteWords = meaningfulWords(noteTitle)
        if (noteWords.isEmpty()) return null
        val eventWords = meaningfulWords(eventText)

        for (eventWord in eventWords) {
            for (noteWord in noteWords) {
                if (noteWord == eventWord) return eventWord
                val expanded = expandAbbreviation(noteWord)?.lowercase()
                if (expanded != null && expanded == eventWord) return eventWord
                val eventStem = stem(eventWord)
                val noteStem = stem(noteWord)
                if (eventStem.length >= 3 && eventStem == noteStem) return eventWord
                if (fuzzyMatch(noteWord, eventWord)) return eventWord
            }
        }
        return null
    }

    /**
     * Simple stemming via suffix removal.
     * Handles common English suffixes: -ing, -ed, -s, -tion, -ly, -er, -est
     */
    private fun stem(word: String): String {
        var result = word
        // Order matters - remove longer suffixes first
        when {
            result.endsWith("tion") -> result = result.dropLast(4)
            result.endsWith("ing") && result.length > 5 -> result = result.dropLast(3)
            result.endsWith("ed") && result.length > 4 -> result = result.dropLast(2)
            result.endsWith("ly") && result.length > 4 -> result = result.dropLast(2)
            result.endsWith("er") && result.length > 4 -> result = result.dropLast(2)
            result.endsWith("est") && result.length > 5 -> result = result.dropLast(3)
            result.endsWith("s") && result.length > 3 -> result = result.dropLast(1)
        }
        return result
    }

    /**
     * Known OCR confusions for first-letter validation in title matching.
     *
     * Intentionally MORE RESTRICTIVE than OcrCorrector.CONFUSION_COSTS: first letters are usually
     * written carefully, so only high-confidence confusions count. This prevents "lond" → "bond".
     */
    private val FIRST_LETTER_OCR_CONFUSIONS = setOf(
        // Tier 1: Very high confidence (font/OCR)
        'l' to '1', '1' to 'l',
        'l' to 'i', 'i' to 'l',
        'i' to '1', '1' to 'i',
        'o' to '0', '0' to 'o',
        'c' to 'e', 'e' to 'c',
        'n' to 'h', 'h' to 'n',
        'u' to 'v', 'v' to 'u',
        'r' to 'n', 'n' to 'r',

        // Tier 2: High confidence (added Jan 2026)
        'a' to 'o', 'o' to 'a',  // Handwriting: open/closed
        'g' to 'q', 'q' to 'g',  // Font confusion
        's' to '5', '5' to 's',  // OCR confusion
        'p' to 'q', 'q' to 'p',  // Mirror (common in dyslexia/handwriting)
        'b' to 'd', 'd' to 'b',  // Mirror (common in dyslexia/handwriting)
        't' to 'l', 'l' to 't'   // Missing crossbar
        // NOTE: Do NOT add b↔l here - "lond"↔"bond" should be rejected
    )

    /**
     * OCR-aware fuzzy match: same first letter (or a known OCR confusion), OCR-weighted edit distance
     * within a threshold that grows with word length. Catches "rneet" → "meet", rejects "lond" → "bond".
     */
    private fun fuzzyMatch(word1: String, word2: String): Boolean {
        if (word1.length < 4 || word2.length < 4) return false
        if (abs(word1.length - word2.length) > 2) return false
        if (!isPlausibleFirstLetterOcrMatch(word1[0], word2[0])) return false

        val distance = OcrCorrector.weightedEditDistance(word1, word2)
        val threshold = when (minOf(word1.length, word2.length)) {
            4 -> 0.8f      // Short words: strict
            5 -> 1.0f      // Medium-short: moderate
            in 6..8 -> 1.3f // Medium: reasonable
            else -> 1.6f   // Long words: lenient
        }
        return distance <= threshold
    }

    private fun isPlausibleFirstLetterOcrMatch(c1: Char, c2: Char): Boolean =
        c1 == c2 || (c1 to c2) in FIRST_LETTER_OCR_CONFUSIONS
}
