package com.boox.day.ocr

import android.content.Context
import android.util.Log

/**
 * Factory for creating and caching language-specific HandwritingNormalizer instances.
 *
 * Usage:
 * ```
 * val normalizer = HandwritingNormalizerFactory.getInstance(context, "es-ES")
 * val normalized = normalizer?.normalize(text)
 * ```
 *
 * Features:
 * - Single-language cache (only active language cached, ~100-160KB)
 * - Automatic eviction on language switch (constant memory footprint)
 * - Automatic language support detection (checks for dictionary files)
 * - Lazy loading (dictionaries loaded on first access)
 * - Thread-safe
 *
 * Memory model:
 * - Only ONE language's dictionaries are cached at a time
 * - When language changes, old dictionaries are evicted, new ones loaded
 * - Reload latency: ~50-100ms (imperceptible, language switch is rare)
 * - Memory: ~100-160KB constant (vs. up to 800KB with multi-language cache)
 */
object HandwritingNormalizerFactory {

    private const val TAG = "HandwritingNormalizerFactory"

    // Single-language cache (only active language)
    private var activeNormalizer: HandwritingNormalizer? = null
    private var activeLanguageTag: String = ""

    // Track which languages have full dictionary support
    private val supportedLanguages = mutableSetOf<String>()
    private var supportedLanguagesChecked = false

    /**
     * Get or create a HandwritingNormalizer for the specified language.
     *
     * If the requested language differs from the cached language, the cache
     * is evicted and the new language's dictionaries are loaded.
     *
     * @param context Android context for loading assets
     * @param languageTag BCP-47 language tag (e.g., "en-US", "es-ES")
     * @return HandwritingNormalizer instance for the language, or null if not supported
     */
    @Synchronized
    fun getInstance(context: Context, languageTag: String): HandwritingNormalizer? {
        // Check if language has dictionary support
        if (!hasLanguageSupport(context, languageTag)) {
            Log.w(TAG, "No dictionary support for $languageTag, use UniversalHandwritingNormalizer instead")
            return null
        }

        // Cache hit - same language as currently cached
        if (languageTag == activeLanguageTag && activeNormalizer != null) {
            return activeNormalizer
        }

        // Cache miss - different language, evict old and load new
        if (activeLanguageTag.isNotEmpty()) {
            Log.i(TAG, "Language switch: $activeLanguageTag → $languageTag (evicting old cache)")
        }

        val startTime = System.currentTimeMillis()
        activeNormalizer = HandwritingNormalizer.create(languageTag, context).apply {
            initialize()
        }
        activeLanguageTag = languageTag
        val elapsed = System.currentTimeMillis() - startTime

        Log.i(TAG, "Loaded $languageTag dictionaries in ${elapsed}ms (words=${activeNormalizer?.getWordDictSize()}, names=${activeNormalizer?.getNamesDictSize()})")
        return activeNormalizer
    }

    /**
     * Check if a language has full dictionary support (common_words.txt + names.txt exist).
     *
     * @param context Android context
     * @param languageTag BCP-47 language tag
     * @return true if both dictionary files exist for the language
     */
    @Synchronized
    fun hasLanguageSupport(context: Context, languageTag: String): Boolean {
        // Check cache first
        if (supportedLanguagesChecked && languageTag in supportedLanguages) {
            return true
        }
        if (supportedLanguagesChecked && languageTag !in supportedLanguages) {
            return false
        }

        // Check if language directory and files exist
        return try {
            val languageDir = "text_processing/$languageTag"
            val files = context.assets.list(languageDir) ?: emptyArray()

            val hasCommonWords = "common_words.txt" in files
            val hasNames = "names.txt" in files
            val supported = hasCommonWords && hasNames

            if (supported) {
                supportedLanguages.add(languageTag)
                Log.i(TAG, "Language $languageTag has full dictionary support")
            } else {
                Log.w(TAG, "Language $languageTag missing dictionaries (common_words: $hasCommonWords, names: $hasNames)")
            }

            supported
        } catch (e: Exception) {
            Log.e(TAG, "Error checking language support for $languageTag: ${e.message}")
            false
        }
    }

    /**
     * Get list of all supported languages (languages with full dictionary support).
     *
     * @param context Android context
     * @return Set of BCP-47 language tags that have full dictionary support
     */
    @Synchronized
    fun getSupportedLanguages(context: Context): Set<String> {
        if (!supportedLanguagesChecked) {
            try {
                val languageDirs = context.assets.list("text_processing") ?: emptyArray()
                languageDirs.forEach { languageTag ->
                    // Skip files, only check directories
                    try {
                        val files = context.assets.list("text_processing/$languageTag")
                        if (files != null && files.isNotEmpty()) {
                            hasLanguageSupport(context, languageTag)
                        }
                    } catch (e: Exception) {
                        // Not a directory, skip
                    }
                }
                supportedLanguagesChecked = true
                Log.i(TAG, "Supported languages: $supportedLanguages")
            } catch (e: Exception) {
                Log.e(TAG, "Error listing supported languages: ${e.message}")
            }
        }
        return supportedLanguages.toSet()
    }

    /**
     * Clear cached normalizer instance (for testing or memory management).
     */
    @Synchronized
    fun clearCache() {
        activeNormalizer = null
        activeLanguageTag = ""
        supportedLanguages.clear()
        supportedLanguagesChecked = false
        Log.i(TAG, "Cleared HandwritingNormalizer cache")
    }

    /**
     * Get memory usage estimate for cached normalizer.
     *
     * @return Estimated memory usage in bytes
     */
    @Synchronized
    fun getMemoryUsage(): Long {
        val normalizer = activeNormalizer ?: return 0L
        // Rough estimate: word count * average word length * 2 (String overhead)
        val words = normalizer.getWordDictSize()
        val names = normalizer.getNamesDictSize()
        val bytes = ((words + names) * 8 * 2).toLong()  // ~8 chars/word, 2 bytes/char
        Log.d(TAG, "$activeLanguageTag: ~${bytes / 1024}KB (${words} words, ${names} names)")
        return bytes
    }

    /**
     * Get number of cached normalizer instances.
     * With single-language cache, this is always 0 or 1.
     */
    @Synchronized
    fun getCachedInstanceCount(): Int = if (activeNormalizer != null) 1 else 0

    /**
     * Get the currently cached language tag, or empty string if none.
     */
    @Synchronized
    fun getActiveLanguageTag(): String = activeLanguageTag
}
