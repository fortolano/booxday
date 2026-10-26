package com.boox.day.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * A dialog's X (Oct 2026, from the Boox Notes header): a touch-size box with the close icon. It
 * closes the whole dialog; a back arrow next to it goes one step back.
 */
@Composable
fun DialogCloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(EInkTheme.Global.touchTarget)
            .noRippleClickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = EInkTheme.Colors.primary,
            modifier = Modifier.size(28.dp)
        )
    }
}
