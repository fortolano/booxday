package com.boox.day.ai

import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.AiPreferences.Companion.activeMode
import com.boox.day.data.ai.AiPreferences.Companion.keptOptions
import com.boox.day.data.ai.PreferenceConstraints

/**
 * Turns the AI options into Optimize My Day's planning constraints.
 *
 * Oct 2026: nothing invented. The planning window is the whole day for everyone (the user gives no
 * working hours) and there are no made-up blocked times. Since 29 Oct 2026 only the kept options
 * apply (Vacation, Health and Social suggestions); the other old AI Preferences answers didn't change
 * the plans in a measured test and were removed.
 */
object PreferenceInterpreter {

    fun interpret(preferences: AiPreferences?): PreferenceConstraints {
        val options = preferences.keptOptions()
        return PreferenceConstraints.default().copy(
            activeMode = options.activeMode(),
            includeHealthReminders = options.includeHealthReminders,
            includeSocialReminders = options.includeSocialReminders
        )
    }
}
