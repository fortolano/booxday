package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.boox.day.ui.theme.EInkTheme

/**
 * Maintenance mode banner - E-Ink optimized.
 *
 * Displays at top of screen when server reports maintenance.active=true.
 * Informational only - app remains functional in offline mode.
 */
@Composable
fun MaintenanceBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Warning.background)
            .border(
                width = EInkTheme.Warning.borderWidth,
                color = EInkTheme.Warning.borderColor
            )
            .padding(
                horizontal = EInkTheme.Global.padding,
                vertical = EInkTheme.Global.paddingSmall
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.spacingSmall)
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = "Maintenance",
            modifier = Modifier.size(EInkTheme.Global.iconSizeSmall),
            tint = EInkTheme.Warning.iconColor
        )
        Text(
            text = message,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = EInkTheme.Warning.textColor,
            modifier = Modifier.weight(1f)
        )
    }
}
