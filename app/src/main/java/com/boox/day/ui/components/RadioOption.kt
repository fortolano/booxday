package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * E-Ink optimized radio option composable.
 * Uses noRippleClickable and EInkTheme constants.
 */
@Composable
fun RadioOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .noRippleClickable(onClick = onClick)
            .padding(vertical = EInkTheme.Global.spacingSmall, horizontal = EInkTheme.Global.spacingSmall)
    ) {
        // Custom radio button (e-ink friendly)
        Box(
            modifier = Modifier
                .size(EInkTheme.Checkbox.size)
                .border(EInkTheme.Checkbox.borderWidth, EInkTheme.Checkbox.borderColor, CircleShape)
                .padding(EInkTheme.Global.spacingSmall),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(EInkTheme.Global.paddingMedium)
                        .clip(CircleShape)
                        .background(EInkTheme.Checkbox.checkedBackground)
                )
            }
        }

        Spacer(modifier = Modifier.width(EInkTheme.Global.spacingSmall))

        Text(
            text = label,
            fontSize = EInkTheme.Preferences.radioLabelFontSize,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = EInkTheme.Colors.primary
        )
    }
}
