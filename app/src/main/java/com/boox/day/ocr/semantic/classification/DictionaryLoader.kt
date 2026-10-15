package com.boox.day.ocr.semantic.classification

import android.content.Context
import android.util.Log
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.Intent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Loads classification dictionaries from assets.
 * Handles language fallback and user customization.
 *
 * Directory structure:
 * assets/classification/
 * ├── en/                    # English (default)
 * │   ├── config.json
 * │   ├── domains/
 * │   │   └── work.json, school.json, tech.json, ...
 * │   ├── intents/
 * │   │   └── todo_list.json, planning.json, ...
 * │   ├── collocations.json
 * │   └── ocr_corrections.json
 * └── shared/                # Language-independent
 *     ├── structure_patterns.json
 *     └── international.json
 */
class DictionaryLoader(private val context: Context) {

    companion object {
        private const val TAG = "DictionaryLoader"
        private const val BASE_PATH = "classification"
        private const val DEFAULT_LANGUAGE = "en"
        private const val SHARED_PATH = "$BASE_PATH/shared"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Load complete dictionary set for a language.
     * Falls back to English for missing files.
     */
    fun loadDictionaries(language: String): LanguageDictionaries {
        val langPath = if (languageExists(language)) language else DEFAULT_LANGUAGE

        return LanguageDictionaries(
            language = langPath,
            domains = loadDomains(langPath),
            intents = loadIntents(langPath),
            structurePatterns = loadSharedStructure()
        )
    }

    private fun languageExists(language: String): Boolean {
        return try {
            context.assets.list("$BASE_PATH/$language")?.isNotEmpty() == true
        } catch (e: Exception) {
            false
        }
    }

    private fun loadDomains(language: String): Map<Domain, DomainDictionary> {
        val result = mutableMapOf<Domain, DomainDictionary>()

        for (domain in Domain.entries) {
            if (domain == Domain.MISC) continue

            val filename = domain.name.lowercase() + ".json"
            val content = readAsset("$BASE_PATH/$language/domains/$filename")
                ?: readAsset("$BASE_PATH/$DEFAULT_LANGUAGE/domains/$filename")
                ?: continue

            try {
                val dict = json.decodeFromString<DomainDictionaryJson>(content)
                result[domain] = dict.toDomainDictionary()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load $filename: ${e.message}")
            }
        }

        return result
    }

    private fun loadIntents(language: String): Map<Intent, IntentDictionary> {
        val result = mutableMapOf<Intent, IntentDictionary>()

        for (intent in Intent.entries) {
            if (intent == Intent.FREEFORM) continue

            val filename = intent.name.lowercase() + ".json"
            val content = readAsset("$BASE_PATH/$language/intents/$filename")
                ?: readAsset("$BASE_PATH/$DEFAULT_LANGUAGE/intents/$filename")
                ?: continue

            try {
                val dict = json.decodeFromString<IntentDictionaryJson>(content)
                result[intent] = dict.toIntentDictionary()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load $filename: ${e.message}")
            }
        }

        return result
    }

    private fun loadSharedStructure(): StructurePatternsData {
        val content = readAsset("$SHARED_PATH/structure_patterns.json")
            ?: return StructurePatternsData.DEFAULT

        return try {
            json.decodeFromString(content)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load structure patterns: ${e.message}")
            StructurePatternsData.DEFAULT
        }
    }

    private fun readAsset(path: String): String? {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load event-specific patterns dictionary.
     * Contains domain and intent keywords for calendar event classification.
     */
    fun loadEventPatterns(language: String = DEFAULT_LANGUAGE): EventPatternsDictionary {
        val path = "$BASE_PATH/$language/event_patterns.json"
        val content = readAsset(path)
            ?: readAsset("$BASE_PATH/$DEFAULT_LANGUAGE/event_patterns.json")
            ?: return EventPatternsDictionary.DEFAULT

        return try {
            json.decodeFromString(content)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load event patterns: ${e.message}")
            EventPatternsDictionary.DEFAULT
        }
    }

    /**
     * Load abbreviations dictionary for text matching.
     */
    fun loadAbbreviations(language: String = DEFAULT_LANGUAGE): AbbreviationsDictionary {
        val path = "$BASE_PATH/$language/abbreviations.json"
        val content = readAsset(path)
            ?: readAsset("$BASE_PATH/$DEFAULT_LANGUAGE/abbreviations.json")
            ?: return AbbreviationsDictionary.DEFAULT

        return try {
            json.decodeFromString(content)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load abbreviations: ${e.message}")
            AbbreviationsDictionary.DEFAULT
        }
    }

    /**
     * Get list of available languages.
     */
    fun getAvailableLanguages(): List<LanguageInfo> {
        val languages = mutableListOf<LanguageInfo>()

        try {
            context.assets.list(BASE_PATH)?.forEach { folder ->
                if (folder != "shared" && folder != "_template" && folder != "user_custom") {
                    val configPath = "$BASE_PATH/$folder/config.json"
                    val config = readAsset(configPath)?.let {
                        try {
                            json.decodeFromString<LanguageConfig>(it)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    languages.add(
                        LanguageInfo(
                            code = folder,
                            name = config?.languageName ?: folder.uppercase(),
                            completeness = config?.completeness?.domains ?: 0
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list languages: ${e.message}")
        }

        return languages.sortedByDescending { it.completeness }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DATA CLASSES FOR JSON PARSING
// ═══════════════════════════════════════════════════════════════════════════

@Serializable
data class LanguageConfig(
    val languageCode: String,
    val languageName: String,
    val version: Int = 1,
    val completeness: CompletenessInfo = CompletenessInfo()
)

@Serializable
data class CompletenessInfo(
    val domains: Int = 0,
    val intents: Int = 0,
    val collocations: Int = 0,
    val ocrCorrections: Int = 0
)

@Serializable
data class DomainDictionaryJson(
    val domain: String,
    val tierA: TierInfo = TierInfo(),
    val tierB: TierInfo = TierInfo(),
    val tierC: TierInfo = TierInfo(),
    val phrasePatterns: List<PhrasePattern> = emptyList(),
    val subdomainHints: Map<String, List<String>> = emptyMap()
) {
    fun toDomainDictionary() = DomainDictionary(
        domain = Domain.valueOf(domain),
        tierA = tierA.terms to tierA.weight,
        tierB = tierB.terms to tierB.weight,
        tierC = tierC.terms to tierC.weight,
        phrasePatterns = phrasePatterns,
        subdomainHints = subdomainHints
    )
}

@Serializable
data class IntentDictionaryJson(
    val intent: String,
    val tierA: TierInfo = TierInfo(),
    val tierB: TierInfo = TierInfo(),
    val tierC: TierInfo = TierInfo(),
    val phrasePatterns: List<PhrasePattern> = emptyList(),
    val structureBoosts: Map<String, Float> = emptyMap()
) {
    fun toIntentDictionary() = IntentDictionary(
        intent = Intent.valueOf(intent),
        tierA = tierA.terms to tierA.weight,
        tierB = tierB.terms to tierB.weight,
        tierC = tierC.terms to tierC.weight,
        phrasePatterns = phrasePatterns,
        structureBoosts = structureBoosts
    )
}

@Serializable
data class TierInfo(
    val weight: Float = 1.0f,
    val terms: List<String> = emptyList()
)

@Serializable
data class PhrasePattern(
    val pattern: String,
    val weight: Float = 1.0f
)

@Serializable
data class StructurePatternsData(
    val version: Int = 1,
    val patterns: Map<String, StructurePatternDef> = emptyMap()
) {
    companion object {
        val DEFAULT = StructurePatternsData(
            patterns = mapOf(
                "CHECKBOX_UNCHECKED" to StructurePatternDef(
                    regex = """(?:^|\n)\s*[\[\(]\s*[\]\)]""",
                    inferredIntent = "TODO_LIST",
                    weight = 5.0f
                ),
                "CHECKBOX_CHECKED" to StructurePatternDef(
                    regex = """(?:^|\n)\s*[\[\(][xX✓✔][\]\)]""",
                    inferredIntent = "TODO_LIST",
                    weight = 5.0f
                ),
                "BULLET_LIST" to StructurePatternDef(
                    regex = """(?:^|\n)\s*[-•*]\s+\S""",
                    minMatches = 2,
                    weight = 2.0f
                ),
                "NUMBERED_LIST" to StructurePatternDef(
                    regex = """(?:^|\n)\s*\d+[.):\s]+\S""",
                    minMatches = 2,
                    inferredIntent = "PLANNING",
                    weight = 2.5f
                ),
                "CODE_BLOCK" to StructurePatternDef(
                    regex = """```[\s\S]*?```""",
                    inferredDomain = "TECH",
                    weight = 6.0f
                )
            )
        )
    }
}

@Serializable
data class StructurePatternDef(
    val regex: String,
    val minMatches: Int = 1,
    val inferredIntent: String? = null,
    val inferredDomain: String? = null,
    val weight: Float = 1.0f
)

@Serializable
data class EventPatternsDictionary(
    val version: Int = 1,
    val description: String = "",
    val domainEventKeywords: Map<String, DomainEventEntry> = emptyMap(),
    val intentEventKeywords: Map<String, IntentEventEntry> = emptyMap(),
    val exclusionPatterns: List<String> = emptyList()
) {
    companion object {
        val DEFAULT = EventPatternsDictionary()
    }

    /**
     * Get keywords for a domain.
     */
    fun getKeywordsForDomain(domain: Domain): Set<String> {
        return domainEventKeywords[domain.name]?.keywords?.toSet() ?: emptySet()
    }

    /**
     * Get weight for a domain.
     */
    fun getWeightForDomain(domain: Domain): Float {
        return domainEventKeywords[domain.name]?.weight ?: 2.5f
    }

    /**
     * Get keywords for an intent.
     */
    fun getKeywordsForIntent(intent: Intent): Set<String> {
        return intentEventKeywords[intent.name]?.keywords?.toSet() ?: emptySet()
    }

    /**
     * Get weight for an intent.
     */
    fun getWeightForIntent(intent: Intent): Float {
        return intentEventKeywords[intent.name]?.weight ?: 2.0f
    }
}

@Serializable
data class DomainEventEntry(
    val keywords: List<String> = emptyList(),
    val weight: Float = 2.5f
)

@Serializable
data class IntentEventEntry(
    val keywords: List<String> = emptyList(),
    val weight: Float = 2.0f
)

@Serializable
data class AbbreviationsDictionary(
    val version: Int = 1,
    val description: String = "",
    val abbreviations: Map<String, String> = emptyMap()
) {
    companion object {
        val DEFAULT = AbbreviationsDictionary()
    }

    /**
     * Get expanded form for an abbreviation.
     */
    fun expand(abbreviation: String): String? {
        return abbreviations[abbreviation.lowercase()]
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RUNTIME DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════

data class DomainDictionary(
    val domain: Domain,
    val tierA: Pair<List<String>, Float>,
    val tierB: Pair<List<String>, Float>,
    val tierC: Pair<List<String>, Float>,
    val phrasePatterns: List<PhrasePattern>,
    val subdomainHints: Map<String, List<String>>
) {
    // Pre-computed lowercase sets for O(1) lookup instead of O(N) list creation per call
    private val tierALower: Set<String> = tierA.first.map { it.lowercase() }.toSet()
    private val tierBLower: Set<String> = tierB.first.map { it.lowercase() }.toSet()
    private val tierCLower: Set<String> = tierC.first.map { it.lowercase() }.toSet()

    /** Get all terms with their weights */
    fun allTermsWithWeights(): List<Pair<String, Float>> {
        return tierA.first.map { it to tierA.second } +
               tierB.first.map { it to tierB.second } +
               tierC.first.map { it to tierC.second }
    }

    /** Check if a term is in any tier (O(1) lookup) */
    fun containsTerm(term: String): Boolean {
        val lower = term.lowercase()
        return lower in tierALower || lower in tierBLower || lower in tierCLower
    }

    /** Get weight for a term (O(1) lookup) */
    fun getWeight(term: String): Float {
        val lower = term.lowercase()
        return when {
            lower in tierALower -> tierA.second
            lower in tierBLower -> tierB.second
            lower in tierCLower -> tierC.second
            else -> 0f
        }
    }

    /** Get tier for a term (O(1) lookup) */
    fun getTier(term: String): String? {
        val lower = term.lowercase()
        return when {
            lower in tierALower -> "A"
            lower in tierBLower -> "B"
            lower in tierCLower -> "C"
            else -> null
        }
    }

    /** Get Tier A terms as lowercase set (for fuzzy matching) */
    fun getTierATerms(): Set<String> = tierALower
}

data class IntentDictionary(
    val intent: Intent,
    val tierA: Pair<List<String>, Float>,
    val tierB: Pair<List<String>, Float>,
    val tierC: Pair<List<String>, Float>,
    val phrasePatterns: List<PhrasePattern>,
    val structureBoosts: Map<String, Float>
) {
    // Pre-computed lowercase sets for O(1) lookup instead of O(N) list creation per call
    private val tierALower: Set<String> = tierA.first.map { it.lowercase() }.toSet()
    private val tierBLower: Set<String> = tierB.first.map { it.lowercase() }.toSet()
    private val tierCLower: Set<String> = tierC.first.map { it.lowercase() }.toSet()

    /** Check if a term is in any tier (O(1) lookup) */
    fun containsTerm(term: String): Boolean {
        val lower = term.lowercase()
        return lower in tierALower || lower in tierBLower || lower in tierCLower
    }

    /** Get weight for a term (O(1) lookup) */
    fun getWeight(term: String): Float {
        val lower = term.lowercase()
        return when {
            lower in tierALower -> tierA.second
            lower in tierBLower -> tierB.second
            lower in tierCLower -> tierC.second
            else -> 0f
        }
    }

    /** Get tier for a term (O(1) lookup) */
    fun getTier(term: String): String? {
        val lower = term.lowercase()
        return when {
            lower in tierALower -> "A"
            lower in tierBLower -> "B"
            lower in tierCLower -> "C"
            else -> null
        }
    }

    /** Get Tier A terms as lowercase set (for fuzzy matching) */
    fun getTierATerms(): Set<String> = tierALower
}

data class LanguageInfo(
    val code: String,
    val name: String,
    val completeness: Int
)

// ═══════════════════════════════════════════════════════════════════════════
// INVERTED INDEX FOR O(1) TOKEN LOOKUP
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Represents a domain match for a token in the inverted index.
 */
data class DomainMatch(
    val domain: Domain,
    val tier: String,
    val weight: Float
)

/**
 * Represents an intent match for a token in the inverted index.
 */
data class IntentMatch(
    val intent: Intent,
    val tier: String,
    val weight: Float
)

/**
 * Inverted index for O(1) token lookup during classification.
 * Built once at dictionary load time, used for every classification.
 */
data class InvertedIndex(
    /** token (lowercase) -> list of (Domain, tier, weight) */
    val domainIndex: Map<String, List<DomainMatch>>,
    /** token (lowercase) -> list of (Intent, tier, weight) */
    val intentIndex: Map<String, List<IntentMatch>>,
    /** All Tier A domain terms (for fuzzy matching) */
    val tierADomainTerms: Set<String>,
    /** All Tier A intent terms (for fuzzy matching) */
    val tierAIntentTerms: Set<String>
) {
    companion object {
        /** Base weight multiplier for fuzzy matches (scaled by match quality) */
        const val FUZZY_WEIGHT_MULTIPLIER = 0.8f

        /** Minimum term length for fuzzy matching */
        const val MIN_FUZZY_TERM_LENGTH = 4

        // ═══════════════════════════════════════════════════════════════════════
        // OCR-AWARE FUZZY MATCHING (Enhanced Jan 2026)
        // Uses weighted edit distance that understands OCR confusion patterns
        // ═══════════════════════════════════════════════════════════════════════

        /**
         * Known OCR confusions for first-letter validation.
         * OCR rarely gets the first letter completely wrong unless it's
         * one of these visually similar character pairs.
         */
        private val FIRST_LETTER_CONFUSIONS = setOf(
            'l' to '1', '1' to 'l',
            'l' to 'i', 'i' to 'l',
            'i' to '1', '1' to 'i',
            'o' to '0', '0' to 'o',
            'c' to 'e', 'e' to 'c',
            'n' to 'h', 'h' to 'n',
            'u' to 'v', 'v' to 'u',
            'r' to 'n', 'n' to 'r'
        )

        /**
         * Check if first letters could plausibly be OCR confusion.
         * This is a strong signal - if first letters don't match and
         * aren't a known confusion, it's probably not an OCR error.
         */
        private fun isPlausibleFirstLetterMatch(c1: Char, c2: Char): Boolean {
            val lc1 = c1.lowercaseChar()
            val lc2 = c2.lowercaseChar()
            if (lc1 == lc2) return true
            return (lc1 to lc2) in FIRST_LETTER_CONFUSIONS
        }

        /**
         * Get adaptive OCR distance threshold based on word length.
         * Shorter words need stricter matching (higher false positive rate).
         * Longer words can tolerate more variation.
         */
        private fun getOcrDistanceThreshold(wordLength: Int): Float = when (wordLength) {
            in 1..3 -> 0.5f     // Very short: near-exact only
            4 -> 0.7f           // Short: strict (prevents lond→bond)
            5 -> 0.9f           // Medium-short: moderate
            in 6..8 -> 1.2f     // Medium: reasonable tolerance
            else -> 1.5f        // Long words: more lenient
        }

        /**
         * Calculate quality-adjusted weight multiplier for fuzzy match.
         * Better matches (lower distance) get higher weight retention.
         *
         * @param distance OCR-weighted edit distance (0.0 = perfect, higher = worse)
         * @param threshold Maximum acceptable distance
         * @return Weight multiplier between 0.5 and 0.9
         */
        private fun getFuzzyWeightMultiplier(distance: Float, threshold: Float): Float {
            // Perfect match through OCR expansion (e.g., rn→m) gets 0.9x
            if (distance <= 0.3f) return 0.9f
            // Good match (single OCR confusion) gets 0.8x
            if (distance <= 0.7f) return 0.8f
            // Moderate match gets 0.7x
            if (distance <= 1.0f) return 0.7f
            // Weak match (near threshold) gets 0.6x
            return 0.6f
        }

        /**
         * Check if a token is a valid OCR-aware fuzzy match for a dictionary term.
         *
         * This is smarter than plain Levenshtein because:
         * 1. First-letter validation - OCR rarely garbles first letters
         * 2. OCR-weighted edit distance - known confusions cost less
         * 3. Adaptive thresholds - shorter words need stricter matching
         *
         * @param ocrToken Token from OCR (potentially garbled)
         * @param dictTerm Dictionary term to match against
         * @return Pair of (isMatch, distance) or null if no match
         */
        fun isValidOcrFuzzyMatch(ocrToken: String, dictTerm: String): Pair<Boolean, Float>? {
            // Length difference check (already exists, keep it)
            if (kotlin.math.abs(ocrToken.length - dictTerm.length) > 1) return null

            // First letter validation - strong signal
            if (ocrToken.isNotEmpty() && dictTerm.isNotEmpty()) {
                if (!isPlausibleFirstLetterMatch(ocrToken[0], dictTerm[0])) {
                    return null  // First letters don't match and aren't OCR confusions
                }
            }

            // OCR-weighted edit distance
            val distance = OcrCorrector.weightedEditDistance(ocrToken, dictTerm)
            val threshold = getOcrDistanceThreshold(dictTerm.length)

            return if (distance <= threshold) {
                true to distance
            } else {
                null
            }
        }

        /**
         * Calculate Levenshtein edit distance between two strings.
         * Returns the minimum number of single-character edits (insertions,
         * deletions, substitutions) required to change one string into another.
         */
        fun levenshteinDistance(s1: String, s2: String): Int {
            val m = s1.length
            val n = s2.length

            // Quick checks
            if (m == 0) return n
            if (n == 0) return m
            if (s1 == s2) return 0

            // Early exit if length difference > 1 (since we only care about distance ≤ 1)
            if (kotlin.math.abs(m - n) > 1) return 2

            // Use two rows instead of full matrix for O(n) space
            var prevRow = IntArray(n + 1) { it }
            var currRow = IntArray(n + 1)

            for (i in 1..m) {
                currRow[0] = i
                for (j in 1..n) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    currRow[j] = minOf(
                        prevRow[j] + 1,      // deletion
                        currRow[j - 1] + 1,  // insertion
                        prevRow[j - 1] + cost // substitution
                    )
                }
                // Swap rows
                val temp = prevRow
                prevRow = currRow
                currRow = temp
            }

            return prevRow[n]
        }

        /**
         * Build inverted index from domain and intent dictionaries.
         */
        fun build(
            domains: Map<Domain, DomainDictionary>,
            intents: Map<Intent, IntentDictionary>
        ): InvertedIndex {
            val domainIndex = mutableMapOf<String, MutableList<DomainMatch>>()
            val intentIndex = mutableMapOf<String, MutableList<IntentMatch>>()
            val tierADomainTerms = mutableSetOf<String>()
            val tierAIntentTerms = mutableSetOf<String>()

            // Build domain index
            for ((domain, dict) in domains) {
                // Tier A
                for (term in dict.tierA.first) {
                    val lower = term.lowercase()
                    domainIndex.getOrPut(lower) { mutableListOf() }
                        .add(DomainMatch(domain, "A", dict.tierA.second))
                    tierADomainTerms.add(lower)
                }
                // Tier B
                for (term in dict.tierB.first) {
                    val lower = term.lowercase()
                    domainIndex.getOrPut(lower) { mutableListOf() }
                        .add(DomainMatch(domain, "B", dict.tierB.second))
                }
                // Tier C
                for (term in dict.tierC.first) {
                    val lower = term.lowercase()
                    domainIndex.getOrPut(lower) { mutableListOf() }
                        .add(DomainMatch(domain, "C", dict.tierC.second))
                }
            }

            // Build intent index
            for ((intent, dict) in intents) {
                // Tier A
                for (term in dict.tierA.first) {
                    val lower = term.lowercase()
                    intentIndex.getOrPut(lower) { mutableListOf() }
                        .add(IntentMatch(intent, "A", dict.tierA.second))
                    tierAIntentTerms.add(lower)
                }
                // Tier B
                for (term in dict.tierB.first) {
                    val lower = term.lowercase()
                    intentIndex.getOrPut(lower) { mutableListOf() }
                        .add(IntentMatch(intent, "B", dict.tierB.second))
                }
                // Tier C
                for (term in dict.tierC.first) {
                    val lower = term.lowercase()
                    intentIndex.getOrPut(lower) { mutableListOf() }
                        .add(IntentMatch(intent, "C", dict.tierC.second))
                }
            }

            return InvertedIndex(
                domainIndex = domainIndex,
                intentIndex = intentIndex,
                tierADomainTerms = tierADomainTerms,
                tierAIntentTerms = tierAIntentTerms
            )
        }
    }

    /**
     * Look up domain matches for a token. O(1) operation.
     */
    fun lookupDomain(token: String): List<DomainMatch> {
        return domainIndex[token.lowercase()] ?: emptyList()
    }

    /**
     * Look up intent matches for a token. O(1) operation.
     */
    fun lookupIntent(token: String): List<IntentMatch> {
        return intentIndex[token.lowercase()] ?: emptyList()
    }

    /**
     * Check if a token matches any domain term.
     */
    fun hasDomainMatch(token: String): Boolean {
        return token.lowercase() in domainIndex
    }

    /**
     * Check if a token matches any intent term.
     */
    fun hasIntentMatch(token: String): Boolean {
        return token.lowercase() in intentIndex
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OCR-AWARE FUZZY MATCHING (Enhanced Jan 2026)
    // Uses weighted edit distance + first-letter validation to reduce false positives
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Fuzzy lookup for domain matches using OCR-aware matching.
     *
     * Enhanced features (Jan 2026):
     * - First-letter validation: rejects matches where first letters differ
     *   unless they're known OCR confusions (l/1/i, o/0, etc.)
     * - OCR-weighted edit distance: known OCR confusions cost less
     * - Adaptive thresholds: shorter words require stricter matching
     * - Quality-scaled weights: better matches get higher weight retention
     *
     * This prevents false positives like "lond" → "bond" while still
     * catching real OCR errors like "rneet" → "meet".
     *
     * @param token The token to fuzzy match (must be ≥ 4 chars)
     * @return List of fuzzy domain matches with quality-adjusted weights
     */
    fun fuzzyLookupDomain(token: String): List<DomainMatch> {
        val lower = token.lowercase()

        // Skip short tokens (too many false positives)
        if (lower.length < MIN_FUZZY_TERM_LENGTH) return emptyList()

        // Skip if exact match exists (no need for fuzzy)
        if (lower in domainIndex) return emptyList()

        val fuzzyMatches = mutableListOf<DomainMatch>()

        for (term in tierADomainTerms) {
            // Skip short terms
            if (term.length < MIN_FUZZY_TERM_LENGTH) continue

            // Use OCR-aware fuzzy matching (checks first-letter, weighted distance, adaptive threshold)
            val matchResult = isValidOcrFuzzyMatch(lower, term)
            if (matchResult != null) {
                val (_, distance) = matchResult
                // Calculate quality-adjusted weight multiplier
                val threshold = getOcrDistanceThreshold(term.length)
                val weightMultiplier = getFuzzyWeightMultiplier(distance, threshold)

                // Found a valid OCR fuzzy match - get all domain matches for this term
                domainIndex[term]?.forEach { match ->
                    // Only include Tier A matches with quality-adjusted weight
                    if (match.tier == "A") {
                        fuzzyMatches.add(
                            match.copy(weight = match.weight * weightMultiplier)
                        )
                    }
                }
            }
        }

        return fuzzyMatches
    }

    /**
     * Fuzzy lookup for intent matches using OCR-aware matching.
     *
     * Enhanced features (Jan 2026):
     * - First-letter validation: rejects matches where first letters differ
     *   unless they're known OCR confusions (l/1/i, o/0, etc.)
     * - OCR-weighted edit distance: known OCR confusions cost less
     * - Adaptive thresholds: shorter words require stricter matching
     * - Quality-scaled weights: better matches get higher weight retention
     *
     * @param token The token to fuzzy match (must be ≥ 4 chars)
     * @return List of fuzzy intent matches with quality-adjusted weights
     */
    fun fuzzyLookupIntent(token: String): List<IntentMatch> {
        val lower = token.lowercase()

        // Skip short tokens (too many false positives)
        if (lower.length < MIN_FUZZY_TERM_LENGTH) return emptyList()

        // Skip if exact match exists (no need for fuzzy)
        if (lower in intentIndex) return emptyList()

        val fuzzyMatches = mutableListOf<IntentMatch>()

        for (term in tierAIntentTerms) {
            // Skip short terms
            if (term.length < MIN_FUZZY_TERM_LENGTH) continue

            // Use OCR-aware fuzzy matching (checks first-letter, weighted distance, adaptive threshold)
            val matchResult = isValidOcrFuzzyMatch(lower, term)
            if (matchResult != null) {
                val (_, distance) = matchResult
                // Calculate quality-adjusted weight multiplier
                val threshold = getOcrDistanceThreshold(term.length)
                val weightMultiplier = getFuzzyWeightMultiplier(distance, threshold)

                // Found a valid OCR fuzzy match - get all intent matches for this term
                intentIndex[term]?.forEach { match ->
                    // Only include Tier A matches with quality-adjusted weight
                    if (match.tier == "A") {
                        fuzzyMatches.add(
                            match.copy(weight = match.weight * weightMultiplier)
                        )
                    }
                }
            }
        }

        return fuzzyMatches
    }
}

data class LanguageDictionaries(
    val language: String,
    val domains: Map<Domain, DomainDictionary>,
    val intents: Map<Intent, IntentDictionary>,
    val structurePatterns: StructurePatternsData
) {
    /** Lazily built inverted index for O(1) token lookup */
    val invertedIndex: InvertedIndex by lazy {
        InvertedIndex.build(domains, intents)
    }
}
