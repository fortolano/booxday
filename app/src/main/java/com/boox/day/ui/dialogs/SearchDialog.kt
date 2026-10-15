package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.boox.day.R
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.db.UnifiedOcrRepository
import com.boox.day.data.db.UnifiedOcrSearchResult
import com.boox.day.ocr.model.OcrSource
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Search Dialog
 *
 * Full-screen popup for searching across memos, tasks, events, and Boox Notes.
 * Uses FTS4 full-text search with synonym expansion via UnifiedOcrRepository.
 *
 * Search scope: All indexed content in the unified_ocr_text FTS4 table.
 * This includes memos, tasks, calendar events (Google/Outlook/local), and Boox Notes.
 * Content is indexed by the OCR pipeline up to dateLookbackDays (server-configured, default 730 days).
 * Once indexed, content is searchable forever regardless of age.
 *
 * E-Ink optimized: Popup (not Dialog), no animations, high contrast, large touch targets.
 *
 * @param ocrRepository Repository for FTS4 search queries
 * @param onNavigateToDate Callback when user taps a result — navigates to that day
 * @param onOpenBooxNote Callback when user taps a Boox Note result — opens note by uniqueId
 * @param onDismiss Callback to close the dialog
 */
@Composable
fun SearchDialog(
    ocrRepository: UnifiedOcrRepository,
    onNavigateToDate: (String) -> Unit,
    onOpenBooxNote: (uniqueId: String) -> Unit = {},
    /** Task hits open the task itself (plan T3); null = just its day */
    onOpenTask: ((taskId: String, dayDate: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }

    var query by remember { mutableStateOf("") }
    var allResults by remember { mutableStateOf<List<UnifiedOcrSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    // null = All, otherwise filter by source type
    // "EVENTS" is a special sentinel — we search both CALENDAR_EVENT and LOCAL_EVENT
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    // Combined search: FTS4 index + direct table queries.
    // Always search ALL sources so filter chip badges show correct counts.
    // Filtering is applied client-side after fetching.
    LaunchedEffect(query) {
        if (query.length < 2) {
            allResults = emptyList()
            return@LaunchedEffect
        }
        isSearching = true
        delay(300) // debounce
        allResults = withContext(Dispatchers.IO) {
            val collected = mutableListOf<UnifiedOcrSearchResult>()
            val seenKeys = mutableSetOf<String>()

            // Helper to add a result with dedup
            fun addResult(result: UnifiedOcrSearchResult, key: String) {
                if (key !in seenKeys) {
                    seenKeys.add(key)
                    collected.add(result)
                }
            }

            // Helper to convert event to search result
            fun eventToResult(id: String, type: String, title: String, dayId: String, desc: String?, score: Double) =
                UnifiedOcrSearchResult(id = id, sourceType = type, sourceId = id, pageId = "",
                    sourceTitle = title, sourceDate = dayId, snippet = desc?.take(200) ?: title, score = score)

            // Helper to convert task to search result
            fun taskToResult(task: com.boox.day.data.db.Task, score: Double): UnifiedOcrSearchResult {
                val text = task.typedText ?: task.convertedText ?: task.recognizedText ?: ""
                return UnifiedOcrSearchResult(id = task.id, sourceType = "TASK", sourceId = task.id,
                    pageId = task.pageId, sourceTitle = null, sourceDate = task.dayDate, snippet = text, score = score)
            }

            // ═══════════════════════════════════════════════════════════════
            // TIER 1: Exact word-boundary matches (score = 2.0)
            // "mary" matches "Mary", "Meeting with Mary" — NOT "primary"
            // ═══════════════════════════════════════════════════════════════
            try {
                db.calendarEventDao().searchEventsExact(query, 20).forEach { e ->
                    addResult(eventToResult(e.id, "CALENDAR_EVENT", e.title, e.dayId, e.description, 2.0), "CE_${e.id}")
                }
                db.localEventDao().searchEventsExact(query, 20).forEach { e ->
                    addResult(eventToResult(e.id, "LOCAL_EVENT", e.title, e.dayId, e.description, 2.0), "LE_${e.id}")
                }
            } catch (_: Exception) {}
            try {
                db.taskDao().searchTasksExact(query, 20).forEach { t ->
                    addResult(taskToResult(t, 2.0), "T_${t.id}")
                }
            } catch (_: Exception) {}

            // ═══════════════════════════════════════════════════════════════
            // TIER 2: FTS4 for MEMO and BOOX_NOTE only (score = 1.0)
            // Tasks and events are fully covered by direct queries above.
            // FTS task/event results have generic titles and are ALWAYS excluded.
            // ═══════════════════════════════════════════════════════════════
            // Query each type separately so each gets its own result limit
            try {
                ocrRepository.searchByType(query, OcrSource.MEMO, limit = 30).forEach { r ->
                    addResult(r.copy(score = 1.0), "${r.sourceType}_${r.pageId}")
                }
            } catch (_: Exception) {}
            try {
                ocrRepository.searchByType(query, OcrSource.BOOX_NOTE, limit = 30).forEach { r ->
                    addResult(r.copy(score = 1.0), "${r.sourceType}_${r.pageId}")
                }
            } catch (_: Exception) {}

            // ═══════════════════════════════════════════════════════════════
            // TIER 3: Partial substring matches (score = 0.5)
            // "mary" inside "primary" — shown last with "Partial match" label
            // Only add results NOT already found by Tier 1
            // ═══════════════════════════════════════════════════════════════
            try {
                db.calendarEventDao().searchEventsPartial(query, 20).forEach { e ->
                    addResult(eventToResult(e.id, "CALENDAR_EVENT", e.title, e.dayId, e.description, 0.5), "CE_${e.id}")
                }
                db.localEventDao().searchEventsPartial(query, 20).forEach { e ->
                    addResult(eventToResult(e.id, "LOCAL_EVENT", e.title, e.dayId, e.description, 0.5), "LE_${e.id}")
                }
            } catch (_: Exception) {}
            try {
                db.taskDao().searchTasksPartial(query, 20).forEach { t ->
                    addResult(taskToResult(t, 0.5), "T_${t.id}")
                }
            } catch (_: Exception) {}

            // Sort: Tier 1 (2.0) → Tier 2 (1.0) → Tier 3 (0.5), then by date
            collected.sortedWith(compareByDescending<UnifiedOcrSearchResult> { it.score }
                .thenByDescending { it.sourceDate ?: "" })
                .take(50)
        }
        isSearching = false
    }

    // Client-side filter: allResults always has everything, filteredResults is what we display
    val filteredResults = remember(allResults, selectedFilter) {
        if (selectedFilter == null) allResults
        else allResults.filter { r ->
            when (selectedFilter) {
                "EVENTS" -> r.sourceType == "CALENDAR_EVENT" || r.sourceType == "LOCAL_EVENT"
                else -> r.sourceType == selectedFilter
            }
        }
    }

    // Anchor to top so the dialog doesn't shift when the virtual keyboard appears.
    // Fixed height (480dp = ~2/3 of Boox screen) prevents resize on keyboard open.
    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth(0.92f)
                    .height(600.dp)
                    .background(
                        EInkTheme.PopupOverlay.background,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .border(
                        EInkTheme.PopupOverlay.borderWidth,
                        EInkTheme.PopupOverlay.borderColor,
                        RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
                    )
                    .padding(EInkTheme.PopupOverlay.padding)
            ) {
                // Header: Title + Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search",
                        fontSize = EInkTheme.ModernDialog.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier
                            .size(28.dp)
                            .noRippleClickable { onDismiss() },
                        tint = EInkTheme.Colors.darkGray
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field — larger for e-ink
                SearchInputField(
                    query = query,
                    onQueryChange = { query = it },
                    isSearching = isSearching
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Per-type result counts for filter badges — computed from ALL results
                // so badges are always visible regardless of selected filter.
                // "EVENTS" merges CALENDAR_EVENT + LOCAL_EVENT
                val typeCounts = remember(allResults) {
                    val counts = mutableMapOf<String, Int>()
                    allResults.forEach { r ->
                        when (r.sourceType) {
                            "CALENDAR_EVENT", "LOCAL_EVENT" -> counts["EVENTS"] = (counts["EVENTS"] ?: 0) + 1
                            else -> counts[r.sourceType] = (counts[r.sourceType] ?: 0) + 1
                        }
                    }
                    counts
                }

                // Source type filter chips with count badges
                SourceFilterRow(
                    selected = selectedFilter,
                    onSelect = { selectedFilter = it },
                    totalCount = allResults.size,
                    typeCounts = typeCounts
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Subtle divider
                Divider(color = EInkTheme.Colors.dividerHairline, thickness = 1.dp)

                Spacer(modifier = Modifier.height(8.dp))

                // Results
                if (query.length < 2) {
                    // Empty state — no query yet
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = EInkTheme.Colors.lightGray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Search memos, tasks, events, and notes",
                                fontSize = EInkTheme.Typography.bodyLarge,
                                color = EInkTheme.Colors.gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Type at least 2 characters to begin",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.lightGray
                            )
                        }
                    }
                } else if (filteredResults.isEmpty() && !isSearching) {
                    // No results
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No results found for \"$query\"",
                            fontSize = EInkTheme.Typography.bodyLarge,
                            color = EInkTheme.Colors.gray
                        )
                    }
                } else {
                    // Result list with scroll indicator
                    val listState = rememberLazyListState()
                    val canScrollForward by remember {
                        derivedStateOf { listState.canScrollForward }
                    }

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredResults, key = { "${it.sourceId}_${it.pageId}" }) { result ->
                                SearchResultCard(
                                    result = result,
                                    onClick = {
                                        if (result.sourceType == "BOOX_NOTE") {
                                            // Open Boox Note directly in Notes app
                                            onOpenBooxNote(result.sourceId)
                                            onDismiss()
                                        } else {
                                            val date = result.sourceDate
                                            if (date != null) {
                                                if (result.sourceType == "TASK" && onOpenTask != null) {
                                                    onOpenTask(result.sourceId, date)
                                                } else {
                                                    onNavigateToDate(date)
                                                }
                                                onDismiss()
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // "Scroll for more" indicator (matches AboutDialog pattern)
                        if (canScrollForward) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(EInkTheme.Colors.white)
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(EInkTheme.Colors.offWhite)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Scroll down",
                                        tint = EInkTheme.Colors.darkGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Scroll for more",
                                        fontSize = EInkTheme.Typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = EInkTheme.Colors.darkGray
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = EInkTheme.Colors.darkGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search input field — sized for e-ink readability.
 * Taller than standard (48dp), larger font, clear button.
 */
@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .border(1.5.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            modifier = Modifier.size(24.dp),
            tint = EInkTheme.Colors.darkGray
        )

        Spacer(modifier = Modifier.width(10.dp))

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                fontSize = 18.sp,
                color = Color.Black
            ),
            singleLine = true,
            cursorBrush = SolidColor(Color.Black),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions.Default,
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search all content...",
                            fontSize = 18.sp,
                            color = EInkTheme.Colors.gray
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Status / clear
        when {
            isSearching -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = EInkTheme.Colors.darkGray
                )
            }
            query.isNotEmpty() -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    modifier = Modifier
                        .size(20.dp)
                        .noRippleClickable { onQueryChange("") },
                    tint = EInkTheme.Colors.gray
                )
            }
        }
    }
}

