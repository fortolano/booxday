package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.api.AiJob
import com.boox.day.api.AiModelSettings
import com.boox.day.api.CatalogModel
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.OpenRouterModelCatalog
import com.boox.day.data.datastore.AiModelChoice
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.ScrollMoreIndicator
import com.boox.day.ui.components.description
import com.boox.day.ui.components.label
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The pinned row's key: Boox Day's choice (no pick saved for the job). */
private const val BOOX_DAY_CHOICE = ""

/**
 * Width that fits the Show filters (about 330 dp) and the longest Sort label ("Sort: Price: high
 * to low", about 200 dp) on one row: the Go 10.3 list is about 626 dp wide, the Go 6 about 507 dp.
 */
private val FILTERS_AND_SORT_ONE_ROW = 560.dp

/**
 * One AI job's model list (Oct 2026, plan step d), opened from the AI Models card: OpenRouter's
 * models that can do the job, with search, Show, Sort and at most 200 rows
 * ([OpenRouterModelCatalog]). Boox Day's choice is pinned first; choosing it removes the pick.
 * Use Model saves the pick with the recommended reasoning level and output tokens (the settings
 * step comes in plan step e).
 *
 * E-ink: Popup, no ripple, no animation, rows at least 44 dp, inverted selection.
 */
@Composable
fun ModelPickerPopup(job: AiJob, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val snapshot = OpenRouterModelCatalog.snapshot
    val loading = OpenRouterModelCatalog.loading
    val loadError = OpenRouterModelCatalog.error
    val currentPick = GlobalAppSettings.current.aiModels.forJob(job)
    val currentKey = currentPick?.id ?: BOOX_DAY_CHOICE

    var query by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(OpenRouterModelCatalog.Show.ALL) }
    var sort by remember { mutableStateOf(OpenRouterModelCatalog.Sort.MOST_POPULAR) }
    var selectedKey by remember { mutableStateOf(currentKey) }

    // A list older than a day is downloaded again when the list opens
    LaunchedEffect(Unit) { OpenRouterModelCatalog.refresh(force = false) }

    val today = remember { LocalDate.now() }
    val result = remember(snapshot, query, show, sort) {
        snapshot?.let {
            OpenRouterModelCatalog.listFor(job, it.models, show, sort, query, it.allowedIds?.toSet(), today)
        }
    }
    val inUseId = ModelConfigProvider.getChain(job).first().id
    val defaultName = ModelConfigProvider.defaultModel(job).displayName

    fun useSelected() {
        val chosen: CatalogModel? = if (selectedKey == BOOX_DAY_CHOICE) null
        else snapshot?.models?.firstOrNull { it.id == selectedKey }
        if (selectedKey != BOOX_DAY_CHOICE && chosen == null) return
        val choice = chosen?.let {
            AiModelChoice(
                id = it.id,
                name = it.name,
                lab = it.lab,
                reasoning = AiModelSettings.recommendedReasoning(job, it.reasoning),
                maxOutputTokens = null, // the recommended value for the reasoning level
                reasoningOptions = it.reasoning,
                modelMaxOutputTokens = it.maxOutput,
                contextLength = it.contextLength,
                inputs = it.inputs
            )
        }
        val settings = GlobalAppSettings.current
        KvProxy(context).setAppSettings(settings.copy(aiModels = settings.aiModels.withJob(job, choice)))
        onDismiss()
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)
                    .fillMaxHeight(EInkTheme.PanelDialog.heightFraction)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupOverlay.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
            ) {
                // ── Header ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = job.label,
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(EInkTheme.Global.touchTarget)
                            .noRippleClickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = EInkTheme.Colors.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Text(text = job.description, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.darkGray)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            loading -> "Updating the model list..."
                            snapshot != null -> "Updated ${agoText(snapshot.fetchedAtMs)}"
                            else -> ""
                        },
                        fontSize = EInkTheme.Typography.bodySmall,
                        color = EInkTheme.Colors.darkGray,
                        modifier = Modifier.weight(1f)
                    )
                    EinkSecondaryButton(
                        text = "Refresh",
                        enabled = !loading,
                        onClick = { scope.launch { OpenRouterModelCatalog.refresh(force = true) } }
                    )
                }

                // ── Search ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EInkTheme.Global.touchTarget)
                        .border(1.dp, EInkTheme.Colors.darkGray)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = EInkTheme.Colors.darkGray, modifier = Modifier.size(20.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Search models...", fontSize = EInkTheme.Typography.bodyLarge, color = EInkTheme.Colors.gray)
                            }
                            inner()
                        }
                    )
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(EInkTheme.Global.touchTarget)
                                .noRippleClickable { query = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = EInkTheme.Colors.darkGray, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // ── Show, with Sort at the right of the same row (owner, Oct 2026). Where both
                // don't fit (Go 6), Sort goes under the filters, still at the right ──
                val showChips: @Composable () -> Unit = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                    ) {
                        Text("Show:", fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
                        for (option in OpenRouterModelCatalog.Show.entries) {
                            OptionChip(text = showLabel(option), selected = show == option) { show = option }
                        }
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= FILTERS_AND_SORT_ONE_ROW) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            showChips()
                            Spacer(modifier = Modifier.weight(1f))
                            SortMenu(sort = sort, onSort = { sort = it })
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
                            showChips()
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                SortMenu(sort = sort, onSort = { sort = it })
                            }
                        }
                    }
                }

                // ── List ──
                val listState = rememberLazyListState()
                val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        item(key = "boox-day-choice") {
                            ModelRow(
                                title = "Boox Day's choice: $defaultName",
                                detail = "Tested models, with backups. Follows app updates.",
                                selected = selectedKey == BOOX_DAY_CHOICE,
                                inUse = currentPick == null || inUseId != currentPick.id
                            ) { selectedKey = BOOX_DAY_CHOICE }
                        }
                        when {
                            result != null && result.rows.isNotEmpty() -> {
                                items(result.rows, key = { it.id }) { model ->
                                    ModelRow(
                                        title = model.name,
                                        detail = OpenRouterModelCatalog.priceLine(model),
                                        selected = selectedKey == model.id,
                                        inUse = currentPick != null && model.id == currentPick.id && inUseId == currentPick.id
                                    ) { selectedKey = model.id }
                                }
                                item(key = "count") {
                                    Note(
                                        if (result.matching > result.rows.size) {
                                            "Showing ${result.rows.size} of ${result.matching} models. Search to find the others."
                                        } else {
                                            "Showing ${result.rows.size} models"
                                        }
                                    )
                                }
                            }
                            result != null -> item(key = "empty") { Note(emptyText(job, show, query)) }
                            loading -> item(key = "loading") { Note("Loading models...") }
                            else -> item(key = "error") {
                                Note(loadError ?: "Can't load the model list. Check the connection, then tap Refresh.")
                            }
                        }
                    }
                    if (canScrollForward) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) { ScrollMoreIndicator() }
                    }
                }

                // ── Buttons ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    EinkSecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(0.4f))
                    ActionButton(
                        text = "Use Model",
                        enabled = selectedKey != currentKey,
                        onClick = { useSelected() },
                        modifier = Modifier.weight(0.6f)
                    )
                }
            }
        }
    }
}

