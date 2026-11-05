package com.boox.day.meetings

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
import com.boox.day.ui.theme.EInkTheme

private val Ink = EInkTheme.Colors.primary

data class RecapActionUi(val text: String, val due: String? = null, val ticked: Boolean = true)

/** The parts of a recap on one page of the sheet; an empty part isn't drawn. */
data class RecapPageUi(
    val summary: String? = null,
    val decisions: List<String> = emptyList(),
    val actions: List<RecapActionUi> = emptyList(),
    val followUps: List<String> = emptyList(),
    val questions: List<String> = emptyList()
)

@Composable
private fun Frame(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .background(EInkTheme.ModernDialog.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .border(EInkTheme.ModernDialog.borderWidth, EInkTheme.ModernDialog.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
            .padding(EInkTheme.ModernDialog.padding),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) { content() }
}

@Composable
private fun Title(text: String, line: String?) {
    Text(text = text, fontSize = EInkTheme.Typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    line?.let { Text(text = it, fontSize = EInkTheme.Typography.bodyMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun Heading(text: String, button: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
        button?.invoke()
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "•", fontSize = EInkTheme.Typography.bodyLarge, color = Ink)
        Text(text = text, fontSize = EInkTheme.Typography.bodyLarge, color = Ink)
    }
}

@Composable
private fun Tick(text: String, note: String?, ticked: Boolean) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).border(1.dp, Ink), contentAlignment = Alignment.Center) {
            if (ticked) Icon(Icons.Default.Check, contentDescription = "Ticked", tint = Ink, modifier = Modifier.size(22.dp))
        }
        Text(text = text, fontSize = EInkTheme.Typography.bodyLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        note?.let { Text(text = it, fontSize = EInkTheme.Typography.bodyMedium, color = Ink, maxLines = 1) }
    }
}

@Composable
private fun Pager(page: Int, pages: Int) {
    if (pages <= 1) return
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page", tint = Ink) }
        Text(text = "$page of $pages", fontSize = EInkTheme.Typography.bodyMedium, color = Ink)
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.ChevronRight, contentDescription = "Next page", tint = Ink) }
    }
}

@Composable
private fun Buttons(left: String, middle: String?, right: String?, middleWidth: Int = 120, rightWidth: Int? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing), verticalAlignment = Alignment.CenterVertically) {
        EinkSecondaryButton(text = left, onClick = {})
        Spacer(Modifier.weight(1f))
        middle?.let { EinkSecondaryButton(text = it, onClick = {}, width = middleWidth.dp) }
        right?.let { ActionButton(text = it, modifier = rightWidth?.let { w -> Modifier.width(w.dp) } ?: Modifier, onClick = {}) }
    }
}

/** The recap: what the notes say, to check, keep or send. */
@Composable
fun MeetingRecapCard(title: String, line: String, content: RecapPageUi, page: Int, pages: Int, modifier: Modifier = Modifier, changedNotes: Boolean = false, saved: Boolean = false) {
    Frame(modifier) {
        Title("Recap · $title", line)
        if (changedNotes) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Your notes changed since this recap.", fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                EinkSecondaryButton(text = "Update Recap", onClick = {}, width = 140.dp)
            }
        }
        content.summary?.let {
            Heading("Summary")
            Text(text = it, fontSize = EInkTheme.Typography.bodyLarge, color = Ink)
        }
        if (content.decisions.isNotEmpty()) {
            Heading("Decisions")
            content.decisions.forEach { Bullet(it) }
        }
        if (content.actions.isNotEmpty()) {
            val count = content.actions.count { it.ticked }
            Heading("Action items") { EinkSecondaryButton(text = "Add $count ${if (count == 1) "Task" else "Tasks"}", onClick = {}, width = 130.dp) }
            content.actions.forEach { Tick(it.text, it.due, it.ticked) }
        }
        if (content.followUps.isNotEmpty()) {
            Heading("Follow-ups")
            content.followUps.forEach { Bullet(it) }
        }
        if (content.questions.isNotEmpty()) {
            Heading("Open questions")
            content.questions.forEach { Bullet(it) }
        }
        Pager(page, pages)
        Buttons("Close", "Share PDF", if (saved) null else "Save to Notes", rightWidth = 140)
    }
}

/** Recap can't run: the notes are never stuck. */
@Composable
fun MeetingRecapBlockedCard(title: String, message: String, modifier: Modifier = Modifier) {
    Frame(modifier) {
        Title("Recap · $title", null)
        Text(text = message, fontSize = EInkTheme.Typography.bodyLarge, color = Ink)
        Buttons("Close", "Export", "Action Items", rightWidth = 130)
    }
}

@Composable
fun MeetingReadingCard(title: String, message: String, modifier: Modifier = Modifier) {
    Frame(modifier) {
        Title(title, null)
        Text(text = message, fontSize = EInkTheme.Typography.bodyLarge, color = Ink)
        Buttons("Cancel", null, null)
    }
}

