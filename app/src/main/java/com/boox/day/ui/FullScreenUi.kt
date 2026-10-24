package com.boox.day.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boox.day.editor.DrawCanvas
import com.boox.day.editor.EditorTabManager
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Height of the full screen date line above the toolbar. */
val FULL_SCREEN_DATE_LINE_HEIGHT = 26.dp

/** The surface gets its new size well within this after a switch; past it, redraw anyway. */
private const val SURFACE_RESIZE_TIMEOUT_MS = 2_000L

/** Lets surfaceChanged's own redraw (DrawCanvas) finish before the final full redraw. */
private const val SURFACE_SETTLE_MS = 150L

/**
 * MainScreen's full screen housekeeping (Oct 2026, [FullScreenState]). Its own composable, outside
 * MainScreen's root lambda (at the JVM method-size limit):
 * - Back leaves full screen;
 * - before a switch, the active canvas closes a text box and puts down a lasso selection (the resize
 *   would drop both);
 * - entering calls [onEnter] (MainScreen closes the expanded events panel);
 * - once the canvas has its new size: one full raw-drawing cycle ([DrawCanvas.forceUpdate]: the
 *   protected close, redraw, wait, open, with the limit rect of the new size); leaving also lets the
 *   calendar reach the screen. No full e-ink refresh (GC flash): tried on the Go 6, the switch looked
 *   better without it (owner's call, Oct 2026).
 */
@Composable
fun FullScreenEffects(tabManager: EditorTabManager, onEnter: () -> Unit) {
    val isOn = FullScreenState.isOn
    BackHandler(enabled = isOn) { FullScreenState.collapse() }

    DisposableEffect(tabManager) {
        FullScreenState.beforeChange = {
            tabManager.activeControlTower?.let { tower ->
                tower.saveAndExitTextBox()
                val selection = tower.getSnapshotOfSelectionState()
                if (selection.selectedBitmap != null) {
                    tower.applySelectionDisplace()
                    selection.reset()
                    tower.setIsDrawing(true)
                }
            }
        }
        onDispose { FullScreenState.beforeChange = null }
    }

    val currentOnEnter = rememberUpdatedState(onEnter)
    var shownState by remember { mutableStateOf(isOn) }
    LaunchedEffect(isOn) {
        if (isOn == shownState) return@LaunchedEffect  // first composition: nothing switched
        shownState = isOn
        if (isOn) currentOnEnter.value()
        val before = DrawCanvas.surfaceSize.value
        withTimeoutOrNull(SURFACE_RESIZE_TIMEOUT_MS) {
            DrawCanvas.surfaceSize.first { it != null && it != before }
        }
        delay(SURFACE_SETTLE_MS)
        DrawCanvas.forceUpdate.emit(null)
        if (!isOn) DrawCanvas.forceUnfreeze()
    }
}

/**
 * Full screen only: the selected day above the toolbar, as the calendar header is hidden
 * (e.g. "Wednesday · 22 Oct 2026", the day header's words). Tapping it goes back to the calendar.
 */
@Composable
fun FullScreenDateLine(selectedDate: String) {
    if (!FullScreenState.isOn) return
    val text = remember(selectedDate) {
        runCatching {
            val date = LocalDate.parse(selectedDate)
            val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "$weekday · ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))}"
        }.getOrDefault(selectedDate)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(FULL_SCREEN_DATE_LINE_HEIGHT)
            .noRippleClickable { FullScreenState.collapse() }
    ) {
        Text(
            text = text,
            fontSize = EInkTheme.Typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = EInkTheme.Colors.primary,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = 12.dp)
        )
        Divider(color = EInkTheme.Colors.borderStrong, thickness = 0.5.dp)
    }
}