/**
 * Horizontal row of equal-width filter chips with result count badges.
 * All chips share the same width (1/5 of row) for visual symmetry.
 */
@Composable
private fun SourceFilterRow(
    selected: String?,
    onSelect: (String?) -> Unit,
    totalCount: Int = 0,
    typeCounts: Map<String, Int> = emptyMap()
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            label = "All",
            count = totalCount,
            isSelected = selected == null,
            modifier = Modifier.weight(1f)
        ) { onSelect(null) }
        FilterChip(
            label = "Memos",
            count = typeCounts["MEMO"] ?: 0,
            isSelected = selected == "MEMO",
            modifier = Modifier.weight(1f)
        ) { onSelect("MEMO") }
        FilterChip(
            label = "Tasks",
            count = typeCounts["TASK"] ?: 0,
            isSelected = selected == "TASK",
            modifier = Modifier.weight(1f)
        ) { onSelect("TASK") }
        FilterChip(
            label = "Events",
            count = typeCounts["EVENTS"] ?: 0,
            isSelected = selected == "EVENTS",
            modifier = Modifier.weight(1f)
        ) { onSelect("EVENTS") }
        FilterChip(
            label = "Notes",
            count = typeCounts["BOOX_NOTE"] ?: 0,
            isSelected = selected == "BOOX_NOTE",
            modifier = Modifier.weight(1f)
        ) { onSelect("BOOX_NOTE") }
    }
}

