package com.boox.day.editor.ui.toolbar

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
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.db.CalendarEvent
import com.boox.day.meetings.MeetingPages
import com.boox.day.meetings.MeetingRecapUi
import com.boox.day.ui.AiKeyGate
import com.boox.day.ui.LocalNestedPopupOffset
import com.boox.day.ui.SnackConf
import com.boox.day.ui.SnackState
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.convertDpToPixel
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import com.boox.day.ui.views.EditorTab

/**
 * Unified Contextual AI Menu (Phase 4 / Decision D3 P3 Option B):
 * - One sparkles mental model across Memos, Tasks, Go 6, and Go 10.3.
 * - Extracted outside [SharedToolbar] to keep SharedToolbar well under ART's 16,383-unit JIT ceiling.
 * - Sectioned into Create / Understand / Review, contextual to the active [EditorTab].
 * - Self-teaching disabled taps:
 *   - When AI Features is OFF, the sparkles button remains tappable (visually muted, no alpha) and
 *     opens the menu with an "AI is off" banner + "Open AI Settings" action; tapping any item routes
 *     to AI Settings instead of being a silent dead end.
 *   - When AI Features is ON without a saved key, tapping any action routes through [AiKeyGate.withKey].
 *   - When a Memos action needs page content on an empty page, tapping shows a brief guidance snackbar.
 */
@Composable
fun UnifiedAiToolbarButton(
    tab: EditorTab,
    aiEnabled: Boolean,
    hasSelectedStrokes: Boolean = false,
    hasPageContent: Boolean = false,
    hasTextAnnotations: Boolean = false,
    pageId: String? = null,
    onVoiceInput: () -> Unit = {},
    onRecognizePage: () -> Unit = {},
    onSummarize: () -> Unit = {},
    onTranslate: () -> Unit = {},
    onConvertMemoToTasks: () -> Unit = {},
    onPlanMyDay: () -> Unit = {},
    onSortByPriority: () -> Unit = {},
    onSmartSuggestions: () -> Unit = {},
    onTaskBreakdown: () -> Unit = {},
    onDailySummary: () -> Unit = {},
    onWeeklySummary: () -> Unit = {},
    onOpenAiSettings: () -> Unit = { AiKeyGate.requestOpenAiSettings() },
    onMenuOpenChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onActivate: () -> Unit = {},
    isOpen: Boolean = false,
    onOpenChange: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current

    var internalMenuOpen by remember { mutableStateOf(false) }
    val isMenuOpen = if (onOpenChange != null) isOpen else internalMenuOpen
    val setMenuOpen: (Boolean) -> Unit = { value ->
        if (onOpenChange != null) onOpenChange(value) else internalMenuOpen = value
    }

    if (onMenuOpenChange != null) {
        LaunchedEffect(isMenuOpen) {
            onMenuOpenChange(isMenuOpen)
        }
    }

    Box(modifier = modifier) {
        ToolbarButton(
            iconId = R.drawable.ai_sparkles_text,
            contentDescription = if (aiEnabled) "AI Tools" else "AI Tools (off)",
            isSelected = isActive && aiEnabled,
            enabled = true,
            muted = !aiEnabled,
            hasDropdown = true,
            iconSize = 36.dp,
            iconPadding = 1,
            onSelect = {
                onActivate()
                setMenuOpen(true)
            }
        )

        if (isMenuOpen) {
            Popup(
                offset = IntOffset(0, convertDpToPixel(EInkTheme.PopupMenu.offsetY, context).toInt()) + LocalNestedPopupOffset.current,
                onDismissRequest = { setMenuOpen(false) },
                properties = PopupProperties(focusable = false),
                alignment = Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier
                        .width(EInkTheme.PopupMenu.settingsWidth)
                        .background(EInkTheme.PopupMenu.background)
                        .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                ) {
                    UnifiedAiMenuItems(
                        tab = tab,
                        aiEnabled = aiEnabled,
                        hasSelectedStrokes = hasSelectedStrokes,
                        hasPageContent = hasPageContent,
                        hasTextAnnotations = hasTextAnnotations,
                        pageId = pageId,
                        onVoiceInput = onVoiceInput,
                        onRecognizePage = onRecognizePage,
                        onSummarize = onSummarize,
                        onTranslate = onTranslate,
                        onConvertMemoToTasks = onConvertMemoToTasks,
                        onPlanMyDay = onPlanMyDay,
                        onSortByPriority = onSortByPriority,
                        onSmartSuggestions = onSmartSuggestions,
                        onTaskBreakdown = onTaskBreakdown,
                        onDailySummary = onDailySummary,
                        onWeeklySummary = onWeeklySummary,
                        onOpenAiSettings = onOpenAiSettings,
                        onDone = { setMenuOpen(false) }
                    )
                }
            }
        }
    }
}

