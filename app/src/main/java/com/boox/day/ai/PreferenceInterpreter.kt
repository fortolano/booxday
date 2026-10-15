package com.boox.day.ai

import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.EnergyPattern
import com.boox.day.data.ai.Industry
import com.boox.day.data.ai.LifeSituation
import com.boox.day.data.ai.SuggestionStyle
import com.boox.day.data.ai.BlockedSlot
import com.boox.day.data.ai.PreferenceConstraints
import com.boox.day.data.ai.Profession
import com.boox.day.data.ai.SpecialMode
import com.boox.day.data.ai.SuggestionAggressiveness

/**
 * Interprets AiPreferences into actionable PreferenceConstraints.
 *
 * Transforms user's raw preference data into rules that guide
 * the Agentic Planning Service's proposal generation.
 */
object PreferenceInterpreter {

    /**
     * Convert user preferences to planning constraints.
     *
     * @param preferences User's AiPreferences (nullable - uses defaults if null)
     * @return PreferenceConstraints with interpreted rules
     */
    fun interpret(preferences: AiPreferences?): PreferenceConstraints {
        if (preferences == null) {
            return PreferenceConstraints.default()
        }

        return PreferenceConstraints(
            priorityRanking = buildPriorityRanking(preferences),
            blockedSlots = inferBlockedSlots(preferences),
            workHoursStart = inferWorkHoursStart(preferences),
            workHoursEnd = inferWorkHoursEnd(preferences),
            activeMode = inferActiveMode(preferences),
            aggressiveness = inferAggressiveness(preferences),
            includeHealthReminders = preferences.includeHealthReminders,
            includeSocialReminders = preferences.includeSocialReminders,
            energyPattern = preferences.energyPattern,
            lifestyleContext = buildLifestyleContext(preferences)
        )
    }

