package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boox.day.ui.dialogs.ActionButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * The Task Sync card in Preferences (task sync plan step 2.6, Oct 2026; owner decision 8): off by
 * default; once on, the Google account, the lists to sync, "New tasks go to", what happened last
 * and what needs the user. Design approved by the owner from render images (30 Oct 2026): every
 * line in a section with a heading, lists in two columns, "New tasks go to" as one picker line.
 */
internal data class TaskSyncCardState(
    val on: Boolean,
    /** Why it can't be turned on (Private Mode, no Google account), or null. */
    val cantTurnOn: String? = null,
    val account: String? = null,
    /** The connected Google accounts; with more than one, the account is chosen while off (decision 32). */
    val accounts: List<String> = emptyList(),
    val lists: List<TaskSyncCardList> = emptyList(),
    val newTasksListId: String? = null,
    /** "Last synced today at 10:42", "Syncing…", "Waiting for a connection". */
    val status: String? = null,
    val needsReconnect: Boolean = false,
    /** Deletes waiting for the user's OK (more than 20 at once). */
    val deletesWaiting: Int = 0,
    /** Days a new task from Google waits on (all 10 pages full), as shown ("Mon 3 Nov"). */
    val fullDays: List<String> = emptyList(),
    /** Tasks deleted in Google but changed here since, now on the tablet only. */
    val keptChangedHere: Int = 0
)

internal data class TaskSyncCardList(val id: String, val title: String, val ticked: Boolean)

internal class TaskSyncCardActions(
    val turnOn: (Boolean) -> Unit = {},
    val pickAccount: (String) -> Unit = {},
    val tickList: (String, Boolean) -> Unit = { _, _ -> },
    val newTasksTo: (String) -> Unit = {},
    val syncNow: () -> Unit = {},
    val reconnect: () -> Unit = {},
    val keepInGoogle: () -> Unit = {},
    val deleteInGoogle: () -> Unit = {}
)

@Composable
internal fun TaskSyncCardContent(state: TaskSyncCardState, actions: TaskSyncCardActions = TaskSyncCardActions()) {
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sync with Google Tasks",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
            EInkToggle(checked = state.on, onCheckedChange = actions.turnOn, enabled = state.on || state.cantTurnOn == null)
        }

        if (!state.on) {
            state.cantTurnOn?.let { Section("Before you can turn it on") { Note(it) } }
            if (state.cantTurnOn == null && state.accounts.size > 1) {
                Section("Google account") {
                    Column {
                        for (email in state.accounts) {
                            RadioOption(label = email, isSelected = email == state.account, onClick = { actions.pickAccount(email) })
                        }
                    }
                }
            }
            state.status?.let { status -> Section("Sync status") { Note(status) } }
            Section("When you turn it on") {
                Note(
                    "Your unfinished tasks from today on go to Google Tasks, and Google's unfinished tasks " +
                        "come to their days here. Ticked tasks and older days stay on the tablet."
                )
            }
            return@Column
        }

        // Each part in its own section, what needs the user first
        val noList = state.lists.isNotEmpty() && state.lists.none { it.ticked }
        val needsYou = state.needsReconnect || state.deletesWaiting > 0 || state.fullDays.isNotEmpty() || noList
        if (needsYou) {
            Section("Needs your attention") {
                if (noList) Attention("Tick at least one list below: until then nothing syncs.") {}
                if (state.needsReconnect) {
                    Attention("Google needs you to sign in again before tasks can sync.") {
                        ActionButton(text = "Reconnect", onClick = actions.reconnect)
                    }
                }
                if (state.deletesWaiting > 0) {
                    Attention(
                        "${state.deletesWaiting} tasks you cleared on the tablet would be deleted in Google Tasks. " +
                            "Sync waits until you choose."
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
                            EinkSecondaryButton(text = "Keep in Google", onClick = actions.keepInGoogle, width = 180.dp)
                            ActionButton(text = "Delete in Google", onClick = actions.deleteInGoogle, modifier = Modifier.width(180.dp))
                        }
                    }
                }
                for (day in state.fullDays) {
                    Attention("A task from Google is waiting: $day is full (10 pages). It comes once a row is free.") {}
                }
            }
        }

        Section("Google account") {
            Text(state.account.orEmpty(), fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
        }

        Section("Lists to sync") {
            // Two columns: people keep many lists (the owner has 8), in Google's own order
            Column {
                for (pair in state.lists.chunked(2)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
                        for (list in pair) ListTick(list, actions, Modifier.weight(1f))
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Section("New tasks go to") {
            Note("Tasks you write in BooxDay. Tasks from Google stay in their own list.")
            NewTasksToPicker(state, actions)
        }

        Section("Sync status") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.status.orEmpty(),
                    modifier = Modifier.weight(1f),
                    fontSize = EInkTheme.Typography.bodyMedium,
                    color = EInkTheme.Colors.primary
                )
                EinkSecondaryButton(text = "Sync Now", onClick = actions.syncNow, width = 120.dp)
            }
        }

        if (state.keptChangedHere > 0) {
            Section("Kept on the tablet") {
                Note(
                    if (state.keptChangedHere == 1) "1 task deleted in Google stays on the tablet only, because you changed it here."
                    else "${state.keptChangedHere} tasks deleted in Google stay on the tablet only, because you changed them here."
                )
            }
        }
    }
}

