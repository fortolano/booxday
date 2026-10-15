package com.boox.day.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.delay

/**
 * E-ink friendly loading indicator using animated dots.
 *
 * CircularProgressIndicator doesn't work on e-ink displays due to slow refresh rates.
 * This component shows 3 dots that fill in sequentially (left-to-right),
 * creating a clear loading animation that works with e-ink's ~200-300ms refresh.
 *
 * Animation cycle (500ms per frame):
 * - Frame 0: · · · (all gray)
 * - Frame 1: . · · (first black)
 * - Frame 2: . . · (first two black)
 * - Frame 3: . . . (all black)
 *
 * @param modifier Modifier for the row container
 * @param fontSize Size of the dots (default 48sp for large display, use 16sp for inline)
 * @param spacing Space between dots (default 12dp for large, use 4dp for inline)
 * @param activeColor Color for active/visible dots
 * @param inactiveColor Color for inactive/placeholder dots
 */
@Composable
fun EinkLoadingDots(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = EInkTheme.AiProcessingFeedback.dotsFontSize,
    spacing: Dp = EInkTheme.AiProcessingFeedback.dotsSpacing,
    activeColor: Color = EInkTheme.AiProcessingFeedback.dotsActiveColor,
    inactiveColor: Color = EInkTheme.AiProcessingFeedback.dotsInactiveColor
) {
    // 0 = all gray, 1 = first black, 2 = first two black, 3 = all black
    var dotCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotCount = (dotCount + 1) % 4
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        // Dot 1 - visible when dotCount >= 1
        Text(
            text = ".",
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = if (dotCount >= 1) activeColor else inactiveColor
        )
        // Dot 2 - visible when dotCount >= 2
        Text(
            text = ".",
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = if (dotCount >= 2) activeColor else inactiveColor
        )
        // Dot 3 - visible when dotCount >= 3
        Text(
            text = ".",
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = if (dotCount >= 3) activeColor else inactiveColor
        )
    }
}

/**
 * Compact version of EinkLoadingDots for inline use (next to text, in buttons).
 * Uses smaller font size and spacing suitable for inline display.
 */
@Composable
fun EinkLoadingDotsInline(
    modifier: Modifier = Modifier,
    color: Color = Color.Black
) {
    EinkLoadingDots(
        modifier = modifier,
        fontSize = 16.sp,
        spacing = 2.dp,
        activeColor = color,
        inactiveColor = color.copy(alpha = 0.3f)
    )
}
