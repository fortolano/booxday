package com.boox.day.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.ui.dialogs.ActionButton
import com.boox.day.ui.theme.EInkTheme

/**
 * One thing a feature needs before it can work (Oct 2026, plan B2): "Step n of m" when there are
 * several, a title, one sentence of why, and one button with a specific verb. The same look for
 * reminders (notifications, then the BOOX battery step) and the other features that need Android's
 * permission, so each asks the same way.
 */
@Composable
fun SetupStep(
    title: String,
    why: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    step: Int? = null,
    steps: Int? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.background,
        border = BorderStroke(1.dp, EInkTheme.Colors.primary),
        elevation = 0.dp,
        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
    ) {
        Column(
            modifier = Modifier.padding(EInkTheme.Global.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
        ) {
            if (step != null && steps != null && steps > 1) {
                Text(
                    text = "Step $step of $steps",
                    fontSize = EInkTheme.Typography.bodySmall,
                    color = EInkTheme.Colors.darkGray
                )
            }
            Text(
                text = title,
                fontSize = EInkTheme.Typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EInkTheme.Colors.primary
            )
            Text(
                text = why,
                fontSize = EInkTheme.Typography.bodyMedium,
                color = EInkTheme.Colors.primary
            )
            ActionButton(text = buttonText, onClick = onClick, modifier = Modifier.fillMaxWidth())
        }
    }
}
