package com.boox.day.editor.ui.toolbar

import com.boox.day.ui.LocalNestedPopupOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.datastore.BUTTON_SIZE
import com.boox.day.ui.convertDpToPixel
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * AI tools button for Tasks mode.
 * Provides task-specific AI features:
 *
 * - Voice to Task: Voice input to create tasks (with date extraction)
 * - Sort by Priority: AI-powered task reordering
 * - Smart Suggestions: Context-aware task suggestions
 * - Task Breakdown: Expand complex tasks into subtasks
 * - Daily Summary: End-of-day task review
 * - Weekly Summary: 7-day task review with trends and patterns
 */
@Composable
fun TaskAiButton(
    onVoiceToTask: () -> Unit,
    onSortByPriority: () -> Unit = {},
    onSmartSuggestions: () -> Unit = {},
    onTaskBreakdown: () -> Unit = {},
    onDailySummary: () -> Unit = {},
    onWeeklySummary: () -> Unit = {},
    onPlanMyDay: () -> Unit = {},
    onMenuOpenChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
            iconId = R.drawable.ai_sparkles_text,
            isSelected = isActive && enabled,  // Don't show active state when disabled
            enabled = enabled,
            onSelect = {
                if (enabled) {
                    onActivate()
                    setShowMenu(true)
                }
            },
            hasDropdown = true,
            contentDescription = "AI tools for tasks",
            iconSize = 36.dp,  // 50% larger than default 24dp
            iconPadding = 1    // Reduce padding to give icon room to grow
        )

        // Only show popup when enabled AND menu is open
        if (enabled && showMenu) {
            Popup(
                offset = IntOffset(0, convertDpToPixel(EInkTheme.PopupMenu.offsetY, context).toInt()) + LocalNestedPopupOffset.current,
                onDismissRequest = { setShowMenu(false) },
                properties = PopupProperties(focusable = false),
                alignment = Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier
                        .width(EInkTheme.PopupMenu.width)
                        .background(EInkTheme.PopupMenu.background)
                        .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                ) {
                    TaskAiMenuItems(
                        onPlanMyDay = onPlanMyDay,
                        onSortByPriority = onSortByPriority,
                        onSmartSuggestions = onSmartSuggestions,
                        onTaskBreakdown = onTaskBreakdown,
                        onDailySummary = onDailySummary,
                        onWeeklySummary = onWeeklySummary,
                        onDone = { setShowMenu(false) }
                    )
                }
            }
        }
    }
}

/**
 * The Task AI menu's items, shared by the toolbar dropdown and the toolbar menu (≡) when Task AI is
 * not on the bar (Oct 2026). [onDone] closes whichever menu shows them.
 */
@Composable
fun TaskAiMenuItems(
    onPlanMyDay: () -> Unit,
    onSortByPriority: () -> Unit,
    onSmartSuggestions: () -> Unit,
    onTaskBreakdown: () -> Unit,
    onDailySummary: () -> Unit,
    onWeeklySummary: () -> Unit,
    onDone: () -> Unit
) {
    // ═══════════════════════════════════════════════════════
    // AGENTIC PLANNING (Flagship Feature)
    // ═══════════════════════════════════════════════════════

    // Optimize My Day - AI-powered day optimization
    TaskAiMenuItem(
        icon = Icons.Default.AutoAwesome,
        label = "Optimize My Day",
        onClick = {
            onDone()
            onPlanMyDay()
        }
    )

    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)

    // ═══════════════════════════════════════════════════════
    // PRIMARY FEATURES (Implemented)
    // ═══════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════
    // DISABLED FOR MVP LAUNCH - Voice to Task
    // This feature needs investigation before release.
    // Issues to investigate:
    // - Audio recording reliability on various Boox devices
    // - Gemini API quota handling edge cases
    // - Task parsing accuracy with complex voice input
    // Re-enable after post-MVP investigation is complete.
    // ═══════════════════════════════════════════════════════
    // Voice to Task - Voice input to create tasks
    // TaskAiMenuItem(
    //     icon = Icons.Default.Mic,
    //     label = "Voice to Task",
    //     onClick = {
    //         onDone()
    //         onVoiceToTask()
    //     }
    // )
    //
    // Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)

    // Sort by Priority - AI reorders all tasks for the day
    TaskAiMenuItem(
        icon = Icons.Default.Sort,
        label = "Sort by Priority",
        onClick = {
            onDone()
            onSortByPriority()
        }
    )

    // ═══════════════════════════════════════════════════════
    // COMING SOON (Stubs - gated by feature access)
    // ═══════════════════════════════════════════════════════

    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)

    // Smart Suggestions - Context-aware recommendations
    TaskAiMenuItem(
        icon = Icons.Default.Lightbulb,
        label = "Smart Suggestions",
        onClick = {
            onDone()
            onSmartSuggestions()
        }
    )

    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)

    // Task Breakdown - Split complex tasks into subtasks
    TaskAiMenuItem(
        icon = Icons.Default.CallSplit,
        label = "Break Down Task",
        onClick = {
            onDone()
            onTaskBreakdown()
        }
    )

    // ═══════════════════════════════════════════════════════
    // INSIGHTS & SUMMARIES
    // ═══════════════════════════════════════════════════════

    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)

    // Daily Summary - End-of-day task review and migration
    TaskAiMenuItem(
        icon = Icons.Default.Summarize,
        label = "Daily Summary",
        onClick = {
            onDone()
            onDailySummary()
        }
    )

    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)

    // Weekly Summary - 7-day trends and patterns review
    TaskAiMenuItem(
        icon = Icons.Default.DateRange,
        label = "Weekly Summary",
        onClick = {
            onDone()
            onWeeklySummary()
        }
    )
}

@Composable
private fun TaskAiMenuItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val textColor = if (enabled) EInkTheme.MenuItem.textColor else EInkTheme.MenuItem.textColorDisabled

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .then(if (enabled) Modifier.noRippleClickable { onClick() } else Modifier)
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = textColor
        )
        Text(
            text = label,
            fontSize = EInkTheme.MenuItem.fontSize,
            color = textColor
        )
    }
}
