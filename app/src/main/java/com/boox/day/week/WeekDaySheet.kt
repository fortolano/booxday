package com.boox.day.week

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

/** One 48 dp row of the Day sheet: an event when [time] is set, otherwise a task. */
data class WeekSheetRow(
    val text: String,
    val time: String? = null,
    val ticked: Boolean = false,
    val note: String? = null,
    /** False for a repeat with no row yet, and a task just moved away. */
    val canAct: Boolean = true
)

private val Ink = EInkTheme.Colors.primary

/** A day's events and tasks, or the Overdue list ([showDayButtons] false): paged, never scrolled. */
@Composable
fun WeekDaySheetCard(
    title: String,
    rows: List<WeekSheetRow>,
    page: Int,
    pages: Int,
    showDayButtons: Boolean = true,
    message: String? = null,
    onRow: (Int) -> Unit = {},
    onMove: (Int) -> Unit = {},
    onPage: (Int) -> Unit = {},
    onClose: () -> Unit = {},
    onAddEvent: () -> Unit = {},
    onOpenDay: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .width(EInkTheme.PopupOverlay.width)
            .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(EInkTheme.PopupOverlay.padding)
    ) {
        Text(text = title, fontSize = EInkTheme.Typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
        rows.forEachIndexed { index, row -> SheetRow(row, { onRow(index) }, { onMove(index) }) }
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
        message?.let { Text(text = it, fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink) }
        Spacer(Modifier.height(EInkTheme.Global.spacingSmall))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing), verticalAlignment = Alignment.CenterVertically) {
            EinkSecondaryButton(text = "Close", onClick = onClose)
            Spacer(Modifier.weight(1f))
            if (showDayButtons) {
                EinkSecondaryButton(text = "Add Event", onClick = onAddEvent, width = 110.dp)
                ActionButton(text = "Open Day", onClick = onOpenDay)
            }
        }
    }
}

@Composable
private fun SheetRow(row: WeekSheetRow, onTap: () -> Unit, onMove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (row.time != null) {
            Row(Modifier.weight(1f).noRippleClickable(onClick = onTap), verticalAlignment = Alignment.CenterVertically) {
                Text(text = row.time, fontSize = EInkTheme.Typography.bodyMedium, color = Ink, maxLines = 1, modifier = Modifier.width(166.dp))
                Text(text = row.text, fontSize = EInkTheme.Typography.bodyLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Event details", tint = Ink)
        } else {
            Box(Modifier.size(44.dp).noRippleClickable(onClick = onTap), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.size(22.dp).border(1.dp, Ink), contentAlignment = Alignment.Center) {
                    if (row.ticked) Icon(Icons.Default.Check, contentDescription = "Done", tint = Ink, modifier = Modifier.size(20.dp))
                }
            }
            Text(text = row.text, fontSize = EInkTheme.Typography.bodyLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            row.note?.let { Text(text = it, fontSize = EInkTheme.Typography.bodySmall, color = Ink, maxLines = 1) }
            if (row.canAct) {
                Row(Modifier.height(44.dp).noRippleClickable(onClick = onMove), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Move", fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Ink)
                }
            }
        }
    }
}
