package com.boox.day.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.boox.day.data.ai.AiPreferences
import com.boox.day.data.ai.AiPreferencesRepository
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * "Vacation mode is on until Wed 5 Nov" (Oct 2026), shown in Smart Suggestions and Optimize My Day
 * while Vacation changes their answers, so it can't stay on unnoticed. Nothing when it's off.
 */
@Composable
fun VacationNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var options by remember { mutableStateOf<AiPreferences?>(null) }
    LaunchedEffect(Unit) {
        options = withContext(Dispatchers.IO) { AiPreferencesRepository(context.applicationContext).getPreferences() }
    }
    val current = options ?: return
    if (!current.vacationMode) return
    VacationNoticeText(vacationNoticeText(current.vacationUntil), modifier)
}

/** The notice's words: the last day when there is one. */
internal fun vacationNoticeText(until: String?, today: LocalDate = LocalDate.now()): String {
    val last = until?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val period = if (last != null) "until ${vacationDayLabel(last, today)}" else "until you turn it off"
    return "Vacation mode is on $period: lighter plans and suggestions. Change it in AI Settings → AI options."
}

@Composable
internal fun VacationNoticeText(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(EInkTheme.Global.borderWidth, EInkTheme.Colors.primary, RoundedCornerShape(EInkTheme.Global.cornerRadius))
            .padding(horizontal = EInkTheme.Global.paddingSmall, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.BeachAccess, contentDescription = null, tint = EInkTheme.Colors.primary, modifier = Modifier.size(20.dp))
        Text(text = text, fontSize = EInkTheme.Typography.bodySmall, color = EInkTheme.Colors.primary)
    }
}
