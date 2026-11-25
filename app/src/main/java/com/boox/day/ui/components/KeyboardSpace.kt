package com.boox.day.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * How much of the screen's bottom the on-screen keyboard covers right now, 0 when it is closed
 * (Oct 2026, plan 3.1). A dialog pads its full-screen frame by this much, so it moves up and fits
 * above the keyboard instead of hiding its Save button under it. Compose's Popup sets no keyboard
 * mode and handles no insets itself, so each dialog asks here.
 */
@Composable
fun keyboardHeight(): Dp {
    val density = LocalDensity.current
    val px = WindowInsets.ime.getBottom(density)
    LaunchedEffect(px) { if (px > 0) Log.d("KeyboardSpace", "keyboard covers $px px") }
    return with(density) { px.toDp() }.coerceAtLeast(0.dp)
}

/**
 * Shared E-Ink popup container that always keeps dialog content above the on-screen keyboard
 * without any scrim or animation (Phase 4 / Decision D3 P6).
 */
@Composable
fun KeyboardSafePopup(
    onDismissRequest: () -> Unit,
    alignment: Alignment = Alignment.Center,
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable BoxScope.(keyboardHeight: Dp) -> Unit
) {
    Popup(
        alignment = alignment,
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        val keyboard = keyboardHeight()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = keyboard),
            contentAlignment = alignment
        ) {
            content(keyboard)
        }
    }
}
