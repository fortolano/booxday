package com.boox.day.data.model

/**
 * Provider-agnostic writable calendar for event creation.
 */
data class WritableCalendar(
    val id: String,
    val name: String,
    val accountId: String,
    val accountEmail: String,
    val providerId: ProviderId,
    val isPrimary: Boolean,
    val colorHex: String? = null
)
