package com.boox.day.proposals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.dialogs.ActionButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/** One suggested item as the sheet shows it. [check] is the bold "Check the date" line. */
data class ProposalCardUi(
    val kind: String,
    val title: String,
    val whenLine: String,
    val target: String? = null,
    val note: String? = null,
    val check: String? = null,
    val ticked: Boolean = true
)

private val Ink = EInkTheme.Colors.primary

@Composable
private fun SheetFrame(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(EInkTheme.ModernDialog.padding),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
    ) { content() }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text = text, fontSize = EInkTheme.Typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
}

/** What the AI found, for the user to check. Nothing is created until the main button is tapped. */
@Composable
fun ProposalReviewCard(
    readAs: String,
    cards: List<ProposalCardUi>,
    page: Int,
    pages: Int,
    mainLabel: String,
    modifier: Modifier = Modifier,
    showMoreDetails: Boolean = true,
    canAdd: Boolean = true,
    onTick: (Int) -> Unit = {},
    onKind: (Int) -> Unit = {},
    onTitle: (Int) -> Unit = {},
    onWhen: (Int) -> Unit = {},
    onPage: (Int) -> Unit = {},
    onCancel: () -> Unit = {},
    onMore: () -> Unit = {},
    onAdd: () -> Unit = {}
) {
    SheetFrame(modifier) {
        SheetTitle("Add to your calendar")
        Text(
            text = "Read as: \"$readAs\"",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val many = cards.size > 1 || pages > 1
        cards.forEachIndexed { index, card ->
            Card(card, many, { onTick(index) }, { onKind(index) }, { onTitle(index) }, { onWhen(index) })
        }
        if (pages > 1) {
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).noRippleClickable { onPage(page - 1) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page", tint = Ink)
                }
                Text(text = "$page of $pages", fontSize = EInkTheme.Typography.bodyMedium, color = Ink)
                Box(Modifier.size(44.dp).noRippleClickable { onPage(page + 1) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next page", tint = Ink)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing), verticalAlignment = Alignment.CenterVertically) {
            EinkSecondaryButton(text = "Cancel", onClick = onCancel)
            Spacer(Modifier.weight(1f))
            if (showMoreDetails) EinkSecondaryButton(text = "More Details", onClick = onMore, width = 130.dp)
            ActionButton(text = mainLabel, enabled = canAdd, onClick = onAdd)
        }
    }
}

@Composable
private fun Card(card: ProposalCardUi, withTick: Boolean, onTick: () -> Unit, onKind: () -> Unit, onTitle: () -> Unit, onWhen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, Ink, RoundedCornerShape(4.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (withTick) {
            Box(Modifier.size(24.dp).border(1.dp, Ink).noRippleClickable(onClick = onTick), contentAlignment = Alignment.Center) {
                if (card.ticked) Icon(Icons.Default.Check, contentDescription = "Will be added", tint = Ink, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.kind,
                    fontSize = EInkTheme.Typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.border(1.dp, Ink, RoundedCornerShape(4.dp)).noRippleClickable(onClick = onKind).padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Text(text = card.title, fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.noRippleClickable(onClick = onTitle))
            }
            Text(text = card.whenLine, fontSize = EInkTheme.Typography.bodyLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.noRippleClickable(onClick = onWhen))
            card.target?.let { Text(text = it, fontSize = EInkTheme.Typography.bodyMedium, color = Ink, maxLines = 1) }
            card.note?.let { Text(text = it, fontSize = EInkTheme.Typography.bodyMedium, color = Ink, maxLines = 1) }
            card.check?.let { Text(text = it, fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink) }
        }
    }
}

/** While the AI reads: one static message, so the panel updates once. */
@Composable
fun ProposalReadingCard(modifier: Modifier = Modifier, onCancel: () -> Unit = {}) {
    SheetFrame(modifier) {
        SheetTitle("Add to your calendar")
        Text(text = "Your AI is reading the selected writing…", fontSize = EInkTheme.Typography.bodyLarge, color = Ink)
        Row(Modifier.fillMaxWidth()) { EinkSecondaryButton(text = "Cancel", onClick = onCancel) }
    }
}

/** Nothing to add, or no connection: never a dead end. */
@Composable
fun ProposalNothingCard(message: String, modifier: Modifier = Modifier, onClose: () -> Unit = {}, onByHand: () -> Unit = {}) {
    SheetFrame(modifier) {
        SheetTitle("Add to your calendar")
        Text(text = message, fontSize = EInkTheme.Typography.bodyLarge, color = Ink)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EinkSecondaryButton(text = "Close", onClick = onClose)
            Spacer(Modifier.weight(1f))
            ActionButton(text = "Add Event Myself", modifier = Modifier.width(180.dp), onClick = onByHand)
        }
    }
}

/** Every model failed: the normal message, and another try. */
@Composable
fun ProposalFailedCard(message: String, modifier: Modifier = Modifier, onClose: () -> Unit = {}, onRetry: () -> Unit = {}) {
    SheetFrame(modifier) {
        SheetTitle("Add to your calendar")
        Text(text = message, fontSize = EInkTheme.Typography.bodyLarge, color = Ink)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EinkSecondaryButton(text = "Close", onClick = onClose)
            Spacer(Modifier.weight(1f))
            ActionButton(text = "Retry", onClick = onRetry)
        }
    }
}
