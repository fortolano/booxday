package com.boox.day.tips

/**
 * Context for tip relevance based on current app state.
 *
 * Tips are shown based on which contexts are currently "active":
 * - MEMOS/TASKS: Mutually exclusive based on current tab
 * - CALENDAR/EVENTS: Always visible (top 40% of screen)
 * - GENERAL: Tips applicable in any context
 */
enum class TipContext {
    /** Memos tab is currently active */
    MEMOS,

    /** Tasks tab is currently active */
    TASKS,

    /** Calendar grid (always visible in top section) */
    CALENDAR,

    /** Events panel (always visible in top section) */
    EVENTS,

    /** General tips applicable anywhere */
    GENERAL
}
