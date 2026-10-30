package com.boox.day.ai

import com.boox.day.data.ai.AiPreferences

/**
 * The last check on Smart Suggestions' answer for the AI options (Oct 2026): with Health or Social
 * suggestions off, an idea the AI tagged HEALTH or SOCIAL is dropped, even if the AI ignored the
 * request's "avoid" line.
 */
object SuggestionOptionsFilter {

    fun <T> keep(suggestions: List<T>, options: AiPreferences, category: (T) -> String): List<T> =
        suggestions.filterNot { suggestion ->
            when (category(suggestion).trim().uppercase()) {
                "HEALTH" -> !options.includeHealthReminders
                "SOCIAL" -> !options.includeSocialReminders
                else -> false
            }
        }
}
