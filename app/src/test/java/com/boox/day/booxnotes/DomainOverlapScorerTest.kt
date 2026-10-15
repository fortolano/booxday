package com.boox.day.booxnotes

import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.semantic.classification.ContextDisambiguator
import com.boox.day.ocr.semantic.classification.DomainDictionary
import com.boox.day.ocr.semantic.classification.DomainDictionaryJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * The cached [DomainOverlapScorer] must score exactly like the code it replaced (Oct 2026), on the
 * app's real domain dictionaries.
 */
class DomainOverlapScorerTest {

    private val domains: Map<Domain, DomainDictionary> = loadDomains()

    /** Any LDS check works for the comparison; both sides call the same one */
    private val ldsContext: (String) -> Boolean = { text ->
        val words = text.split(Regex("[^a-z0-9]+")).toSet()
        listOf("bishop", "stake", "ward", "sacrament", "quorum", "ministering").count { it in words } >= 1
    }

    @Test
    fun `scores match the previous code on realistic titles`() {
        val notes = listOf(
            "Ward council", "Bishopric meeting notes", "Notebook-27", "Oct 12, 2026", "S2 conference 2026",
            "youth fireside", "s4 sacrament", "easter devotional", "Sprint planning", "API service design",
            "Budget review", "Primary class lesson", "Elders quorum", "Client call follow-up", "Q4 roadmap",
            "Stake high council", "Ministering interviews", "Family home evening", "Gym class", "Project kickoff"
        )
        val events = listOf(
            "General Conference Saturday Morning Session - Churchwide Events",
            "Coordinating Council", "Stake Council In-Person", "High Council Meeting In-person",
            "Sacrament meeting talk", "Ward council agenda: ministering, primary activities",
            "Sprint retro and planning. Review the service endpoint deploy", "Customer support service desk sync",
            "Privacy and security monthly meeting", "TuGo's AI Technology Roadmap Forum. Please note: not recorded",
            "Adyen Account Review -- TuGo (Oct 6)", "Yoga class with instructor", "Project budget milestone review",
            "Buffer", "Regular meeting", "Bishop youth interviews", "Temple recommend interviews",
            "Elders quorum presidency meeting", "Family dinner", "Doctor appointment"
        )
        notes.forEach { note -> events.forEach { event -> assertSame(note, event) } }
    }

    @Test
    fun `scores match the previous code on random dictionary text`() {
        val vocabulary = domains.values.flatMap { d -> d.tierA.first + d.tierB.first + d.tierC.first }.distinct() +
            ContextDisambiguator.AMBIGUOUS_TERMS.keys +
            ContextDisambiguator.AMBIGUOUS_TERMS.values.flatMap { it.values.flatten() } +
            listOf("the", "and", "notes", "2026", "oct", "a", "x")
        val random = Random(20261015)
        fun text(maxWords: Int) = List(random.nextInt(1, maxWords + 1)) { vocabulary.random(random) }
            .joinToString(listOf(" ", " ", "-", "_", ", ").random(random))

        var overlapping = 0
        repeat(3000) {
            val note = text(6)
            val event = text(25)
            if (assertSame(note, event) > 0f) overlapping++
        }
        assertTrue("too few overlapping pairs to be a real check: $overlapping", overlapping > 300)
    }

    @Test
    fun `the same text gets the same score from the cache`() {
        val scorer = DomainOverlapScorer(domains, ldsContext)
        val first = scorer.score("Ward council", "Ward council agenda: ministering")
        assertTrue(first > 0f)
        assertEquals(first, scorer.score("WARD COUNCIL", "Ward council agenda: ministering"), 0f)
    }

    private val scorer by lazy { DomainOverlapScorer(domains, ldsContext) }
    private val previous by lazy { PreviousOverlap(domains, ldsContext) }

    private fun assertSame(note: String, event: String): Float {
        val expected = previous.calculateSemanticOverlapScore(note, event)
        assertEquals("\"$note\" vs \"$event\"", expected, scorer.score(note, event), 0f)
        return expected
    }

