package com.boox.day.editor.ui.toolbar

import com.boox.day.ui.LocalNestedPopupOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.datastore.BUTTON_SIZE
import com.boox.day.ui.convertDpToPixel
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Task bulk actions dropdown button.
 * Only visible when Tasks tab is active.
 *
 * Focused on bulk task operations only:
 * - "Move tasks..." - Opens dialog to move tasks (current page or all pages)
 * - "Copy tasks..." - Opens dialog to copy tasks (current page or all pages)
 * - "Delete tasks..." - Opens dialog to delete tasks (current page or all pages)
 * - "Convert handwriting..." - Replaces handwritten strokes with recognized text
 *
 * Page operations (Delete Page) moved to Hamburger menu.
 * AI features (Daily/Weekly Summary) moved to Task AI menu.
 */
@Composable
fun TaskBulkActionsButton(
    onMoveTasks: () -> Unit,
    onCopyTasks: () -> Unit,
    onDeleteTasks: () -> Unit,
    onConvertHandwriting: () -> Unit,
    /** "Start-the-day review" (plan T4); null hides it */
    onStartDayReview: (() -> Unit)? = null,
    /** "Cross out = done" switch (plan T4); null hides it */
    crossOutEnabled: Boolean = false,
    onCrossOutToggle: ((Boolean) -> Unit)? = null,
    onMenuOpenChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onActivate: () -> Unit = {},
    isOpen: Boolean = false,
    onOpenChange: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current

    // Use external state if provided, otherwise internal
    var internalShowMenu by remember { mutableStateOf(false) }
    val showMenu = if (onOpenChange != null) isOpen else internalShowMenu
    val setShowMenu: (Boolean) -> Unit = { value ->
        if (onOpenChange != null) {
            onOpenChange(value)
        } else {
            internalShowMenu = value
        }
    }

    if (onMenuOpenChange != null) {
        LaunchedEffect(showMenu) {
            onMenuOpenChange(showMenu)
        }
    }

    Box(modifier = modifier) {
        ToolbarButton(
            iconId = R.drawable.checklist_40px,  // Checklist icon for task bulk actions
            isSelected = isActive,
            onSelect = {
                onActivate()
                setShowMenu(true)
            },
            hasDropdown = true,
            contentDescription = "Task bulk actions"
        )

        if (showMenu) {
            Popup(
                offset = IntOffset(0, convertDpToPixel(EInkTheme.PopupMenu.offsetY, context).toInt()) + LocalNestedPopupOffset.current,
                onDismissRequest = { setShowMenu(false) },
                properties = PopupProperties(focusable = false),
                alignment = Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier
                        .background(EInkTheme.PopupMenu.background)
                        .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                        .width(EInkTheme.PopupMenu.width)
                ) {
                    TaskBulkActionsMenuItems(
                        onMoveTasks = onMoveTasks,
                        onCopyTasks = onCopyTasks,
                        onDeleteTasks = onDeleteTasks,
                        onConvertHandwriting = onConvertHandwriting,
                        onStartDayReview = onStartDayReview,
                        crossOutEnabled = crossOutEnabled,
                        onCrossOutToggle = onCrossOutToggle,
                        onDone = { setShowMenu(false) }
                    )
                }
            }
        }
    }
}

/**
 * The task actions menu's items, shared by the toolbar dropdown and the toolbar menu (≡) when task
 * actions are not on the bar (Oct 2026). [onDone] closes whichever menu shows them.
 */
@Composable
fun TaskBulkActionsMenuItems(
    onMoveTasks: () -> Unit,
    onCopyTasks: () -> Unit,
    onDeleteTasks: () -> Unit,
    onConvertHandwriting: () -> Unit,
    onStartDayReview: (() -> Unit)?,
    crossOutEnabled: Boolean,
    onCrossOutToggle: ((Boolean) -> Unit)?,
    onDone: () -> Unit
) {
    // Move tasks
    TaskBulkMenuItem(
        icon = Icons.AutoMirrored.Filled.DriveFileMove,
        label = "Move tasks...",
        onClick = {
            onDone()
            onMoveTasks()
        }
    )

    DividerCentered()

    // Copy tasks
    TaskBulkMenuItem(
        icon = Icons.Default.ContentCopy,
        label = "Copy tasks...",
        onClick = {
            onDone()
            onCopyTasks()
        }
    )

    DividerCentered()

    // Delete tasks
    TaskBulkMenuItem(
        icon = Icons.Default.DeleteSweep,
        label = "Delete tasks...",
        isDestructive = true,
        onClick = {
            onDone()
            onDeleteTasks()
        }
    )

    DividerCentered()

    // Convert handwriting to text
    TaskBulkMenuItem(
        icon = Icons.Default.TextFields,
        label = "Convert handwriting...",
        onClick = {
            onDone()
            onConvertHandwriting()
        }
    )

    if (onStartDayReview != null) {
        DividerCentered()

        // Start-the-day review (plan T4): unfinished tasks from earlier days
        TaskBulkMenuItem(
            icon = Icons.Default.WbSunny,
            label = "Start-the-day review",
            onClick = {
                onDone()
                onStartDayReview()
            }
        )
    }

    if (onCrossOutToggle != null) {
        DividerCentered()

        // Cross out = done (plan T4): a line through a task checks it; the menu stays
        // open so the new state shows
        TaskBulkMenuItem(
            icon = if (crossOutEnabled) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            label = "Cross out = done",
            onClick = { onCrossOutToggle(!crossOutEnabled) }
        )
    }
}

@Composable
private fun DividerCentered() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp), // Add small vertical breathing room
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.5f)
                .height(EInkTheme.Divider.subtleThickness)
                .background(EInkTheme.Divider.subtleColor)
        )
    }
}

@Composable
private fun TaskBulkMenuItem(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    // E-Ink: Use black for all text (no red for destructive)
    val contentColor = EInkTheme.MenuItem.textColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .noRippleClickable { onClick() }  // e-ink: no ripple
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = contentColor
        )
        Text(
            text = label,
            fontSize = EInkTheme.MenuItem.fontSize,
            color = contentColor
        )
    }
}