/** Two lines: name (and "In use"), then lab and price. Selected = black with white text. */
@Composable
private fun ModelRow(title: String, detail: String, selected: Boolean, inUse: Boolean, onClick: () -> Unit) {
    val foreground = if (selected) EInkTheme.Selection.foreground else EInkTheme.Colors.primary
    val secondary = if (selected) EInkTheme.Selection.foreground else EInkTheme.Colors.darkGray
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EInkTheme.Global.touchTarget)
            .background(if (selected) EInkTheme.Selection.background else EInkTheme.Colors.background)
            .noRippleClickable { onClick() }
            .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = foreground,
                modifier = Modifier.weight(1f)
            )
            if (inUse) {
                Text(text = "In use", fontSize = EInkTheme.Typography.bodySmall, fontWeight = FontWeight.Bold, color = foreground)
            }
        }
        Text(text = detail, fontSize = EInkTheme.Typography.bodyMedium, color = secondary)
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        color = EInkTheme.Colors.darkGray,
        modifier = Modifier.padding(vertical = EInkTheme.Global.paddingSmall, horizontal = EInkTheme.Global.paddingSmall)
    )
}

/** A Show choice: outlined, black with white text when chosen (like IntervalChip). */
@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(EInkTheme.Global.touchTarget)
            .background(if (selected) EInkTheme.Colors.primary else EInkTheme.Colors.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = EInkTheme.Global.paddingSmall),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = EInkTheme.Preferences.chipFontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) EInkTheme.Colors.white else EInkTheme.Colors.primary
        )
    }
}

/** "Sort: Most popular" with its four orders in a menu (like LocalCalendarDropdown). */
@Composable
private fun SortMenu(sort: OpenRouterModelCatalog.Sort, onSort: (OpenRouterModelCatalog.Sort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .height(EInkTheme.Global.touchTarget)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .noRippleClickable { expanded = true }
                .padding(start = EInkTheme.Global.paddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Sort: ${sortLabel(sort)}", fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose the order", tint = EInkTheme.Colors.primary, modifier = Modifier.size(24.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(EInkTheme.Colors.background)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
        ) {
            for (option in OpenRouterModelCatalog.Sort.entries) {
                DropdownMenuItem(onClick = {
                    onSort(option)
                    expanded = false
                }) {
                    Text(
                        text = sortLabel(option),
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal,
                        color = EInkTheme.Colors.primary
                    )
                }
            }
        }
    }
}

private fun showLabel(show: OpenRouterModelCatalog.Show): String = when (show) {
    OpenRouterModelCatalog.Show.ALL -> "All"
    OpenRouterModelCatalog.Show.TOP_PAID -> "Top paid"
    OpenRouterModelCatalog.Show.TOP_FREE -> "Top free"
    OpenRouterModelCatalog.Show.TOP_REASONING -> "Top reasoning"
}

private fun sortLabel(sort: OpenRouterModelCatalog.Sort): String = when (sort) {
    OpenRouterModelCatalog.Sort.MOST_POPULAR -> "Most popular"
    OpenRouterModelCatalog.Sort.NEWEST -> "Newest first"
    OpenRouterModelCatalog.Sort.PRICE_LOW_TO_HIGH -> "Price: low to high"
    OpenRouterModelCatalog.Sort.PRICE_HIGH_TO_LOW -> "Price: high to low"
}

private fun emptyText(job: AiJob, show: OpenRouterModelCatalog.Show, query: String): String = when {
    query.isNotBlank() -> "No model that can do this job matches \"${query.trim()}\"."
    show == OpenRouterModelCatalog.Show.TOP_FREE -> "No free models can do this job."
    else -> "No models can do this job right now."
}

private fun agoText(timeMs: Long): String {
    val minutes = (System.currentTimeMillis() - timeMs) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}
