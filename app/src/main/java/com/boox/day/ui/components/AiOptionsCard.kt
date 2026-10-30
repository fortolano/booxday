package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.AiPreferencesRepository
import com.boox.day.data.ai.SuggestionCount
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
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
            detail = "Lighter plans and suggestions while you're away. Turn it off when you're back.",
            checked = current.vacationMode,
            enabled = enabled,
            onChange = { on -> change { it.copy(vacationMode = on) } }
        )
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
