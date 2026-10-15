package com.boox.day.editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.boox.day.data.datastore.BUTTON_SIZE
import com.boox.day.editor.utils.Pen
import com.boox.day.editor.utils.PenSetting
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Tool Settings Indicator - shows current pen type, color dot, and stroke size.
 * Clicking opens the StrokeMenu for editing color/size.
 *
 * Layout:
 * [Color Dot] [Pen Name]
 *            [Size px]
 */
@Composable
fun ToolSettingsIndicator(
    currentPen: Pen,
    penSetting: PenSetting,
    onClick: () -> Unit,
    onMenuOpenChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showStrokeMenu by remember { mutableStateOf(false) }

    if (onMenuOpenChange != null) {
        LaunchedEffect(showStrokeMenu) {
            onMenuOpenChange(showStrokeMenu)
        }
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .noRippleClickable { onClick() }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator dot
            Box(
                modifier = Modifier
                    .size(EInkTheme.ToolSettingsIndicator.colorDotSize)
                    .background(Color(penSetting.color), CircleShape)
                    .border(EInkTheme.ToolSettingsIndicator.colorDotBorderWidth, EInkTheme.ToolSettingsIndicator.colorDotBorderColor, CircleShape)
            )

            // Pen name and size
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = getPenDisplayName(currentPen),
                    fontSize = EInkTheme.ToolSettingsIndicator.penNameFontSize,
                    color = EInkTheme.ToolSettingsIndicator.penNameColor,
                    lineHeight = EInkTheme.ToolSettingsIndicator.penNameLineHeight
                )
                Text(
                    text = "${penSetting.strokeSize.toInt()}px",
                    fontSize = EInkTheme.ToolSettingsIndicator.sizeFontSize,
                    color = EInkTheme.ToolSettingsIndicator.sizeColor,
                    lineHeight = EInkTheme.ToolSettingsIndicator.sizeLineHeight
                )
            }
        }
    }
}

/**
 * Returns a user-friendly display name for the pen type.
 * Abbreviated to save space in compact toolbar.
 */
private fun getPenDisplayName(pen: Pen): String {
    return when (pen) {
        Pen.BALLPEN, Pen.REDBALLPEN, Pen.GREENBALLPEN, Pen.BLUEBALLPEN -> "Pen"
        Pen.FOUNTAIN -> "Fnt."
        Pen.MARKER -> "Mark."
        Pen.PENCIL -> "Penc."
        Pen.BRUSH -> "Brush"
    }
}
