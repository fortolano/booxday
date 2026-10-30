package com.boox.day.ai

import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.AiPreferences.Companion.activeMode
import com.boox.day.data.ai.AiPreferences.Companion.bestFocusTime
import com.boox.day.data.ai.AiPreferences.Companion.forPrompt
import com.boox.day.data.ai.AiPreferences.Companion.pickedAreas
import com.boox.day.data.ai.EnergyPattern
import com.boox.day.data.ai.Industry
import com.boox.day.data.ai.SuggestionStyle
import com.boox.day.data.ai.PreferenceConstraints
import com.boox.day.data.ai.Profession
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
    /**
     * Oct 2026: nothing invented. The planning window is the whole day for everyone (the user gives no
     * working hours), there are no made-up blocked times ("School pickup 15-16" for anyone with
     * children, weekends included), and only answers the user actually set up are used.
     */
    fun interpret(preferences: AiPreferences?): PreferenceConstraints {
        val prefs = preferences.forPrompt() ?: return PreferenceConstraints.default()
        return PreferenceConstraints(
            priorityRanking = prefs.pickedAreas(),
            blockedSlots = emptyList(),
            workHoursStart = CapacityAnalyzer.DEFAULT_DAY_START,
            workHoursEnd = CapacityAnalyzer.DEFAULT_DAY_END,
            activeMode = prefs.activeMode(),
            aggressiveness = inferAggressiveness(prefs),
            includeHealthReminders = prefs.includeHealthReminders,
            includeSocialReminders = prefs.includeSocialReminders,
            energyPattern = prefs.energyPattern,
            lifestyleContext = buildLifestyleContext(prefs),
            bestFocusTime = prefs.bestFocusTime()
        )
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
        val defaults = AiPreferences()

        // Life situation(s), unless still the pre-selected default
        if (prefs.lifeSituations != defaults.lifeSituations) prefs.lifeSituations.forEach { context.add(it.displayName) }

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

        // Environment, unless still the pre-selected default
        if (prefs.livingEnvironment != defaults.livingEnvironment) context.add(prefs.livingEnvironment.displayName)

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
