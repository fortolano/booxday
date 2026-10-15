package com.boox.day.ocr.semantic.classification

/**
 * Language detection and classification mode selection.
 *
 * Three-mode strategy:
 * - Mode A: Language supported → full pipeline (structure + patterns + dictionaries + disambiguation)
 * - Mode B: Language unknown → structure + digits + punctuation + international signals
 * - Mode C: User-extended → Mode A/B + user custom dictionaries
 */
object LanguageDetector {

    /** Languages with full dictionary support */
    val SUPPORTED_LANGUAGES = setOf("en")  // Expandable: "es", "de", "fr", "pt", "it", "nl"

    /**
     * Detect language from text using simple heuristics (not ML).
     * Uses character patterns, common function words, and Unicode ranges.
     */
    fun detect(text: String): LanguageResult {
        val cleanText = text.lowercase().take(500)

        // Check for non-Latin scripts → MODE_B (structure-only)
        if (containsCJK(cleanText)) return LanguageResult("zh", false)
        if (containsCyrillic(cleanText)) return LanguageResult("ru", false)
        if (containsArabic(cleanText)) return LanguageResult("ar", false)
        if (containsHebrew(cleanText)) return LanguageResult("he", false)
        if (containsDevanagari(cleanText)) return LanguageResult("hi", false)

        // Check common function words for supported languages
        val langScores = SUPPORTED_LANGUAGES.associateWith { lang ->
            LANGUAGE_MARKERS[lang]?.count { cleanText.contains(it) } ?: 0
        }

        val topEntry = langScores.maxByOrNull { it.value }
        val topLang = topEntry?.key ?: "en"
        val topScore = topEntry?.value ?: 0

        return if (topScore >= 3) {
            LanguageResult(topLang, true)
        } else {
            // Default to English with lower confidence if Latin script
            if (isLatinScript(cleanText)) {
                LanguageResult("en", true)  // Assume English for Latin text
            } else {
                LanguageResult("unknown", false)
            }
        }
    }

    // Ultra-common function words that rarely translate
    private val LANGUAGE_MARKERS = mapOf(
        "en" to listOf(" the ", " and ", " to ", " of ", " is ", " in ", " for ", " with ", " that ", " this "),
        "es" to listOf(" el ", " la ", " de ", " que ", " en ", " los ", " y ", " las ", " del ", " por "),
        "de" to listOf(" der ", " die ", " und ", " ist ", " den ", " das ", " mit ", " nicht ", " ein ", " auf "),
        "fr" to listOf(" le ", " la ", " de ", " et ", " les ", " des ", " est ", " que ", " pour ", " dans "),
        "pt" to listOf(" o ", " a ", " de ", " que ", " em ", " os ", " e ", " do ", " da ", " para "),
        "it" to listOf(" il ", " la ", " di ", " che ", " in ", " per ", " è ", " non ", " del ", " con "),
        "nl" to listOf(" de ", " het ", " en ", " van ", " een ", " in ", " te ", " op ", " dat ", " voor ")
    )

    // Unicode range checks
    private fun containsCJK(text: String): Boolean {
        return text.any { char ->
            val code = char.code
            (code in 0x4E00..0x9FFF) ||   // CJK Unified Ideographs
            (code in 0x3400..0x4DBF) ||   // CJK Extension A
            (code in 0x3040..0x309F) ||   // Hiragana
            (code in 0x30A0..0x30FF)      // Katakana
        }
    }

    private fun containsCyrillic(text: String): Boolean {
        return text.any { it.code in 0x0400..0x04FF }
    }

    private fun containsArabic(text: String): Boolean {
        return text.any { it.code in 0x0600..0x06FF }
    }

    private fun containsHebrew(text: String): Boolean {
        return text.any { it.code in 0x0590..0x05FF }
    }

    private fun containsDevanagari(text: String): Boolean {
        return text.any { it.code in 0x0900..0x097F }
    }

    private fun isLatinScript(text: String): Boolean {
        val latinChars = text.count { char ->
            val code = char.code
            (code in 0x0041..0x007A) ||   // Basic Latin A-z
            (code in 0x00C0..0x00FF) ||   // Latin Extended-A
            (code in 0x0100..0x017F)      // Latin Extended-B
        }
        return latinChars > text.length * 0.5  // More than 50% Latin
    }

    data class LanguageResult(
        val language: String,
        val isSupported: Boolean
    )
}

/**
 * Classification mode based on language support and user customization.
 */
enum class ClassificationMode {
    /** Full pipeline: structure + patterns + dictionaries + disambiguation */
    MODE_A_FULL,

    /** Structure-only: structure + digits + punctuation + international signals */
    MODE_B_STRUCTURE,

    /** User-extended: MODE_A or MODE_B + user custom dictionaries */
    MODE_C_EXTENDED
}

/**
 * Select classification mode based on language and user dictionaries.
 */
object ModeSelector {
    fun selectMode(
        languageResult: LanguageDetector.LanguageResult,
        hasUserDictionaries: Boolean
    ): ClassificationMode {
        return when {
            hasUserDictionaries -> ClassificationMode.MODE_C_EXTENDED
            languageResult.isSupported -> ClassificationMode.MODE_A_FULL
            else -> ClassificationMode.MODE_B_STRUCTURE
        }
    }
}
