package com.boox.day.booxnotes

import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.semantic.classification.ContextDisambiguator
import com.boox.day.ocr.semantic.classification.DomainDictionary

/**
 * Smart Link's topic overlap between a note title and an event's text (the SEMANTIC_GROUP_MATCH
 * bonus): how strongly both use the same domain vocabulary (tier-weighted, geometric mean, with
 * exact-term, Tier A, LDS-context and collocation bonuses).
 *
 * Oct 2026: moved out of BooxNoteIndexer and cached. It used to analyse both texts again for every
 * note × event pair, lowercasing and regex-splitting every multi-word Tier A term of all 22 domains
 * each time: about 40 ms a pair, 2-3 s per note, 26 s to suggest links for all notes on a Go 10.3.
 * Each text is now analysed once (an event's analysis never depends on the note). Scores are
 * unchanged; DomainOverlapScorerTest checks them against the old code.
 */
internal class DomainOverlapScorer(
    private val domains: Map<Domain, DomainDictionary>,
    private val hasLdsContext: (String) -> Boolean
) {
    /** One domain's vocabulary found in a text */
    private class DomainHit(val score: Float, val tierACount: Int, val matchedTerms: Set<String>)

    /** Everything the overlap needs from one text; [hits] holds only domains with a score above 0 */
    private class TextAnalysis(
        val hasLdsContext: Boolean,
        val disambiguated: Map<String, Domain>,
        val hits: Map<Domain, DomainHit>
    )

    /** Multi-word Tier A terms ("ward council", "elders-quorum") as their parts, split once */
    private val multiWordTierA: Map<Domain, List<List<String>>> = domains.mapValues { (_, dictionary) ->
        dictionary.tierA.first
            .map { it.lowercase() }
            .filter { it.contains("-") || it.contains(" ") }
            .map { it.split(TERM_PARTS) }
    }

    private val cache = object : LinkedHashMap<String, TextAnalysis>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TextAnalysis>): Boolean =
            size > MAX_CACHED_TEXTS
    }

    /** Overlap quality: 0 = none, about 0.2 weak, 0.4 moderate, 0.7+ strong (can exceed 1.0) */
    fun score(noteText: String, eventText: String): Float {
        val note = analysis(noteText)
        val event = analysis(eventText)
        val bothHaveLdsContext = note.hasLdsContext && event.hasLdsContext
        var bestOverlapScore = 0f

        for ((domain, noteHit) in note.hits) {
            val eventHit = event.hits[domain] ?: continue

            // Same words on both sides
            val exactOverlap = noteHit.matchedTerms.intersect(eventHit.matchedTerms)
            val exactOverlapBonus = when {
                exactOverlap.isEmpty() -> 1.0f
                exactOverlap.size == 1 -> 1.3f
                else -> 1.5f
            }

            // High-value terms on both sides
            val tierABonus = when {
                noteHit.tierACount > 0 && eventHit.tierACount > 0 -> 1.5f
                noteHit.tierACount > 0 || eventHit.tierACount > 0 -> 1.2f
                else -> 1.0f
            }

            // Both sides have LDS context: tells LDS leadership apart from generic business terms
            val ldsContextBoost = if (bothHaveLdsContext && domain == Domain.LDS_LEADERSHIP) 1.4f else 1.0f

            // The same ambiguous term resolved to this domain on both sides
            val collocationAgreementCount = note.disambiguated.keys.intersect(event.disambiguated.keys).count { term ->
                note.disambiguated[term] == domain && event.disambiguated[term] == domain
            }
            val collocationAgreementBonus = when (collocationAgreementCount) {
                0 -> 1.0f
                1 -> 1.3f
                else -> 1.5f
            }

            // Geometric mean needs both sides strong; 8 = a very strong match on both
            val geometricMean = kotlin.math.sqrt(noteHit.score * eventHit.score)
            val normalizedScore = (geometricMean / 8f) * exactOverlapBonus * tierABonus * ldsContextBoost * collocationAgreementBonus
            if (normalizedScore > bestOverlapScore) {
                bestOverlapScore = normalizedScore
            }
        }
        return bestOverlapScore
    }

    private fun analysis(text: String): TextAnalysis {
        val lower = text.lowercase()
        synchronized(cache) { cache[lower] }?.let { return it }
        val analysed = analyse(lower)
        synchronized(cache) { cache[lower] = analysed }
        return analysed
    }

    private fun analyse(lower: String): TextAnalysis {
        val tokens = lower.split(TOKEN_SPLIT).filter { it.length >= 2 }
        val words = tokens.toSet()

        // Ambiguous terms ("primary", "service") resolved from their neighbours, helped by the
        // domains the unambiguous words already point to
        val priors = priorScoresFromUnambiguous(words)
        val disambiguated = mutableMapOf<String, Domain>()
        for ((index, token) in tokens.withIndex()) {
            if (ContextDisambiguator.isAmbiguous(token)) {
                val result = ContextDisambiguator.resolve(term = token, tokens = tokens, termIndex = index, priorScores = priors)
                if (result != null && result.confidence >= 0.6f) {
                    disambiguated[token] = result.resolvedDomain
                }
            }
        }

        val hits = mutableMapOf<Domain, DomainHit>()
        for ((domain, dictionary) in domains) {
            var score = 0f
            var tierACount = 0
            val matchedTerms = mutableSetOf<String>()

            for (word in words) {
                var weight = dictionary.getWeight(word)
                if (weight > 0) {
                    val resolved = disambiguated[word]
                    if (resolved != null) {
                        weight *= if (resolved == domain) 1.5f else 0.3f
                    }
                    score += weight
                    matchedTerms.add(word)
                    if (dictionary.getTier(word) == "A") tierACount++
                }
            }

            // Multi-word Tier A terms count when every part is a whole word of the text
            for (parts in multiWordTierA.getValue(domain)) {
                if (parts.all { it in words }) {
                    score += dictionary.tierA.second
                    tierACount++
                    matchedTerms.addAll(parts)
                }
            }

            if (score > 0) hits[domain] = DomainHit(score, tierACount, matchedTerms)
        }
        return TextAnalysis(hasLdsContext(lower), disambiguated, hits)
    }

    private fun priorScoresFromUnambiguous(words: Set<String>): Map<Domain, Float> {
        val unambiguous = words.filter { it !in ContextDisambiguator.AMBIGUOUS_TERMS.keys }
        val scores = mutableMapOf<Domain, Float>()
        for ((domain, dictionary) in domains) {
            var score = 0f
            for (word in unambiguous) {
                val weight = dictionary.getWeight(word)
                if (weight > 0) score += weight
            }
            if (score > 0) scores[domain] = score
        }
        return scores
    }

    private companion object {
        val TOKEN_SPLIT = Regex("[\\s\\-_]+")
        val TERM_PARTS = Regex("[\\-\\s]+")

        /** Every note title and event text of a few months fits */
        const val MAX_CACHED_TEXTS = 4096
    }
}
