package com.boox.day.data.ai

import kotlinx.serialization.Serializable

/**
 * Tracks the state of AI Preferences prompt system.
 * Implements progressive backoff to avoid annoying users.
 *
 * Backoff Strategy:
 * - Phase 1 (INITIAL): Every 2 days, max 2 attempts
 * - Phase 2 (REMINDED): Every 4 days, max 2 attempts
 * - Phase 3 (PERSISTENT): Weekly, max 3 attempts
 * - Phase 4 (DEACTIVATED): No more proactive prompts
 *
 * Total: 7 proactive prompts max before giving up.
 * User can still access preferences via Settings menu.
 */
@Serializable
data class AiPreferencesPromptState(
    val phase: PromptPhase = PromptPhase.INITIAL,
    val totalCloses: Int = 0,
    val closesInCurrentPhase: Int = 0,
    val lastPromptTimestamp: Long = 0L,
    val isDeactivated: Boolean = false
) {
    companion object {
        // Time constants in milliseconds
        const val TWO_DAYS_MS = 2L * 24 * 60 * 60 * 1000
        const val FOUR_DAYS_MS = 4L * 24 * 60 * 60 * 1000
        const val ONE_WEEK_MS = 7L * 24 * 60 * 60 * 1000

        // Max closes before deactivation
        const val MAX_TOTAL_CLOSES = 7
    }

    /**
     * Returns the minimum time that must pass before showing the next prompt.
     */
    fun getIntervalMs(): Long = when (phase) {
        PromptPhase.INITIAL -> TWO_DAYS_MS
        PromptPhase.REMINDED -> FOUR_DAYS_MS
        PromptPhase.PERSISTENT -> ONE_WEEK_MS
        PromptPhase.DEACTIVATED -> Long.MAX_VALUE
    }

    /**
     * Returns the max closes allowed in the current phase before advancing.
     */
    fun getMaxClosesForPhase(): Int = when (phase) {
        PromptPhase.INITIAL -> 2
        PromptPhase.REMINDED -> 2
        PromptPhase.PERSISTENT -> 3
        PromptPhase.DEACTIVATED -> Int.MAX_VALUE
    }

    /**
     * Returns the next phase after exceeding max closes in current phase.
     */
    fun getNextPhase(): PromptPhase = when (phase) {
        PromptPhase.INITIAL -> PromptPhase.REMINDED
        PromptPhase.REMINDED -> PromptPhase.PERSISTENT
        PromptPhase.PERSISTENT -> PromptPhase.DEACTIVATED
        PromptPhase.DEACTIVATED -> PromptPhase.DEACTIVATED
    }

    /**
     * Checks if it's time to show a proactive prompt.
     */
    fun shouldShowPrompt(currentTimeMs: Long): Boolean {
        if (isDeactivated || phase == PromptPhase.DEACTIVATED) return false
        if (totalCloses >= MAX_TOTAL_CLOSES) return false

        val timeSinceLastPrompt = currentTimeMs - lastPromptTimestamp
        return timeSinceLastPrompt >= getIntervalMs()
    }

    /**
     * Returns updated state after user dismisses the prompt.
     */
    fun onPromptDismissed(currentTimeMs: Long): AiPreferencesPromptState {
        val newTotalCloses = totalCloses + 1
        val newClosesInPhase = closesInCurrentPhase + 1

        // Check if we should advance to next phase
        val shouldAdvancePhase = newClosesInPhase >= getMaxClosesForPhase()

        // Check if we've hit the total limit
        val shouldDeactivate = newTotalCloses >= MAX_TOTAL_CLOSES

        return if (shouldDeactivate) {
            copy(
                phase = PromptPhase.DEACTIVATED,
                totalCloses = newTotalCloses,
                closesInCurrentPhase = newClosesInPhase,
                lastPromptTimestamp = currentTimeMs,
                isDeactivated = true
            )
        } else if (shouldAdvancePhase) {
            copy(
                phase = getNextPhase(),
                totalCloses = newTotalCloses,
                closesInCurrentPhase = 0, // Reset for new phase
                lastPromptTimestamp = currentTimeMs
            )
        } else {
            copy(
                totalCloses = newTotalCloses,
                closesInCurrentPhase = newClosesInPhase,
                lastPromptTimestamp = currentTimeMs
            )
        }
    }

    /**
     * Returns updated state after user completes preferences setup.
     * This permanently deactivates proactive prompts (goal achieved).
     */
    fun onPreferencesCompleted(): AiPreferencesPromptState {
        return copy(
            phase = PromptPhase.DEACTIVATED,
            isDeactivated = true
        )
    }

    /**
     * Returns a human-readable description of current state (for debugging).
     */
    fun getDebugDescription(): String {
        return "Phase: ${phase.name}, Total closes: $totalCloses, " +
                "Phase closes: $closesInCurrentPhase, Deactivated: $isDeactivated"
    }
}

/**
 * Phases of the progressive backoff system.
 */
@Serializable
enum class PromptPhase(val displayName: String, val description: String) {
    INITIAL("Initial", "First encounters - prompt every 2 days"),
    REMINDED("Reminded", "Been reminded - prompt every 4 days"),
    PERSISTENT("Persistent", "Final attempts - prompt weekly"),
    DEACTIVATED("Deactivated", "User declined enough - no more prompts")
}
