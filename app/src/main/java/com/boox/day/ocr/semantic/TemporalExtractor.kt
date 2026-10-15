package com.boox.day.ocr.semantic

import com.boox.day.ocr.model.TemporalSignals

/**
 * Step 9: Temporal signal extraction.
 *
 * Extracts:
 * - Explicit dates (2024-01-15, Jan 15, 1/15)
 * - Explicit times (14:30, 2:30pm)
 * - Relative dates (tomorrow, next week)
 * - Weekdays (Monday, Friday)
 * - Date ranges (Jan 5-10)
 *
 * Computes dominantDateCandidate: closest to createdAt or most frequent.
 */
object TemporalExtractor {

    /**
     * Extract temporal signals from text.
     *
     * @param text The text to analyze
     * @param createdAt Source creation timestamp (for dominant date calculation)
     * @return TemporalSignals bundle
     */
    fun extract(text: String, createdAt: Long? = null): TemporalSignals {
        val explicitDates = mutableListOf<String>()
        val explicitTimes = mutableListOf<String>()
        val relativeDates = mutableListOf<String>()
        val weekdays = mutableListOf<String>()
        val dateRanges = mutableListOf<String>()

        // Extract explicit dates
        extractExplicitDates(text, explicitDates)

        // Extract explicit times
        extractExplicitTimes(text, explicitTimes)

        // Extract relative dates
        extractRelativeDates(text, relativeDates)

        // Extract weekdays
        extractWeekdays(text, weekdays)

        // Extract date ranges
        extractDateRanges(text, dateRanges)

        // Calculate dominant date candidate
        val dominantDate = calculateDominantDate(
            explicitDates, relativeDates, weekdays, createdAt
        )

        return TemporalSignals(
            explicitDates = explicitDates.distinct(),
            explicitTimes = explicitTimes.distinct(),
            relativeDates = relativeDates.distinct(),
            weekdays = weekdays.distinct(),
            dateRanges = dateRanges.distinct(),
            dominantDateCandidate = dominantDate
        )
    }

    /**
     * Extract explicit date patterns.
     */
    private fun extractExplicitDates(text: String, results: MutableList<String>) {
        // ISO format: 2024-01-15
        Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b").findAll(text).forEach {
            results.add(it.value)
        }

        // US format: 01/15/2024 or 1/15/24
        Regex("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b").findAll(text).forEach {
            results.add(it.value)
        }

        // European format: 15.01.2024
        Regex("\\b\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}\\b").findAll(text).forEach {
            results.add(it.value)
        }

        // Month name: Jan 15, January 15th, 15 Jan
        Regex("\\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{4})?\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value) }

        Regex("\\b\\d{1,2}(?:st|nd|rd|th)?\\s+(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)(?:,?\\s+\\d{4})?\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value) }
    }

    /**
     * Extract explicit time patterns.
     */
    private fun extractExplicitTimes(text: String, results: MutableList<String>) {
        // HH:MM format
        Regex("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b").findAll(text).forEach {
            results.add(it.value)
        }

        // AM/PM format
        Regex("\\b\\d{1,2}(?::\\d{2})?\\s*[ap]\\.?m\\.?\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value) }

        // O'clock
        Regex("\\b\\d{1,2}\\s+o'clock\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value) }

        // Named times
        Regex("\\b(noon|midnight)\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value) }
    }

    /**
     * Extract relative date patterns.
     */
    private fun extractRelativeDates(text: String, results: MutableList<String>) {
        // Today, tomorrow, yesterday
        Regex("\\b(today|tomorrow|yesterday)\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value.lowercase()) }

        // Next/last/this week/month/year
        Regex("\\b(next|last|this)\\s+(week|month|year|day)\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value.lowercase()) }

        // In N days/weeks/months
        Regex("\\bin\\s+\\d+\\s+(day|week|month|year)s?\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value.lowercase()) }

        // N days/weeks ago
        Regex("\\b\\d+\\s+(day|week|month|year)s?\\s+ago\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value.lowercase()) }

        // End of week/month/year
        Regex("\\bend\\s+of\\s+(the\\s+)?(week|month|year)\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value.lowercase()) }
    }

    /**
     * Extract weekday patterns.
     */
    private fun extractWeekdays(text: String, results: MutableList<String>) {
        val weekdayPattern = Regex("\\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\b", RegexOption.IGNORE_CASE)

        weekdayPattern.findAll(text).forEach {
            results.add(it.value.lowercase().replaceFirstChar { c -> c.uppercase() })
        }
    }

    /**
     * Extract date range patterns.
     */
    private fun extractDateRanges(text: String, results: MutableList<String>) {
        // Jan 5-10, 5th-10th
        Regex("\\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2}(?:st|nd|rd|th)?\\s*[-–]\\s*\\d{1,2}(?:st|nd|rd|th)?\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value) }

        // 1/15-1/20
        Regex("\\b\\d{1,2}/\\d{1,2}\\s*[-–]\\s*\\d{1,2}/\\d{1,2}\\b")
            .findAll(text).forEach { results.add(it.value) }

        // 15-20 January
        Regex("\\b\\d{1,2}\\s*[-–]\\s*\\d{1,2}\\s+(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\b", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { results.add(it.value) }
    }

    /**
     * Calculate the dominant date candidate.
     *
     * Priority:
     * 1. Most frequent explicit date
     * 2. "tomorrow" or "today"
     * 3. First explicit date found
     * 4. First weekday found
     */
    private fun calculateDominantDate(
        explicitDates: List<String>,
        relativeDates: List<String>,
        weekdays: List<String>,
        createdAt: Long?
    ): String? {
        // Check for high-priority relative dates
        if ("tomorrow" in relativeDates.map { it.lowercase() }) {
            return "tomorrow"
        }
        if ("today" in relativeDates.map { it.lowercase() }) {
            return "today"
        }

        // Find most frequent explicit date
        if (explicitDates.isNotEmpty()) {
            val frequency = explicitDates.groupingBy { it }.eachCount()
            val mostFrequent = frequency.maxByOrNull { it.value }
            if (mostFrequent != null && mostFrequent.value > 1) {
                return mostFrequent.key
            }
            // Return first explicit date
            return explicitDates.first()
        }

        // Return first relative date
        if (relativeDates.isNotEmpty()) {
            return relativeDates.first()
        }

        // Return first weekday
        if (weekdays.isNotEmpty()) {
            return weekdays.first()
        }

        return null
    }
}
