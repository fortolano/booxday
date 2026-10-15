package com.boox.day.ai

import android.content.Context
import android.util.Log
import com.boox.day.BuildConfig
import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.AiPreferences.Companion.getDailyRhythmSummary
import com.boox.day.data.ai.AiPreferences.Companion.getLifestyleSummary
import com.boox.day.data.ai.AiPreferences.Companion.getPrioritySummary
import com.boox.day.data.ai.AiPreferences.Companion.getSituationSummary
import com.boox.day.data.ai.AiPreferences.Companion.getWorkProfileSummary
import com.boox.day.data.ai.AiPreferencesRepository
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ocr.semantic.SemanticGeniusService
import com.boox.day.ocr.semantic.model.DateSemanticContext
import com.boox.day.ui.views.hasFilePermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

object TaskAiContextBuilderV1 {
    private const val TAG = "TaskAiContextBuilderV1"

    const val MAX_TASK_LINES = 50
    const val MAX_TASK_LINE_CHARS = 120

    private const val USER_CONTEXT_MAX_CHARS = 1500
    private const val TASK_SCOPE_MAX_CHARS = 3000
    private const val SEMANTIC_CONTEXT_MAX_CHARS_PER_DAY = 1500
    private const val CALENDAR_CONTEXT_MAX_CHARS = 2000
    private const val BOOX_NOTES_MAX_CHARS = 1500
    private const val BOOX_NOTES_LOOKBACK_DAYS = 30
    private const val TOTAL_CONTEXT_MAX_CHARS = 16000

    private const val MAX_DOMAINS = 3
    private const val MAX_INTENTS = 5
    private const val MAX_KEYWORDS = 15
    private const val MAX_ENTITIES = 10
    private const val MAX_ACTION_ITEMS = 10
    private const val MAX_NOTES = 10
    private const val MAX_NOTE_KEYWORDS = 5
    private const val MAX_NOTE_SYNOPSIS_CHARS = 300

    @Volatile
    private var lastSemanticRangeCache: SemanticRangeCacheEntry? = null
    @Volatile
    private var appContext: Context? = null

    data class TaskAiContextSectionV1(
        val title: String,
        val body: String,
        val charCount: Int
    )

    data class TaskAiContextBlockV1(
        val sections: List<TaskAiContextSectionV1>,
        val totalChars: Int
    ) {
        fun asPromptBlock(): String {
            return sections.joinToString(separator = "\n\n") { section ->
                "## ${section.title}\n${section.body}"
            }
        }
    }

    enum class ContextTrimPolicy {
        DEFAULT,
        PLAN_MY_DAY
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun buildContextBlock(
        context: Context,
        date: LocalDate,
        rangeStart: LocalDate? = null,
        rangeEnd: LocalDate? = null,
        includeBooxNotes: Boolean,
        includeSourceTypes: Set<OcrSource> = setOf(OcrSource.TASK, OcrSource.MEMO),
        taskScopeSection: String? = null,
        calendarSection: String? = null,
        patternSection: String? = null,
        semanticFallbackSection: String? = null,
        semanticContextsOverride: List<DateSemanticContext>? = null,
        referenceHour: Int? = null,
        trimPolicy: ContextTrimPolicy = ContextTrimPolicy.DEFAULT
    ): TaskAiContextBlockV1 = coroutineScope {
        val currentHour = (referenceHour ?: LocalTime.now().hour).coerceIn(0, 23)
        val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

        val preferencesDeferred = async(Dispatchers.IO) {
            AiPreferencesRepository(context).getPreferences()
        }

        val semanticDeferred = if (semanticContextsOverride == null) {
            async(Dispatchers.IO) {
                loadSemanticContexts(
                    context = context,
                    date = date,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    includeSourceTypes = includeSourceTypes
                )
            }
        } else null

        val booxNotesDeferred = if (includeBooxNotes) {
            async(Dispatchers.IO) {
                loadBooxNotesSummary(
                    context = context,
                    date = date,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd
                )
            }
        } else null

        val preferences = preferencesDeferred.await()
        val semanticContexts = semanticContextsOverride ?: semanticDeferred?.await()
        val booxNotesSummary = booxNotesDeferred?.await()

        val sections = mutableListOf<TaskAiContextSectionV1>()

        val userContextBody = buildUserContextSection(
            date = date,
            dayOfWeek = dayOfWeek,
            currentHour = currentHour,
            preferences = preferences,
            patternSection = patternSection
        )
        sections.add(buildSection("USER CONTEXT", userContextBody, USER_CONTEXT_MAX_CHARS))

        taskScopeSection?.takeIf { it.isNotBlank() }?.let {
            sections.add(buildSection("TASK SCOPE", it, TASK_SCOPE_MAX_CHARS))
        }

        val semanticBody = buildSemanticMemorySection(semanticContexts)
            ?: semanticFallbackSection?.takeIf { it.isNotBlank() }
        semanticBody?.let {
            sections.add(buildSection("SEMANTIC MEMORY", it, SEMANTIC_CONTEXT_MAX_CHARS_PER_DAY))
        }

        calendarSection?.takeIf { it.isNotBlank() }?.let {
            sections.add(buildSection("CALENDAR CONTEXT", it, CALENDAR_CONTEXT_MAX_CHARS))
        }

        booxNotesSummary?.takeIf { it.isNotBlank() }?.let {
            sections.add(buildSection("BOOX NOTES CONTEXT", it, BOOX_NOTES_MAX_CHARS))
        }

        val capped = enforceTotalCap(sections, trimPolicy)

        if (BuildConfig.DEBUG) {
            val total = capped.sumOf { it.charCount }
            capped.forEach { section ->
                Log.d(TAG, "Context section ${section.title}: ${section.charCount} chars")
            }
            Log.d(TAG, "Context total chars: $total")
        }

        TaskAiContextBlockV1(
            sections = capped,
            totalChars = capped.sumOf { it.charCount }
        )
    }

