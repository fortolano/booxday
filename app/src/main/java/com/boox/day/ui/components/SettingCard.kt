package com.boox.day.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.ui.theme.EInkTheme

/**
 * Reusable settings card for grouping related settings with a title and description.
 * E-Ink optimized: No shadows, uses EInkTheme constants.
 */
@Composable
fun SettingCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = EInkTheme.Colors.background,
        border = BorderStroke(0.5.dp, EInkTheme.Colors.borderStrong),
        elevation = 0.dp,
        shape = RoundedCornerShape(EInkTheme.Global.cornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EInkTheme.Global.paddingMedium)
        ) {
            Text(
                text = title,
                fontSize = EInkTheme.Preferences.cardTitleFontSize,
                fontWeight = FontWeight.SemiBold,
                color = EInkTheme.Colors.primary
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

            Text(
                text = description,
                fontSize = EInkTheme.Preferences.cardDescriptionFontSize,
                color = EInkTheme.Colors.darkGray
            )

            Spacer(modifier = Modifier.height(EInkTheme.Global.spacingSmall))

            content()
        }
    }
}
