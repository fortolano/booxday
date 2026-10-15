package com.boox.day.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.boox.day.ui.theme.EInkTheme

/**
 * E-Ink optimized toggle switch with clear visual distinction between ON and OFF states.
 *
 * Unlike Material Switch which is nearly invisible on E-Ink displays,
 * this toggle has:
 * - Solid black border always visible
 * - Clear black/white contrast between states
 * - "ON" / "OFF" labels inside for absolute clarity
 *
 * OFF state: White background, black border, "OFF" text, thumb on left
 * ON state: Black background, white "ON" text, thumb on right
 *
 * Uses EInkTheme.Toggle constants for consistent styling.
 */
@Composable
fun EInkToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(EInkTheme.Toggle.cornerRadius)

    val backgroundColor = when {
        !enabled -> EInkTheme.Toggle.disabledTrackColor
        checked -> EInkTheme.Toggle.trackColorOn
        else -> EInkTheme.Toggle.trackColorOff
    }

    val borderColor = when {
        !enabled -> EInkTheme.Toggle.disabledBorderColor
        else -> EInkTheme.Toggle.borderColor
    }

    val thumbColor = when {
        !enabled -> EInkTheme.Toggle.disabledThumbColor
        checked -> EInkTheme.Toggle.thumbColorOn
        else -> EInkTheme.Toggle.thumbColorOff
    }

    val textColor = when {
        !enabled -> EInkTheme.Toggle.disabledThumbColor
        checked -> EInkTheme.Toggle.labelColorOn
        else -> EInkTheme.Toggle.labelColor
    }

    Box(
        modifier = modifier
            .width(EInkTheme.Toggle.width)
            .height(EInkTheme.Toggle.height)
            .clip(shape)
            .background(backgroundColor, shape)
            .border(EInkTheme.Toggle.borderWidth, borderColor, shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.matchParentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (checked) {
                // ON state: Text on left, thumb on right
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = EInkTheme.Toggle.labelPadding),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "ON",
                        fontSize = EInkTheme.Toggle.labelFontSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(end = EInkTheme.Toggle.thumbPadding)
                        .size(EInkTheme.Toggle.thumbSize)
                        .clip(CircleShape)
                        .background(thumbColor, CircleShape)
                        .border(EInkTheme.Toggle.thumbBorderWidth, EInkTheme.Toggle.borderColor, CircleShape)
                )
            } else {
                // OFF state: Thumb on left, text on right
                Box(
                    modifier = Modifier
                        .padding(start = EInkTheme.Toggle.thumbPadding)
                        .size(EInkTheme.Toggle.thumbSize)
                        .clip(CircleShape)
                        .background(thumbColor, CircleShape)
                        .border(EInkTheme.Toggle.thumbBorderWidth, EInkTheme.Toggle.borderColor, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = EInkTheme.Toggle.labelPadding),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "OFF",
                        fontSize = EInkTheme.Toggle.labelFontSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }
}

/**
 * Compact E-Ink toggle without labels - for tighter spaces.
 *
 * Still maintains clear visual distinction:
 * OFF: White track, black border, black thumb on left
 * ON: Black track, white thumb on right
 *
 * Uses EInkTheme.Toggle compact constants for consistent styling.
 */
@Composable
fun EInkToggleCompact(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(EInkTheme.Toggle.compactCornerRadius)

    val backgroundColor = when {
        !enabled -> EInkTheme.Toggle.disabledTrackColor
        checked -> EInkTheme.Toggle.trackColorOn
        else -> EInkTheme.Toggle.trackColorOff
    }

    val borderColor = when {
        !enabled -> EInkTheme.Toggle.disabledBorderColor
        else -> EInkTheme.Toggle.borderColor
    }

    val thumbColor = when {
        !enabled -> EInkTheme.Toggle.disabledThumbColor
        checked -> EInkTheme.Toggle.thumbColorOn
        else -> EInkTheme.Toggle.thumbColorOff
    }

    Box(
        modifier = modifier
            .width(EInkTheme.Toggle.compactWidth)
            .height(EInkTheme.Toggle.compactHeight)
            .clip(shape)
            .background(backgroundColor, shape)
            .border(EInkTheme.Toggle.borderWidth, borderColor, shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = EInkTheme.Toggle.thumbPadding)
                .size(EInkTheme.Toggle.compactThumbSize)
                .clip(CircleShape)
                .background(thumbColor, CircleShape)
                .border(
                    EInkTheme.Toggle.thumbBorderWidth,
                    if (!enabled) EInkTheme.Toggle.disabledBorderColor else EInkTheme.Toggle.borderColor,
                    CircleShape
                )
        )
    }
}
