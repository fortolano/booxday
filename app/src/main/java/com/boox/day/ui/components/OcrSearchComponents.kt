package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.UnifiedOcrSearchResult
import com.boox.day.ocr.model.Domain
import com.boox.day.ocr.model.Intent
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * OCR SEARCH COMPONENTS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Self-contained search UI components for OCR full-text search.
 * These components are designed to be:
 * 1. Modular - can be used in any dialog/screen
 * 2. Easily removable - delete this file to remove search UI
 * 3. Relocatable - can be moved to a standalone search dialog
 *
 * To disable search: pass null for ocrRepository at call site.
 */

/**
 * Search input field with icon and clear button.
 * E-ink optimized: no animations, high contrast.
 */
@Composable
fun OcrSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    resultCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search icon
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            modifier = Modifier.size(20.dp),
            tint = EInkTheme.Colors.gray
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Text input
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                fontSize = EInkTheme.Typography.bodyMedium,
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
                            text = "Search handwritten notes...",
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.gray
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Status indicator
        when {
            isSearching -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = EInkTheme.Colors.darkGray
                )
            }
            query.length >= 2 -> {
                Text(
                    text = "$resultCount results",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.gray
                )
            }
        }

        // Clear button
        if (query.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear",
                modifier = Modifier
                    .size(20.dp)
                    .noRippleClickable { onQueryChange("") },
                tint = EInkTheme.Colors.gray
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// COMPACT SEARCH FILTER ROW (Jan 2026)
// ═══════════════════════════════════════════════════════════════════════════════
//
// Single 44dp row replacing the ~240dp SemanticFilterPanel.
// Combines: Search field + Category dropdown + Purpose dropdown + Expand button

/**
 * Primary domains for the Category dropdown (12 total).
 * Ordered with LDS first for user base.
 */
private val primaryDomains = listOf(
    Domain.LDS_LEADERSHIP, Domain.SPIRITUAL, Domain.PROFESSIONAL,
    Domain.EDUCATION, Domain.HEALTH, Domain.LIFE,
    Domain.TECH, Domain.FINANCE, Domain.TRAVEL, Domain.HOME,
    Domain.CREATIVE, Domain.BUSINESS
)

/**
 * Primary intents for the Purpose dropdown (11 total).
 * Ordered with LDS intents first for user base.
 */
private val primaryIntents = listOf(
    Intent.LDS_COUNCIL, Intent.LDS_MINISTERING,
    Intent.MEETING_NOTES, Intent.TODO_LIST, Intent.PLANNING,
    Intent.JOURNAL, Intent.BRAINSTORM, Intent.LECTURE_NOTES,
    Intent.REFERENCE, Intent.RESEARCH, Intent.GOAL_SETTING
)

/**
 * Compact search and filter row - single 44dp row replacing ~240dp filter panel.
 * E-ink optimized: dropdowns instead of scrolling chips, no animations.
 *
 * Layout: [Search field (38%)] [Category dropdown (26%)] [Purpose dropdown (26%)] [Expand/Collapse (44dp)]
 */
@Composable
fun CompactSearchFilterRow(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    resultCount: Int,
    selectedDomain: Domain?,
    onDomainSelected: (Domain?) -> Unit,
    selectedIntent: Intent?,
    onIntentSelected: (Intent?) -> Unit,
    domainCounts: Map<Domain, Int>,
    intentCounts: Map<Intent, Int>,
    isExpanded: Boolean,
    expandEnabled: Boolean,
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(EInkTheme.Global.touchTarget)  // 44dp
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search field (38% width) - compact version
        Row(
            modifier = Modifier
                .weight(0.38f)
                .height(EInkTheme.Global.touchTarget)
                .background(Color.White, RoundedCornerShape(6.dp))
                .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                modifier = Modifier.size(18.dp),
                tint = EInkTheme.Colors.gray
            )
            Spacer(modifier = Modifier.width(6.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontSize = EInkTheme.Typography.bodySmall,
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
                                text = "Search...",
                                fontSize = EInkTheme.Typography.bodySmall,
                                color = EInkTheme.Colors.gray
                            )
                        }
                        innerTextField()
                    }
                }
            )
            // Show spinner or result count
            when {
                isSearching -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                query.isNotEmpty() -> {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        modifier = Modifier
                            .size(16.dp)
                            .noRippleClickable { onQueryChange("") },
                        tint = EInkTheme.Colors.gray
                    )
                }
            }
        }

        // Category dropdown (26% width)
        CategoryDropdown(
            selectedDomain = selectedDomain,
            onDomainSelected = onDomainSelected,
            domainCounts = domainCounts,
            modifier = Modifier.weight(0.26f)
        )

        // Purpose dropdown (26% width)
        PurposeDropdown(
            selectedIntent = selectedIntent,
            onIntentSelected = onIntentSelected,
            intentCounts = intentCounts,
            modifier = Modifier.weight(0.26f)
        )

        // Expand/Collapse toggle button (44dp fixed)
        // When expanded: always enabled (to allow collapsing)
        // When collapsed: only enabled if a note is selected
        val buttonEnabled = isExpanded || expandEnabled
        Box(
            modifier = Modifier
                .size(EInkTheme.Global.touchTarget)
                .background(
                    if (buttonEnabled) Color.White else EInkTheme.Colors.offWhite,
                    RoundedCornerShape(6.dp)
                )
                .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(6.dp))
                .alpha(if (buttonEnabled) 1f else 0.4f)
                .then(
                    if (buttonEnabled) Modifier.noRippleClickable(onClick = onExpandToggle) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(22.dp),
                tint = EInkTheme.Colors.darkGray
            )
        }
    }
}

