package com.boox.day.booxnotes

import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.CalendarEvent
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class SmartLinkDecisionMode {
    LEGACY,
    MANUAL_LINK,
    MANUAL_ACCEPT_SUGGESTION,
    DAY_ONLY_FALLBACK,
    SAME_DAY_AUTO_HIGH,
    SAME_DAY_AUTO_MEDIUM,
    SAME_DAY_SUGGESTION,
    CROSS_DAY_BULK_APPLY,
    AI_APPLY
}

enum class SmartLinkDataQualityFlag {
    SEMANTIC_RUNTIME_DISABLED,
    NO_NOTE_SEMANTICS,
    NO_EVENT_SEMANTICS,
    LINKED_EVENT_DELETED,
    AI_GENERATED
}

internal object SmartLinkDecisionSupport {

    fun mergeMatchReasons(
        reasons: List<MatchReason>,
        addSemanticContext: Boolean = false,
        addAiSuggested: Boolean = false
    ): List<MatchReason> {
        val merged = linkedSetOf<MatchReason>()
        merged.addAll(reasons)
        if (addSemanticContext) {
            merged.add(MatchReason.SEMANTIC_CONTEXT_MATCH)
        }
        if (addAiSuggested) {
            merged.add(MatchReason.AI_SUGGESTED_MATCH)
        }
        return merged.toList()
    }

    /** Reasons that say the note is ABOUT the event, not just written near its time */
    private val CONTENT_REASONS = setOf(
        MatchReason.TITLE_MATCH,
        MatchReason.SEMANTIC_GROUP_MATCH,
        MatchReason.SEMANTIC_CONTEXT_MATCH,
        MatchReason.AI_SUGGESTED_MATCH
    )

    fun hasContentSignal(reasons: Collection<MatchReason>): Boolean = reasons.any { it in CONTENT_REASONS }

    /**
     * The confidence a suggestion is SHOWN with. Timing alone ("written during the event") is a
     * Possible, never a Strong: on a busy day several events fit the time, and "Link N strong…"
     * links every Strong at once. Auto-link keeps its own thresholds (Oct 2026).
     */
    fun suggestionConfidence(confidence: LinkConfidence, reasons: Collection<MatchReason>): LinkConfidence =
        if (confidence == LinkConfidence.HIGH && !hasContentSignal(reasons)) LinkConfidence.MEDIUM else confidence

    fun mergeDataQualityFlags(vararg flagGroups: Collection<SmartLinkDataQualityFlag>): List<SmartLinkDataQualityFlag> {
        val merged = linkedSetOf<SmartLinkDataQualityFlag>()
        flagGroups.forEach { merged.addAll(it) }
        return merged.toList()
    }

    fun parseDismissedEventIds(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            json.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun filterEligibleEvents(
        note: BooxNoteIndex,
        events: List<CalendarEvent>,
        dismissedEventIds: Set<String> = emptySet(),
        searchDays: Int? = null
    ): List<CalendarEvent> {
        val noteDate = if (searchDays != null) {
            runCatching { LocalDate.parse(note.originalDate) }.getOrNull()
        } else {
            null
        }

        return events.asSequence()
            .filter { !it.isAllDay }
            .filter { it.id !in dismissedEventIds }
            .filter { !EventExclusionRules.shouldExclude(it) }
            .filter { event ->
                if (noteDate == null || searchDays == null) {
                    true
                } else {
                    runCatching {
                        abs(ChronoUnit.DAYS.between(noteDate, LocalDate.parse(event.dayId)).toInt()) <= searchDays
                    }.getOrDefault(false)
                }
            }
            .toList()
    }
}