    suspend fun buildContextBlockOrEmpty(
        context: Context?,
        date: LocalDate,
        rangeStart: LocalDate? = null,
        rangeEnd: LocalDate? = null,
        includeBooxNotes: Boolean,
        includeSourceTypes: Set<OcrSource> = setOf(OcrSource.TASK, OcrSource.MEMO),
        taskScopeSection: String? = null,
        calendarSection: String? = null,
        patternSection: String? = null,
        semanticFallbackSection: String? = null,
        referenceHour: Int? = null,
        trimPolicy: ContextTrimPolicy = ContextTrimPolicy.DEFAULT
    ): String {
        val resolved = context ?: appContext ?: return ""
        return buildContextBlock(
            context = resolved,
            date = date,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            includeBooxNotes = includeBooxNotes,
            includeSourceTypes = includeSourceTypes,
            taskScopeSection = taskScopeSection,
            calendarSection = calendarSection,
            patternSection = patternSection,
            semanticFallbackSection = semanticFallbackSection,
            referenceHour = referenceHour,
            trimPolicy = trimPolicy
        ).asPromptBlock()
    }

    suspend fun hasSemanticContextAvailable(
        context: Context,
        date: LocalDate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        includeSourceTypes: Set<OcrSource>
    ): Boolean {
        val contexts = loadSemanticContexts(
            context = context,
            date = date,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            includeSourceTypes = includeSourceTypes
        ) ?: return false
        return contexts.any { hasSemanticSignals(it) }
    }

    private fun buildUserContextSection(
        date: LocalDate,
        dayOfWeek: String,
        currentHour: Int,
        preferences: AiPreferences,
        patternSection: String?
    ): String {
        val timeOfDay = when (currentHour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }

        return buildString {
            appendLine("Date: $date")
            appendLine("Time: ${String.format("%02d", currentHour)}:00 ($timeOfDay)")
            appendLine("Day: $dayOfWeek")
            run {
                appendLine()
                appendLine("Preferences:")
                appendLine("- Priorities: ${preferences.getPrioritySummary()}")
                appendLine("- Daily rhythm: ${preferences.getDailyRhythmSummary()}")
                appendLine("- Life situation: ${preferences.lifeSituations.joinToString(" + ") { it.displayName }}")
                appendLine("- Energy pattern: ${preferences.energyPattern.displayName}")
                appendLine("- Living environment: ${preferences.livingEnvironment.displayName}")

                if (preferences.interests.isNotEmpty()) {
                    val interestNames = preferences.interests.joinToString(", ") { it.displayName }
                    appendLine("- Interests: $interestNames")
                }
                if (!preferences.includeDeadlineWarnings) {
                    appendLine("- Deadline warnings: disabled")
                }

                val workProfile = preferences.getWorkProfileSummary()
                if (workProfile.isNotBlank()) {
                    appendLine("- Work profile: $workProfile")
                }

                val lifestyle = preferences.getLifestyleSummary()
                if (lifestyle.isNotBlank()) {
                    appendLine("- Lifestyle: $lifestyle")
                }
                val situation = preferences.getSituationSummary()
                if (situation.isNotBlank()) {
                    appendLine("- Situation: $situation")
                }

                val modes = mutableListOf<String>()
                if (preferences.vacationMode) modes.add("vacation")
                if (preferences.crunchMode) modes.add("crunch")
                if (preferences.holidayMode) modes.add("holiday")
                if (modes.isNotEmpty()) {
                    appendLine("- Active modes: ${modes.joinToString(", ")}")
                }
                appendLine("- Suggestion style: ${preferences.suggestionStyle.displayName}")
                appendLine("- Suggestion count: ${preferences.suggestionCount.displayName}")
            }
            patternSection?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine(it.trim())
            }
        }
    }