    private fun loadDomains(): Map<Domain, DomainDictionary> {
        val dir = listOf("src/main/assets/classification/en/domains", "app/src/main/assets/classification/en/domains")
            .map { File(it) }.first { it.isDirectory }
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        return Domain.entries.filter { it != Domain.MISC }.mapNotNull { domain ->
            val file = File(dir, domain.name.lowercase() + ".json")
            if (!file.exists()) null
            else domain to json.decodeFromString<DomainDictionaryJson>(file.readText()).toDomainDictionary()
        }.toMap().also { assertTrue(it.size >= 20) }
    }

    /** BooxNoteIndexer.calculateSemanticOverlapScore before Oct 2026, verbatim (comments removed) */
    private class PreviousOverlap(
        private val domains: Map<Domain, DomainDictionary>,
        private val hasLdsContext: (String) -> Boolean
    ) {
            fun calculatePriorScoresFromUnambiguous(
            tokens: Set<String>,
            ambiguousTerms: Set<String>
        ): Map<Domain, Float> {
            val unambiguousTokens = tokens.filter { it !in ambiguousTerms }
            val scores = mutableMapOf<Domain, Float>()

            for ((domainName, domainDict) in domains) {
                var score = 0f
                for (token in unambiguousTokens) {
                    val weight = domainDict.getWeight(token)
                    if (weight > 0) {
                        score += weight
                    }
                }
                if (score > 0) {
                    scores[domainName] = score
                }
            }

            return scores
        }

            fun calculateSemanticOverlapScore(noteTitle: String, eventText: String): Float {
            val noteTitleLower = noteTitle.lowercase()
            val eventTitleLower = eventText.lowercase()

            val noteTokensList = noteTitleLower.split(Regex("[\\s\\-_]+"))
                .filter { it.length >= 2 }
            val noteWords = noteTokensList.toSet()
            val eventTokensList = eventTitleLower.split(Regex("[\\s\\-_]+"))
                .filter { it.length >= 2 }
            val eventWords = eventTokensList.toSet()

            val noteHasLdsContext = hasLdsContext(noteTitleLower)
            val eventHasLdsContext = hasLdsContext(eventTitleLower)
            val bothHaveLdsContext = noteHasLdsContext && eventHasLdsContext


            val ambiguousTerms = ContextDisambiguator.AMBIGUOUS_TERMS.keys
            val notePriors = calculatePriorScoresFromUnambiguous(noteWords, ambiguousTerms)
            val eventPriors = calculatePriorScoresFromUnambiguous(eventWords, ambiguousTerms)

            val noteDisambiguated = mutableMapOf<String, Domain>()
            for ((idx, token) in noteTokensList.withIndex()) {
                if (ContextDisambiguator.isAmbiguous(token)) {
                    val result = ContextDisambiguator.resolve(
                        term = token,
                        tokens = noteTokensList,
                        termIndex = idx,
                        priorScores = notePriors  // BUGFIX: Now uses real priors
                    )
                    if (result != null && result.confidence >= 0.6f) {
                        noteDisambiguated[token] = result.resolvedDomain
                    }
                }
            }

            val eventDisambiguated = mutableMapOf<String, Domain>()
            for ((idx, token) in eventTokensList.withIndex()) {
                if (ContextDisambiguator.isAmbiguous(token)) {
                    val result = ContextDisambiguator.resolve(
                        term = token,
                        tokens = eventTokensList,
                        termIndex = idx,
                        priorScores = eventPriors  // BUGFIX: Now uses real priors
                    )
                    if (result != null && result.confidence >= 0.6f) {
                        eventDisambiguated[token] = result.resolvedDomain
                    }
                }
            }

            var bestOverlapScore = 0f

            for ((domainName, domainDict) in domains) {
                var noteScore = 0f
                var noteTierACount = 0
                val noteMatchedTerms = mutableSetOf<String>()

                for (word in noteWords) {
                    var weight = domainDict.getWeight(word)
                    if (weight > 0) {
                        val disambiguatedDomain = noteDisambiguated[word]
                        if (disambiguatedDomain != null) {
                            weight *= if (disambiguatedDomain == domainName) {
                                1.5f  // Boost: context confirms this domain
                            } else {
                                0.3f  // Penalty: context points to different domain
                            }
                        }

                        noteScore += weight
                        noteMatchedTerms.add(word)
                        if (domainDict.getTier(word) == "A") noteTierACount++
                    }
                }

                for (term in domainDict.tierA.first) {
                    val termLower = term.lowercase()
                    if (termLower.contains("-") || termLower.contains(" ")) {
                        val parts = termLower.split(Regex("[\\-\\s]+"))
                        if (parts.all { it in noteWords }) {  // Token match, not substring
                            noteScore += domainDict.tierA.second
                            noteTierACount++
                            noteMatchedTerms.addAll(parts)
                        }
                    }
                }

                var eventScore = 0f
                var eventTierACount = 0
                val eventMatchedTerms = mutableSetOf<String>()

                for (word in eventWords) {
                    var weight = domainDict.getWeight(word)
                    if (weight > 0) {
                        val disambiguatedDomain = eventDisambiguated[word]
                        if (disambiguatedDomain != null) {
                            weight *= if (disambiguatedDomain == domainName) {
                                1.5f  // Boost: context confirms this domain
                            } else {
                                0.3f  // Penalty: context points to different domain
                            }
                        }

                        eventScore += weight
                        eventMatchedTerms.add(word)
                        if (domainDict.getTier(word) == "A") eventTierACount++
                    }
                }

                for (term in domainDict.tierA.first) {
                    val termLower = term.lowercase()
                    if (termLower.contains("-") || termLower.contains(" ")) {
                        val parts = termLower.split(Regex("[\\-\\s]+"))
                        if (parts.all { it in eventWords }) {  // Token match, not substring
                            eventScore += domainDict.tierA.second
                            eventTierACount++
                            eventMatchedTerms.addAll(parts)
                        }
                    }
                }

                if (noteScore > 0 && eventScore > 0) {
                    val exactOverlap = noteMatchedTerms.intersect(eventMatchedTerms)
                    val exactOverlapBonus = when {
                        exactOverlap.isEmpty() -> 1.0f      // No bonus
                        exactOverlap.size == 1 -> 1.3f     // Single shared term
                        else -> 1.5f                        // Multiple shared terms
                    }

                    val tierABonus = when {
                        noteTierACount > 0 && eventTierACount > 0 -> 1.5f  // Both have TierA
                        noteTierACount > 0 || eventTierACount > 0 -> 1.2f  // One has TierA
                        else -> 1.0f                                        // No TierA
                    }

                    val ldsContextBoost = if (bothHaveLdsContext &&
                        domainName == Domain.LDS_LEADERSHIP) {
                        1.4f  // 40% boost for LDS domain when both have context
                    } else {
                        1.0f
                    }

                    val sharedAmbiguousTerms = noteDisambiguated.keys.intersect(eventDisambiguated.keys)
                    val collocationAgreementCount = sharedAmbiguousTerms.count { term ->
                        noteDisambiguated[term] == domainName && eventDisambiguated[term] == domainName
                    }
                    val collocationAgreementBonus = when (collocationAgreementCount) {
                        0 -> 1.0f      // No agreement
                        1 -> 1.3f      // One shared disambiguated term
                        else -> 1.5f   // Multiple shared disambiguated terms
                    }

                    val geometricMean = kotlin.math.sqrt(noteScore * eventScore)

                    val normalizedScore = (geometricMean / 8f) * exactOverlapBonus * tierABonus * ldsContextBoost * collocationAgreementBonus

                    if (normalizedScore > bestOverlapScore) {
                        bestOverlapScore = normalizedScore  // Allow scores > 1.0 for ranking
                    }
                }
            }

            return bestOverlapScore
        }

    }
}
