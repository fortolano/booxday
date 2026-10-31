package com.boox.day.editor.ui.toolbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.boox.day.editor.DrawCanvas
import com.boox.day.ui.FullScreenState
import com.boox.day.ui.theme.EInkTheme

/**
 * Full screen switch (Oct 2026), just before ≡ on both toolbars ([FullScreenState]; in ≡ where the bar
 * has no room, and on the regular bar's Tasks tab, where Task AI takes its place): expand shows
 * the toolbar and canvas on the whole screen, collapse brings the calendar back. In the normal view
 * a small down arrow under the icon says the memo page has more ink below the visible area. It is on
 * the toolbar, not on the canvas, so the pen can tap it (on the canvas the pen draws).
 */
@Composable
fun FullScreenToolbarButton() {
    val isOn = FullScreenState.isOn
    val moreBelow = !isOn && FullScreenState.memoHasMoreBelow
    val buttonSize = EInkTheme.Toolbar.buttonSizeMedium * LocalToolbarIconScale.current
    Box(modifier = Modifier.size(buttonSize)) {
        ToolbarButton(
            modifier = Modifier.size(buttonSize),
            onSelect = {
                DrawCanvas.toolbarInteraction.tryEmit(Unit)  // closes the expanded events panel
                FullScreenState.toggle()
            },
            vectorIcon = if (isOn) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull,
            contentDescription = if (isOn) "normal view" else if (moreBelow) "full screen, more below" else "full screen"
        )
        if (moreBelow) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 1.dp)
                    .size(width = 10.dp, height = 6.dp)
            ) {
                val arrow = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(arrow, EInkTheme.Colors.primary)
            }
        }
    }
}
