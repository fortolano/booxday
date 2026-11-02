package com.boox.day.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boox.day.BuildConfig
import com.boox.day.R
import com.boox.day.data.db.AppDatabase
import com.boox.day.editor.ui.toolbar.ToolbarMenuTool
import com.boox.day.tasksync.TaskSync
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * "New tasks go to" at the top of the toolbar menu (≡) on the Tasks tab (owner decisions 46 and 52,
 * 31 Oct 2026): the same setting as in the Task Sync card, switched here before writing. Only with
 * Task Sync on and 2+ synced lists; its page lists them with a round mark on the chosen one.
 * Read when the menu opens (the menu's tools are built only then).
 */
internal fun newTasksMenuTool(context: Context): ToolbarMenuTool? {
    if (!BuildConfig.TASK_SYNC_ENABLED || !TaskSync.isOn(context)) return null
    val target = TaskSync.target(context) ?: return null
    val lists = AppDatabase.getDatabase(context.applicationContext).taskSyncDao()
        .getListsFor(target.provider, target.accountEmail, target.serverUrl)
    if (lists.size < 2) return null
    val chosen = lists.firstOrNull { it.isDefaultForNew }
    return ToolbarMenuTool(
        label = "New tasks go to: ${chosen?.title ?: "none"}",
        icon = { tint ->
            Icon(painterResource(R.drawable.format_list_bulleted_48px), contentDescription = null, modifier = Modifier.fillMaxSize(), tint = tint)
        },
        page = { closeMenu ->
            for (list in lists) {
                NewTasksListRow(list.title, selected = list.listId == chosen?.listId) {
                    if (list.listId != chosen?.listId) {
                        TaskSync.newTasksTo(context, target.provider, target.accountEmail, list.listId, target.serverUrl)
                    }
                    closeMenu()
                }
            }
        }
    )
}

/** One list in the menu page: a round mark, the chosen one filled (as the task card's choices). */
@Composable
private fun ColumnScope.NewTasksListRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .noRippleClickable { onClick() }
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(EInkTheme.MenuItem.iconSize), contentAlignment = Alignment.Center) {
            Box(Modifier.size(22.dp).border(2.dp, EInkTheme.Colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(11.dp).background(EInkTheme.Colors.primary, CircleShape))
            }
        }
        Text(
            text = title,
            color = EInkTheme.MenuItem.textColor,
            fontSize = EInkTheme.MenuItem.fontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
