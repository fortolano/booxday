package com.boox.day.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.ui.CalendarAiNotice
import com.boox.day.ui.components.EinkSecondaryButton
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/** OpenRouter's privacy settings: the Zero Data Retention switches (checked Oct 2026). */
private const val OPENROUTER_PRIVACY_URL = "https://openrouter.ai/settings/privacy"

/**
 * MainScreen's host for [CalendarAiNoticePopup]: shown while [CalendarAiNotice] is open. "Open AI
 * Settings" closes it without running the feature and calls [onOpenAiSettings]. Its own composable
 * keeps MainScreen's root lambda small.
 */
@Composable
fun CalendarAiNoticeHost(onOpenAiSettings: () -> Unit) {
    if (CalendarAiNotice.isOpen) {
        CalendarAiNoticePopup(
            onNotNow = { CalendarAiNotice.onCancel() },
            onContinue = { CalendarAiNotice.onContinue() },
            onOpenAiSettings = {
                CalendarAiNotice.onCancel()
                onOpenAiSettings()
            }
        )
    }
}

/**
 * "AI and your calendar" (Oct 2026): before the first AI feature that sends the calendar to
 * OpenRouter, says what is sent and how the user can keep it private. Disclosure only; Continue runs
 * the feature with the user's current models (see [CalendarAiNotice]).
 */
@Composable
fun CalendarAiNoticePopup(
    onNotNow: () -> Unit,
    onContinue: () -> Unit,
    onOpenAiSettings: () -> Unit
) {
    val context = LocalContext.current
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onNotNow,
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(EInkTheme.PopupOverlay.width)
                    .heightIn(max = EInkTheme.PopupOverlay.maxHeight)
                    .background(EInkTheme.PopupOverlay.background, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .border(EInkTheme.PopupOverlay.borderWidth, EInkTheme.PopupOverlay.borderColor, RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius))
                    .padding(EInkTheme.PopupOverlay.padding),
                verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = EInkTheme.Colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "AI and your calendar",
                        fontSize = EInkTheme.PopupOverlay.titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = EInkTheme.PopupOverlay.titleColor
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)
                ) {
                    NoticeText(
                        "Optimize My Day, Daily Summary, Weekly Summary and Smart Suggestions send your events (titles, times, " +
                            "places, descriptions and the email addresses of invited people) with your tasks " +
                            "and notes to OpenRouter, on your OpenRouter account, and to the company that runs " +
                            "the AI model. Smart Suggestions and Optimize My Day also send your AI options (how many " +
                            "suggestions, Vacation, health and social ideas)."
                    )
                    NoticeText("Free models may keep what they are sent. To keep your calendar private:")
                    NoticeStep(
                        lead = "1. Use paid models.",
                        detail = " With credit on your OpenRouter account, BooxDay uses paid models. You can " +
                            "also pick a model in AI Settings → AI Models."
                    )
                    NoticeStep(
                        lead = "2. Turn on Zero Data Retention",
                        detail = " in your OpenRouter account's privacy settings, so only providers that keep " +
                            "no copy answer."
                    )
                    NoticeLink(text = "openrouter.ai/settings/privacy", icon = true) {
                        OpenRouterLinks.open(context, OPENROUTER_PRIVACY_URL)
                    }
                    NoticeText("Or keep using free models. BooxDay shows this once.")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
                ) {
                    EinkSecondaryButton(
                        text = "Not Now",
                        onClick = onNotNow,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = "Continue",
                        onClick = onContinue,
                        modifier = Modifier.weight(1f)
                    )
                }
                NoticeLink(text = "Open AI Settings", icon = false, onClick = onOpenAiSettings)
            }
        }
    }
}

@Composable
private fun NoticeText(text: String) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        color = EInkTheme.Colors.primary
    )
}

@Composable
private fun NoticeStep(lead: String, detail: String) {
    Text(
        text = buildAnnotatedString {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(lead)
            pop()
            append(detail)
        },
        fontSize = EInkTheme.Typography.bodyMedium,
        color = EInkTheme.Colors.primary
    )
}

@Composable
private fun NoticeLink(text: String, icon: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EInkTheme.Global.touchTarget)
            .noRippleClickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing)
    ) {
        if (icon) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open in browser",
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyMedium,
            color = EInkTheme.Colors.primary,
            textDecoration = TextDecoration.Underline
        )
    }
}
