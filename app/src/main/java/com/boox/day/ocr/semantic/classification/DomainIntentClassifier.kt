package com.boox.day.ocr.semantic.classification

import android.content.Context
import android.util.Log
import com.boox.day.ocr.model.AmbiguousTermResolution
import com.boox.day.ocr.model.ClassificationExplanation
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.DomainCandidate
import com.boox.day.ocr.model.Intent
import com.boox.day.ocr.model.IntentCandidate
import com.boox.day.ocr.model.IntentScore
import com.boox.day.ocr.model.NegativeEvidenceTrace
import com.boox.day.ocr.model.PatternSignal
import com.boox.day.ocr.model.StructureSignal
import com.boox.day.ocr.model.TokenSignal

/**
 * Main classification engine that orchestrates all components.
 *
 * Pipeline:
 * 1. Language detection → mode selection
 * 2. OCR correction
 * 3. Structure signal detection (universal)
 * 4. Dominant evidence check
 * 5. Token scoring with negative signals
 * 6. Ambiguous term resolution
 * 7. Score combination
 * 8. Per-user priors (if applicable)
 * 9. Final domain selection
 * 10. Intent classification
 * 11. Subdomain hint detection
 * 12. Build explanation
 */
class DomainIntentClassifier(private val context: Context) {

    companion object {
        private const val TAG = "DomainIntentClassifier"
        const val ENGINE_VERSION = 2

        /** Minimum confidence to not return MISC */
        private const val MIN_CONFIDENCE_THRESHOLD = 0.25f

        /** Maximum intents to return */
        private const val MAX_INTENTS = 4
    }

    // Components
    private val dictionaryLoader = DictionaryLoader(context)
    private val userPriors = UserPriors(context)

    // Cached dictionaries (loaded once per language)
    private var dictionaries: LanguageDictionaries? = null
    private var currentLanguage: String? = null