/**
 * Shared contextual AI menu body used by both [UnifiedAiToolbarButton] and the overflow menu (≡).
 */
@Composable
fun UnifiedAiMenuItems(
    tab: EditorTab,
    aiEnabled: Boolean,
    hasSelectedStrokes: Boolean = false,
    hasPageContent: Boolean = false,
    hasTextAnnotations: Boolean = false,
    pageId: String? = null,
    onVoiceInput: () -> Unit = {},
    onRecognizePage: () -> Unit = {},
    onSummarize: () -> Unit = {},
    onTranslate: () -> Unit = {},
    onConvertMemoToTasks: () -> Unit = {},
    onPlanMyDay: () -> Unit = {},
    onSortByPriority: () -> Unit = {},
    onSmartSuggestions: () -> Unit = {},
    onTaskBreakdown: () -> Unit = {},
    onDailySummary: () -> Unit = {},
    onWeeklySummary: () -> Unit = {},
    onOpenAiSettings: () -> Unit = { AiKeyGate.requestOpenAiSettings() },
    onDone: () -> Unit
) {
    if (!aiEnabled) {
        AiOffCallout(
            onOpenSettings = {
                onDone()
                onOpenAiSettings()
            }
        )
        Divider(color = EInkTheme.Divider.color, thickness = EInkTheme.Divider.thickness)
    }

    fun runWhenContentReady(ready: Boolean, emptyHint: String, action: () -> Unit) {
        onDone()
        if (!aiEnabled) {
            onOpenAiSettings()
            return
        }
        if (!ready) {
            SnackState.globalSnackFlow.tryEmit(
                SnackConf(text = emptyHint, duration = SnackConf.DURATION_STANDARD)
            )
            return
        }
        action()
    }

    fun runAi(action: () -> Unit) {
        onDone()
        if (!aiEnabled) {
            onOpenAiSettings()
            return
        }
        action()
    }

    if (tab == EditorTab.Memos) {
        AiMenuSectionHeader("Create")
        AiDrawableRow(
            label = "Voice to Text",
            iconRes = R.drawable.speech_to_text_40px,
            enabled = aiEnabled,
            onClick = { runAi { AiKeyGate.withKey(onVoiceInput) } }
        )
        AiRowDivider()
        AiDrawableRow(
            label = "Convert Memo to Tasks",
            iconRes = R.drawable.ai_memo_to_task_lasso,
            enabled = aiEnabled && (hasPageContent || hasSelectedStrokes),
            onClick = {
                runWhenContentReady(
                    ready = hasPageContent || hasSelectedStrokes,
                    emptyHint = "Write on this page first to convert it to tasks",
                    action = onConvertMemoToTasks
                )
            }
        )

        Divider(color = EInkTheme.Divider.color, thickness = EInkTheme.Divider.thickness)
        AiMenuSectionHeader("Understand")
        AiDrawableRow(
            label = "Recognize Handwriting",
            iconRes = R.drawable.convert_to_text,
            enabled = aiEnabled && hasPageContent,
            onClick = {
                runWhenContentReady(
                    ready = hasPageContent,
                    emptyHint = "Write on this page first to recognize handwriting",
                    action = { AiKeyGate.withKey(onRecognizePage) }
                )
            }
        )
        AiRowDivider()
        AiVectorRow(
            label = "Summarize",
            icon = Icons.Default.Summarize,
            enabled = aiEnabled && (hasPageContent || hasTextAnnotations),
            onClick = {
                runWhenContentReady(
                    ready = hasPageContent || hasTextAnnotations,
                    emptyHint = "Add handwriting or text on this page first to summarize",
                    action = { AiKeyGate.withKey(onSummarize) }
                )
            }
        )
        AiRowDivider()
        AiVectorRow(
            label = "Translate",
            icon = Icons.Default.Translate,
            enabled = aiEnabled && (hasPageContent || hasTextAnnotations),
            onClick = {
                runWhenContentReady(
                    ready = hasPageContent || hasTextAnnotations,
                    emptyHint = "Add handwriting or text on this page first to translate",
                    action = { AiKeyGate.withKey(onTranslate) }
                )
            }
        )

        pageId?.let { id ->
            UnifiedMeetingRecapSection(
                pageId = id,
                aiEnabled = aiEnabled,
                onOpenAiSettings = onOpenAiSettings,
                onDone = onDone
            )
        }
    } else {
        AiMenuSectionHeader("Create")
        AiVectorRow(
            label = "Smart Suggestions",
            icon = Icons.Default.Lightbulb,
            enabled = aiEnabled,
            onClick = { runAi(onSmartSuggestions) }
        )
        AiRowDivider()
        AiVectorRow(
            label = "Break Down Task",
            icon = Icons.AutoMirrored.Filled.CallSplit,
            enabled = aiEnabled,
            onClick = { runAi(onTaskBreakdown) }
        )

        Divider(color = EInkTheme.Divider.color, thickness = EInkTheme.Divider.thickness)
        AiMenuSectionHeader("Understand")
        AiVectorRow(
            label = "Optimize My Day",
            icon = Icons.Default.AutoAwesome,
            enabled = aiEnabled,
            onClick = { runAi(onPlanMyDay) }
        )
        AiRowDivider()
        AiVectorRow(
            label = "Sort by Priority",
            icon = Icons.AutoMirrored.Filled.Sort,
            enabled = aiEnabled,
            onClick = { runAi(onSortByPriority) }
        )

        Divider(color = EInkTheme.Divider.color, thickness = EInkTheme.Divider.thickness)
        AiMenuSectionHeader("Review")
        AiVectorRow(
            label = "Daily Summary",
            icon = Icons.Default.Summarize,
            enabled = aiEnabled,
            onClick = { runAi(onDailySummary) }
        )
        AiRowDivider()
        AiVectorRow(
            label = "Weekly Summary",
            icon = Icons.Default.DateRange,
            enabled = aiEnabled,
            onClick = { runAi(onWeeklySummary) }
        )
    }
}

