package com.boox.day.ai.intelligence

import com.boox.day.data.ai.IntelligenceFeature
import com.boox.day.data.ai.ScorerIds

/**
 * Weight profiles for each AI feature.
 * Weights must sum to 1.0 for normalized scoring.
 */
object FeatureWeightProfiles {

    /**
     * Get weight profile for a feature.
     * @return Map of scorerId → weight (0.0 to 1.0)
     */
    fun getWeights(feature: IntelligenceFeature): Map<String, Float> {
        return when (feature) {
            IntelligenceFeature.SMART_SUGGESTIONS -> smartSuggestionsWeights
            IntelligenceFeature.SMART_PRIORITIZATION -> smartPrioritizationWeights
            IntelligenceFeature.PLAN_MY_DAY -> planMyDayWeights
            IntelligenceFeature.BREAK_DOWN_TASK -> breakDownTaskWeights
            IntelligenceFeature.DAILY_SUMMARY -> dailySummaryWeights
            IntelligenceFeature.WEEKLY_SUMMARY -> weeklySummaryWeights
        }
    }

    /**
     * SMART SUGGESTIONS
     * Focus: Discover new tasks aligned with user preferences and patterns.
     * De-emphasize: Urgency (no deadlines for new suggestions).
     */
    private val smartSuggestionsWeights = mapOf(
        ScorerIds.URGENCY to 0.10f,      // Low - suggestions don't have deadlines
        ScorerIds.PREFERENCE to 0.25f,   // HIGH - align with user priorities
        ScorerIds.CALENDAR to 0.20f,     // Medium - event prep/follow-up
        ScorerIds.PATTERN to 0.20f,      // HIGH - fill gaps in patterns
        ScorerIds.SEMANTIC to 0.15f,     // Medium - semantic relevance
        ScorerIds.CAPACITY to 0.10f,     // Low - fit available time
        ScorerIds.COMPLEXITY to 0.00f    // Not applicable
    )

    /**
     * SMART PRIORITIZATION
     * Focus: Rank existing tasks by urgency and importance.
     * De-emphasize: Patterns (focus on what's due).
     */
    private val smartPrioritizationWeights = mapOf(
        ScorerIds.URGENCY to 0.30f,      // HIGHEST - deadlines matter most
        ScorerIds.PREFERENCE to 0.20f,   // Medium - user priorities
        ScorerIds.CALENDAR to 0.20f,     // Medium - event relationships
        ScorerIds.PATTERN to 0.10f,      // Low - less relevant
        ScorerIds.SEMANTIC to 0.10f,     // Low - context awareness
        ScorerIds.CAPACITY to 0.10f,     // Low - time fitting
        ScorerIds.COMPLEXITY to 0.00f    // Can inform but not weight
    )

    /**
     * PLAN MY DAY
     * Focus: Build realistic daily schedule respecting capacity.
     * De-emphasize: Patterns (focus on today).
     */
    private val planMyDayWeights = mapOf(
        ScorerIds.URGENCY to 0.20f,      // Medium - due today matters
        ScorerIds.PREFERENCE to 0.15f,   // Medium - user priorities
        ScorerIds.CALENDAR to 0.25f,     // HIGH - schedule around events
        ScorerIds.PATTERN to 0.15f,      // Medium - typical day patterns
        ScorerIds.SEMANTIC to 0.05f,     // Low
        ScorerIds.CAPACITY to 0.20f,     // HIGH - realistic scheduling
        ScorerIds.COMPLEXITY to 0.00f
    )

    /**
     * BREAK DOWN TASK
     * Focus: Identify complex tasks needing decomposition.
     * De-emphasize: Calendar, capacity (not about scheduling).
     */
    private val breakDownTaskWeights = mapOf(
        ScorerIds.URGENCY to 0.15f,      // Medium - urgent complex tasks first
        ScorerIds.PREFERENCE to 0.10f,   // Low
        ScorerIds.CALENDAR to 0.10f,     // Low
        ScorerIds.PATTERN to 0.05f,      // Very low
        ScorerIds.SEMANTIC to 0.10f,     // Low - context for decomposition
        ScorerIds.CAPACITY to 0.10f,     // Low
        ScorerIds.COMPLEXITY to 0.40f    // HIGHEST - core purpose
    )

    /**
     * DAILY SUMMARY
     * Focus: Highlight achievements and key patterns.
     * De-emphasize: Urgency (looking back, not forward).
     */
    private val dailySummaryWeights = mapOf(
        ScorerIds.URGENCY to 0.15f,      // Medium - met deadlines
        ScorerIds.PREFERENCE to 0.25f,   // HIGH - priority alignment
        ScorerIds.CALENDAR to 0.15f,     // Medium - events attended
        ScorerIds.PATTERN to 0.25f,      // HIGH - streak/balance
        ScorerIds.SEMANTIC to 0.10f,     // Low - themes
        ScorerIds.CAPACITY to 0.10f,     // Low - utilization
        ScorerIds.COMPLEXITY to 0.00f
    )

    /**
     * WEEKLY SUMMARY
     * Focus: Long-term patterns and trends.
     * De-emphasize: Urgency, calendar (weekly view).
     */
    private val weeklySummaryWeights = mapOf(
        ScorerIds.URGENCY to 0.10f,      // Low - weekly perspective
        ScorerIds.PREFERENCE to 0.20f,   // Medium - priority alignment
        ScorerIds.CALENDAR to 0.10f,     // Low
        ScorerIds.PATTERN to 0.35f,      // HIGHEST - weekly trends
        ScorerIds.SEMANTIC to 0.15f,     // Medium - themes
        ScorerIds.CAPACITY to 0.10f,     // Low - utilization
        ScorerIds.COMPLEXITY to 0.00f
    )
}