    /**
     * Classify text and return full explanation.
     *
     * @param text The text to classify
     * @param title Optional title for boosting keywords found in title (2.0x weight)
     * @param recordPriors If true, record this classification to user priors.
     *                     Set to false for external content (events) to avoid
     *                     polluting user priors with non-user-generated content.
     *                     Default is true for backward compatibility.
     * @return Classification explanation with domain, intents, and supporting evidence
     */
    fun classify(
        text: String,
        title: String? = null,
        recordPriors: Boolean = true
    ): ClassificationExplanation {
        val startTime = System.currentTimeMillis()

        // ═══════════════════════════════════════════════════════════════════
        // STEP 1: Language Detection & Mode Selection
        // ═══════════════════════════════════════════════════════════════════
        val langResult = LanguageDetector.detect(text)
        val mode = ModeSelector.selectMode(langResult, hasUserDictionaries = false)

        // Load dictionaries for detected language
        ensureDictionariesLoaded(langResult.language)

        // ═══════════════════════════════════════════════════════════════════
        // STEP 2: OCR Correction
        // ═══════════════════════════════════════════════════════════════════
        val correctedText = OcrCorrector.applyDirectSubstitutions(text)
        val tokens = tokenize(correctedText)

        // Extract title tokens for boosting (A3: Title-Weighted Scoring)
        val titleTokens = title?.lowercase()
            ?.split(Regex("[\\s,;:.!?-]+"))
            ?.filter { it.length >= 3 }
            ?.toSet()
            ?: emptySet()

        // ═══════════════════════════════════════════════════════════════════
        // STEP 3: Structure Signal Detection (Universal)
        // ═══════════════════════════════════════════════════════════════════
        val structureSignals = detectStructureSignals(correctedText)

        // ═══════════════════════════════════════════════════════════════════
        // STEP 4: Dominant Evidence Check
        // ═══════════════════════════════════════════════════════════════════
        val dominantRules = DominantEvidenceRules.detect(correctedText)
        val dominantDomain = DominantEvidenceRules.getDominantDomain(dominantRules)

        // ═══════════════════════════════════════════════════════════════════
        // STEP 4b: Dictionary Phrase Patterns (New Engine Feature)
        // ═══════════════════════════════════════════════════════════════════
        val (phraseSignals, intentPhraseMatches) = detectPhrasePatterns(correctedText)

        // ═══════════════════════════════════════════════════════════════════
        // STEP 5: Token Scoring (with negative signals if dominant exists)
        // ═══════════════════════════════════════════════════════════════════
        val tokenScoringResult = scoreTokens(
            tokens = tokens,
            titleTokens = titleTokens,
            dominantDomain = dominantDomain,
            mode = mode
        )

        // ═══════════════════════════════════════════════════════════════════
        // STEP 6: Ambiguous Term Resolution
        // ═══════════════════════════════════════════════════════════════════
        val disambiguationResult = resolveAmbiguousTerms(
            tokens = tokens,
            priorScores = tokenScoringResult.domainScores
        )

        // ═══════════════════════════════════════════════════════════════════
        // STEP 7: Combine All Scores
        // ═══════════════════════════════════════════════════════════════════
        val combinedScores = combineScores(
            structureSignals = structureSignals,
            phraseSignals = phraseSignals,
            tokenScores = tokenScoringResult.domainScores,
            disambiguatedScores = disambiguationResult.additionalScores,
            dominantRules = dominantRules
        )

        // ═══════════════════════════════════════════════════════════════════
        // STEP 8: Apply Per-User Priors (if applicable)
        // ═══════════════════════════════════════════════════════════════════
        val rawConfidence = computeConfidence(combinedScores)
        val priorResult = userPriors.getDomainPriors(text.length, rawConfidence)

        val finalScores = if (priorResult != null) {
            userPriors.applyPriors(combinedScores, priorResult)
        } else {
            combinedScores
        }

        // ═══════════════════════════════════════════════════════════════════
        // STEP 9: Select Final Domain
        // ═══════════════════════════════════════════════════════════════════
        val (winningDomain, domainConfidence) = selectDomain(finalScores, dominantRules)

        // ═══════════════════════════════════════════════════════════════════
        // STEP 10: Classify Intents
        // ═══════════════════════════════════════════════════════════════════
        val intents = classifyIntents(tokens, structureSignals, intentPhraseMatches, winningDomain)

        // ═══════════════════════════════════════════════════════════════════
        // STEP 11: Detect Subdomain Hint (optional)
        // ═══════════════════════════════════════════════════════════════════
        val subdomainHint = detectSubdomainHint(tokens, winningDomain)

        // ═══════════════════════════════════════════════════════════════════
        // STEP 12: Build Explanation (Phase 9b - Debug Observability)
        // ═══════════════════════════════════════════════════════════════════

        // Build top domain candidates sorted by score (for debug observability)
        val topDomainCandidates = finalScores.entries
            .sortedByDescending { it.value }
            .filter { it.value > 0 }
            .take(5)
            .map { DomainCandidate(it.key.name, it.value) }

        // Build top intent candidates sorted by score (for debug observability)
        val topIntentCandidates = intents
            .take(5)
            .map { IntentCandidate(it.intent.name, it.confidence) }

        val explanation = ClassificationExplanation(
            // Phase 9b: Input tracking for debug observability
            inputText = text,
            normalizedText = correctedText,
            // Core results
            languageHint = langResult.language,
            modeUsed = when (mode) {
                ClassificationMode.MODE_A_FULL -> "A"
                ClassificationMode.MODE_B_STRUCTURE -> "B"
                ClassificationMode.MODE_C_EXTENDED -> "C"
            },
            domain = winningDomain,
            domainConfidence = domainConfidence,
            intents = intents,
            subdomainHint = subdomainHint,
            structureSignals = structureSignals,
            patternSignals = buildPatternSignals(dominantRules) + phraseSignals,
            tokenSignals = tokenScoringResult.tokenSignals,
            dominantEvidence = dominantRules.map { it.id },
            negativeEvidenceApplied = tokenScoringResult.negativeTraces,
            ambiguousTermsResolved = disambiguationResult.resolutions,
            priorBoosts = priorResult?.boosts,
            priorReason = priorResult?.reason,
            rawScores = combinedScores.mapKeys { it.key.name },
            finalScores = finalScores.mapKeys { it.key.name },
            // Phase 9b: Candidate rankings for debug observability
            topDomainCandidates = topDomainCandidates,
            topIntentCandidates = topIntentCandidates,
            disambiguationApplied = disambiguationResult.resolutions.isNotEmpty(),
            processingTimeMs = System.currentTimeMillis() - startTime
        )

        // ═══════════════════════════════════════════════════════════════════
        // STEP 13: Record for Priors (after classification)
        // Only record if recordPriors is true (default for user-generated content)
        // Skip for external content like calendar events to avoid polluting priors
        // ═══════════════════════════════════════════════════════════════════
        if (recordPriors) {
            userPriors.recordClassification(winningDomain, intents.map { it.intent })
        }

        Log.d(TAG, "Classification: ${winningDomain.name} (${(domainConfidence * 100).toInt()}%) in ${explanation.processingTimeMs}ms")

        return explanation
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun ensureDictionariesLoaded(language: String) {
        if (dictionaries == null || currentLanguage != language) {
            dictionaries = dictionaryLoader.loadDictionaries(language)
            currentLanguage = language
            Log.d(TAG, "Loaded dictionaries for $language: ${dictionaries?.domains?.size} domains, ${dictionaries?.intents?.size} intents")
        }
    }

    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s'-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
    }

