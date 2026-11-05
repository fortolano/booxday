package com.boox.day.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.datastore.A4_HEIGHT
import com.boox.day.data.datastore.A4_WIDTH
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.data.db.DayPageRepository
import com.boox.day.data.db.KvProxy
import com.boox.day.editor.ui.MonthGrid
import com.boox.day.io.ExportChoices
import com.boox.day.io.ExportFolders
import com.boox.day.io.ExportKind
import com.boox.day.io.ExportPreview
import com.boox.day.io.ExportRun
import com.boox.day.io.ExportScope
import com.boox.day.io.ExportTarget
import com.boox.day.io.ExportWhat
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.RadioOption
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.LayoutProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Past this the dialog would only stretch: on a 13.3 inch screen 85% is wider than its content needs. */
private val MAX_WIDTH = 700.dp

private val SMALL_PREVIEW_WIDTH = 124.dp
private val LARGE_PREVIEW_WIDTH = 130.dp

private const val PREVIEW_PAUSE_MS = 150L

/**
 * The Export dialog (Nov 2026, Phase 1B): choose what and how, see the first page, save or share.
 *
 * @param date The day that's open; null outside the calendar, where only the page can be exported.
 * @param dayPageIds The pages of that day, in order.
 */
@Composable
fun ExportDialog(
    date: String?,
    currentPageId: String,
    dayPageIds: List<String>,
    onExport: (ExportRun.Job) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val openDay = remember(date) { date?.let(LocalDate::parse) ?: LocalDate.now() }
    val firstDay = GlobalAppSettings.current.firstDayOfWeek
    val hasPages = dayPageIds.size > 1

    var choices by remember {
        mutableStateOf(GlobalAppSettings.current.exportChoices.let { saved ->
            when {
                date == null -> saved.copy(what = ExportWhat.PAGE, kind = if (saved.kind == ExportKind.MARKDOWN) ExportKind.PDF else saved.kind)
                saved.what == ExportWhat.PAGE && !hasPages -> saved.copy(what = ExportWhat.DAY)
                saved.what == ExportWhat.PAGE && saved.kind == ExportKind.MARKDOWN -> saved.copy(what = ExportWhat.DAY)
                else -> saved
            }
        })
    }
    val week = remember(openDay, firstDay) { ExportScope.weekOf(openDay, firstDay) }
    var from by remember { mutableStateOf(week.start) }
    var to by remember { mutableStateOf(week.endInclusive) }
    var picking by remember { mutableStateOf<DateField?>(null) }

    var target by remember { mutableStateOf<ExportTarget?>(null) }
    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }
    var canExport by remember { mutableStateOf(false) }
    var folderLabel by remember(choices.kind) { mutableStateOf(ExportFolders.label(context, choices.kind)) }

    val rangeOk = choices.what != ExportWhat.DATES || ExportScope.isValid(from, to)
    val previewWidth = if (LayoutProfiles.isSmallTablet) SMALL_PREVIEW_WIDTH else LARGE_PREVIEW_WIDTH
    val previewPx = with(LocalDensity.current) { previewWidth.roundToPx() }

    // What each choice covers, and its picture; a new choice replaces the one being drawn
    LaunchedEffect(choices, from, to) {
        if (!rangeOk) {
            target = null
            canExport = false
            summary = null
            picture = null
            return@LaunchedEffect
        }
        delay(PREVIEW_PAUSE_MS)
        val chosen = withContext(Dispatchers.IO) {
            val repository = DayPageRepository(context)
            when (choices.what) {
                ExportWhat.PAGE -> ExportTarget.Page(currentPageId)
                ExportWhat.DAY -> ExportTarget.DayPages(date ?: openDay.toString(), dayPageIds)
                ExportWhat.WEEK -> ExportScope.resolve(repository, week.start, week.endInclusive)
                ExportWhat.DATES -> ExportScope.resolve(repository, from, to)
            }
        }
        val view = runCatching { ExportPreview.make(context, chosen, choices, previewPx) }.getOrNull()
        target = chosen
        picture = view?.picture?.asImageBitmap()
        summary = view?.summary
        canExport = view?.canExport == true
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            runCatching { ExportFolders.choose(context, choices.kind, tree) }
                .onSuccess { folderLabel = ExportFolders.label(context, choices.kind) }
                .onFailure {
                    SnackState.globalSnackFlow.tryEmit(
                        SnackConf(text = "That folder can't be used. Please choose another one.", duration = SnackConf.DURATION_ERROR)
                    )
                }
        }
    }

    fun finish(share: Boolean) {
        val chosen = target ?: return
        KvProxy(context).setAppSettings(GlobalAppSettings.current.copy(exportChoices = choices))
        onExport(ExportRun.Job(chosen, choices, share))
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ExportDialogContent(
                modifier = Modifier
                    .fillMaxWidth(EInkTheme.PanelDialog.widthFraction)
                    .widthIn(max = MAX_WIDTH),
                ui = ExportDialogUi(
                    choices = choices,
                    showPage = hasPages && date != null && choices.kind != ExportKind.MARKDOWN,
                    showDays = date != null,
                    from = dayLabel(from, openDay),
                    to = dayLabel(to, openDay),
                    rangeProblem = if (rangeOk) null else "Choose up to ${ExportScope.MAX_DAYS} days.",
                    summary = summary,
                    picture = picture,
                    previewWidth = previewWidth,
                    folder = folderLabel,
                    canExport = canExport && rangeOk
                ),
                actions = ExportDialogActions(
                    change = { new ->
                        choices = if (new.kind == ExportKind.MARKDOWN && new.what == ExportWhat.PAGE) new.copy(what = ExportWhat.DAY) else new
                    },
                    pickDate = { picking = it },
                    changeFolder = { pickFolder.launch(null) },
                    cancel = onDismiss,
                    share = { finish(share = true) },
                    save = { finish(share = false) }
                )
            )
        }
        picking?.let { field ->
            DatePopup(
                selected = if (field == DateField.FROM) from else to,
                onPick = { day ->
                    if (field == DateField.FROM) from = day else to = day
                    if (to.isBefore(from)) {
                        val first = to
                        to = from
                        from = first
                    }
                    picking = null
                },
                onDismiss = { picking = null }
            )
        }
    }
}