data class PrepUi(
    val title: String,
    val line: String,
    val people: String? = null,
    val lastTime: String? = null,
    val lastPoints: List<String> = emptyList(),
    val tasks: List<String> = emptyList(),
    val tasksHeading: String = "May be related",
    val notes: List<String> = emptyList(),
    val brief: List<String> = emptyList(),
    val worthAsking: List<String> = emptyList()
)

/** Everything here but Brief Me is read on the tablet, with no AI. */
@Composable
fun MeetingPrepCard(data: PrepUi, page: Int, pages: Int, modifier: Modifier = Modifier) {
    Frame(modifier) {
        Title("Prepare · ${data.title}", data.line)
        data.people?.let { Text(text = it, fontSize = EInkTheme.Typography.bodyMedium, color = Ink) }
        if (data.brief.isNotEmpty()) {
            Heading("Brief")
            data.brief.forEach { Bullet(it) }
            if (data.worthAsking.isNotEmpty()) {
                Heading("Worth asking")
                data.worthAsking.forEach { Bullet(it) }
            }
        } else {
            data.lastTime?.let {
                Heading("Last time · $it") { EinkSecondaryButton(text = "Open", onClick = {}, width = 80.dp) }
                data.lastPoints.forEach { point -> Bullet(point) }
            }
            if (data.lastTime == null && data.tasks.isEmpty() && data.notes.isEmpty()) {
                Text(text = "No earlier notes for this meeting.", fontSize = EInkTheme.Typography.bodyLarge, color = Ink, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (data.tasks.isNotEmpty()) {
                Heading(data.tasksHeading)
                data.tasks.forEach { Tick(it, null, false) }
            }
            if (data.notes.isNotEmpty()) {
                Heading("Related notes")
                data.notes.forEach { note ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = note, fontSize = EInkTheme.Typography.bodyLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        EinkSecondaryButton(text = "Open", onClick = {}, width = 80.dp)
                    }
                }
            }
        }
        Pager(page, pages)
        if (data.brief.isEmpty()) {
            Text(text = "Brief Me uses your AI.", fontSize = EInkTheme.Typography.bodySmall, color = Ink, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        Buttons("Close", if (data.brief.isEmpty()) "Brief Me" else null, "Take Notes", rightWidth = 130)
    }
}

/** On the event's Meeting notes card: what can be done with this meeting's notes. */
@Composable
fun MeetingCardActions(hasNotes: Boolean, beforeEnd: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.border(1.dp, Ink).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Meeting notes", fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Bold, color = Ink)
            Text(text = if (hasNotes) "3 pages" else "Not started", fontSize = EInkTheme.Typography.bodySmall, color = Ink)
        }
        val wide = Modifier.fillMaxWidth()
        if (!hasNotes) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EinkSecondaryButton(text = "Take Notes", onClick = {}, modifier = Modifier.weight(1f))
                if (beforeEnd) EinkSecondaryButton(text = "Prepare", onClick = {}, modifier = Modifier.weight(1f))
            }
        } else {
            Row(wide, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EinkSecondaryButton(text = "Open Notes", onClick = {}, modifier = Modifier.weight(1f))
                if (beforeEnd) EinkSecondaryButton(text = "Prepare", onClick = {}, modifier = Modifier.weight(1f))
            }
            Row(wide, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EinkSecondaryButton(text = "Recap", onClick = {}, modifier = Modifier.weight(1f))
                EinkSecondaryButton(text = "Action Items", onClick = {}, modifier = Modifier.weight(1f))
                EinkSecondaryButton(text = "Export", onClick = {}, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** What arrived from another app. AI only runs on the user's tap, and the line says so. */
@Composable
fun ShareCard(preview: String, modifier: Modifier = Modifier, picture: (@Composable () -> Unit)? = null, findLabel: String = "Find Events and Tasks") {
    Frame(modifier) {
        Title("Add to BooxDay", null)
        Box(Modifier.fillMaxWidth().border(1.dp, Ink, RoundedCornerShape(4.dp)).padding(12.dp)) {
            if (picture != null) picture() else Text(text = preview, fontSize = EInkTheme.Typography.bodyLarge, color = Ink, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        Text(text = "$findLabel uses your AI.", fontSize = EInkTheme.Typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing), verticalAlignment = Alignment.CenterVertically) {
            EinkSecondaryButton(text = "Cancel", onClick = {}, width = 90.dp)
            Spacer(Modifier.weight(1f))
            EinkSecondaryButton(text = "Save as Note", onClick = {}, width = 130.dp)
            ActionButton(text = findLabel, modifier = Modifier.width(if (findLabel.length > 12) 210.dp else 130.dp), onClick = {})
        }
    }
}
