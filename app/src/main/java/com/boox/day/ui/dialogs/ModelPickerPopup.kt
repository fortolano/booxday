package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.api.AiJob
import com.boox.day.api.AiModelSettings
import com.boox.day.api.AiPickStatus
import com.boox.day.api.CatalogModel
import com.boox.day.api.ModelConfigProvider
import com.boox.day.api.OpenRouterAccount
import com.boox.day.api.OpenRouterModelCatalog
import com.boox.day.data.datastore.AiModelChoice
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.KvProxy
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.ScrollMoreIndicator
import com.boox.day.ui.components.description
import com.boox.day.ui.components.keyboardHeight
import com.boox.day.ui.components.label
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/** The pinned row's key: BooxDay's choice (no pick saved for the job). */
private const val BOOX_DAY_CHOICE = ""

/**
 * Width that fits the Show filters (about 330 dp) and the longest Sort label ("Sort: Price: high
 * to low", about 200 dp) on one row: the Go 10.3 list is about 626 dp wide, the Go 6 about 507 dp.
 */
private val FILTERS_AND_SORT_ONE_ROW = 560.dp

/**
 * One AI job's model list (Oct 2026, plan step d), opened from the AI Models card: OpenRouter's
 * models that can do the job, with search, Show, Sort and at most 200 rows
 * ([OpenRouterModelCatalog]). BooxDay's choice is pinned first; Use Model on it removes the
 * pick. Next on a model opens its settings step ([ModelSettingsStep], plan step e), whose Use
 * Model saves the pick.
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

    // The settings step (plan step e) for this model, or null while the list shows
    var settingsFor by remember { mutableStateOf<CatalogModel?>(null) }

    /** Saves the job's pick (null = BooxDay's choice) and closes. */
    fun save(choice: AiModelChoice?) {
        // A new pick starts without the old one's failure note (plan step f)
        if (choice?.id != currentPick?.id) AiPickStatus.clear(job)
        val settings = GlobalAppSettings.current
        KvProxy(context).setAppSettings(settings.copy(aiModels = settings.aiModels.withJob(job, choice)))
        onDismiss()
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        // The Popup gets no keyboard room of its own: leave the keyboard's height free at the
        // bottom and use the rest, so the search field and the buttons stay above the keyboard
        val keyboard = keyboardHeight()
        Box(modifier = Modifier.fillMaxSize().padding(bottom = keyboard), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)
                    .fillMaxHeight(if (keyboard > 0.dp) 0.96f else EInkTheme.PanelDialog.heightFraction)
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
                val settingsTarget = settingsFor
                if (settingsTarget != null) {
                    ModelSettingsStep(
                        job = job,
                        model = settingsTarget,
                        saved = currentPick?.takeIf { it.id == settingsTarget.id },
                        onBack = { settingsFor = null },
                        onUse = { save(it) }
                    )
                } else {
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
                                title = "BooxDay's choice: $defaultName",
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

                // ── Buttons: BooxDay's choice saves at once; a model goes to its settings ──
                val selectedModel = snapshot?.models?.firstOrNull { it.id == selectedKey }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    EinkSecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(0.4f))
                    if (selectedKey == BOOX_DAY_CHOICE) {
                        ActionButton(
                            text = "Use Model",
                            enabled = currentKey != BOOX_DAY_CHOICE,
                            onClick = { save(null) },
                            modifier = Modifier.weight(0.6f)
                        )
                    } else {
                        ActionButton(
                            text = "Next",
                            enabled = selectedModel != null,
                            onClick = { settingsFor = selectedModel },
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                }
                }
            }
        }
    }
}

