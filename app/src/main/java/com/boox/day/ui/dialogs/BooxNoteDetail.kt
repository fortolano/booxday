package com.boox.day.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.boox.day.ui.components.DialogCloseButton
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

// Note pane of the Boox Notes dialog (Oct 2026): title and when, one link row, then the page itself as
// the largest element (tap it to read the page full size), page arrows and "Open in Boox Notes".

/** What the link row says about the selected note */
internal sealed interface NoteLinkUi {
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

@Composable
internal fun BooxNotePane(
    note: BooxNoteIndex?,
    link: NoteLinkUi,
    page: NotePageState,
    pageIndex: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onLinkSuggestion: (CrossDayLinkSuggestion) -> Unit,
    onChooseEvent: () -> Unit,
    onUnlink: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenInBooxNotes: () -> Unit,
    modifier: Modifier = Modifier
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
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = note.title.ifBlank { "Untitled note" },
                fontSize = theme.paneTitleFontSize,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = noteMetaLine(note, withDay = true, fullDay = true),
                fontSize = theme.paneMetaFontSize,
                color = EInkTheme.Colors.darkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        NoteLinkRow(
            note = note,
            link = link,
            onLinkSuggestion = onLinkSuggestion,
            onChooseEvent = onChooseEvent,
            onUnlink = onUnlink
        )

        NotePageView(
            page = page,
            onOpenReader = onOpenReader,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        if (pageCount > 1) {
            PageArrows(
                pageIndex = pageIndex,
                pageCount = pageCount,
                onPageChange = onPageChange,
                buttonSize = theme.chevronSize,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ActionButton(
            text = "Open in Boox Notes",
            onClick = onOpenInBooxNotes,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NoteLinkRow(
    note: BooxNoteIndex,
    link: NoteLinkUi,
    onLinkSuggestion: (CrossDayLinkSuggestion) -> Unit,
    onChooseEvent: () -> Unit,
    onUnlink: () -> Unit
) {
    val theme = EInkTheme.BooxNotes
    val strongSuggestion = link is NoteLinkUi.Suggested && link.suggestion.confidence == LinkConfidence.HIGH
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (strongSuggestion) theme.cardStrongBorderWidth else theme.cardBorderWidth,
                color = EInkTheme.Colors.primary,
                shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .padding(theme.linkRowPadding),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        when (link) {
            NoteLinkUi.Checking -> {
                LinkRowLabel(icon = null, text = "Not linked")
                LinkRowText("Looking for a suggestion…", muted = true)
            }

            is NoteLinkUi.Linked -> {
                LinkRowLabel(icon = Icons.Default.Link, text = "Linked to")
                val event = link.event
                if (event != null) {
                    LinkRowTitle(event.title)
                    LinkRowText(listOfNotNull(eventWhen(event, withDay = true), eventRelationToNote(note, event)).joinToString(" · "))
                } else {
                    LinkRowText("An event that is no longer in your calendar")
                }
                if (!link.byUser) {
                    LinkRowText("Linked by Smart Link", muted = true)
                }
                LinkRowButtons {
                    EinkSecondaryButton(text = "Unlink", onClick = onUnlink, modifier = Modifier.weight(1f))
                    EinkSecondaryButton(text = "Change event…", onClick = onChooseEvent, modifier = Modifier.weight(1f))
                }
            }

            is NoteLinkUi.Suggested -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinkRowLabel(icon = Icons.Default.Star, text = "Suggested event", modifier = Modifier.weight(1f))
                    ConfidenceTag(link.suggestion.confidence)
                }
                val event = link.suggestion.event
                LinkRowTitle(event.title)
                LinkRowText(listOfNotNull(eventWhen(event, withDay = true), eventRelationToNote(note, event)).joinToString(" · "))
                LinkRowText(link.reason, muted = true)
                LinkRowButtons {
                    EinkSecondaryButton(text = "Other event…", onClick = onChooseEvent, modifier = Modifier.weight(1f))
                    ActionButton(
                        text = "Link",
                        onClick = { onLinkSuggestion(link.suggestion) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            is NoteLinkUi.NotLinked -> {
                LinkRowLabel(icon = null, text = "Not linked")
                LinkRowText(
                    if (link.sameDayEventCount > 0) "No suggestion for this note."
                    else "No events on ${formatDayShort(note.originalDate)}."
                )
                LinkRowButtons {
                    EinkSecondaryButton(text = "Link to event…", onClick = onChooseEvent, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LinkRowLabel(icon: ImageVector?, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
            )
        }
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
    }
}

@Composable
private fun LinkRowTitle(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = EInkTheme.Colors.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun LinkRowText(text: String, muted: Boolean = false) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        color = if (muted) EInkTheme.Colors.darkGray else EInkTheme.Colors.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun LinkRowButtons(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = EInkTheme.Global.spacingSmall),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Button.spacing),
        content = content
    )
}

/** The page as a 3:4 sheet that fills the space it gets; tapping it opens the reader. */
@Composable
private fun NotePageView(
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
                result is BooxNotePreviewResult.TextPreview -> Text(
                    text = result.text,
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(EInkTheme.Global.padding)
                        .verticalScroll(rememberScrollState())
                )
                else -> PageMessage(
                    (result as? BooxNotePreviewResult.Unavailable)?.diagnostics?.message ?: "This page can't be shown."
                )
            }
        }
    }
}

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
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.padding, Alignment.CenterHorizontally),
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
            color = EInkTheme.Colors.primary
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

/** A page at full size: back, the page, then page arrows and "Open in Boox Notes". */
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
        // Back goes to the note list, the X closes Boox Notes (Oct 2026)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(EInkTheme.Global.touchTarget)
                    .noRippleClickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = EInkTheme.Colors.primary,
                    modifier = Modifier.size(EInkTheme.Global.iconSize)
                )
            }
            Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title.ifBlank { "Untitled note" },
                    fontSize = EInkTheme.Typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = noteMetaLine(note, withDay = true),
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.darkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DialogCloseButton(onClose = onClose)
        }
        NotePageView(
            page = page,
            onOpenReader = {},
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(EInkTheme.Global.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.padding)
        ) {
            if (pageCount > 1) {
                // Same arrow size as the reader always had (44 dp)
                PageArrows(
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    onPageChange = onPageChange,
                    buttonSize = EInkTheme.Global.touchTarget
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            ActionButton(text = "Open in Boox Notes", onClick = onOpenInBooxNotes)
        }
    }
}
