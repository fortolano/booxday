package com.boox.day.ui

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset

/**
 * Popups opened from inside another popup — a tool's menu in the compact toolbar's "…" popup, the
 * settings menu in the header's ⋮ popup — are placed with the outer popup's own coordinates, so they
 * land shifted up and left by the outer popup's position on screen (seen on the Go 6, Oct 2026).
 * The outer popup provides that position here and the inner popup adds it to its offset.
 * Zero everywhere else, so popups opened from the screen itself are unchanged.
 */
val LocalNestedPopupOffset = compositionLocalOf { IntOffset.Zero }

/**
 * Wraps the content of a popup whose children open popups of their own. [hostView] is the view the
 * outer popup was opened from (LocalView outside the popup).
 */
@Composable
fun NestedPopupHost(hostView: View, content: @Composable () -> Unit) {
    val popupView = LocalView.current
    var offset by remember { mutableStateOf(IntOffset.Zero) }
    Box(
        modifier = Modifier.onGloballyPositioned {
            val inPopup = IntArray(2)
            val inHost = IntArray(2)
            popupView.getLocationOnScreen(inPopup)
            hostView.getLocationOnScreen(inHost)
            val shift = IntOffset(inPopup[0] - inHost[0], inPopup[1] - inHost[1])
            if (shift != offset) offset = shift
        }
    ) {
        CompositionLocalProvider(LocalNestedPopupOffset provides offset, content = content)
    }
}
