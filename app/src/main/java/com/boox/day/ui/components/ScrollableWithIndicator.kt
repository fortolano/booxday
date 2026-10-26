package com.boox.day.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 *
 * [wrapHeight] (Oct 2026): as tall as the content, up to the space given, for panels sized to
 * their content. Otherwise it fills the space given.
 */
@Composable
fun ScrollableWithIndicator(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    wrapHeight: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(modifier = if (wrapHeight) modifier else modifier.fillMaxSize()) {
        // Scrollable content
        Column(
            modifier = (if (wrapHeight) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .verticalScroll(scrollState)
        ) {
            content()
        }

        // "Scroll for more" indicator at bottom (no animation for E-Ink)
        ScrollMoreBar(scrollState)
    }
}

/**
 * The "Scroll for more" bar at the bottom of a [BoxScope], shown only while [state] has more below
 * (Oct 2026). Works for plain scrolling ([ScrollState]) and lists ([androidx.compose.foundation.lazy.LazyListState]):
 * put it in the Box that holds the scrolling content, after the content.
 */
@Composable
fun BoxScope.ScrollMoreBar(state: ScrollableState) {
    val canScrollForward by remember(state) { derivedStateOf { state.canScrollForward } }
    if (canScrollForward) {
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            ScrollMoreIndicator()
        }
    }
}

/**
 * Visual indicator showing there's more content below.
 * Features a down arrow and "Scroll for more" text.
 *
 * E-Ink optimized: solid white, no gradient (gradients dither on e-ink). Oct 2026 (owner): black on
 * white with a thin black line on top, like the rest of the meaning in the app; it was grey text on
 * an off-white chip, which users missed. About the same height as before (~43 dp).
 * Also used by the toolbar menu (≡) when it is taller than the space below the bar (Oct 2026).
 */
@Composable
internal fun ScrollMoreIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInkTheme.Colors.white)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(EInkTheme.Colors.primary)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll down",
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Scroll for more",
                fontSize = EInkTheme.Typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = EInkTheme.Colors.primary,
                // One line: in a narrow column (Help's topic list on the Go 6) the second arrow
                // gives way instead of the bar growing taller
                maxLines = 1,
                softWrap = false
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = EInkTheme.Colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