/**
 * Category dropdown - single-select from 12 primary domains.
 * E-ink optimized: uses DropdownMenu (popup-based), no animations.
 */
@Composable
private fun CategoryDropdown(
    selectedDomain: Domain?,
    onDomainSelected: (Domain?) -> Unit,
    domainCounts: Map<Domain, Int>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Trigger button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Global.touchTarget)
                .background(Color.White, RoundedCornerShape(6.dp))
                .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(6.dp))
                .noRippleClickable { expanded = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedDomain?.let { formatDomainLabel(it) } ?: "Category",
                fontSize = EInkTheme.Typography.bodySmall,
                color = if (selectedDomain != null) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Select category",
                modifier = Modifier.size(20.dp),
                tint = EInkTheme.Colors.darkGray
            )
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(EInkTheme.Colors.background)
                .border(1.dp, EInkTheme.Colors.primary)
                .heightIn(max = 300.dp)
        ) {
            // "All Categories" option (clears filter)
            DropdownMenuItem(
                onClick = {
                    onDomainSelected(null)
                    expanded = false
                }
            ) {
                Text(
                    text = "All Categories",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = if (selectedDomain == null) FontWeight.Bold else FontWeight.Normal,
                    color = EInkTheme.Colors.primary
                )
            }

            // Domain items with counts - sorted by count descending
            primaryDomains.sortedByDescending { domainCounts[it] ?: 0 }.forEach { domain ->
                val count = domainCounts[domain] ?: 0
                DropdownMenuItem(
                    onClick = {
                        onDomainSelected(domain)
                        expanded = false
                    }
                ) {
                    Text(
                        text = "${formatDomainLabel(domain)} ($count)",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = if (selectedDomain == domain) FontWeight.Bold else FontWeight.Normal,
                        color = EInkTheme.Colors.primary,
                        modifier = Modifier.alpha(if (count > 0) 1f else 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Purpose dropdown - single-select from 11 primary intents.
 * E-ink optimized: uses DropdownMenu (popup-based), no animations.
 */
@Composable
private fun PurposeDropdown(
    selectedIntent: Intent?,
    onIntentSelected: (Intent?) -> Unit,
    intentCounts: Map<Intent, Int>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Trigger button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Global.touchTarget)
                .background(Color.White, RoundedCornerShape(6.dp))
                .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(6.dp))
                .noRippleClickable { expanded = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedIntent?.let { formatIntentLabel(it) } ?: "Purpose",
                fontSize = EInkTheme.Typography.bodySmall,
                color = if (selectedIntent != null) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Select purpose",
                modifier = Modifier.size(20.dp),
                tint = EInkTheme.Colors.darkGray
            )
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(EInkTheme.Colors.background)
                .border(1.dp, EInkTheme.Colors.primary)
                .heightIn(max = 300.dp)
        ) {
            // "All Purposes" option (clears filter)
            DropdownMenuItem(
                onClick = {
                    onIntentSelected(null)
                    expanded = false
                }
            ) {
                Text(
                    text = "All Purposes",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = if (selectedIntent == null) FontWeight.Bold else FontWeight.Normal,
                    color = EInkTheme.Colors.primary
                )
            }

            // Intent items with counts - sorted by count descending
            primaryIntents.sortedByDescending { intentCounts[it] ?: 0 }.forEach { intent ->
                val count = intentCounts[intent] ?: 0
                DropdownMenuItem(
                    onClick = {
                        onIntentSelected(intent)
                        expanded = false
                    }
                ) {
                    Text(
                        text = "${formatIntentLabel(intent)} ($count)",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        fontWeight = if (selectedIntent == intent) FontWeight.Bold else FontWeight.Normal,
                        color = EInkTheme.Colors.primary,
                        modifier = Modifier.alpha(if (count > 0) 1f else 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Expanded reading mode content - full-screen note preview.
 * Shows larger preview with page navigation and action buttons.
 */
@Composable
fun ExpandedReadModeContent(
    noteTitle: String,
    thumbnailBitmap: ImageBitmap?,
    isLoadingThumbnail: Boolean,
    currentPageIndex: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    onCollapse: () -> Unit,
    onOpenInBooxNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header row (36dp): Back button | Note title | Page indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .noRippleClickable(onClick = onCollapse),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp),
                    tint = EInkTheme.Colors.primary
                )
            }

            // Note title (centered)
            Text(
                text = noteTitle,
                fontSize = EInkTheme.Typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            // Page indicator
            Text(
                text = "${currentPageIndex + 1}/$totalPages",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )
        }

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(EInkTheme.Colors.lightGray)
        )

        // Preview area (fills remaining space)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoadingThumbnail -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                thumbnailBitmap != null -> {
                    androidx.compose.foundation.Image(
                        bitmap = thumbnailBitmap,
                        contentDescription = "Note preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, EInkTheme.Colors.lightGray)
                    )
                }
                else -> {
                    Text(
                        text = "No preview available",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.gray
                    )
                }
            }
        }

        // Footer row (44dp): Prev | Open in Boox Notes | Next
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Global.touchTarget)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Previous page button
            Box(
                modifier = Modifier
                    .size(EInkTheme.Global.touchTarget)
                    .background(
                        if (currentPageIndex > 0) Color.White else EInkTheme.Colors.offWhite,
                        RoundedCornerShape(6.dp)
                    )
                    .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(6.dp))
                    .alpha(if (currentPageIndex > 0) 1f else 0.4f)
                    .then(
                        if (currentPageIndex > 0) Modifier.noRippleClickable { onPageChange(currentPageIndex - 1) } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous page",
                    modifier = Modifier.size(28.dp),
                    tint = EInkTheme.Colors.darkGray
                )
            }

            // Open in Boox Notes button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(EInkTheme.Global.touchTarget)
                    .padding(horizontal = 16.dp)
                    .background(EInkTheme.Colors.primary, RoundedCornerShape(6.dp))
                    .noRippleClickable(onClick = onOpenInBooxNotes),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Open in Boox Notes",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EInkTheme.Colors.background
                )
            }

            // Next page button
            Box(
                modifier = Modifier
                    .size(EInkTheme.Global.touchTarget)
                    .background(
                        if (currentPageIndex < totalPages - 1) Color.White else EInkTheme.Colors.offWhite,
                        RoundedCornerShape(6.dp)
                    )
                    .border(1.dp, EInkTheme.Colors.darkGray, RoundedCornerShape(6.dp))
                    .alpha(if (currentPageIndex < totalPages - 1) 1f else 0.4f)
                    .then(
                        if (currentPageIndex < totalPages - 1) Modifier.noRippleClickable { onPageChange(currentPageIndex + 1) } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next page",
                    modifier = Modifier.size(28.dp),
                    tint = EInkTheme.Colors.darkGray
                )
            }
        }
    }
}

