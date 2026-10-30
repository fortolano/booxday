package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.AiPreferencesRepository
import com.boox.day.data.ai.SuggestionCount
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI options in AI Settings (29 Oct 2026): the three AI Preferences answers that measurably changed
 * the AI's answers (data/ai/AiPreferences.keptOptions), in place of the five-tab questionnaire. How
 * many suggestions, Vacation mode, and the Health and Social suggestion switches. Saved at once.
 */
@Composable
fun AiOptionsCard(aiEnabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AiPreferencesRepository(context.applicationContext) }
    var options by remember { mutableStateOf<AiPreferences?>(null) }
    LaunchedEffect(Unit) { options = withContext(Dispatchers.IO) { repository.getPreferences() } }

    fun change(update: (AiPreferences) -> AiPreferences) {
        val current = options ?: return
        val updated = update(current)
        options = updated
        scope.launch(Dispatchers.IO) { repository.savePreferences(updated) }
    }

    SettingCard(
        title = "AI options",
        description = "How Smart Suggestions and Optimize My Day work for you.",
        modifier = modifier
    ) {
        val current = options ?: return@SettingCard
        AiOptionsContent(current, aiEnabled) { update -> change(update) }
    }
}

/** The card's controls; its own composable for the render test. */
@Composable
internal fun AiOptionsContent(current: AiPreferences, enabled: Boolean, change: ((AiPreferences) -> AiPreferences) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingMedium)) {
        Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
            OptionTitle("Number of suggestions", enabled)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
            ) {
                for ((count, label) in listOf(
                    SuggestionCount.MINIMAL to "Fewer",
                    SuggestionCount.MODERATE to "Normal",
                    SuggestionCount.COMPREHENSIVE to "More"
                )) {
                    CountChip(
                        label = label,
                        selected = current.suggestionCount == count,
                        enabled = enabled,
                        onClick = { change { it.copy(suggestionCount = count) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        OptionSwitch(
            title = "Vacation mode",
            detail = "Lighter plans and suggestions while you're away. It turns itself off after the last day.",
            checked = current.vacationMode,
            enabled = enabled,
            // Oct 2026: a week by default, so it can't stay on unnoticed
            onChange = { on ->
                change {
                    it.copy(vacationMode = on, vacationUntil = if (on) LocalDate.now().plusDays(6).toString() else null)
                }
            }
        )
        if (current.vacationMode) {
            VacationEnd(
                until = current.vacationUntil,
                enabled = enabled,
                onChange = { until -> change { it.copy(vacationUntil = until) } }
            )
        }
        OptionSwitch(
            title = "Health suggestions",
            detail = "Ideas like exercise, sleep and a walk.",
            checked = current.includeHealthReminders,
            enabled = enabled,
            onChange = { on -> change { it.copy(includeHealthReminders = on) } }
        )
        OptionSwitch(
            title = "Social suggestions",
            detail = "Ideas to call, meet or catch up with people.",
            checked = current.includeSocialReminders,
            enabled = enabled,
            onChange = { on -> change { it.copy(includeSocialReminders = on) } }
        )
        if (!enabled) {
            Text(
                text = "Turn on AI Features above to change these.",
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.gray
            )
        }
    }
}

@Composable
private fun OptionTitle(text: String, enabled: Boolean) {
    Text(
        text = text,
        fontSize = EInkTheme.Typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray
    )
}

@Composable
private fun OptionSwitch(title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            OptionTitle(title, enabled)
            Text(
                text = detail,
                fontSize = EInkTheme.Preferences.explanationFontSize,
                color = EInkTheme.Colors.darkGray
            )
        }
        EInkToggle(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** "Until Wed 5 Nov" with a day less / a day more (44 dp each), and "No end date" / "Set an end date". */
@Composable
private fun VacationEnd(until: String?, enabled: Boolean, onChange: (String?) -> Unit) {
    val today = LocalDate.now()
    val last = until?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    Column(verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)) {
        if (last != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepButton("A day less", Icons.Default.Remove, enabled && last.isAfter(today)) {
                    onChange(last.minusDays(1).toString())
                }
                Text(
                    text = "Until ${vacationDayLabel(last)}",
                    modifier = Modifier.weight(1f),
                    fontSize = EInkTheme.Typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                    textAlign = TextAlign.Center
                )
                StepButton("A day more", Icons.Default.Add, enabled) { onChange(last.plusDays(1).toString()) }
            }
        } else {
            Text(
                text = "On until you turn it off",
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = EInkTheme.Global.touchTarget)
                .noRippleClickable(enabled = enabled) {
                    onChange(if (last != null) null else today.plusDays(6).toString())
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (last != null) "No end date" else "Set an end date",
                fontSize = EInkTheme.Typography.bodyMedium,
                color = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}

@Composable
private fun StepButton(description: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    Box(
        modifier = Modifier
            .size(EInkTheme.Global.touchTarget)
            .border(EInkTheme.Global.borderWidth, if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray, shape)
            .noRippleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray)
    }
}

/** "Wed 5 Nov"; with the year when it isn't this year. */
internal fun vacationDayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String {
    val pattern = if (day.year == today.year) "EEE d MMM" else "EEE d MMM yyyy"
    return day.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))  // the app is in English
}

/** One of Fewer / Normal / More: 44 dp, black when picked. */
@Composable
private fun CountChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
    Box(
        modifier = modifier
            .height(EInkTheme.Global.touchTarget)
            .background(if (selected && enabled) EInkTheme.Colors.primary else EInkTheme.Colors.background, shape)
            .border(EInkTheme.Global.borderWidth, if (enabled) EInkTheme.Colors.primary else EInkTheme.Colors.gray, shape)
            .noRippleClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = EInkTheme.Global.paddingSmall),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                selected && enabled -> EInkTheme.Colors.white
                enabled -> EInkTheme.Colors.primary
                else -> EInkTheme.Colors.gray
            }
        )
    }
}
