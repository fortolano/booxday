package com.boox.day.week

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

enum class WeekMark { DUE, REPEATS }

/** One read-only line in a day's cell: an event when [minute] or [allDay] is set, otherwise a task. */
data class WeekLine(
    val text: String,
    val minute: Int? = null,
    val allDay: Boolean = false,
    val mark: WeekMark? = null,
    val ink: ImageBitmap? = null
) {
    val isTask: Boolean get() = minute == null && !allDay
}

data class WeekCell(
    val title: String,
    val lines: List<WeekLine> = emptyList(),
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val doneCount: Int = 0,
    val count: Int? = null
)

data class WeekPageData(val rangeLong: String, val rangeShort: String, val isThisWeek: Boolean, val cells: List<WeekCell>)

private val Ink = EInkTheme.Colors.primary

/** The whole Week page: a header, then two columns of four cells, read down then across. */
@Composable
fun WeekPage(
    page: WeekPageData,
    spec: WeekLayoutSpec,
    use24Hour: Boolean = false,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onThisWeek: () -> Unit = {},
    onClose: () -> Unit = {},
    onCell: (Int) -> Unit = {}
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        WeekHeader(page, spec, onPrevious, onNext, onThisWeek, onClose)
        Rule()
        Row(Modifier.fillMaxWidth().weight(1f)) {
            for (column in 0..1) {
                if (column == 1) Box(Modifier.width(1.dp).fillMaxHeight().background(Ink))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    for (row in 0..3) {
                        val index = column * 4 + row
                        if (row > 0) Rule()
                        WeekCellView(page.cells[index], spec, use24Hour, Modifier.weight(1f).fillMaxWidth().noRippleClickable { onCell(index) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink))
}

@Composable
private fun WeekHeader(
    page: WeekPageData,
    spec: WeekLayoutSpec,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(spec.headerDp.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderIcon(Icons.Default.ChevronLeft, "Previous week", onPrevious)
        Text(
            text = if (spec.compact) page.rangeShort else page.rangeLong,
            fontSize = EInkTheme.Typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Ink,
            maxLines = 1
        )
        HeaderIcon(Icons.Default.ChevronRight, "Next week", onNext)
        Spacer(Modifier.weight(1f))
        if (!page.isThisWeek) {
            Text(
                text = "This Week",
                fontSize = EInkTheme.Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.noRippleClickable(onClick = onThisWeek).padding(horizontal = 12.dp, vertical = 12.dp)
            )
        }
        HeaderIcon(Icons.Default.Close, "Close", onClose)
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).noRippleClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Ink, modifier = Modifier.size(28.dp))
    }
}

@Composable
internal fun WeekCellView(cell: WeekCell, spec: WeekLayoutSpec, use24Hour: Boolean, modifier: Modifier = Modifier) {
    val framed = if (cell.isSelected) modifier.border(2.dp, Ink) else modifier
    Column(framed.padding(horizontal = 8.dp, vertical = 4.dp)) {
        CellTitle(cell, spec)
        val fits = cell.lines.size <= spec.linesPerDay
        val shown = if (fits) cell.lines else cell.lines.take(spec.linesPerDay - 1)
        shown.forEach { WeekLineView(it, spec, use24Hour) }
        if (!fits) {
            Text(
                text = "+${cell.lines.size - shown.size} more",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.height(spec.lineDp.dp)
            )
        }
    }
}

@Composable
private fun CellTitle(cell: WeekCell, spec: WeekLayoutSpec) {
    Row(Modifier.fillMaxWidth().height(spec.titleDp.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = cell.title,
            fontSize = EInkTheme.Typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (cell.isToday) Color.White else Ink,
            modifier = if (cell.isToday) {
                Modifier.background(Ink, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 1.dp)
            } else {
                Modifier
            }
        )
        Spacer(Modifier.weight(1f))
        cell.count?.let {
            Text(text = "$it", fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Bold, color = Ink)
        }
        if (cell.doneCount > 0) {
            Icon(Icons.Default.Check, contentDescription = "Finished", tint = Ink, modifier = Modifier.size(16.dp))
            Text(text = "${cell.doneCount}", fontSize = EInkTheme.Typography.bodyMedium, color = Ink)
        }
    }
}

@Composable
internal fun WeekLineView(line: WeekLine, spec: WeekLayoutSpec, use24Hour: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(spec.lineDp.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (line.isTask) {
            Box(Modifier.padding(end = 2.dp).size(14.dp).border(1.dp, Ink))
        } else {
            Text(
                text = if (line.allDay) "All day" else WeekLayoutSpec.timeText(line.minute ?: 0, use24Hour, spec.compact),
                fontSize = EInkTheme.Typography.bodyMedium,
                color = Ink,
                maxLines = 1,
                modifier = Modifier.width(spec.timeColumnDp.dp)
            )
        }
        Box(Modifier.weight(1f)) {
            val ink = line.ink
            if (ink != null) {
                Image(
                    bitmap = ink,
                    contentDescription = "Handwritten task",
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.height((spec.lineDp - 3).dp).fillMaxWidth()
                )
            } else {
                Text(text = line.text, fontSize = EInkTheme.Typography.bodyMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when (line.mark) {
            WeekMark.DUE -> Text(text = "Due", fontSize = EInkTheme.Typography.bodySmall, fontWeight = FontWeight.Bold, color = Ink)
            WeekMark.REPEATS ->
                if (spec.compact) {
                    Icon(Icons.Default.Repeat, contentDescription = "Repeats", tint = Ink, modifier = Modifier.size(16.dp))
                } else {
                    Text(text = "Repeats", fontSize = EInkTheme.Typography.bodySmall, color = Ink)
                }
            null -> Unit
        }
    }
}
