package com.boox.day.search

import java.text.Normalizer
import java.util.Locale

/**
 * How search compares what the user typed with stored text (Oct 2026, idea from CrossInk's
 * library search): both sides are folded (case and accents dropped, punctuation turned into
 * spaces), the query is split into words, and every word of the query has to be found.
 *
 * - [matchesWordStarts]: every query word starts some word of the text, in any order:
 *   "meet" finds "Meeting", "dar mat" finds "dark matter", "mary" does not find "primary".
 * - [matchesSubstrings]: every query word is somewhere in the text ("mary" finds "primary").
 * - [ftsPrefixQuery]: the same word-start rule as an FTS4 MATCH query.
 *
 * Nothing here goes into a LIKE pattern, so % and _ typed by the user are plain characters.
 */
object SearchText {

    /** Letters with no Unicode decomposition that people type without their mark. */
    private val EXTRA_FOLDS = mapOf(
        'ł' to "l", 'đ' to "d", 'ð' to "d", 'ø' to "o", 'ß' to "ss", 'æ' to "ae",
        'œ' to "oe", 'ı' to "i", 'þ' to "th", 'ħ' to "h", 'ŧ' to "t",
    )

    private val MARKS = Regex("\\p{Mn}+")

    /**
     * Lowercase, accents removed, every run of characters that is not a letter or a digit
     * turned into one space, trimmed. "L'Énéide, 50%!" -> "l eneide 50".
     *
     * [extraFolds] also maps the letters in [EXTRA_FOLDS] (ł -> l, ß -> ss). The FTS index
     * keeps those letters as they are (SQLite's unicode61 tokenizer only removes marks that
     * Unicode decomposes), so FTS queries are folded without them.
     */
    fun fold(text: String, extraFolds: Boolean = true): String {
        if (text.isEmpty()) return ""
        val decomposed = MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
            .lowercase(Locale.ROOT)
        val out = StringBuilder(decomposed.length)
        var pendingSpace = false
        for (c in decomposed) {
            val mapped = if (extraFolds) EXTRA_FOLDS[c] else null
            when {
                mapped != null -> {
                    if (pendingSpace && out.isNotEmpty()) out.append(' ')
                    pendingSpace = false
                    out.append(mapped)
                }
                c.isLetterOrDigit() -> {
                    if (pendingSpace && out.isNotEmpty()) out.append(' ')
                    pendingSpace = false
                    out.append(c)
                }
                else -> pendingSpace = true
            }
        }
        return out.toString()
    }

    /** The folded words of a query, in order, without repeats. Empty when nothing is searchable. */
    fun queryWords(query: String, extraFolds: Boolean = true): List<String> =
        fold(query, extraFolds).split(' ').filter { it.isNotEmpty() }.distinct()

    /** True when every word starts some word of [foldedText] (already passed through [fold]). */
    fun matchesWordStarts(foldedText: String, words: List<String>): Boolean {
        if (words.isEmpty()) return false
        return words.all { word -> containsWordStart(foldedText, word) }
    }

    /** True when every word is found anywhere in [foldedText] (already passed through [fold]). */
    fun matchesSubstrings(foldedText: String, words: List<String>): Boolean {
        if (words.isEmpty()) return false
        return words.all { foldedText.contains(it) }
    }

    /**
     * FTS4 query where every word has to start a word of the document: "dar mat" -> `dar* mat*`
     * (terms side by side are ANDed). The words are folded, so they hold only letters and digits
     * and no FTS operator. Empty when the query has no searchable word.
     */
    fun ftsPrefixQuery(query: String): String =
        queryWords(query, extraFolds = false).joinToString(" ") { "$it*" }

    private fun containsWordStart(foldedText: String, word: String): Boolean {
        var from = 0
        while (true) {
            val at = foldedText.indexOf(word, from)
            if (at < 0) return false
            if (at == 0 || foldedText[at - 1] == ' ') return true
            from = at + 1
        }
    }
}
