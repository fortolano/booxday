package com.boox.day.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.ui.theme.EInkTheme

/**
 * A scrollable container that shows a "scroll for more" indicator
 * when there's more content below the visible area.
 *
 * E-Ink optimized: Clear visual cue with down arrow and text.
 * The indicator disappears when user scrolls to the bottom.
 */
@Composable
fun ScrollableWithIndicator(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit
) {
    // Detect if there's more content to scroll
    val canScrollForward by remember {
        derivedStateOf { scrollState.canScrollForward }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            content()
        }

        // "Scroll for more" indicator at bottom (no animation for E-Ink)
        if (canScrollForward) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                ScrollMoreIndicator()
            }
        }
    }
}

/**
 * Visual indicator showing there's more content below.
 * Features a down arrow and "Scroll for more" text.
 *
 * E-Ink optimized: Uses solid white background instead of gradient.
 * Gradients cause dithering artifacts on e-ink displays.
 * Also used by the toolbar menu (≡) when it is taller than the space below the bar (Oct 2026).
 */
@Composable
internal fun ScrollMoreIndicator() {
    // Solid white background - NO gradient (causes dithering on E-Ink)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.white)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(EInkTheme.Colors.offWhite)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll down",
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Scroll for more",
                fontSize = EInkTheme.Typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.darkGray
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = EInkTheme.Colors.darkGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
