package com.boox.day.data.ai

import android.content.Context
import androidx.lifecycle.LiveData
import com.boox.day.data.db.KvProxy

/**
 * Repository for AI Preferences and Prompt State persistence.
 * Uses KvProxy to store serialized data in the Kv table.
 */
class AiPreferencesRepository(private val context: Context) {

    private val kvProxy = KvProxy(context)

    companion object {
        const val AI_PREFERENCES_KEY = "AI_PREFERENCES"
        const val AI_PREFERENCES_PROMPT_STATE_KEY = "AI_PREFERENCES_PROMPT_STATE"
    }

    // ========== AI Preferences ==========

    /**
     * Get current AI preferences, or default if not set.
     */
    fun getPreferences(): AiPreferences {
        return kvProxy.get(AI_PREFERENCES_KEY, AiPreferences.serializer())
            ?: AiPreferences()
    }

    /**
     * Observe AI preferences changes as LiveData.
     */
    fun observePreferences(): LiveData<AiPreferences?> {
        return kvProxy.observeKv(AI_PREFERENCES_KEY, AiPreferences.serializer(), AiPreferences())
    }

    /**
     * Save AI preferences.
     * Automatically updates lastUpdated timestamp.
     */
    fun savePreferences(preferences: AiPreferences) {
        val updated = preferences.copy(lastUpdated = System.currentTimeMillis())
        kvProxy.setKv(AI_PREFERENCES_KEY, updated, AiPreferences.serializer())
        // Oct 2026: results worked out with the old answers go (scores 15 min, summaries 4-20 h);
        // their cache keys don't include the preferences
        com.boox.day.ai.intelligence.IntelligenceCache.invalidateAll()
        runCatching { com.boox.day.data.db.SummaryCacheRepository(context).clearAll() }
    }

    /**
     * Mark preferences as setup complete.
     */
    fun markSetupComplete() {
        val current = getPreferences()
        savePreferences(current.copy(setupComplete = true))
    }

    /**
     * Check if user has completed preferences setup.
     */
    fun isSetupComplete(): Boolean {
        return getPreferences().setupComplete
    }

    // ========== Prompt State ==========

    /**
     * Get current prompt state, or default if not set.
     */
    fun getPromptState(): AiPreferencesPromptState {
        return kvProxy.get(AI_PREFERENCES_PROMPT_STATE_KEY, AiPreferencesPromptState.serializer())
            ?: AiPreferencesPromptState()
    }

    /**
     * Observe prompt state changes as LiveData.
     */
    fun observePromptState(): LiveData<AiPreferencesPromptState?> {
        return kvProxy.observeKv(
            AI_PREFERENCES_PROMPT_STATE_KEY,
            AiPreferencesPromptState.serializer(),
            AiPreferencesPromptState()
        )
    }

    /**
     * Save prompt state.
     */
    fun savePromptState(state: AiPreferencesPromptState) {
        kvProxy.setKv(AI_PREFERENCES_PROMPT_STATE_KEY, state, AiPreferencesPromptState.serializer())
    }

    /**
     * Record that user dismissed the preferences prompt.
     * Updates state with progressive backoff logic.
     */
    fun onPromptDismissed() {
        val currentState = getPromptState()
        val updatedState = currentState.onPromptDismissed(System.currentTimeMillis())
        savePromptState(updatedState)
    }

    /**
     * Record that user completed preferences setup.
     * Permanently deactivates proactive prompts.
     */
    fun onPreferencesCompleted() {
        val currentState = getPromptState()
        val updatedState = currentState.onPreferencesCompleted()
        savePromptState(updatedState)
        markSetupComplete()
    }

    /**
     * Check if we should show the proactive preferences prompt.
     */
    fun shouldShowProactivePrompt(): Boolean {
        // Don't show if already set up
        if (isSetupComplete()) return false

        val state = getPromptState()
        return state.shouldShowPrompt(System.currentTimeMillis())
    }

    /**
     * Record that the proactive prompt was shown (updates timestamp).
     * Call this when showing the prompt, before user interaction.
     */
    fun onPromptShown() {
        val currentState = getPromptState()
        savePromptState(currentState.copy(lastPromptTimestamp = System.currentTimeMillis()))
    }

    // ========== Special Modes ==========

    /**
     * Toggle vacation mode.
     */
    fun setVacationMode(enabled: Boolean) {
        val current = getPreferences()
        savePreferences(current.copy(vacationMode = enabled))
    }

    /**
     * Toggle crunch mode (deadline-focused).
     */
    fun setCrunchMode(enabled: Boolean) {
        val current = getPreferences()
        savePreferences(current.copy(crunchMode = enabled))
    }

    /**
     * Toggle holiday mode.
     */
    fun setHolidayMode(enabled: Boolean) {
        val current = getPreferences()
        savePreferences(current.copy(holidayMode = enabled))
    }

    // ========== Debug/Reset ==========

    /**
     * Reset all AI preferences to defaults.
     * Useful for testing or user-requested reset.
     */
    fun resetPreferences() {
        savePreferences(AiPreferences())
    }

    /**
     * Reset prompt state (allows prompts to show again).
     * Useful for testing.
     */
    fun resetPromptState() {
        savePromptState(AiPreferencesPromptState())
    }
}
