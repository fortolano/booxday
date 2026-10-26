package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * E-ink optimized button components for BooxDay.
 * High contrast black/white design for optimal readability on e-ink displays.
 * Uses EInkTheme tokens for consistency across the app.
 */

/**
 * Primary action button - DarkGray background with white text (ModernDialog style).
 * Use for primary/confirm actions.
 */
@Composable
fun EinkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 100.dp,
    height: Dp = EInkTheme.Button.height
) {
    val backgroundColor = if (enabled) EInkTheme.ModernDialog.PrimaryButton.background else EInkTheme.Button.disabledBackground
    val textColor = if (enabled) EInkTheme.ModernDialog.PrimaryButton.textColor else EInkTheme.Button.disabledTextColor
    val borderColor = if (enabled) EInkTheme.ModernDialog.PrimaryButton.background else EInkTheme.Button.disabledBorderColor

    Box(
        modifier = modifier
            .height(height)
            .widthIn(min = width)
            .background(backgroundColor, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
            .border(EInkTheme.ModernDialog.borderWidth, borderColor, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
            .noRippleClickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = EInkTheme.Button.fontSize,
            color = textColor
        )
    }
}

/**
 * Secondary action button - White background with light gray border (ModernDialog style).
 * Use for secondary/cancel actions.
 */
@Composable
fun EinkSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 100.dp,
    height: Dp = EInkTheme.Button.height
) {
    val backgroundColor = EInkTheme.ModernDialog.background
    val textColor = if (enabled) EInkTheme.ModernDialog.CancelLink.textColor else EInkTheme.Button.disabledTextColor
    val borderColor = if (enabled) EInkTheme.ModernDialog.borderColor else EInkTheme.Button.disabledBorderColor

    Box(
        modifier = modifier
            .height(height)
            .widthIn(min = width)
            .background(backgroundColor, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
            .border(EInkTheme.ModernDialog.borderWidth, borderColor, RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius))
            .noRippleClickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = EInkTheme.Button.fontSize,
            color = textColor
        )
    }
}