@Composable
private fun AiOffCallout(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.offWhite)
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "AI is off",
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = EInkTheme.Colors.primary
        )
        Text(
            text = "Turn on AI Features to use these tools on your OpenRouter account.",
            fontSize = EInkTheme.Typography.bodySmall,
            color = EInkTheme.Colors.darkGray
        )
        EinkSecondaryButton(
            text = "Open AI Settings",
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun UnifiedMeetingRecapSection(
    pageId: String,
    aiEnabled: Boolean,
    onOpenAiSettings: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var meeting by remember(pageId) { mutableStateOf<CalendarEvent?>(null) }
    LaunchedEffect(pageId) { meeting = MeetingPages.eventOf(context, pageId) }
    val event = meeting ?: return

    Divider(color = EInkTheme.Divider.color, thickness = EInkTheme.Divider.thickness)
    AiMenuSectionHeader("Review")
    AiVectorRow(
        label = "Meeting Recap",
        icon = Icons.Default.Summarize,
        enabled = aiEnabled,
        onClick = {
            onDone()
            if (!aiEnabled) onOpenAiSettings() else MeetingRecapUi.open(event)
        }
    )
}

@Composable
private fun AiMenuSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = EInkTheme.Colors.gray,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = EInkTheme.MenuItem.horizontalPadding,
                end = EInkTheme.MenuItem.horizontalPadding,
                top = 8.dp,
                bottom = 2.dp
            )
    )
}

@Composable
private fun AiRowDivider() {
    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)
}

@Composable
private fun AiDrawableRow(
    label: String,
    iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) EInkTheme.MenuItem.textColor else EInkTheme.MenuItem.textColorDisabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = contentColor
        )
        Text(
            text = label,
            fontSize = EInkTheme.MenuItem.fontSize,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun AiVectorRow(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) EInkTheme.MenuItem.textColor else EInkTheme.MenuItem.textColorDisabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            color = contentColor,
            maxLines = 1
        )
    }
}