/** One part of the card: a line above it, its heading, then its content. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(EInkTheme.Global.borderWidth)
                .background(EInkTheme.Colors.borderStrong)
        )
        Heading(title)
        content()
    }
}

@Composable
private fun ListTick(list: TaskSyncCardList, actions: TaskSyncCardActions, modifier: Modifier) {
    Row(
        modifier = modifier
            .sizeIn(minHeight = 40.dp)
            .noRippleClickable { actions.tickList(list.id, !list.ticked) },
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = list.ticked,
            onCheckedChange = { actions.tickList(list.id, it) },
            colors = CheckboxDefaults.colors(
                checkedColor = EInkTheme.Colors.primary,
                uncheckedColor = EInkTheme.Colors.darkGray,
                checkmarkColor = EInkTheme.Colors.white
            )
        )
        Text(
            list.title,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The chosen list on one line; tapped, the ticked lists to choose from (one line, however many lists). */
@Composable
private fun NewTasksToPicker(state: TaskSyncCardState, actions: TaskSyncCardActions) {
    var open by remember { mutableStateOf(false) }
    val ticked = state.lists.filter { it.ticked }
    val chosen = ticked.firstOrNull { it.id == state.newTasksListId }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = EInkTheme.Global.touchTarget)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
                .noRippleClickable(enabled = ticked.isNotEmpty()) { open = true }
                .padding(horizontal = EInkTheme.Global.paddingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chosen?.title ?: "Choose a list",
                modifier = Modifier.weight(1f),
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose", tint = EInkTheme.Colors.primary)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier
                .background(EInkTheme.Colors.background)
                .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary)
                .heightIn(max = 360.dp)
        ) {
            NewTasksToChoices(ticked, state.newTasksListId) { id ->
                actions.newTasksTo(id)
                open = false
            }
        }
    }
}

/** The choices inside the picker; its own composable for the render test (a menu is a popup). */
@Composable
internal fun NewTasksToChoices(lists: List<TaskSyncCardList>, chosenId: String?, choose: (String) -> Unit) {
    Column {
        for (list in lists) {
            RadioOption(label = list.title, isSelected = list.id == chosenId, onClick = { choose(list.id) })
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = EInkTheme.Typography.bodyMedium, fontWeight = FontWeight.Bold, color = EInkTheme.Colors.primary)
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = EInkTheme.Preferences.explanationFontSize, color = EInkTheme.Colors.darkGray)
}

/** A boxed message with what to do about it. */
@Composable
private fun Attention(text: String, buttons: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(EInkTheme.Global.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        Text(text, fontSize = EInkTheme.Typography.bodyMedium, color = EInkTheme.Colors.primary)
        buttons()
    }
}
