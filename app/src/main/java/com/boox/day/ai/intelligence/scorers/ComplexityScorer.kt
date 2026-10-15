package com.boox.day.ai.intelligence.scorers

import com.boox.day.data.ai.*

/**
 * Scores items based on complexity signals:
 * - Multiple action verbs (compound tasks)
 * - Scope indicators ("all", "complete", "entire")
 * - Subtask potential (logical decomposition)
 * - Vague terms that need clarification
 */
class ComplexityScorer : Scorer {
    override val id = ScorerIds.COMPLEXITY

    companion object {
        // Action verbs that indicate task components
        private val ACTION_VERBS = listOf(
            "write", "read", "review", "send", "call", "meet", "create",
            "update", "finish", "complete", "prepare", "plan", "organize",
            "research", "analyze", "design", "build", "fix", "test"
        )

        // Scope indicators that suggest complexity
        private val SCOPE_WORDS = listOf(
            "all", "complete", "entire", "whole", "full", "comprehensive",
            "thorough", "everything", "multiple", "various", "several"
        )

        // Conjunctions that indicate multiple steps
        private val CONJUNCTION_WORDS = listOf(
            "and", "then", "also", "plus", "including", "as well as"
        )

        // Vague terms that need breakdown
        private val VAGUE_WORDS = listOf(
            "stuff", "things", "etc", "various", "some", "misc"
        )

        // Scoring weights
        private const val MULTI_VERB_SCORE = 0.70f
        private const val SCOPE_WORD_SCORE = 0.55f
        private const val CONJUNCTION_SCORE = 0.50f
        private const val VAGUE_TERM_SCORE = 0.60f
        private const val LENGTH_THRESHOLD_HIGH = 80
    }

    override suspend fun score(input: ScoringInput): ScorerResult {
        val scores = mutableMapOf<String, Float>()
        val signals = mutableMapOf<String, MutableList<String>>()

        input.itemsToScore.forEach { item ->
            val itemSignals = mutableListOf<String>()
            var maxScore = 0f

            val lowerText = item.text.lowercase()
            val words = lowerText.split(Regex("\\s+"))

            // 1. Count action verbs
            val verbCount = ACTION_VERBS.count { verb ->
                words.any { it.startsWith(verb) || it.endsWith(verb) }
            }
            if (verbCount >= 3) {
                maxScore = MULTI_VERB_SCORE + 0.15f
                itemSignals.add("Multiple actions detected ($verbCount verbs)")
            } else if (verbCount == 2) {
                if (MULTI_VERB_SCORE > maxScore) {
                    maxScore = MULTI_VERB_SCORE
                    itemSignals.add("Two-step task detected")
                }
            }

            // 2. Check scope words (use word boundaries to avoid false positives like "call" matching "all")
            val scopeCount = SCOPE_WORDS.count { scope -> words.contains(scope) }
            if (scopeCount > 0) {
                val scopeScore = (SCOPE_WORD_SCORE + scopeCount * 0.05f).coerceAtMost(0.80f)
                if (scopeScore > maxScore) {
                    maxScore = scopeScore
                    itemSignals.add("Broad scope indicated")
                }
            }

            // 3. Check conjunctions
            val conjunctionCount = CONJUNCTION_WORDS.count { lowerText.contains(" $it ") }
            if (conjunctionCount >= 2) {
                if (CONJUNCTION_SCORE + 0.10f > maxScore) {
                    maxScore = CONJUNCTION_SCORE + 0.10f
                    itemSignals.add("Multiple steps connected by 'and'/'then'")
                }
            }

            // 4. Check vague terms (use word boundaries to avoid false positives like "some" matching "someone")
            if (VAGUE_WORDS.any { vague -> words.contains(vague) }) {
                if (VAGUE_TERM_SCORE > maxScore) {
                    maxScore = VAGUE_TERM_SCORE
                    itemSignals.add("Contains vague terms needing clarification")
                }
            }

            // 5. Length-based complexity
            if (item.text.length > LENGTH_THRESHOLD_HIGH) {
                val lengthScore = 0.45f + (item.text.length - LENGTH_THRESHOLD_HIGH) * 0.001f
                if (lengthScore > maxScore) {
                    maxScore = lengthScore.coerceAtMost(0.65f)
                    itemSignals.add("Long description suggests complexity")
                }
            }

            // 6. Project/milestone indicators
            if (lowerText.contains("project") || lowerText.contains("milestone") ||
                lowerText.contains("phase") || lowerText.contains("launch")) {
                if (0.75f > maxScore) {
                    maxScore = 0.75f
                    itemSignals.add("Project-level task detected")
                }
            }

            scores[item.id] = maxScore.coerceIn(0f, 1f)
            signals[item.id] = itemSignals
        }

        return ScorerResult(
            scorerId = id,
            scores = scores,
            signals = signals
        )
    }

    // This scorer is primarily for BREAK_DOWN_TASK
    override fun isApplicable(input: ScoringInput): Boolean {
        return input.feature == IntelligenceFeature.BREAK_DOWN_TASK ||
               input.feature == IntelligenceFeature.SMART_PRIORITIZATION
    }
}
