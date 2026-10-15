package com.boox.day.tips

/**
 * Represents a single tip to display to the user.
 *
 * @param id Unique identifier for tracking shown tips (e.g., "memos_selection")
 * @param message The tip text to display in the snackbar
 * @param contexts Set of contexts where this tip is relevant
 * @param isDiscovery If true, this tip promotes features in OTHER contexts (shown 25% of the time)
 */
data class Tip(
    val id: String,
    val message: String,
    val contexts: Set<TipContext>,
    val isDiscovery: Boolean = false
)
