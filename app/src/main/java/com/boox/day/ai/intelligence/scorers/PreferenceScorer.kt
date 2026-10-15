package com.boox.day.ai.intelligence.scorers

import com.boox.day.data.ai.*

/**
 * Scores items based on alignment with user preferences:
 * - Category match to life priorities (work, health, family, etc.)
 * - Mode alignment (respects crunch/vacation/holiday mode)
 * - Interest alignment
 * - Style preference (practical vs holistic)
 */
class PreferenceScorer : Scorer {
    override val id = ScorerIds.PREFERENCE

    companion object {
        // Category keywords for priority matching
        private val CATEGORY_KEYWORDS = mapOf(
            "work" to listOf("work", "meeting", "email", "project", "client", "deadline",
                            "presentation", "report", "office", "boss", "colleague", "job"),
            "education" to listOf("study", "learn", "course", "read", "book", "class",
                                 "practice", "exam", "homework", "research", "tutorial"),
            "family" to listOf("family", "kids", "children", "spouse", "parent", "mom",
                              "dad", "wife", "husband", "dinner", "home", "pick up"),
            "health" to listOf("exercise", "gym", "run", "walk", "yoga", "meditation",
                              "doctor", "health", "sleep", "water", "vitamin", "stretch"),
            "social" to listOf("friend", "call", "meet", "lunch", "dinner", "party",
                              "birthday", "coffee", "hang out", "catch up", "visit"),
            "hobby" to listOf("hobby", "game", "play", "watch", "music", "art", "craft",
                             "garden", "cook", "bake", "photo", "travel"),
            "finance" to listOf("budget", "bill", "pay", "save", "invest", "bank",
                               "money", "expense", "tax", "insurance", "account")
        )

        // Mode modifiers
        private const val CRUNCH_MODE_WORK_BOOST = 0.25f
        private const val VACATION_MODE_WORK_PENALTY = -0.30f
        private const val HOLIDAY_MODE_FAMILY_BOOST = 0.20f

        // Preference alignment boosts
        private const val INTEREST_MATCH_BOOST = 0.15f
        private const val LIFE_SITUATION_BOOST = 0.10f
        private const val ENVIRONMENT_MATCH_BOOST = 0.08f

        private val ENVIRONMENT_KEYWORDS = mapOf(
            LivingEnvironment.URBAN to listOf("commute", "transit", "subway", "city", "bus", "train"),
            LivingEnvironment.SUBURBAN to listOf("drive", "car", "yard", "home"),
            LivingEnvironment.RURAL to listOf("farm", "garden", "outdoors", "trail", "barn"),
            LivingEnvironment.REMOTE to listOf("remote", "online", "virtual", "delivery", "ship")
        )
    }