    private fun buildSemanticMemorySection(contexts: List<DateSemanticContext>?): String? {
        if (contexts.isNullOrEmpty()) return null
        val blocks = contexts.mapNotNull { context ->
            if (!hasSemanticSignals(context)) return@mapNotNull null
            val block = buildString {
                appendLine("Date: ${context.date}")
                val domains = context.dominantDomains.take(MAX_DOMAINS)
                if (domains.isNotEmpty()) {
                    appendLine("Domains: ${domains.joinToString(", ") { "${it.domain.name} (${it.count})" }}")
                }
                val intents = context.dominantIntents.take(MAX_INTENTS)
                if (intents.isNotEmpty()) {
                    appendLine("Intents: ${intents.joinToString(", ") { "${it.intent.name} (${it.count})" }}")
                }
                val keywords = context.allKeywords.take(MAX_KEYWORDS).map { it.term }
                if (keywords.isNotEmpty()) {
                    appendLine("Keywords: ${keywords.joinToString(", ")}")
                }
                val entities = context.allEntities.take(MAX_ENTITIES).map { it.text }
                if (entities.isNotEmpty()) {
                    appendLine("Entities: ${entities.joinToString(", ")}")
                }
                val actions = context.pendingActionItems.take(MAX_ACTION_ITEMS)
                    .map { "${it.actionItem.verb} ${it.actionItem.objectText}".trim() }
                    .filter { it.isNotBlank() }
                if (actions.isNotEmpty()) {
                    appendLine("Action items: ${actions.joinToString("; ")}")
                }
            }
            truncate(block.trim(), SEMANTIC_CONTEXT_MAX_CHARS_PER_DAY)
        }
        if (blocks.isEmpty()) return null

        val selectedBlocks = mutableListOf<String>()
        var total = 0
        for (block in blocks.asReversed()) {
            val separator = if (selectedBlocks.isEmpty()) 0 else 2
            if (total + separator + block.length > SEMANTIC_CONTEXT_MAX_CHARS_PER_DAY) {
                break
            }
            selectedBlocks.add(block)
            total += separator + block.length
        }
        return selectedBlocks.asReversed().joinToString("\n\n")
    }

    private suspend fun loadBooxNotesSummary(
        context: Context,
        date: LocalDate,
        rangeStart: LocalDate?,
        rangeEnd: LocalDate?
    ): String? {
        if (!hasFilePermission(context) || !GlobalAppSettings.isBooxNotesReady) {
            return null
        }

        val service = SemanticGeniusService(context)
        val hasData = try {
            service.hasAnyOcrData(OcrSource.BOOX_NOTE)
        } catch (e: Exception) {
            Log.w(TAG, "Boox notes availability check failed: ${e.message}")
            false
        }
        if (!hasData) return null

        val profilesByNote = try {
            service.getAllProfilesForSourceType(OcrSource.BOOX_NOTE)
        } catch (e: Exception) {
            Log.w(TAG, "Boox notes profile load failed: ${e.message}")
            return null
        }

        // Boox Notes use a wider 30-day window (reference documents stay relevant longer)
        val notesStart = date.minusDays(BOOX_NOTES_LOOKBACK_DAYS.toLong())
        val notesEnd = date

        val notes = profilesByNote.mapNotNull { (_, profiles) ->
            val best = profiles
                .filter { it.hasSemanticData }
                .maxByOrNull { it.keywords.size + it.actionItems.size + it.entities.size }
                ?: return@mapNotNull null

            val sourceDate = best.sourceDate ?: return@mapNotNull null
            val noteDate = runCatching { LocalDate.parse(sourceDate) }.getOrNull() ?: return@mapNotNull null
            if (noteDate.isBefore(notesStart) || noteDate.isAfter(notesEnd)) return@mapNotNull null

            val title = best.sourceTitle?.ifBlank { "Untitled Note" } ?: "Untitled Note"
            val synopsis = best.synopsis?.takeIf { it.isNotBlank() }
                ?: best.keySentences.firstOrNull().orEmpty()
            val trimmedSynopsis = truncate(synopsis, MAX_NOTE_SYNOPSIS_CHARS)
            val keywords = best.keywords.take(MAX_NOTE_KEYWORDS).map { it.term }

            NoteSummary(
                date = noteDate,
                title = title,
                synopsis = trimmedSynopsis,
                keywords = keywords
            )
        }.sortedByDescending { it.date }
            .take(MAX_NOTES)

        if (notes.isEmpty()) return null

        return buildString {
            notes.forEach { note ->
                appendLine("Title: ${note.title}")
                if (note.synopsis.isNotBlank()) {
                    appendLine("Synopsis: ${note.synopsis}")
                }
                if (note.keywords.isNotEmpty()) {
                    appendLine("Keywords: ${note.keywords.joinToString(", ")}")
                }
                appendLine()
            }
        }.trim()
    }