/**
 * List of search results with note info and text snippets.
 */
@Composable
fun OcrSearchResultsList(
    results: List<UnifiedOcrSearchResult>,
    onResultClick: (UnifiedOcrSearchResult) -> Unit,
    selectedNoteId: String?,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) {
        // Empty state
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = EInkTheme.Colors.lightGray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Search your handwritten notes",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Type at least 2 characters",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.lightGray
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(results, key = { "${it.sourceId}_${it.pageId}" }) { result ->
                OcrSearchResultItem(
                    result = result,
                    isSelected = result.sourceId == selectedNoteId,
                    onClick = { onResultClick(result) }
                )
            }
        }
    }
}

/**
 * Single search result item showing note title, date, and matching snippet.
 */
@Composable
fun OcrSearchResultItem(
    result: UnifiedOcrSearchResult,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) EInkTheme.Colors.offWhite else Color.Transparent
            )
            .border(
                width = if (isSelected) EInkTheme.Global.borderWidth else 1.dp,
                color = if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray
            )
            .noRippleClickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column {
            // Note title (fallback to sourceId if no title)
            Text(
                text = result.sourceTitle ?: result.sourceId,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Date and source type
            Text(
                text = "${result.sourceType} • ${formatSearchDate(result.sourceDate ?: "")}",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Snippet with matching text
            Text(
                text = "...${result.snippet}...",
                fontSize = EInkTheme.Typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = EInkTheme.Colors.darkGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Format date string for search results (compact format).
 */
private fun formatSearchDate(dateStr: String): String {
    return try {
        // Input: "2025-01-15" -> Output: "Jan 15, 2025"
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            val month = when (parts[1]) {
                "01" -> "Jan"
                "02" -> "Feb"
                "03" -> "Mar"
                "04" -> "Apr"
                "05" -> "May"
                "06" -> "Jun"
                "07" -> "Jul"
                "08" -> "Aug"
                "09" -> "Sep"
                "10" -> "Oct"
                "11" -> "Nov"
                "12" -> "Dec"
                else -> parts[1]
            }
            val day = parts[2].toIntOrNull() ?: parts[2]
            "$month $day, ${parts[0]}"
        } else {
            dateStr
        }
    } catch (e: Exception) {
        dateStr
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SEMANTIC FILTER COMPONENTS (Legacy - removed in Jan 2026 refactoring)
// ═══════════════════════════════════════════════════════════════════════════════
// SemanticFilterPanel and FilterChip have been replaced by CompactSearchFilterRow
// which provides the same functionality in a single 44dp row using dropdowns.

/**
 * List of notes filtered by domain/intent.
 */
@Composable
fun SemanticFilteredNotesList(
    notes: List<BooxNoteIndex>,
    onNoteClick: (BooxNoteIndex) -> Unit,
    selectedNoteId: String?,
    noteDomains: Map<String, Domain?>,  // noteId -> Domain
    noteSecondaryDomains: Map<String, Domain?> = emptyMap(),  // A2: noteId -> Secondary Domain (for Mixed)
    noteIntents: Map<String, List<Intent>>,  // noteId -> Intents
    modifier: Modifier = Modifier
) {
    if (notes.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = EInkTheme.Colors.lightGray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No notes match your filters",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Try selecting different categories",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.lightGray
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(notes, key = { it.uniqueId }) { note ->
                SemanticFilteredNoteItem(
                    note = note,
                    isSelected = note.uniqueId == selectedNoteId,
                    domain = noteDomains[note.uniqueId],
                    secondaryDomain = noteSecondaryDomains[note.uniqueId],  // A2: Mixed category
                    intents = noteIntents[note.uniqueId] ?: emptyList(),
                    onClick = { onNoteClick(note) }
                )
            }
        }
    }
}

/**
 * Single note item showing title, date, and semantic badges.
 */
@Composable
fun SemanticFilteredNoteItem(
    note: BooxNoteIndex,
    isSelected: Boolean,
    domain: Domain?,
    secondaryDomain: Domain? = null,  // A2: For Mixed category display
    intents: List<Intent>,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) EInkTheme.Colors.offWhite else Color.Transparent
            )
            .border(
                width = if (isSelected) EInkTheme.Global.borderWidth else 1.dp,
                color = if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray
            )
            .noRippleClickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column {
            // Note title
            Text(
                text = note.title ?: note.uniqueId,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Date and page count
            Text(
                text = "${formatSearchDate(note.originalDate)} • ${note.pageCount} pages",
                fontSize = EInkTheme.Typography.bodySmall,
                color = EInkTheme.Colors.gray
            )

            // Domain/Intent badges - limit to 3 total to prevent layout shift
            if (domain != null || intents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))

                // Build list of badges: domain first, then intents
                val badges = mutableListOf<String>()
                domain?.let { badges.add(formatDomainLabel(it)) }
                intents.forEach { badges.add(formatIntentLabel(it)) }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    badges.take(3).forEach { badgeText ->
                        SmallBadge(text = badgeText)
                    }
                }
            }
        }
    }
}

