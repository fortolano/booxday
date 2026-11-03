package com.boox.day.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.data.db.AppDatabase
import com.boox.day.data.model.ConnectedAccount
import com.boox.day.tasksync.TaskSync
import com.boox.day.tasksync.TaskSyncTarget
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * A CalDAV account is used for what its server has (owner decision 78, Nov 2026): its calendars, its
 * task lists, or both. Before, a server with task lists and no calendar was refused.
 */

/** What a CalDAV account's server has and what the account is used for, as its row in Calendar Accounts shows it. */
data class CalDavUses(
    val calendarCount: Int,
    val taskListCount: Int,
    val calendarsOn: Boolean,
    val tasksOn: Boolean,
    /** The task lists Task Sync syncs with this account ("Tasks, Errands"), or what Task Sync does now when it's off here. */
    val tasksNote: String
) {
    val hasCalendars: Boolean get() = calendarCount > 0
    val hasTaskLists: Boolean get() = taskListCount > 0
}

/** "4 calendars", "1 task list". */
internal fun countOf(count: Int, one: String, many: String) = if (count == 1) "1 $one" else "$count $many"

/**
 * The step after signing in when the server has calendars and task lists: what to use it for, both
 * ticked to start (owner decision 78). Connect needs at least one.
 */
@Composable
internal fun CalDavUseForContent(
    server: String,
    calendarCount: Int,
    taskListCount: Int,
    useCalendars: Boolean,
    useTasks: Boolean,
    connecting: Boolean = false,
    onCalendars: () -> Unit = {},
    onTasks: () -> Unit = {},
    onCancel: () -> Unit = {},
    onConnect: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
        Text(
            text = "Use $server For",
            fontSize = EInkTheme.PopupOverlay.titleFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.PopupOverlay.titleColor
        )
        Text(
            text = "This server has calendars and task lists. Pick what BooxDay uses; you can change it later in Calendar Accounts.",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary
        )
        UseRow(
            title = "Calendars",
            note = "${countOf(calendarCount, "calendar", "calendars")}: their events show and sync",
            checked = useCalendars, enabled = !connecting, onClick = onCalendars
        )
        UseRow(
            title = "Tasks",
            note = "${countOf(taskListCount, "task list", "task lists")}: Task Sync starts with this account",
            checked = useTasks, enabled = !connecting, onClick = onTasks
        )
        Row(Modifier.fillMaxWidth().padding(top = EInkTheme.Global.spacingSmall)) {
            EinkSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(0.4f))
            Spacer(Modifier.weight(0.2f))
            EinkPrimaryButton(
                text = if (connecting) "Connecting…" else "Connect",
                enabled = (useCalendars || useTasks) && !connecting,
                onClick = onConnect,
                modifier = Modifier.weight(0.4f)
            )
        }
    }
}

@Composable
private fun UseRow(title: String, note: String, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = EInkTheme.Global.touchTarget)
            .noRippleClickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onClick() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = EInkTheme.Colors.primary,
                uncheckedColor = EInkTheme.Colors.darkGray,
                checkmarkColor = EInkTheme.Colors.white
            )
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Medium, color = EInkTheme.Colors.primary)
            Text(note, fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
        }
    }
}

/**
 * Task Sync already uses another service (owner decision 78: ask before switching). Switching
 * follows decision 32: the tablet's tasks stay, and sync with the new account as if never synced.
 */
@Composable
internal fun TaskSyncSwitchContent(
    currentService: String,
    newServer: String,
    onKeep: () -> Unit = {},
    onSwitch: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)) {
        Text(
            text = "Switch Task Sync?",
            fontSize = EInkTheme.PopupOverlay.titleFontSize,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.PopupOverlay.titleColor
        )
        Text(
            text = "Task Sync uses $currentService now. Switch it to $newServer?",
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary
        )
        Text(
            text = "Your tasks stay on the tablet and sync with $newServer from now on. $currentService keeps its copies.",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray
        )
        Row(Modifier.fillMaxWidth().padding(top = EInkTheme.Global.spacingSmall)) {
            EinkSecondaryButton(text = "Keep $currentService", onClick = onKeep, modifier = Modifier.weight(0.5f))
            Spacer(Modifier.weight(0.1f))
            EinkPrimaryButton(text = "Switch", onClick = onSwitch, modifier = Modifier.weight(0.4f))
        }
    }
}

/**
 * The account's "Use for" switches at the top of its row in Calendar Accounts: Calendars and Tasks,
 * each only when the server has that kind (owner decision 78).
 */
@Composable
internal fun CalDavUsesSection(
    uses: CalDavUses,
    onCalendars: (Boolean) -> Unit = {},
    onTasks: (Boolean) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Use for:",
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Colors.darkGray
        )
        if (uses.hasCalendars) {
            SwitchRow(
                title = "Calendars",
                note = if (uses.calendarsOn) countOf(uses.calendarCount, "calendar", "calendars")
                else "Off: its events don't show or sync",
                checked = uses.calendarsOn, onChange = onCalendars
            )
        }
        if (uses.hasTaskLists) {
            SwitchRow(title = "Tasks", note = uses.tasksNote, checked = uses.tasksOn, onChange = onTasks)
        }
    }
}

@Composable
private fun SwitchRow(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = EInkTheme.Global.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = EInkTheme.Typography.bodyLarge, fontWeight = FontWeight.Medium, color = EInkTheme.Colors.primary)
            Text(note, fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.darkGray)
        }
        EInkToggle(checked = checked, onCheckedChange = onChange)
    }
}

/** What each CalDAV account's row shows in "Use for" ([calendarCounts]: its calendars as Calendar Accounts lists them). */
internal fun loadCalDavUses(context: Context, accounts: List<ConnectedAccount>, calendarCounts: Map<String, Int>): Map<String, CalDavUses> {
    val db = AppDatabase.getDatabase(context.applicationContext)
    val on = TaskSync.isOn(context)
    val inUse = TaskSync.target(context)
    return accounts.associate { account ->
        val dav = db.calDavDao().getAccount(account.id)
        val target = TaskSyncTarget.calDav(account)
        val tasksOn = on && inUse?.key == target.key
        val note = when {
            tasksOn -> db.taskSyncDao().getListsFor(target.provider, target.accountEmail, target.serverUrl)
                .joinToString(", ") { it.title }.ifEmpty { "no list ticked yet" }.let { "Task Sync: $it" }
            on && inUse != null -> "Off: Task Sync uses ${inUse.nameBeside(target)}"
            else -> "Off"
        }
        account.id to CalDavUses(
            calendarCount = calendarCounts[account.id] ?: 0,
            taskListCount = dav?.taskListCount ?: 0,
            calendarsOn = dav?.useCalendars ?: true,
            tasksOn = tasksOn,
            tasksNote = note
        )
    }
}
