package com.boox.day.ocr

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Language-specific handwriting normalizer for Digital Ink recognition results.
 *
 * One instance per language, cached by HandwritingNormalizerFactory.
 * ~60KB memory per language.
 *
 * Pipeline (5 stages):
 * 1. Word Segmentation - split merged words ("ADDINGA" → "ADDING A")
 * 2. Spell Correction - fix typos ("intereview" → "interview")
 * 3. Case Normalization - lowercase everything
 * 4. Proper Noun Detection - capitalize names ("rajin" → "Rajin")
 * 5. Sentence Capitalization - capitalize first word
 *
 * NOTE: This is different from ocr/semantic/TextNormalizer.kt which does
 * OCR cleanup (NFKC, whitespace, garbage removal). This file does
 * handwriting-specific spell correction and word segmentation.
 */
class HandwritingNormalizer private constructor(
    private val languageTag: String,
    private val context: Context
) {
    companion object {
        private const val TAG = "HandwritingNormalizer"

        // Known acronyms to preserve (language-independent for now)
        private val acronyms = setOf(
            "api", "usa", "asap", "pdf", "ceo", "hr", "it", "cfo", "cto", "coo",
            "vp", "svp", "evp", "md", "phd", "mba", "ba", "bs", "ms", "ma",
            "pm", "am", "est", "pst", "gmt", "utc", "etc", "fyi", "btw", "tbd",
            "eta", "rsvp", "diy", "faq", "id", "pin", "atm", "gps", "wifi",
            "tv", "pc", "mac", "ios", "ok", "aka", "vs", "re", "cc", "bcc"
        )

        // Words that indicate a following word is likely a name
        private val nameIndicators = setOf(
            "call", "email", "meet", "contact", "text", "message", "reach",
            "to", "from", "with", "for", "ask", "tell", "remind", "notify",
            "invite", "schedule", "book", "see", "visit", "thank", "congratulate"
        )

        /**
         * Factory method - use HandwritingNormalizerFactory.getInstance() instead.
         * This is exposed for testing only.
         */
        internal fun create(languageTag: String, context: Context): HandwritingNormalizer {
            return HandwritingNormalizer(languageTag, context)
        }
    }

    // Asset file paths (language-specific)
    private val commonWordsFile = "text_processing/$languageTag/common_words.txt"
    private val namesFile = "text_processing/$languageTag/names.txt"

    // Loaded once per language, cached forever
    private var wordDict: Set<String>? = null
    private var namesDict: Set<String>? = null
    private var initialized = false

    // Pipeline components (lazy initialized after dictionary load)
    private var wordSegmenter: WordSegmenter? = null
    private var spellCorrector: SpellCorrector? = null

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

                // Initialize pipeline components with language-specific alphabet
                wordSegmenter = WordSegmenter(wordDict!!)
                val alphabet = SpellCorrector.getAlphabetForLanguage(languageTag)
                // CRITICAL: Include namesDict so spell corrector doesn't "correct" valid names
                // e.g., "Bob" was being corrected to "job" because "bob" wasn't in wordDict
                val combinedDict = wordDict!! + namesDict!!
                spellCorrector = SpellCorrector(combinedDict, emptyMap(), 0.70f, alphabet)

                initialized = true

                val elapsed = System.currentTimeMillis() - startTime
                Log.i(TAG, "Dictionaries loaded for $languageTag in ${elapsed}ms (words=${wordDict!!.size}, names=${namesDict!!.size})")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dictionaries for $languageTag: ${e.message}", e)
                // Initialize with empty sets to prevent crashes
                wordDict = emptySet()
                namesDict = emptySet()
                wordSegmenter = WordSegmenter(emptySet())
                val fallbackAlphabet = SpellCorrector.getAlphabetForLanguage(languageTag)
                spellCorrector = SpellCorrector(emptySet(), emptyMap(), 0.70f, fallbackAlphabet)
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

        try {
            var result = text

            // Stage 1: Word Segmentation
            result = wordSegmenter?.segment(result) ?: result

            // Stage 2: Spell Correction
            result = spellCorrector?.correctText(result) ?: result

            // Stage 3 & 4: Case Normalization + Proper Noun Detection
            result = normalizeAndDetectNouns(result, text)

            // Stage 5: Sentence Capitalization
            result = capitalizeSentence(result)

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
     * Stage 3 & 4: Normalize case and detect proper nouns.
     */
    private fun normalizeAndDetectNouns(text: String, original: String): String {
        val words = text.split(Regex("\\s+"))
        val originalWords = original.split(Regex("\\s+"))

        val normalized = words.mapIndexed { index, word ->
            normalizeWord(word, index, words, originalWords.getOrNull(index))
        }

        return normalized.joinToString(" ")
    }

    /**
     * Normalize a single word with context awareness.
     */
    private fun normalizeWord(
        word: String,
        index: Int,
        allWords: List<String>,
        originalWord: String?
    ): String {
        val lowerWord = word.lowercase()

        // Check if it's an acronym
        if (lowerWord in acronyms) {
            return lowerWord.uppercase()
        }

        // Check if it's a known name
        if (lowerWord in (namesDict ?: emptySet())) {
            return lowerWord.replaceFirstChar { it.uppercase() }
        }

        // Check if previous word indicates this is a name
        if (index > 0) {
            val prevWord = allWords[index - 1].lowercase()
            if (prevWord in nameIndicators) {
                // If original was capitalized or all caps, treat as name
                val wasCapitalized = originalWord?.firstOrNull()?.isUpperCase() == true
                if (wasCapitalized && lowerWord !in (wordDict ?: emptySet())) {
                    return lowerWord.replaceFirstChar { it.uppercase() }
                }
            }
        }

        // Check for numbers attached to words (e.g., "task1" → keep structure)
        if (word.any { it.isDigit() }) {
            return preserveNumberStructure(word)
        }

        // Default: lowercase
        return lowerWord
    }

    /**
     * Preserve number structure in words like "Task1", "TASK4".
     */
    private fun preserveNumberStructure(word: String): String {
        // Split into text and number parts
        val textPart = word.takeWhile { !it.isDigit() }
        val numberPart = word.dropWhile { !it.isDigit() }

        return textPart.lowercase() + numberPart
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