/**
 * Small badge for domain/intent display.
 */
@Composable
private fun SmallBadge(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = EInkTheme.Colors.offWhite,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = EInkTheme.Typography.labelSmall,
            color = EInkTheme.Colors.darkGray
        )
    }
}

/**
 * Format domain enum to readable label.
 */
internal fun formatDomainLabel(domain: Domain): String {
    return when (domain) {
        // Merged domains
        Domain.EDUCATION -> "Education"
        Domain.PROFESSIONAL -> "Professional"
        Domain.LIFE -> "Life"
        // Distinct domains
        Domain.TEACHING -> "Teaching"
        Domain.TECH -> "Tech"
        Domain.FINANCE -> "Finance"
        Domain.HEALTH -> "Health"
        Domain.SPIRITUAL -> "Spiritual"
        Domain.CREATIVE -> "Creative"
        Domain.TRAVEL -> "Travel"
        Domain.LEGAL -> "Legal"
        Domain.HOME -> "Home"
        Domain.SHOPPING -> "Shopping"
        Domain.BUSINESS -> "Business"
        Domain.STRATEGY -> "Strategy"
        Domain.SOCIAL_MEDIA -> "Social Media"
        Domain.ENTREPRENEURSHIP -> "Startup"
        Domain.LDS_LEADERSHIP -> "Church"
        Domain.FOOD -> "Food"
        Domain.COMMUNITY -> "Community"
        Domain.PET -> "Pets"
        Domain.MISC -> "Other"
    }
}

