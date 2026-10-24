package com.boox.day.ocr

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Tidies the capitals of English Digital Ink recognition results (typed task text in the search index
 * too). The words themselves are never changed: ML Kit already reads with its own language model.
 *
 * One instance per language, cached by HandwritingNormalizerFactory. Other languages keep the
 * recognizer's capitals ([UniversalHandwritingNormalizer]).
 *
 * Rules, word by word:
 * - ordinary words go lower case, the line starts with a capital ("CALL MOM" → "Call mom")
 * - "I" (and I'm, I'll…) is a capital; other single letters keep their capital ("TASK B" → "Task B")
 * - known abbreviations are capitals ("3 pm" → "3 PM", "review pr" → "review PR"); words that are
 *   also ordinary words ("it", "am", "re", "pin") keep the case they were written in
 * - names are capitalized ("rajin" → "Rajin"); names that are also ordinary words ("Mark", "Will")
 *   only when written with a capital or after "call", "email", "meet"…
 * - in a line written in normal case, a word that isn't an ordinary word keeps the capitals it was
 *   written with ("Call IBM", "Meet Santos", "Q4"); in a line all in capitals they tell nothing, so
 *   only the rules above apply there
 *
 * Oct 2026: the earlier word splitting and spell correction against these word lists were removed.
 * The lists are too small (no "bread", "taxes", "chicken"), so they turned correct words into others:
 * 11 of 40 everyday tasks ("Buy bread" → "Buy read"). The lists are now only used for capitals.
 *
 * NOTE: This is different from ocr/semantic/TextNormalizer.kt which does
 * OCR cleanup (NFKC, whitespace, garbage removal).
 */
