package com.boox.day.data.ai

import android.content.Context
import com.boox.day.data.ai.AiPreferences.Companion.keptOptions
import com.boox.day.data.db.KvProxy
import com.boox.day.data.db.KvRepository

/**
 * The AI options (AI Settings → AI options), kept in the Kv table under the old AI Preferences key.
 * Since 29 Oct 2026 only the kept options are read or saved ([keptOptions]): how many suggestions,
 * Vacation, and the Health and Social suggestion switches.
 */
class AiPreferencesRepository(private val context: Context) {

    private val kvProxy = KvProxy(context)

    companion object {
        const val AI_PREFERENCES_KEY = "AI_PREFERENCES"

        /** The removed questionnaire's invite schedule (deleted by [removeOldAnswers]). */
        private const val OLD_PROMPT_STATE_KEY = "AI_PREFERENCES_PROMPT_STATE"
    }

    /** The AI options; defaults when none were saved. */
    fun getPreferences(): AiPreferences =
        kvProxy.get(AI_PREFERENCES_KEY, AiPreferences.serializer()).keptOptions()

    /**
     * Saves the AI options (only the kept ones). Results worked out with the old options are dropped
     * (scores 15 min, summaries 4-20 h): their cache keys don't include the options.
     */
    fun savePreferences(preferences: AiPreferences) {
        val kept = preferences.keptOptions().copy(lastUpdated = System.currentTimeMillis())
        kvProxy.setKv(AI_PREFERENCES_KEY, kept, AiPreferences.serializer())
        com.boox.day.ai.intelligence.IntelligenceCache.invalidateAll()
        runCatching { com.boox.day.data.db.SummaryCacheRepository(context).clearAll() }
    }

    /**
     * Once after the update that removed the AI Preferences questions (29 Oct 2026): the stored answers
     * other than the kept options, and the questions' invite schedule, are deleted from the tablet.
     */
    fun removeOldAnswers() {
        val saved = kvProxy.get(AI_PREFERENCES_KEY, AiPreferences.serializer())
        if (saved != null) {
            kvProxy.setKv(AI_PREFERENCES_KEY, saved.keptOptions(), AiPreferences.serializer())
        }
        KvRepository(context).delete(OLD_PROMPT_STATE_KEY)
    }
}
