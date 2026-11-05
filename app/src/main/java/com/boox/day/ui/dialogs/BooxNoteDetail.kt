package com.boox.day.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boox.day.booxnotes.BooxNotePreviewResult
import com.boox.day.booxnotes.CrossDayLinkSuggestion
import com.boox.day.booxnotes.LinkConfidence
import com.boox.day.data.db.BooxNoteIndex
import com.boox.day.data.db.CalendarEvent
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.components.ScrollMoreBar
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.theme.ScreenColour

// Note place of the Boox Notes window (Nov 2026): the page as the largest element (tap it to read it
// full size), then the link zone. The zone has one height in every state, so nothing moves the page.

/** What the link zone says about the note */
internal sealed interface NoteLinkUi {
    /** A suggestion is being looked for: linking by hand works all the same */
    data object Checking : NoteLinkUi

    /** [event] is null when the linked event is no longer in the calendar */
    data class Linked(val event: CalendarEvent?, val byUser: Boolean) : NoteLinkUi

    data class Suggested(val suggestion: CrossDayLinkSuggestion, val reason: String) : NoteLinkUi

    data class NotLinked(val sameDayEventCount: Int) : NoteLinkUi
}

/** The page being shown, as loaded by BooxNotePreviewCoordinator */
internal data class NotePageState(
    val loading: Boolean = true,
    val result: BooxNotePreviewResult? = null
)

/** What the zone can do; [undo] is set right after a change, and takes an existing button's place */
internal class NoteLinkActions(
    val onLink: (CrossDayLinkSuggestion) -> Unit,
    val onReject: (CrossDayLinkSuggestion) -> Unit,
    val onChooseEvent: () -> Unit,
    val onUnlink: () -> Unit,
    val undo: (() -> Unit)? = null,
    /** A suggestion that came in while the note was open: said on a line, never swapping the buttons */
    val lateSuggestion: String? = null,
    val onShowLate: () -> Unit = {}
)