class HandwritingNormalizer private constructor(
    private val languageTag: String,
    private val context: Context
) {
    companion object {
        private const val TAG = "HandwritingNormalizer"

        // Abbreviations always written in capitals
        private val acronyms = setOf(
            "api", "usa", "asap", "pdf", "ceo", "hr", "cfo", "cto", "coo",
            "vp", "svp", "evp", "md", "phd", "mba", "ba", "bs",
            "pm", "est", "pst", "gmt", "utc", "fyi", "btw", "tbd",
            "eta", "rsvp", "diy", "faq", "id", "atm", "gps", "wifi",
            "tv", "pc", "ios", "ok", "aka", "vs", "cc", "bcc",
            "pr", "ui", "ux", "qa", "ci", "kpi", "okr", "roi", "seo", "crm", "sql", "url", "usb",
            "irs", "dmv", "nyc", "ny", "uk", "eu", "pto", "eod", "ooo", "wfh", "tba", "aa", "aaa"
        )

        // Abbreviations that are also ordinary words or titles: kept as written ("Do it", "Call IT").
        // "am" after a number is a time ("9 am" → "9 AM").
        private val ambiguousAcronyms = setOf("it", "am", "re", "pin", "mac", "ma", "ms", "la")

        // Words that indicate a following word is likely a name
        private val nameIndicators = setOf(
            "call", "email", "meet", "contact", "text", "message", "reach",
            "to", "from", "with", "for", "ask", "tell", "remind", "notify",
            "invite", "schedule", "book", "see", "visit", "thank", "congratulate"
        )

        // After these, a name that is also an ordinary word ("call mark") is the name
        private val contactVerbs = setOf(
            "call", "email", "meet", "contact", "text", "message", "ask", "tell", "remind",
            "invite", "visit", "thank", "congratulate"
        )

        /**
         * Factory method - use HandwritingNormalizerFactory.getInstance() instead.
         * This is exposed for testing only.
         */
        internal fun create(languageTag: String, context: Context): HandwritingNormalizer {
            return HandwritingNormalizer(languageTag, context)
        }

        /** Tests: a normalizer with these word lists instead of the asset files. */
        internal fun withWordLists(
            languageTag: String,
            context: Context,
            words: Set<String>,
            names: Set<String>
        ): HandwritingNormalizer = HandwritingNormalizer(languageTag, context).apply {
            wordDict = words
            namesDict = names
            initialized = true
        }
    }

    // Asset file paths (language-specific)
    private val commonWordsFile = "text_processing/$languageTag/common_words.txt"
    private val namesFile = "text_processing/$languageTag/names.txt"

    // Loaded once per language, cached forever
    private var wordDict: Set<String>? = null
    private var namesDict: Set<String>? = null
    private var initialized = false

    /**
     * Initialize this language's normalizer.
     * Called by factory on first access.
     * Safe to call multiple times - only loads once.
     */
    internal fun initialize() {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            try {
                Log.i(TAG, "Loading dictionaries for $languageTag...")
                val startTime = System.currentTimeMillis()

                wordDict = loadDictionary(context, commonWordsFile)
                namesDict = loadDictionary(context, namesFile)

                initialized = true

                val elapsed = System.currentTimeMillis() - startTime
                Log.i(TAG, "Dictionaries loaded for $languageTag in ${elapsed}ms (words=${wordDict!!.size}, names=${namesDict!!.size})")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dictionaries for $languageTag: ${e.message}", e)
                // Initialize with empty sets to prevent crashes
                wordDict = emptySet()
                namesDict = emptySet()
                initialized = true
            }
        }
    }

    /**
     * Check if the normalizer is initialized.
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Normalize recognized text from Digital Ink.
     *
     * @param text Raw recognition result
     * @return Normalized text
     */
    fun normalize(text: String): String {
        if (!initialized) {
            Log.w(TAG, "Normalizer for $languageTag not initialized, returning original text")
            return text
        }

        if (text.isBlank()) return text

        // The capital rules below are English ones (German nouns keep their capitals)
        if (!languageTag.startsWith("en")) return UniversalHandwritingNormalizer.normalize(text, languageTag)

        try {
            val words = text.trim().split(Regex("\\s+"))
            val allCapitals = text.any { it.isLetter() } && text.none { it.isLowerCase() }
            val result = capitalizeSentence(
                words.mapIndexed { index, word -> normalizeWord(word, index, words, allCapitals) }.joinToString(" ")
            )

            Log.d(TAG, "[$languageTag] Normalized: '$text' → '$result'")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "Normalization failed for '$text' [$languageTag]: ${e.message}")
            return text
        }
    }

    /**
     * Batch normalize multiple results.
     */
    fun normalizeBatch(texts: List<String>): List<String> {
        return texts.map { normalize(it) }
    }

    /**
     * The capitals of one word, as written ([word]) in a line written all in capitals or not
     * ([allCapitals]). Punctuation around the word stays.
     */
    private fun normalizeWord(word: String, index: Int, allWords: List<String>, allCapitals: Boolean): String {
        val key = word.lowercase().trim { !it.isLetterOrDigit() }
        val letters = word.filter { it.isLetter() }
        if (letters.isEmpty()) return word  // numbers, punctuation
        val words = wordDict ?: emptySet()
        val names = namesDict ?: emptySet()
        val previous = allWords.getOrNull(index - 1)?.lowercase()?.trim { !it.isLetterOrDigit() }

        // "I", "I'm", "I'll": the pronoun
        if (key == "i" || key.startsWith("i'")) return capitalized(word)

        // A single letter keeps its capital ("Task B", "Plan A"); a capital "A" before a word in a line
        // all in capitals is the article ("GET A HAIRCUT")
        if (letters.length == 1 && key.length == 1) {
            return if (allCapitals && key == "a" && index < allWords.size - 1) word.lowercase() else word
        }

        // With digits ("Q4", "B12", "5B"): as written, unless it starts with an ordinary word ("TASK4")
        if (key.any { it.isDigit() }) {
            val textPart = key.takeWhile { !it.isDigit() }
            return if (textPart.length > 1 && textPart in words) word.lowercase() else word
        }

        if (key in ambiguousAcronyms) {
            if (key == "am" && previous?.lastOrNull()?.isDigit() == true) return word.uppercase()
            return if (allCapitals) word.lowercase() else word
        }

        if (key in acronyms) return word.uppercase()

        if (key in names) {
            // A name that is also an ordinary word ("mark", "will", "rose") only when it reads as one
            if (key !in words) return capitalized(word)
            val writtenCapitalized = !allCapitals && index > 0 && word.firstOrNull { it.isLetter() }?.isUpperCase() == true
            return if (writtenCapitalized || previous in contactVerbs) capitalized(word) else word.lowercase()
        }

        // In a line written in normal case, a word that isn't an ordinary word keeps the capitals it was
        // written with ("Call IBM", "Meet Santos")
        if (!allCapitals && key !in words && word.any { it.isUpperCase() }) return word

        // Check if previous word indicates this is a name
        if (previous != null && previous in nameIndicators) {
            // If original was capitalized or all caps, treat as name
            val wasCapitalized = word.firstOrNull { it.isLetter() }?.isUpperCase() == true
            if (wasCapitalized && key !in words) return capitalized(word)
        }

        // Default: lowercase
        return word.lowercase()
    }

    /** Lower case with a capital first letter, punctuation kept ("(joe)" → "(Joe)"). */
    private fun capitalized(word: String): String {
        val lower = word.lowercase()
        val first = lower.indexOfFirst { it.isLetter() }
        return if (first < 0) lower else lower.substring(0, first) + lower[first].uppercaseChar() + lower.substring(first + 1)
    }

    /**
     * Stage 5: Capitalize first word of sentence.
     */
    private fun capitalizeSentence(text: String): String {
        if (text.isEmpty()) return text
        return text.replaceFirstChar { it.uppercase() }
    }

    /**
     * Load a dictionary from assets.
     */
    private fun loadDictionary(context: Context, fileName: String): Set<String> {
        val words = mutableSetOf<String>()

        try {
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.forEachLine { line ->
                        val trimmed = line.trim().lowercase()
                        // Skip comments and empty lines
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            words.add(trimmed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionary '$fileName': ${e.message}")
        }

        return words
    }

    /**
     * Get dictionary size (for debugging).
     */
    fun getWordDictSize(): Int = wordDict?.size ?: 0

    /**
     * Get names dictionary size (for debugging).
     */
    fun getNamesDictSize(): Int = namesDict?.size ?: 0

    /**
     * Check if a word is in the dictionary.
     */
    fun isValidWord(word: String): Boolean {
        return word.lowercase() in (wordDict ?: emptySet())
    }

    /**
     * Check if a word is a known name.
     */
    fun isKnownName(word: String): Boolean {
        return word.lowercase() in (namesDict ?: emptySet())
    }
}
