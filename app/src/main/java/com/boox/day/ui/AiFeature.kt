package com.boox.day.ui

/**
 * The way into a new AI feature (Nov 2026), so none can skip the AI off switch: AI off stops here with
 * the normal message, no key opens the connect popup and the feature carries on after it, and a
 * feature that sends calendar data shows its notice first.
 */
object AiFeature {
    fun start(sendsCalendar: Boolean, run: () -> Unit) {
        AiKeyGate.withKey {
            if (sendsCalendar) CalendarAiNotice.afterNotice(run) else run()
        }
    }
}