@Composable
internal fun BooxNotePane(
    note: BooxNoteIndex?,
    link: NoteLinkUi,
    page: NotePageState,
    pageIndex: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    actions: NoteLinkActions,
    onOpenReader: () -> Unit,
    onOpenInBooxNotes: () -> Unit,
    modifier: Modifier = Modifier,
    /** Single pane: the bar with back, title and X; two panes show the title here */
    bar: (@Composable () -> Unit)? = null
) {
    val theme = EInkTheme.BooxNotes
    if (note == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Select a note",
                fontSize = EInkTheme.Typography.bodyLarge,
                color = EInkTheme.Colors.darkGray
            )
        }
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
    ) {
        if (bar != null) {
            bar()
        } else {
            Text(
                text = note.title.ifBlank { "Untitled note" },
                fontSize = theme.paneTitleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = noteMetaLine(note, withDay = true, fullDay = true),
            fontSize = theme.paneMetaFontSize,
            color = EInkTheme.Colors.darkGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        NotePageView(
            page = page,
            onOpenReader = onOpenReader,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        PageRow(pageIndex, pageCount, onPageChange, onOpenInBooxNotes)

        NoteLinkZone(note = note, link = link, actions = actions)
    }
}

/** Page arrows and the only way out to BOOX's app */
@Composable
internal fun PageRow(
    pageIndex: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onOpenInBooxNotes: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(EInkTheme.Global.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
    ) {
        if (pageCount > 1) {
            PageArrows(
                pageIndex = pageIndex,
                pageCount = pageCount,
                onPageChange = onPageChange,
                buttonSize = EInkTheme.BooxNotes.chevronSize
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (onOpenInBooxNotes != null) {
            EinkSecondaryButton(text = "Open in Notes App", onClick = onOpenInBooxNotes, width = 156.dp)
        }
    }
}

@Composable
private fun NoteLinkZone(note: BooxNoteIndex, link: NoteLinkUi, actions: NoteLinkActions) {
    val theme = EInkTheme.BooxNotes
    val likely = link is NoteLinkUi.Suggested && link.suggestion.confidence == LinkConfidence.HIGH
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(theme.linkZoneHeight)
            .border(
                width = if (likely) theme.cardStrongBorderWidth else theme.cardBorderWidth,
                color = EInkTheme.Colors.primary,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .padding(theme.linkRowPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        when (link) {
            is NoteLinkUi.Suggested -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZoneLabel(icon = Icons.Default.Star, tint = ScreenColour.starTint, text = "Is this the event?", modifier = Modifier.weight(1f))
                    ConfidenceTag(link.suggestion.confidence)
                }
                val event = link.suggestion.event
                ZoneTitle(event.title)
                ZoneText(listOfNotNull(eventWhen(event, withDay = true), link.reason).joinToString(" · "), lines = 2)
                Spacer(modifier = Modifier.weight(1f))
                ZoneButtons {
                    EinkSecondaryButton(text = "Other Event…", onClick = actions.onChooseEvent, modifier = Modifier.weight(1.3f))
                    if (actions.undo != null) {
                        EinkSecondaryButton(text = "Undo", onClick = actions.undo, modifier = Modifier.weight(1f))
                    } else {
                        EinkSecondaryButton(text = "Reject", onClick = { actions.onReject(link.suggestion) }, modifier = Modifier.weight(1f))
                    }
                    ActionButton(text = "Link", onClick = { actions.onLink(link.suggestion) }, modifier = Modifier.weight(1f))
                }
            }

            is NoteLinkUi.Linked -> {
                ZoneLabel(icon = Icons.Default.Link, text = "Linked to")
                val event = link.event
                if (event != null) {
                    ZoneTitle(event.title)
                    ZoneText(listOfNotNull(eventWhen(event, withDay = true), eventRelationToNote(note, event)).joinToString(" · "))
                } else {
                    ZoneText("An event that is no longer in your calendar")
                }
                if (!link.byUser) ZoneText("Linked automatically", muted = true)
                Spacer(modifier = Modifier.weight(1f))
                ZoneButtons {
                    if (actions.undo != null) {
                        EinkSecondaryButton(text = "Undo", onClick = actions.undo, modifier = Modifier.weight(1f))
                    } else {
                        EinkSecondaryButton(text = "Unlink", onClick = actions.onUnlink, modifier = Modifier.weight(1f))
                    }
                    EinkSecondaryButton(text = "Change Event…", onClick = actions.onChooseEvent, modifier = Modifier.weight(1f))
                }
            }

            NoteLinkUi.Checking, is NoteLinkUi.NotLinked -> {
                ZoneLabel(icon = null, text = "Not linked")
                val late = actions.lateSuggestion
                when {
                    late != null -> LateSuggestionLine(late, actions.onShowLate)
                    link is NoteLinkUi.NotLinked -> ZoneText(
                        if (link.sameDayEventCount > 0) "No suggestion" else "No events on ${formatDayShort(note.originalDate)}"
                    )
                    else -> ZoneText("Looking for a suggestion…", muted = true)
                }
                Spacer(modifier = Modifier.weight(1f))
                ZoneButtons {
                    if (actions.undo != null) {
                        EinkSecondaryButton(text = "Undo", onClick = actions.undo, modifier = Modifier.weight(1f))
                    }
                    EinkSecondaryButton(text = "Choose Event…", onClick = actions.onChooseEvent, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LateSuggestionLine(title: String, onShow: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        Icon(Icons.Default.Star, contentDescription = null, tint = ScreenColour.starTint, modifier = Modifier.size(18.dp))
        Text(
            text = "Suggested: $title",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Show",
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            modifier = Modifier
                .noRippleClickable(onClick = onShow)
                .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = EInkTheme.Global.spacingSmall)
        )
    }
}

@Composable
private fun ZoneLabel(
    icon: ImageVector?,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = EInkTheme.Colors.primary
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
            )
        }
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun ZoneTitle(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = EInkTheme.Colors.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ZoneText(text: String, muted: Boolean = false, lines: Int = 1) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        color = if (muted) EInkTheme.Colors.darkGray else EInkTheme.Colors.primary,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ZoneButtons(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Button.spacing),
        content = content
    )
}

/** The page as a 3:4 sheet that fills the space it gets; tapping it opens the reader. */
@Composable
internal fun NotePageView(
    page: NotePageState,
    onOpenReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = EInkTheme.BooxNotes
    val result = page.result
    val image = (result as? BooxNotePreviewResult.ImagePreview)?.bitmap
    val imageBitmap = remember(image) { image?.asImageBitmap() }
    val canRead = !page.loading && (imageBitmap != null || result is BooxNotePreviewResult.TextPreview)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .aspectRatio(theme.pageAspectRatio)
                .background(EInkTheme.Colors.background)
                .border(theme.pageBorderWidth, EInkTheme.Colors.primary)
                .then(if (canRead) Modifier.noRippleClickable(onClick = onOpenReader) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            when {
                page.loading -> PageMessage("Loading page…")
                imageBitmap != null -> Image(
                    bitmap = imageBitmap,
                    contentDescription = "Note page. Tap to read it full size.",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                )
                result is BooxNotePreviewResult.TextPreview -> {
                    val textScroll = rememberScrollState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = result.text,
                            fontSize = EInkTheme.Typography.bodyMedium,
                            color = EInkTheme.Colors.primary,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(EInkTheme.Global.padding)
                                .verticalScroll(textScroll)
                        )
                        ScrollMoreBar(textScroll)
                    }
                }
                else -> PageMessage(pageUnavailableText(result))
            }
        }
    }
}

/** Plain words: this is a note with no pages, or a page that can't be drawn */
private fun pageUnavailableText(result: BooxNotePreviewResult?): String =
    if (result?.totalPages == 0) "This note has no pages yet." else "This page can't be shown."

@Composable
private fun PageMessage(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        color = EInkTheme.Colors.darkGray,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(EInkTheme.Global.padding)
    )
}

/** ‹ Page 2 of 7 › */
@Composable
private fun PageArrows(
    pageIndex: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    buttonSize: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PageArrow(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            description = "Previous page",
            enabled = pageIndex > 0,
            size = buttonSize,
            onClick = { onPageChange(pageIndex - 1) }
        )
        Text(
            text = "Page ${pageIndex + 1} of $pageCount",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            maxLines = 1
        )
        PageArrow(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            description = "Next page",
            enabled = pageIndex < pageCount - 1,
            size = buttonSize,
            onClick = { onPageChange(pageIndex + 1) }
        )
    }
}

@Composable
private fun PageArrow(icon: ImageVector, description: String, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    val theme = EInkTheme.BooxNotes
    Box(
        modifier = Modifier
            .size(size)
            .then(if (enabled) Modifier.noRippleClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) theme.chevronIconColor else theme.chevronIconColorDisabled,
            modifier = Modifier.size(theme.chevronIconSize)
        )
    }
}

/** A page at full size: back, the page, then page arrows and Open in Notes App. */
@Composable
internal fun BooxNoteReader(
    note: BooxNoteIndex,
    page: NotePageState,
    pageIndex: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onOpenInBooxNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
    ) {
        BooxNotesBar(title = note.title.ifBlank { "Untitled note" }, onClose = onClose, onBack = onBack)
        Text(
            text = noteMetaLine(note, withDay = true),
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.darkGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        NotePageView(
            page = page,
            onOpenReader = {},
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        PageRow(pageIndex, pageCount, onPageChange, onOpenInBooxNotes)
    }
}