internal enum class DateField { FROM, TO }

/** "Mon 26 Oct"; with the year when it isn't the open day's. */
private fun dayLabel(day: LocalDate, openDay: LocalDate): String {
    val pattern = if (day.year == openDay.year) "EEE d MMM" else "EEE d MMM yyyy"
    return day.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
}

@Composable
private fun DatePopup(selected: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .width(EInkTheme.ExportDialog.datePopupWidth)
                .background(EInkTheme.ModernDialog.background, shape)
                .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, shape)
                .padding(EInkTheme.Global.padding),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
        ) {
            MonthGrid(selected = selected, today = LocalDate.now(), onPick = onPick)
            EinkSecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** What the dialog shows, apart from how it's worked out, so the render test can draw it. */
internal data class ExportDialogUi(
    val choices: ExportChoices,
    val showPage: Boolean,
    val showDays: Boolean,
    val from: String,
    val to: String,
    val rangeProblem: String?,
    val summary: String?,
    val picture: ImageBitmap?,
    val previewWidth: Dp,
    val folder: String,
    val canExport: Boolean
)

internal class ExportDialogActions(
    val change: (ExportChoices) -> Unit = {},
    val pickDate: (DateField) -> Unit = {},
    val changeFolder: () -> Unit = {},
    val cancel: () -> Unit = {},
    val share: () -> Unit = {},
    val save: () -> Unit = {}
)

@Composable
internal fun ExportDialogContent(modifier: Modifier, ui: ExportDialogUi, actions: ExportDialogActions) {
    val choices = ui.choices
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    Column(
        modifier
            .background(EInkTheme.ModernDialog.background, shape)
            .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, shape)
            .padding(EInkTheme.ModernDialog.padding),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        Text(
            "Export",
            fontWeight = FontWeight.Bold,
            fontSize = EInkTheme.ModernDialog.titleFontSize,
            color = EInkTheme.ModernDialog.titleColor
        )
        Labeled("What") {
            if (ui.showPage) WhatChip("This page", ExportWhat.PAGE, 1f, choices, actions)
            if (ui.showDays) {
                WhatChip("This day", ExportWhat.DAY, 1f, choices, actions)
                WhatChip("This week", ExportWhat.WEEK, 1f, choices, actions)
                WhatChip("Choose dates…", ExportWhat.DATES, 1.35f, choices, actions)
            } else {
                WhatChip("This page", ExportWhat.PAGE, 1f, choices, actions)
            }
        }
        if (choices.what == ExportWhat.DATES) {
            Labeled("") {
                DateBox("From", ui.from, Modifier.weight(1f)) { actions.pickDate(DateField.FROM) }
                DateBox("To", ui.to, Modifier.weight(1f)) { actions.pickDate(DateField.TO) }
            }
            ui.rangeProblem?.let { Note(it, Modifier.padding(start = LABEL_WIDTH)) }
        }
        Labeled("As") {
            KindChip("PDF", ExportKind.PDF, choices, actions)
            KindChip("Images", ExportKind.IMAGES, choices, actions)
            if (ui.showDays) KindChip("Markdown", ExportKind.MARKDOWN, choices, actions)
        }
        Hairline()
        Row(horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.padding)) {
            Preview(ui)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
                ui.summary?.let {
                    Text(
                        it,
                        fontSize = EInkTheme.Typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.Colors.primary
                    )
                }
                when (choices.kind) {
                    ExportKind.PDF -> {
                        LayoutPicker(choices, actions)
                        Column {
                            Tick("Typed text under each page", choices.typedText) { actions.change(choices.copy(typedText = it)) }
                            Tick("The day's events and tasks", choices.eventsAndTasks) { actions.change(choices.copy(eventsAndTasks = it)) }
                        }
                    }
                    ExportKind.IMAGES -> Note("One PNG for each page.")
                    ExportKind.MARKDOWN -> {
                        Note("Updates the day's note. Anything you added to it stays.")
                        Tick("Include page images", choices.pageImages) { actions.change(choices.copy(pageImages = it)) }
                    }
                }
            }
        }
        Hairline()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Saves to", fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
                Text(
                    ui.folder,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(EInkTheme.Global.spacing))
            EinkSecondaryButton(text = "Change", onClick = actions.changeFolder)
        }
        if (choices.kind == ExportKind.MARKDOWN) {
            // Save only: a note and its pictures belong together in a folder
            Row(Modifier.fillMaxWidth()) {
                EinkSecondaryButton(text = "Cancel", onClick = actions.cancel, modifier = Modifier.weight(0.4f))
                Spacer(Modifier.weight(0.2f))
                ActionButton(text = "Save", modifier = Modifier.weight(0.4f), enabled = ui.canExport, onClick = actions.save)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
                EinkSecondaryButton(text = "Cancel", onClick = actions.cancel, modifier = Modifier.weight(1f))
                EinkSecondaryButton(
                    text = "Share", onClick = actions.share, modifier = Modifier.weight(1f),
                    enabled = ui.canExport
                )
                ActionButton(text = "Save", modifier = Modifier.weight(1f), enabled = ui.canExport, onClick = actions.save)
            }
        }
    }
}

