package com.boox.day.ocr.semantic

import com.boox.day.ocr.model.ActionItem
import com.boox.day.ocr.model.KeywordEntry
import com.boox.day.ocr.model.TemporalSignals
import com.boox.day.ocr.model.TopicEntry

/**
 * Step 11: Synopsis generation.
 *
 * Creates a structured synopsis for Smart Link LLM matching.
 * Fixed format, max 900 characters.
 *
 * Format:
 * ```
 * TITLE: {sourceTitle}
 * TOPICS: {top 3 topics}
 * DATES: {dates} TIMES: {times}
 * KEYWORDS: {top 10 keywords}
 * ACTIONS: {top 3 action items}
 * SENTENCES: {top 2 key sentences}
 * ```
 */
object SynopsisGenerator {

    /**
     * Maximum synopsis length in characters
     */
    private const val MAX_SYNOPSIS_LENGTH = 900

    /**
     * Maximum keywords in synopsis
     */
    private const val MAX_KEYWORDS = 10

    /**
     * Maximum action items in synopsis
     */
    private const val MAX_ACTIONS = 3

    /**
     * Maximum sentences in synopsis
     */
    private const val MAX_SENTENCES = 2

    /**
     * Generate synopsis from extracted semantic data.
     *
     * @param sourceTitle Source title (note title, day name)
     * @param topics Top topics
     * @param temporalSignals Temporal signals
     * @param keywords Top keywords
     * @param actionItems Action items
     * @param keySentences Key sentences
     * @return Structured synopsis (max 900 chars)
     */
    fun generate(
        sourceTitle: String?,
        topics: List<TopicEntry>,
        temporalSignals: TemporalSignals,
        keywords: List<KeywordEntry>,
        actionItems: List<ActionItem>,
        keySentences: List<String>
    ): String {
        val sb = StringBuilder()

        // Title line
        val title = sourceTitle?.take(50) ?: "Untitled"
        sb.appendLine("TITLE: $title")

        // Topics line
        val topicNames = topics.take(3).joinToString(", ") { it.topic.name }
        if (topicNames.isNotBlank()) {
            sb.appendLine("TOPICS: $topicNames")
        }

        // Dates and times line
        val dates = buildDateString(temporalSignals)
        val times = temporalSignals.explicitTimes.take(3).joinToString(", ")
        if (dates.isNotBlank() || times.isNotBlank()) {
            val dateTimeStr = buildString {
                if (dates.isNotBlank()) append("DATES: $dates")
                if (dates.isNotBlank() && times.isNotBlank()) append(" ")
                if (times.isNotBlank()) append("TIMES: $times")
            }
            sb.appendLine(dateTimeStr)
        }

        // Keywords line
        val keywordStr = keywords.take(MAX_KEYWORDS).joinToString(", ") { it.term }
        if (keywordStr.isNotBlank()) {
            sb.appendLine("KEYWORDS: $keywordStr")
        }

        // Actions line
        val actionsStr = actionItems.take(MAX_ACTIONS).joinToString("; ") { item ->
            "${item.verb} ${item.objectText}".take(60).trim()
        }
        if (actionsStr.isNotBlank()) {
            sb.appendLine("ACTIONS: $actionsStr")
        }

        // Sentences line
        val sentencesStr = keySentences.take(MAX_SENTENCES).joinToString(" | ") { sentence ->
            sentence.take(100).trim()
        }
        if (sentencesStr.isNotBlank()) {
            sb.appendLine("SENTENCES: $sentencesStr")
        }

        // Truncate to max length if needed
        var result = sb.toString().trim()
        if (result.length > MAX_SYNOPSIS_LENGTH) {
            result = truncateSynopsis(result)
        }

        return result
    }

    /**
     * Build date string from temporal signals.
     */
    private fun buildDateString(signals: TemporalSignals): String {
        val parts = mutableListOf<String>()

        // Add dominant date first if available
        signals.dominantDateCandidate?.let { parts.add(it) }

        // Add a few explicit dates (not including dominant)
        val otherDates = signals.explicitDates
            .filter { it != signals.dominantDateCandidate }
            .take(2)
        parts.addAll(otherDates)

        // Add relative dates
        val relativeDates = signals.relativeDates
            .filter { it != signals.dominantDateCandidate }
            .take(2)
        parts.addAll(relativeDates)

        return parts.distinct().take(4).joinToString(", ")
    }

    /**
     * Truncate synopsis intelligently to fit within max length.
     */
    private fun truncateSynopsis(synopsis: String): String {
        if (synopsis.length <= MAX_SYNOPSIS_LENGTH) return synopsis

        val lines = synopsis.split("\n").toMutableList()

        // Strategy: Remove lines from bottom until it fits
        while (lines.joinToString("\n").length > MAX_SYNOPSIS_LENGTH && lines.size > 2) {
            lines.removeAt(lines.lastIndex)
        }

        var result = lines.joinToString("\n")

        // If still too long, hard truncate
        if (result.length > MAX_SYNOPSIS_LENGTH) {
            result = result.take(MAX_SYNOPSIS_LENGTH - 3) + "..."
        }

        return result
    }
}
