package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.boox.day.data.datastore.AppSettings
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Interval selection chip for tip frequency settings.
 * E-Ink optimized: Uses EInkTheme constants, noRippleClickable, proper 48dp touch target.
 */
@Composable
fun IntervalChip(
    interval: AppSettings.TipInterval,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shortLabel = when (interval) {
        AppSettings.TipInterval.EVERY_15_MINUTES -> "15m"
        AppSettings.TipInterval.EVERY_30_MINUTES -> "30m"
        AppSettings.TipInterval.EVERY_HOUR -> "1h"
        AppSettings.TipInterval.EVERY_4_HOURS -> "4h"
        AppSettings.TipInterval.ONCE_A_DAY -> "1d"
    }

    Box(
        modifier = modifier
            .height(EInkTheme.Global.touchTarget)
            .background(
                if (isSelected) EInkTheme.Colors.primary else EInkTheme.Colors.background
            )
            .border(
                EInkTheme.Global.borderWidth,
                EInkTheme.Colors.primary,
                RoundedCornerShape(EInkTheme.ModernDialog.cornerRadius)
            )
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = EInkTheme.Global.paddingSmall),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = shortLabel,
            fontSize = EInkTheme.Preferences.chipFontSize,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) EInkTheme.Colors.white else EInkTheme.Colors.primary
        )
    }
}