/**
 * The settings step (Oct 2026, plan step e): reasoning effort from the model's own levels, with
 * the job's recommended one selected, and max output tokens, which follow the level until the
 * user sets them ([AiModelSettings]). A pick opened again starts from its saved settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ModelSettingsStep(
    job: AiJob,
    model: CatalogModel,
    saved: AiModelChoice?,
    onBack: () -> Unit,
    onUse: (AiModelChoice) -> Unit
) {
    val options = model.reasoning
    val choices = remember(model) { AiModelSettings.reasoningChoices(options) }
    val recommendedLevel = remember(model) { AiModelSettings.recommendedReasoning(job, options) }
    var level by remember(model) { mutableStateOf(saved?.reasoning?.takeIf { it in choices } ?: recommendedLevel) }
    val recommendedTokens = AiModelSettings.recommendedMaxOutputTokens(job, level, options, model.maxOutput)
    // Set by the user: kept when the level changes; otherwise the recommendation is used
    var userTokens by remember(model) { mutableStateOf(saved?.maxOutputTokens) }
    val tokens = userTokens ?: recommendedTokens
    val steps = remember(model, recommendedTokens) { AiModelSettings.outputTokenSteps(model.maxOutput, recommendedTokens) }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        Text(text = model.name, fontSize = EInkTheme.Typography.titleSmall, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.primary)
        Text(text = OpenRouterModelCatalog.priceLine(model), fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.darkGray)
        if (OpenRouterAccount.isFreeOnly() && !model.isFree) {
            Text(
                text = "Your account has no credit: this model won't answer until you add credit. BooxDay's free models answer meanwhile.",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
        }

        // ── Reasoning effort ──
        SectionTitle("Reasoning effort")
        when {
            choices.isNotEmpty() -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                    verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
                ) {
                    for (choice in choices) {
                        OptionChip(text = levelLabel(choice), selected = level == choice) { level = choice }
                    }
                }
                recommendedLevel?.let {
                    Text(
                        text = "Recommended for ${job.label}: ${levelLabel(it)}.",
                        fontSize = EInkTheme.Typography.bodyMedium,
                        color = EInkTheme.Colors.darkGray
                    )
                }
                Text(
                    text = "More reasoning can help hard jobs, but it's slower and uses more tokens.",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
            }
            options.supported && options.mandatory -> Note("This model always reasons; it can't be turned off.")
            else -> Note("This model answers without a reasoning step.")
        }

        // ── Max output tokens ──
        SectionTitle("Max output tokens")
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(Icons.Default.Remove, "Fewer tokens", enabled = tokens > steps.first()) {
                userTokens = AiModelSettings.stepOutputTokens(steps, tokens, up = false)
            }
            Text(
                text = String.format(Locale.US, "%,d", tokens),
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(110.dp)
            )
            StepButton(Icons.Default.Add, "More tokens", enabled = tokens < steps.last()) {
                userTokens = AiModelSettings.stepOutputTokens(steps, tokens, up = true)
            }
        }
        val recommendedText = String.format(Locale.US, "%,d", recommendedTokens)
        if (tokens != recommendedTokens) {
            Row(
                modifier = Modifier
                    .heightIn(min = EInkTheme.Global.touchTarget)
                    .noRippleClickable { userTokens = null },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recommended: $recommendedText",
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    textDecoration = TextDecoration.Underline
                )
            }
        } else {
            Text(
                text = "Recommended: $recommendedText. Reasoning uses part of this limit.",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.darkGray
            )
        }
        if (tokens < recommendedTokens) {
            Text(
                text = "Lower than recommended: answers may be cut off.",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
        }
        val limits = listOfNotNull(
            model.contextLength?.let { String.format(Locale.US, "Can read up to %,d tokens.", it) },
            model.maxOutput?.let { String.format(Locale.US, "Can write up to %,d.", it) }
        )
        if (limits.isNotEmpty()) {
            Text(text = limits.joinToString(" "), fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        EinkSecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.weight(0.4f))
        ActionButton(
            text = "Use Model",
            onClick = {
                onUse(
                    AiModelChoice(
                        id = model.id,
                        name = model.name,
                        lab = model.lab,
                        reasoning = level,
                        // Only a value the user set; otherwise it keeps following the recommendation
                        maxOutputTokens = userTokens?.takeIf { it != recommendedTokens },
                        reasoningOptions = options,
                        modelMaxOutputTokens = model.maxOutput,
                        contextLength = model.contextLength,
                        inputs = model.inputs
                    )
                )
            },
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.primary)
}

/** A 44 dp − or + button. */
@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(EInkTheme.Global.touchTarget)
            .border(
                EInkTheme.Global.borderWidth,
                if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
                RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .noRippleClickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.lightGray,
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun levelLabel(level: String): String = when (level) {
    "none", AiModelSettings.OFF -> "Off"
    AiModelSettings.ON -> "On"
    "minimal" -> "Minimal"
    "low" -> "Low"
    "medium" -> "Medium"
    "high" -> "High"
    "xhigh" -> "Extra high"
    "max" -> "Max"
    else -> level.replaceFirstChar { it.titlecase(Locale.US) }
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