    override suspend fun score(input: ScoringInput): ScorerResult {
        val prefs = input.preferences ?: return emptyResult(input)

        val scores = mutableMapOf<String, Float>()
        val signals = mutableMapOf<String, MutableList<String>>()

        // Get priority values (1-5 scale)
        val priorities = mapOf(
            "work" to prefs.workPriority,
            "education" to prefs.educationPriority,
            "family" to prefs.familyPriority,
            "health" to prefs.healthPriority,
            "social" to prefs.socialPriority,
            "hobby" to prefs.hobbyPriority,
            "finance" to prefs.financePriority
        )

        val interestKeywords = buildInterestKeywords(prefs)
        val environmentKeywords = ENVIRONMENT_KEYWORDS[prefs.livingEnvironment] ?: emptyList()
        val lifeSituationCategories = getLifeSituationCategories(prefs.lifeSituations)

        input.itemsToScore.forEach { item ->
            val itemSignals = mutableListOf<String>()
            var score = 0f

            // 1. Detect category from text
            val detectedCategories = detectCategories(item.text)

            // 2. Calculate base score from priority alignment
            if (detectedCategories.isNotEmpty()) {
                val categoryScores = detectedCategories.map { category ->
                    val priority = priorities[category] ?: 3
                    val categoryScore = (priority - 1) / 4f  // Convert 1-5 to 0.0-1.0

                    if (priority >= 4) {
                        itemSignals.add("Matches HIGH priority: $category")
                    } else if (priority <= 2) {
                        itemSignals.add("Matches LOW priority: $category")
                    }

                    categoryScore
                }
                score = categoryScores.maxOrNull() ?: 0f
            }

            // 3. Apply mode modifiers
            if (prefs.crunchMode) {
                if (detectedCategories.contains("work")) {
                    score = (score + CRUNCH_MODE_WORK_BOOST).coerceAtMost(1f)
                    itemSignals.add("CRUNCH MODE: work tasks prioritized")
                } else {
                    // Non-work tasks get slight penalty in crunch mode
                    score = (score - 0.1f).coerceAtLeast(0f)
                }
            }

            if (prefs.vacationMode && detectedCategories.contains("work")) {
                score = (score + VACATION_MODE_WORK_PENALTY).coerceAtLeast(0f)
                itemSignals.add("VACATION MODE: work tasks deprioritized")
            }

            if (prefs.holidayMode && detectedCategories.contains("family")) {
                score = (score + HOLIDAY_MODE_FAMILY_BOOST).coerceAtMost(1f)
                itemSignals.add("HOLIDAY MODE: family tasks prioritized")
            }

            // 4. If no category detected, give neutral score
            if (detectedCategories.isEmpty()) {
                score = 0.5f
            }

            // 5. Interest-based boosts
            if (interestKeywords.isNotEmpty()) {
                val lowerText = item.text.lowercase()
                if (interestKeywords.any { lowerText.contains(it) }) {
                    score = (score + INTEREST_MATCH_BOOST).coerceAtMost(1f)
                    itemSignals.add("Matches user interest")
                }
            }

            // 6. Life situation alignment
            if (lifeSituationCategories.isNotEmpty() &&
                detectedCategories.any { lifeSituationCategories.contains(it) }) {
                score = (score + LIFE_SITUATION_BOOST).coerceAtMost(1f)
                itemSignals.add("Matches life situation: ${prefs.lifeSituations.joinToString(" + ") { it.displayName }}")
            }

            // 7. Living environment keyword match
            if (environmentKeywords.isNotEmpty()) {
                val lowerText = item.text.lowercase()
                if (environmentKeywords.any { lowerText.contains(it) }) {
                    score = (score + ENVIRONMENT_MATCH_BOOST).coerceAtMost(1f)
                    itemSignals.add("Matches living environment context")
                }
            }

            scores[item.id] = score.coerceIn(0f, 1f)
            signals[item.id] = itemSignals
        }

        return ScorerResult(
            scorerId = id,
            scores = scores,
            signals = signals
        )
    }

    private fun detectCategories(text: String): List<String> {
        val lowerText = text.lowercase()
        val detected = mutableListOf<String>()

        CATEGORY_KEYWORDS.forEach { (category, keywords) ->
            if (keywords.any { lowerText.contains(it) }) {
                detected.add(category)
            }
        }

        return detected
    }

    private fun buildInterestKeywords(prefs: AiPreferences): Set<String> {
        if (prefs.interests.isEmpty()) return emptySet()

        return prefs.interests
            .flatMap { interest ->
                interest.displayName
                    .lowercase()
                    .replace("&", " ")
                    .replace("/", " ")
                    .split(Regex("\\s+"))
                    .filter { it.length >= 3 }
            }
            .toSet()
    }

    private fun getLifeSituationCategories(lifeSituations: Set<LifeSituation>): List<String> {
        return lifeSituations.flatMap { situation ->
            when (situation) {
                LifeSituation.STUDENT -> listOf("education")
                LifeSituation.WORKING_PROFESSIONAL,
                LifeSituation.FREELANCER,
                LifeSituation.ENTREPRENEUR,
                LifeSituation.PART_TIME -> listOf("work")
                LifeSituation.STAY_AT_HOME_PARENT -> listOf("family", "health")
                LifeSituation.RETIRED -> listOf("health", "hobby", "social")
                LifeSituation.BETWEEN_JOBS -> listOf("work", "education")
            }
        }.distinct()
    }

    private fun emptyResult(input: ScoringInput): ScorerResult {
        return ScorerResult(
            scorerId = id,
            scores = input.itemsToScore.associate { it.id to 0.5f },
            signals = input.itemsToScore.associate { it.id to listOf("No preferences set") }
        )
    }

    override fun isApplicable(input: ScoringInput): Boolean {
        return input.preferences?.setupComplete == true
    }
}