/**
 * Equal-width filter chip with count badge.
 * 36dp height, centered label, superscript count badge when > 0.
 */
@Composable
private fun FilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .background(
                if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.5.dp,
                if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.borderStrong,
                RoundedCornerShape(8.dp)
            )
            .noRippleClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) EInkTheme.Colors.background else EInkTheme.Colors.primary,
                maxLines = 1
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) EInkTheme.Colors.background else EInkTheme.Colors.primary,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$count",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Modern search result card — E-Ink optimized with source icon, title, date, and snippet.
 * Enhanced for Memo and Boox Note results with domain badges, word counts, and cleaned snippets.
 */
@Composable
private fun SearchResultCard(
    result: UnifiedOcrSearchResult,
    onClick: () -> Unit
) {
    val sourceInfo = getSourceInfo(result.sourceType)
    val isPartialMatch = result.score < 1.0
    val cleanSnippet = stripFtsMarkup(result.snippet)

    // Title logic:
    // - MEMO/TASK: use first line of cleaned snippet (their sourceTitle is generic)
    // - BOOX_NOTE: use sourceTitle if available (note title from index), fallback to snippet
    // - Events: use sourceTitle (event title)
    val displayTitle = when {
        result.sourceType == "MEMO" -> {
            cleanSnippet.lines().firstOrNull()?.trim()?.take(80) ?: "Handwritten memo"
        }
        result.sourceType == "TASK" -> {
            cleanSnippet.lines().firstOrNull()?.trim()?.take(80) ?: "Task"
        }
        !result.sourceTitle.isNullOrBlank() -> result.sourceTitle
        else -> cleanSnippet.lines().firstOrNull()?.trim()?.take(80) ?: "Untitled"
    }

    // For short content (≤5 words), snippet would be redundant with title — hide it
    val showSnippet = result.wordCount > 5 || (
        result.sourceType != "MEMO" && result.sourceType != "TASK"
    )

    // Domain badge text (skip MISC — not informative)
    val domainLabel = formatDomain(result.domain)

    // E-Ink distinction: exact matches = white bg + black border,
    // partial matches = offWhite bg + gray border (visually receded)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isPartialMatch) EInkTheme.Colors.offWhite else EInkTheme.Colors.background,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .border(
                if (isPartialMatch) 1.dp else 1.dp,
                if (isPartialMatch) EInkTheme.Colors.gray else EInkTheme.Colors.borderStrong,
                RoundedCornerShape(EInkTheme.Global.cornerRadius)
            )
            .noRippleClickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row {
            // Source type icon — vector or drawable
            if (sourceInfo.drawableResId != null) {
                Image(
                    painter = painterResource(id = sourceInfo.drawableResId),
                    contentDescription = sourceInfo.label,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = sourceInfo.vectorIcon ?: Icons.Default.Description,
                    contentDescription = sourceInfo.label,
                    modifier = Modifier.size(24.dp),
                    tint = if (isPartialMatch) EInkTheme.Colors.gray else EInkTheme.Colors.darkGray
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = displayTitle,
                    fontSize = EInkTheme.Typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPartialMatch) EInkTheme.Colors.darkGray else EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Metadata row: badges + source label + date + word count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPartialMatch) {
                        // Bold black pill badge — highly visible on e-ink
                        Box(
                            modifier = Modifier
                                .background(EInkTheme.Colors.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SIMILAR",
                                fontSize = EInkTheme.Typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EInkTheme.Colors.background
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    // Domain badge for Memos and Boox Notes
                    if (domainLabel != null && (result.sourceType == "MEMO" || result.sourceType == "BOOX_NOTE")) {
                        Box(
                            modifier = Modifier
                                .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = domainLabel,
                                fontSize = EInkTheme.Typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = EInkTheme.Colors.darkGray
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = sourceInfo.label,
                        fontSize = EInkTheme.Typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = "  \u2022  ",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                    Text(
                        text = formatSearchDate(result.sourceDate ?: ""),
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                    // Word count for Memos and Boox Notes
                    if (result.wordCount > 0 && (result.sourceType == "MEMO" || result.sourceType == "BOOX_NOTE")) {
                        Text(
                            text = "  \u2022  ${result.wordCount} words",
                            fontSize = EInkTheme.Typography.bodySmall,
                            color = EInkTheme.Colors.gray
                        )
                    }
                }

                // Snippet — only if it adds information beyond the title
                if (showSnippet) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = cleanSnippet,
                        fontSize = EInkTheme.Typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = EInkTheme.Colors.darkGray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Strip FTS4 markup tags from snippet text for clean display.
 */
private fun stripFtsMarkup(text: String): String =
    text.replace("<b>", "").replace("</b>", "").replace("...", "\u2026")

/**
 * Format domain string for display. Returns null for MISC (not informative).
 */
private fun formatDomain(domain: String?): String? = when {
    domain == null || domain == "MISC" -> null
    else -> domain.replace("_", " ")
}

/**
 * Source icon info — either a vector icon or a drawable resource ID.
 */
private data class SourceInfo(
    val label: String,
    val vectorIcon: ImageVector? = null,
    val drawableResId: Int? = null
)

/**
 * Maps sourceType string to a display icon and label.
 */
private fun getSourceInfo(sourceType: String): SourceInfo {
    return when (sourceType) {
        "MEMO" -> SourceInfo("Memo", vectorIcon = Icons.Default.Description)
        "TASK" -> SourceInfo("Task", vectorIcon = Icons.Default.CheckBox)
        "CALENDAR_EVENT" -> SourceInfo("Calendar Event", vectorIcon = Icons.Default.Event)
        "LOCAL_EVENT" -> SourceInfo("Local Event", vectorIcon = Icons.Default.CalendarToday)
        "BOOX_NOTE" -> SourceInfo("Boox Note", drawableResId = R.drawable.boox_b_letter)
        else -> SourceInfo(sourceType, vectorIcon = Icons.Default.Description)
    }
}

/**
 * Format date string for search results (compact format).
 * Input: "2025-01-15" → Output: "Jan 15, 2025"
 */
private fun formatSearchDate(dateStr: String): String {
    return try {
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            val month = when (parts[1]) {
                "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"; "04" -> "Apr"
                "05" -> "May"; "06" -> "Jun"; "07" -> "Jul"; "08" -> "Aug"
                "09" -> "Sep"; "10" -> "Oct"; "11" -> "Nov"; "12" -> "Dec"
                else -> parts[1]
            }
            val day = parts[2].toIntOrNull() ?: parts[2]
            "$month $day, ${parts[0]}"
        } else {
            dateStr
        }
    } catch (_: Exception) {
        dateStr
    }
}