    /**
     * Build priority ranking from preference values (highest to lowest).
     */
    private fun buildPriorityRanking(prefs: AiPreferences): List<String> {
        val priorities = listOf(
            "work" to prefs.workPriority,
            "education" to prefs.educationPriority,
            "family" to prefs.familyPriority,
            "health" to prefs.healthPriority,
            "social" to prefs.socialPriority,
            "hobby" to prefs.hobbyPriority,
            "finance" to prefs.financePriority
        )

        return priorities
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Infer blocked time slots from lifestyle factors.
     */
    private fun inferBlockedSlots(prefs: AiPreferences): List<BlockedSlot> {
        val slots = mutableListOf<BlockedSlot>()

        // Kids: School pickup window (3-4 PM)
        if (prefs.hasKids) {
            slots.add(BlockedSlot(15, 16, "School pickup"))
        }

        // Caregiver: Morning and evening care
        if (prefs.isCareGiver) {
            slots.add(BlockedSlot(7, 8, "Morning care routine"))
            slots.add(BlockedSlot(18, 19, "Evening care routine"))
        }

        // Stay-at-home parent: Multiple blocks
        if (prefs.lifeSituations.contains(LifeSituation.STAY_AT_HOME_PARENT)) {
            slots.add(BlockedSlot(11, 13, "Lunch preparation/mealtime"))
        }

        // Pet owner: Morning and evening walk times
        if (prefs.hasPets && prefs.isActiveLifestyle) {
            slots.add(BlockedSlot(6, 7, "Morning pet care"))
            slots.add(BlockedSlot(17, 18, "Evening pet care"))
        }

        return slots
    }

    /**
     * Infer optimal work hours start based on energy pattern.
     */
    private fun inferWorkHoursStart(prefs: AiPreferences): Int {
        return when (prefs.energyPattern) {
            EnergyPattern.MORNING_PERSON -> 6
            EnergyPattern.NIGHT_OWL -> 11
            EnergyPattern.AFTERNOON_PEAK -> 10
            EnergyPattern.STEADY -> 9
        }
    }

    /**
     * Infer optimal work hours end based on energy pattern and situation.
     */
    private fun inferWorkHoursEnd(prefs: AiPreferences): Int {
        // Base on energy pattern
        val baseEnd = when (prefs.energyPattern) {
            EnergyPattern.MORNING_PERSON -> 14
            EnergyPattern.NIGHT_OWL -> 22
            EnergyPattern.AFTERNOON_PEAK -> 18
            EnergyPattern.STEADY -> 17
        }

        // Adjust for family responsibilities
        return if (prefs.hasKids || prefs.isCareGiver) {
            minOf(baseEnd, 17)  // Cap at 5 PM for family time
        } else {
            baseEnd
        }
    }

    /**
     * Determine if a special mode is active.
     */
    private fun inferActiveMode(prefs: AiPreferences): SpecialMode? {
        return when {
            prefs.vacationMode -> SpecialMode.VACATION
            prefs.crunchMode -> SpecialMode.CRUNCH
            prefs.holidayMode -> SpecialMode.HOLIDAY
            else -> null
        }
    }

    /**
     * Map suggestion style to aggressiveness level.
     */
    private fun inferAggressiveness(prefs: AiPreferences): SuggestionAggressiveness {
        return when (prefs.suggestionStyle) {
            SuggestionStyle.PRACTICAL -> SuggestionAggressiveness.PROACTIVE
            SuggestionStyle.BALANCED -> SuggestionAggressiveness.BALANCED
            SuggestionStyle.HOLISTIC -> SuggestionAggressiveness.GENTLE
        }
    }

    /**
     * Build lifestyle context for AI prompt.
     */
    private fun buildLifestyleContext(prefs: AiPreferences): List<String> {
        val context = mutableListOf<String>()

        // Life situation(s)
        prefs.lifeSituations.forEach { context.add(it.displayName) }

        if (prefs.industry != Industry.NOT_SET) context.add(prefs.industry.displayName)
        if (prefs.profession != Profession.NOT_SET) context.add(prefs.profession.displayName)

        // Key traits
        if (prefs.isActiveLifestyle) context.add("active lifestyle")
        if (prefs.isWeekendWarrior) context.add("weekend warrior")
        if (prefs.isHomebody) context.add("homebody")
        if (prefs.isSocialButterfly) context.add("social butterfly")
        if (prefs.isQuietIntrovert) context.add("quiet introvert")
        if (prefs.isOutdoorsLover) context.add("outdoors lover")
        if (prefs.isFoodie) context.add("foodie")
        if (prefs.isDigitalNative) context.add("digital native")

        // Family situation
        if (prefs.hasKids) context.add("has children")
        if (prefs.hasPets) context.add("has pets")
        if (prefs.isCareGiver) context.add("caregiver")
        if (prefs.livesAlone) context.add("lives alone")

        // Environment
        context.add(prefs.livingEnvironment.displayName)

        return context
    }

    /**
     * Get the peak productivity window based on energy pattern.
     * Returns (startHour, endHour) for the user's most productive time.
     */
    fun getPeakProductivityWindow(prefs: AiPreferences): Pair<Int, Int> {
        return when (prefs.energyPattern) {
            EnergyPattern.MORNING_PERSON -> 6 to 11
            EnergyPattern.NIGHT_OWL -> 19 to 23
            EnergyPattern.AFTERNOON_PEAK -> 13 to 17
            EnergyPattern.STEADY -> 10 to 15
        }
    }

    /**
     * Get suggestion count based on user preference and mode.
     */
    fun getMaxProposalCount(prefs: AiPreferences?): Int {
        if (prefs == null) return 5  // Default

        // Vacation mode = minimal suggestions
        if (prefs.vacationMode) return 2

        // Crunch mode = more aggressive
        if (prefs.crunchMode) return 8

        // Based on suggestion count preference
        return prefs.suggestionCount.count
    }

    /**
     * Determine if a specific priority category should be emphasized.
     * Returns true if the category is rated 4 or 5.
     */
    fun shouldEmphasize(prefs: AiPreferences?, category: String): Boolean {
        if (prefs == null) return false

        val priority = when (category.lowercase()) {
            "work" -> prefs.workPriority
            "education" -> prefs.educationPriority
            "family" -> prefs.familyPriority
            "health" -> prefs.healthPriority
            "social" -> prefs.socialPriority
            "hobby" -> prefs.hobbyPriority
            "finance" -> prefs.financePriority
            else -> 3
        }

        return priority >= 4
    }
}