private val LABEL_WIDTH = 52.dp

@Composable
private fun Labeled(label: String, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(LABEL_WIDTH),
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.darkGray
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing), content = content)
    }
}

@Composable
private fun RowScope.WhatChip(label: String, what: ExportWhat, weight: Float, choices: ExportChoices, actions: ExportDialogActions) {
    Chip(label, choices.what == what, Modifier.weight(weight)) { actions.change(choices.copy(what = what)) }
}

@Composable
private fun RowScope.KindChip(label: String, kind: ExportKind, choices: ExportChoices, actions: ExportDialogActions) {
    Chip(label, choices.kind == kind, Modifier.weight(1f)) { actions.change(choices.copy(kind = kind)) }
}

/** 44 dp, black when picked. */
@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    Box(
        modifier
            .height(EInkTheme.Global.touchTarget)
            .background(if (selected) EInkTheme.Colors.primary else EInkTheme.Colors.background, shape)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, shape)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = EInkTheme.Global.spacingSmall),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            maxLines = 1,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) EInkTheme.Colors.white else EInkTheme.Colors.primary
        )
    }
}

@Composable
private fun DateBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(EInkTheme.Global.touchTarget)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = EInkTheme.Global.paddingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
        Text(value, fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Medium, color = EInkTheme.Colors.primary, maxLines = 1)
    }
}

/** The chosen layout on one line; tapped, the two to choose from. */
@Composable
private fun LayoutPicker(choices: ExportChoices, actions: ExportDialogActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(EInkTheme.Global.touchTarget)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                .noRippleClickable { open = true }
                .padding(horizontal = EInkTheme.Global.paddingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (choices.fitMore) FIT_MORE else ONE_PER_PAGE,
                Modifier.weight(1f),
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose", tint = EInkTheme.Colors.primary)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier
                .background(EInkTheme.Colors.background)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
        ) {
            val row = Modifier.heightIn(min = EInkTheme.Global.touchTarget).padding(horizontal = EInkTheme.Global.spacing)
            RadioOption(label = FIT_MORE, isSelected = choices.fitMore, modifier = row, onClick = {
                actions.change(choices.copy(fitMore = true))
                open = false
            })
            RadioOption(label = ONE_PER_PAGE, isSelected = !choices.fitMore, modifier = row, onClick = {
                actions.change(choices.copy(fitMore = false))
                open = false
            })
        }
    }
}

private const val FIT_MORE = "Fit more on each page"
private const val ONE_PER_PAGE = "One memo per page"

@Composable
private fun Tick(text: String, ticked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .heightIn(min = EInkTheme.Global.touchTarget)
            .offset(x = (-14).dp)  // the box sits 14 dp inside its touch area: line it up with the rest
            .noRippleClickable { onChange(!ticked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = ticked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = EInkTheme.Colors.primary,
                uncheckedColor = EInkTheme.Colors.darkGray,
                checkmarkColor = EInkTheme.Colors.white
            )
        )
        Text(text, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.darkGray)
}

/** A page-shaped box, so the dialog keeps its size while the picture is drawn. */
@Composable
private fun Preview(ui: ExportDialogUi) {
    val height = ui.previewWidth * A4_HEIGHT.toFloat() / A4_WIDTH.toFloat()
    Box(
        Modifier
            .size(ui.previewWidth, height)
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.darkGray),
        contentAlignment = Alignment.TopCenter
    ) {
        ui.picture?.let { Image(it, contentDescription = "Preview of the first page", modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(EInkTheme.Global.borderWidth).background(EInkTheme.Colors.dividerHairline))
}