    private data class IntentPhraseMatch(
        val intent: Intent,
        val weight: Float,
        val count: Int,
        val sampleMatch: String
    )

    private fun detectPhrasePatterns(text: String): Pair<List<PatternSignal>, List<IntentPhraseMatch>> {
        val domainSignals = mutableListOf<PatternSignal>()
        val intentMatches = mutableListOf<IntentPhraseMatch>()
        val loadedDicts = dictionaries ?: return Pair(emptyList(), emptyList())

        // Check Domain phrase patterns
        for ((domain, dict) in loadedDicts.domains) {
            for (phrase in dict.phrasePatterns) {
                try {
                    val regex = Regex(phrase.pattern, RegexOption.IGNORE_CASE)
                    val matches = regex.findAll(text)
                    val count = matches.count()
                    
                    if (count > 0) {
                        domainSignals.add(
                            PatternSignal(
                                patternId = "PHRASE_${domain.name}",
                                matches = count,
                                domain = domain,
                                weight = phrase.weight * count,
                                sampleMatch = matches.first().value
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid regex in ${domain.name}: ${phrase.pattern}")
                }
            }
        }

        // Check Intent phrase patterns
        for ((intent, dict) in loadedDicts.intents) {
            for (phrase in dict.phrasePatterns) {
                try {
                    val regex = Regex(phrase.pattern, RegexOption.IGNORE_CASE)
                    val matches = regex.findAll(text)
                    val count = matches.count()
                    
                    if (count > 0) {
                        intentMatches.add(
                            IntentPhraseMatch(
                                intent = intent,
                                weight = phrase.weight * count,
                                count = count,
                                sampleMatch = matches.first().value
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid regex in intent ${intent.name}: ${phrase.pattern}")
                }
            }
        }
        
        return Pair(domainSignals, intentMatches)
    }

    private fun detectStructureSignals(text: String): List<StructureSignal> {
        val signals = mutableListOf<StructureSignal>()
        val patterns = dictionaries?.structurePatterns?.patterns ?: StructurePatternsData.DEFAULT.patterns

        for ((type, def) in patterns) {
            try {
                val regex = Regex(def.regex)
                val matches = regex.findAll(text).count()

                if (matches >= def.minMatches) {
                    signals.add(
                        StructureSignal(
                            type = type,
                            count = matches,
                            inferredIntent = def.inferredIntent?.let { Intent.fromString(it) },
                            weight = def.weight * matches
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invalid regex pattern for $type: ${e.message}")
            }
        }

        return signals
    }

    private data class TokenScoringResult(
        val domainScores: Map<Domain, Float>,
        val tokenSignals: List<TokenSignal>,
        val negativeTraces: List<NegativeEvidenceTrace>
    )

    private fun scoreTokens(
        tokens: List<String>,
        titleTokens: Set<String>,
        dominantDomain: Domain?,
        mode: ClassificationMode
    ): TokenScoringResult {
        val scores = mutableMapOf<Domain, Float>()
        val tokenSignals = mutableListOf<TokenSignal>()
        val negativeTraces = mutableListOf<NegativeEvidenceTrace>()

        // Initialize all domains
        Domain.entries.forEach { scores[it] = 0f }

        if (mode == ClassificationMode.MODE_B_STRUCTURE) {
            // Structure-only mode - skip token scoring
            return TokenScoringResult(scores, emptyList(), emptyList())
        }

        // Use inverted index for O(1) lookup per token instead of O(N×M) loop
        val index = dictionaries?.invertedIndex ?: return TokenScoringResult(scores, emptyList(), emptyList())

        for (token in tokens) {
            // Skip ambiguous terms in first pass
            if (ContextDisambiguator.isAmbiguous(token)) continue

            // O(1) lookup: get all domain matches for this token
            var matches = index.lookupDomain(token)
            var matchType = "EXACT"

            // If no exact matches, try fuzzy matching (Tier A only, edit distance ≤ 1)
            if (matches.isEmpty()) {
                matches = index.fuzzyLookupDomain(token)
                if (matches.isNotEmpty()) {
                    matchType = "FUZZY"
                }
            }

            for (match in matches) {
                var weight = match.weight

                // Apply negative signal if dominant domain exists
                val (suppressed, wasSuppressed) = NegativeSignalRules.applySupression(
                    dominantDomain, token, weight
                )

                if (wasSuppressed) {
                    negativeTraces.add(
                        NegativeEvidenceTrace(
                            targetTerm = token,
                            suppressedBy = dominantDomain!!,
                            originalWeight = weight,
                            finalWeight = suppressed,
                            reason = "${dominantDomain.name}_DOMINANT"
                        )
                    )
                    weight = suppressed
                }

                // Apply title boost: 2.0x for tokens found in title
                val titleBoost = if (token in titleTokens) 2.0f else 1.0f
                val boostedWeight = weight * titleBoost
                scores[match.domain] = (scores[match.domain] ?: 0f) + boostedWeight

                tokenSignals.add(
                    TokenSignal(
                        term = token,
                        tier = match.tier,
                        domain = match.domain,
                        weight = boostedWeight,
                        matchType = if (titleBoost > 1f) "$matchType+TITLE" else matchType
                    )
                )
            }
        }

        return TokenScoringResult(scores, tokenSignals, negativeTraces)
    }

    private data class DisambiguationResult(
        val resolutions: List<AmbiguousTermResolution>,
        val additionalScores: Map<Domain, Float>
    )

    private fun resolveAmbiguousTerms(
        tokens: List<String>,
        priorScores: Map<Domain, Float>
    ): DisambiguationResult {
        val resolutions = mutableListOf<AmbiguousTermResolution>()
        val additionalScores = mutableMapOf<Domain, Float>()

        for ((index, token) in tokens.withIndex()) {
            if (!ContextDisambiguator.isAmbiguous(token)) continue

            val result = ContextDisambiguator.resolve(
                term = token,
                tokens = tokens,
                termIndex = index,
                priorScores = priorScores
            )

            if (result != null) {
                resolutions.add(
                    AmbiguousTermResolution(
                        term = result.term,
                        chosenDomain = result.resolvedDomain,
                        confidence = result.confidence,
                        reason = result.reason,
                        competingDomains = result.competingDomains
                    )
                )

                // Add score for resolved domain
                val weight = 2.0f * result.confidence  // Tier B equivalent, weighted by confidence
                additionalScores[result.resolvedDomain] =
                    (additionalScores[result.resolvedDomain] ?: 0f) + weight
            }
        }

        return DisambiguationResult(resolutions, additionalScores)
    }

    private fun combineScores(
        structureSignals: List<StructureSignal>,
        phraseSignals: List<PatternSignal>,
        tokenScores: Map<Domain, Float>,
        disambiguatedScores: Map<Domain, Float>,
        dominantRules: List<DominantEvidenceRules.DominantRule>
    ): Map<Domain, Float> {
        val combined = mutableMapOf<Domain, Float>()

        // Start with token scores
        for ((domain, score) in tokenScores) {
            combined[domain] = score
        }

        // Add disambiguated scores
        for ((domain, score) in disambiguatedScores) {
            combined[domain] = (combined[domain] ?: 0f) + score
        }

        // Calculate token-based confidence BEFORE structure signals (A-IMMEDIATE-4)
        val tokenConfidence = if (disambiguatedScores.isEmpty()) 0f else {
            val sorted = disambiguatedScores.values.sortedDescending()
            if (sorted.size >= 2) {
                sorted[0] / (sorted[0] + sorted[1] + 1f)
            } else {
                sorted.firstOrNull()?.let { it / (it + 1f) } ?: 0f
            }
        }

        // Boost structure signals when keyword confidence is low (A-IMMEDIATE-4)
        val structureBoostMultiplier = if (tokenConfidence < 0.4f) 1.5f else 1.0f

        // Add structure signal boosts (MODIFIED: apply multiplier)
        for (signal in structureSignals) {
            // Structure signals can boost domain directly (e.g., CODE_BLOCK -> TECH)
            val structureDef = dictionaries?.structurePatterns?.patterns?.get(signal.type)
            structureDef?.inferredDomain?.let { domainName ->
                Domain.fromString(domainName)?.let { domain ->
                    val boostedWeight = signal.weight * structureBoostMultiplier
                    combined[domain] = (combined[domain] ?: 0f) + boostedWeight
                }
            }
        }

        // Add phrase signal boosts (New Engine Feature)
        for (signal in phraseSignals) {
            combined[signal.domain] = (combined[signal.domain] ?: 0f) + signal.weight
        }

        // Apply dominant evidence boost
        for (rule in dominantRules) {
            combined[rule.domain] = (combined[rule.domain] ?: 0f) + 10f  // Strong boost
        }

        return combined
    }

    private fun computeConfidence(scores: Map<Domain, Float>): Float {
        val sorted = scores.values.sortedDescending()
        if (sorted.isEmpty()) return 0f

        val top = sorted.first()
        val second = sorted.getOrElse(1) { 0f }

        // Softmax-like: top / (top + second + 1)
        return top / (top + second + 1f)
    }

    private fun selectDomain(
        scores: Map<Domain, Float>,
        dominantRules: List<DominantEvidenceRules.DominantRule>
    ): Pair<Domain, Float> {
        // If dominant evidence, use its floor confidence
        if (dominantRules.isNotEmpty()) {
            val dominant = dominantRules.first()
            val confidence = maxOf(
                computeConfidence(scores),
                dominant.confidenceFloor
            )
            return dominant.domain to confidence
        }

        // Normal selection
        val winner = scores.maxByOrNull { it.value }
        if (winner == null || winner.value < 0.1f) {
            return Domain.MISC to 0.1f
        }

        val confidence = computeConfidence(scores)

        // Minimum confidence threshold
        return if (confidence < MIN_CONFIDENCE_THRESHOLD) {
            Domain.MISC to confidence
        } else {
            winner.key to confidence
        }
    }

    private fun classifyIntents(
        tokens: List<String>,
        structureSignals: List<StructureSignal>,
        phraseSignals: List<IntentPhraseMatch>,
        winningDomain: Domain
    ): List<IntentScore> {
        val intentScores = mutableMapOf<Intent, Float>()

        // Initialize
        Intent.entries.forEach { intentScores[it] = 0f }

        // Score from structure signals
        for (signal in structureSignals) {
            signal.inferredIntent?.let { intent ->
                intentScores[intent] = (intentScores[intent] ?: 0f) + signal.weight
            }

            // Apply structure boosts from intent dictionaries
            dictionaries?.intents?.forEach { (intent, dict) ->
                val boost = dict.structureBoosts[signal.type] ?: 0f
                if (boost > 0) {
                    intentScores[intent] = (intentScores[intent] ?: 0f) + (boost * signal.count)
                }
            }
        }
        
        // Score from phrase patterns (New Engine Feature)
        for (match in phraseSignals) {
            intentScores[match.intent] = (intentScores[match.intent] ?: 0f) + match.weight
        }

        // Score from tokens using inverted index for O(1) lookup
        val index = dictionaries?.invertedIndex
        if (index != null) {
            for (token in tokens) {
                // O(1) lookup: get all intent matches for this token
                var matches = index.lookupIntent(token)

                // If no exact matches, try fuzzy matching (Tier A only, edit distance ≤ 1)
                if (matches.isEmpty()) {
                    matches = index.fuzzyLookupIntent(token)
                }

                for (match in matches) {
                    intentScores[match.intent] = (intentScores[match.intent] ?: 0f) + match.weight
                }
            }
        }

        // Convert to sorted list
        val sorted = intentScores
            .filter { it.value > 0f }
            .map { (intent, score) ->
                val totalIntentScore = intentScores.values.sum()
                val confidence = if (totalIntentScore > 0) score / (score + 1f) else 0f
                IntentScore(
                    intent = intent,
                    confidence = confidence,
                    signals = emptyList()  // Could add signal breakdown
                )
            }
            .sortedByDescending { it.confidence }
            .take(MAX_INTENTS)

        // Default to FREEFORM if nothing matched
        return if (sorted.isEmpty()) {
            listOf(IntentScore(Intent.FREEFORM, 0.5f, emptyList()))
        } else {
            sorted
        }
    }

    private fun detectSubdomainHint(tokens: List<String>, domain: Domain): String? {
        if (domain != Domain.SPIRITUAL) return null

        val dict = dictionaries?.domains?.get(Domain.SPIRITUAL) ?: return null
        val subdomainHints = dict.subdomainHints

        if (subdomainHints.isEmpty()) return null

        // Count matches for each subdomain
        val subdomainScores = subdomainHints.mapValues { (_, terms) ->
            tokens.count { token -> token in terms.map { it.lowercase() } }
        }

        // Return subdomain with most matches (if >= 2 matches)
        val winner = subdomainScores.maxByOrNull { it.value }
        return if (winner != null && winner.value >= 2) {
            winner.key
        } else {
            null
        }
    }

    private fun buildPatternSignals(dominantRules: List<DominantEvidenceRules.DominantRule>): List<PatternSignal> {
        return dominantRules.map { rule ->
            PatternSignal(
                patternId = rule.id,
                matches = 1,
                domain = rule.domain,
                weight = 10f,
                sampleMatch = null
            )
        }
    }

    /**
     * Get user priors statistics (for debugging/settings UI).
     */
    fun getUserPriorStats(): UserPriors.PriorStats = userPriors.getStats()

    /**
     * Reset user priors.
     */
    fun resetUserPriors() = userPriors.reset()
}
