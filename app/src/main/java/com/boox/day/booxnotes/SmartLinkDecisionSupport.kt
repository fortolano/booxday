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
