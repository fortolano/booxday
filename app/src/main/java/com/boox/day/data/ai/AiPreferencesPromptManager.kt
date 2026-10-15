package com.boox.day.data.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the AI Preferences prompt system with progressive backoff.
 *
 * This manager coordinates:
 * 1. Proactive popup prompts (with backoff)
 * 2. In-dialog reminder banners
 * 3. Settings menu access
 *
 * Usage:
 * ```
 * val manager = AiPreferencesPromptManager(context)
 *
 * // Check if should show proactive popup
 * if (manager.shouldShowProactivePrompt()) {
 *     showAiPreferencesInvitePopup(
 *         onDismiss = { manager.onPromptDismissed() },
 *         onAccept = { showAiPreferencesDialog() }
 *     )
 * }
 *
 * // In Smart Suggestions dialog, show reminder banner if not setup
 * if (manager.shouldShowInDialogReminder()) {
 *     // Show "Personalize your suggestions" banner
 * }
 * ```
 */
class AiPreferencesPromptManager(context: Context) {

    private val repository = AiPreferencesRepository(context)

    // Observable state for UI
    private val _showProactivePrompt = MutableStateFlow(false)
    val showProactivePrompt: StateFlow<Boolean> = _showProactivePrompt.asStateFlow()

    private val _preferencesSetupComplete = MutableStateFlow(false)
    val preferencesSetupComplete: StateFlow<Boolean> = _preferencesSetupComplete.asStateFlow()

    init {
        refreshState()
    }

    /**
     * Refresh state from repository.
     * Call this after any state changes or when app resumes.
     */
    fun refreshState() {
        _preferencesSetupComplete.value = repository.isSetupComplete()
        _showProactivePrompt.value = repository.shouldShowProactivePrompt()
    }

    // ========== Proactive Popup Logic ==========

    /**
     * Check if we should show the proactive preferences prompt popup.
     * This respects the progressive backoff system.
     */
    fun shouldShowProactivePrompt(): Boolean {
        return repository.shouldShowProactivePrompt()
    }

    /**
     * Call when showing the proactive prompt popup.
     * Updates the timestamp for backoff calculation.
     */
    fun onProactivePromptShown() {
        repository.onPromptShown()
        refreshState()
    }

    /**
     * Call when user dismisses the proactive prompt popup without setting up.
     * Advances the backoff phase if needed.
     */
    fun onProactivePromptDismissed() {
        repository.onPromptDismissed()
        refreshState()
    }

    // ========== In-Dialog Reminder Logic ==========

    /**
     * Check if we should show the in-dialog reminder banner.
     * Shows until user completes setup (doesn't use backoff).
     */
    fun shouldShowInDialogReminder(): Boolean {
        return !repository.isSetupComplete()
    }

    // ========== Preferences Completion ==========

    /**
     * Call when user completes preferences setup.
     * Permanently deactivates all prompts.
     */
    fun onPreferencesCompleted() {
        repository.onPreferencesCompleted()
        refreshState()
    }

    /**
     * Check if user has completed preferences setup.
     */
    fun isSetupComplete(): Boolean {
        return repository.isSetupComplete()
    }

    // ========== Preferences Access ==========

    /**
     * Get current AI preferences.
     */
    fun getPreferences(): AiPreferences {
        return repository.getPreferences()
    }

    /**
     * Save AI preferences.
     */
    fun savePreferences(preferences: AiPreferences) {
        repository.savePreferences(preferences)
        refreshState()
    }

    // ========== Special Modes ==========

    /**
     * Toggle vacation mode.
     * When enabled, AI suggests relaxation and travel tasks.
     */
    fun setVacationMode(enabled: Boolean) {
        repository.setVacationMode(enabled)
    }

    /**
     * Toggle crunch mode.
     * When enabled, AI focuses on deadline-critical tasks.
     */
    fun setCrunchMode(enabled: Boolean) {
        repository.setCrunchMode(enabled)
    }

    /**
     * Toggle holiday mode.
     * When enabled, AI suggests holiday-related tasks.
     */
    fun setHolidayMode(enabled: Boolean) {
        repository.setHolidayMode(enabled)
    }

    // ========== Debug/Testing ==========

    /**
     * Get current prompt state for debugging.
     */
    fun getPromptStateDebug(): String {
        return repository.getPromptState().getDebugDescription()
    }

    /**
     * Reset all preferences and prompt state.
     * For testing purposes only.
     */
    fun resetAll() {
        repository.resetPreferences()
        repository.resetPromptState()
        refreshState()
    }

    /**
     * Get prompt statistics for display.
     */
    fun getPromptStats(): PromptStats {
        val state = repository.getPromptState()
        val prefs = repository.getPreferences()
        return PromptStats(
            phase = state.phase,
            totalDismissals = state.totalCloses,
            maxDismissals = AiPreferencesPromptState.MAX_TOTAL_CLOSES,
            isDeactivated = state.isDeactivated,
            isSetupComplete = prefs.setupComplete,
            lastPromptTime = state.lastPromptTimestamp,
            nextPromptIntervalMs = state.getIntervalMs()
        )
    }
}

/**
 * Statistics about the prompt system for UI display.
 */
data class PromptStats(
    val phase: PromptPhase,
    val totalDismissals: Int,
    val maxDismissals: Int,
    val isDeactivated: Boolean,
    val isSetupComplete: Boolean,
    val lastPromptTime: Long,
    val nextPromptIntervalMs: Long
) {
    /**
     * Human-readable summary.
     */
    fun getSummary(): String {
        return when {
            isSetupComplete -> "Preferences configured"
            isDeactivated -> "Prompts disabled ($totalDismissals dismissals)"
            else -> "Phase: ${phase.displayName}, $totalDismissals/$maxDismissals dismissals"
        }
    }
}