/**
 * Format intent enum to readable label.
 */
internal fun formatIntentLabel(intent: Intent): String {
    return when (intent) {
        Intent.TODO_LIST -> "To-Do"
        Intent.PLANNING -> "Planning"
        Intent.GOAL_SETTING -> "Goals"
        Intent.DAILY_REVIEW -> "Review"
        Intent.FOCUS_SESSION -> "Focus"
        Intent.MEETING_NOTES -> "Meeting"
        Intent.CALL_LOG -> "Call"
        Intent.BRAINSTORM -> "Ideas"
        Intent.JOURNAL -> "Journal"
        Intent.LECTURE_NOTES -> "Lecture"
        Intent.LESSON_PLAN -> "Lesson"
        Intent.GRADING -> "Grading"
        Intent.CLASSROOM_MGMT -> "Class Mgmt"
        Intent.CURRICULUM_DEV -> "Curriculum"
        Intent.PARENT_COMM -> "Parent"
        Intent.IEP_504 -> "IEP/504"
        Intent.FACULTY_MEETING -> "Faculty"
        Intent.STUDENT_RECORD -> "Student"
        Intent.ADVISING -> "Advising"
        Intent.STUDY_PLAN -> "Study"
        Intent.ASSIGNMENT_WORK -> "Assignment"
        Intent.READING_NOTES -> "Reading"
        Intent.LAB_NOTES -> "Lab"
        Intent.THESIS_WORK -> "Thesis"
        Intent.COURSE_PLANNING -> "Course"
        Intent.REFERENCE -> "Reference"
        Intent.INSTRUCTIONS -> "Instructions"
        Intent.CONTACT_INFO -> "Contacts"
        Intent.EXPENSE_LOG -> "Expenses"
        Intent.BUDGET -> "Budget"
        Intent.DRAFT_MESSAGE -> "Draft"
        Intent.TALKING_POINTS -> "Talking Pts"
        Intent.PROS_CONS -> "Pros/Cons"
        Intent.COMPARISON -> "Compare"
        Intent.ACTION_REVIEW -> "Review"
        Intent.TROUBLESHOOTING -> "Troubleshoot"
        Intent.RESEARCH -> "Research"
        Intent.LEADERSHIP_NOTES -> "Leadership"
        Intent.PEOPLE_MANAGEMENT -> "People Mgmt"
        Intent.STRATEGY_PLANNING -> "Strategy"
        Intent.BUSINESS_MODEL -> "Biz Model"
        Intent.METRICS_TRACKING -> "Metrics"
        Intent.STAKEHOLDER_UPDATE -> "Stakeholder"
        Intent.STARTUP_IDEAS -> "Startup"
        Intent.FUNDRAISING -> "Fundraising"
        Intent.PRODUCT_DEV -> "Product"
        Intent.CONTENT_PLAN -> "Content"
        Intent.SOCIAL_ANALYTICS -> "Analytics"
        Intent.CAMPAIGN_NOTES -> "Campaign"
        Intent.LDS_COUNCIL -> "Council"
        Intent.LDS_MINISTERING -> "Ministering"
        Intent.CREATIVE_WRITING -> "Writing"
        Intent.RECIPE -> "Recipe"
        Intent.PACKING_LIST -> "Packing"
        Intent.DEVOTIONAL -> "Devotional"
        Intent.TRANSCRIPT -> "Transcript"
        Intent.CHEAT_SHEET -> "Cheat Sheet"
        Intent.QUOTE_SNIPPET -> "Quote"
        Intent.WISHLIST -> "Wishlist"
        Intent.FREEFORM -> "Notes"
    }
}