    private suspend fun loadSemanticContexts(
        context: Context,
        date: LocalDate,
        rangeStart: LocalDate?,
        rangeEnd: LocalDate?,
        includeSourceTypes: Set<OcrSource>
    ): List<DateSemanticContext>? {
        val service = SemanticGeniusService(context)
        var hasAny = false
        for (source in includeSourceTypes) {
            val available = try {
                service.hasAnyOcrData(source)
            } catch (e: Exception) {
                Log.w(TAG, "Semantic availability check failed: ${e.message}")
                false
            }
            if (available) {
                hasAny = true
                break
            }
        }
        if (!hasAny) return null

        val start = rangeStart ?: date
        val end = rangeEnd ?: date
        val cacheKey = buildSemanticRangeKey(start, end, includeSourceTypes)
        lastSemanticRangeCache?.let { cached ->
            if (cached.key == cacheKey) return cached.contexts
        }

        val contexts = try {
            val dates = generateDateRange(start, end)
            coroutineScope {
                dates.map { day ->
                    async {
                        service.getDateContext(
                            date = day.toString(),
                            includeSourceTypes = includeSourceTypes
                        )
                    }
                }.map { it.await() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Semantic context load failed: ${e.message}")
            null
        }

        if (contexts.isNullOrEmpty()) return null
        lastSemanticRangeCache = SemanticRangeCacheEntry(cacheKey, contexts)
        return contexts
    }

    private fun enforceTotalCap(
        sections: List<TaskAiContextSectionV1>,
        trimPolicy: ContextTrimPolicy
    ): List<TaskAiContextSectionV1> {
        val mutable = sections.toMutableList()
        var total = mutable.sumOf { it.charCount }
        if (total <= TOTAL_CONTEXT_MAX_CHARS) return mutable

        fun dropSection(title: String) {
            val index = mutable.indexOfFirst { it.title == title }
            if (index >= 0) {
                val removed = mutable.removeAt(index)
                total -= removed.charCount
            }
        }

        val dropOrder = when (trimPolicy) {
            ContextTrimPolicy.DEFAULT -> listOf(
                "CALENDAR CONTEXT",
                "BOOX NOTES CONTEXT",
                "SEMANTIC MEMORY"
            )
            ContextTrimPolicy.PLAN_MY_DAY -> listOf(
                "BOOX NOTES CONTEXT",
                "SEMANTIC MEMORY",
                "CALENDAR CONTEXT"
            )
        }
        dropOrder.forEach { title ->
            dropSection(title)
            if (total <= TOTAL_CONTEXT_MAX_CHARS) {
                return mutable
            }
        }
        return mutable
    }

    private fun buildSection(
        title: String,
        body: String,
        maxChars: Int
    ): TaskAiContextSectionV1 {
        val trimmed = truncate(body.trim(), maxChars)
        return TaskAiContextSectionV1(
            title = title,
            body = trimmed,
            charCount = trimmed.length
        )
    }

    private fun hasSemanticSignals(context: DateSemanticContext): Boolean {
        return context.dominantDomains.isNotEmpty() ||
            context.dominantIntents.isNotEmpty() ||
            context.allKeywords.isNotEmpty() ||
            context.allEntities.isNotEmpty() ||
            context.pendingActionItems.isNotEmpty()
    }

    private fun generateDateRange(start: LocalDate, end: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = start
        while (!current.isAfter(end)) {
            dates.add(current)
            current = current.plusDays(1)
        }
        return dates
    }

    private fun truncate(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        if (maxChars <= 3) return text.take(maxChars)
        return text.take(maxChars - 3) + "..."
    }

    private fun buildSemanticRangeKey(
        start: LocalDate,
        end: LocalDate,
        includeSourceTypes: Set<OcrSource>
    ): String {
        val sources = includeSourceTypes.map { it.name }.sorted().joinToString(",")
        return "${start}_$end|$sources"
    }

    private data class NoteSummary(
        val date: LocalDate,
        val title: String,
        val synopsis: String,
        val keywords: List<String>
    )

    private data class SemanticRangeCacheEntry(
        val key: String,
        val contexts: List<DateSemanticContext>
    )
}
