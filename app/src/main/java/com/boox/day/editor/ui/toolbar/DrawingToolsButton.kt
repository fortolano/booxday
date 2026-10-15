package com.boox.day.editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.editor.state.Mode
import com.boox.day.R
import com.boox.day.data.datastore.BUTTON_SIZE
import com.boox.day.editor.utils.Pen
import com.boox.day.ui.convertDpToPixel
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme

/**
 * Drawing tools dropdown button.
 * Shows the currently selected pen with a dropdown to switch between pen types.
 * Color/size editing is handled separately via ToolSettingsIndicator -> StrokeMenu.
 * Mutually exclusive with other mode buttons.
 *
 * Behavior (matches EraserToolsButton):
 * - 1st tap (not in Draw mode): Activates Draw mode with current pen, highlights button
 * - 1st tap (already in Draw mode): Opens dropdown menu, highlights button
 * - 2nd tap (menu open): Closes menu
 */
@Composable
fun DrawingToolsButton(
    currentPen: Pen,
    currentColor: Int,
    currentMode: Mode,
    onPenSelect: (Pen) -> Unit,
    onActivate: () -> Unit = {},  // Called on every tap to highlight button
    onMenuOpenChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    if (onMenuOpenChange != null) {
        LaunchedEffect(showMenu) {
            onMenuOpenChange(showMenu)
        }
    }

    Box(modifier = modifier) {
        ToolbarButton(
            iconId = when (currentPen) {
                Pen.BALLPEN, Pen.REDBALLPEN, Pen.GREENBALLPEN, Pen.BLUEBALLPEN -> R.drawable.stylus_pen_40px
                Pen.FOUNTAIN -> R.drawable.stylus_fountain_pen_40px
                Pen.MARKER -> R.drawable.stylus_highlighter_40px
                Pen.PENCIL -> R.drawable.stylus_pencil_40px
                Pen.BRUSH -> R.drawable.stylus_brush_40px
            },
            isSelected = isActive,
            penColor = Color(currentColor),
            onSelect = {
                if (isActive) {
                    // Button already highlighted: toggle pen-type menu
                    showMenu = !showMenu
                } else {
                    // Button not highlighted: highlight and ensure Draw mode, NO menu
                    onActivate()
                    onPenSelect(currentPen)
                }
            },
            hasDropdown = true,
            contentDescription = "Drawing tools"
        )

        if (showMenu) {
            Popup(
                offset = IntOffset(0, convertDpToPixel(EInkTheme.PopupMenu.offsetY, context).toInt()),
                onDismissRequest = { showMenu = false },
                properties = PopupProperties(focusable = true),
                alignment = Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier
                        .background(EInkTheme.PopupMenu.background)
                        .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
                        .width(EInkTheme.PopupMenu.width)
                ) {
                    // Ballpen (standard)
                    DrawingToolMenuItem(
                        icon = R.drawable.stylus_pen_40px,
                        label = "Pen",
                        selected = currentPen == Pen.BALLPEN || currentPen == Pen.REDBALLPEN ||
                                   currentPen == Pen.GREENBALLPEN || currentPen == Pen.BLUEBALLPEN,
                        onClick = {
                            onPenSelect(Pen.BALLPEN)
                            showMenu = false
                        }
                    )

                    // Fountain Pen
                    DrawingToolMenuItem(
                        icon = R.drawable.stylus_fountain_pen_40px,
                        label = "Fountain Pen",
                        selected = currentPen == Pen.FOUNTAIN,
                        onClick = {
                            onPenSelect(Pen.FOUNTAIN)
                            showMenu = false
                        }
                    )

                    // Marker
                    DrawingToolMenuItem(
                        icon = R.drawable.stylus_highlighter_40px,
                        label = "Marker",
                        selected = currentPen == Pen.MARKER,
                        onClick = {
                            onPenSelect(Pen.MARKER)
                            showMenu = false
                        }
                    )

                    // Pencil
                    DrawingToolMenuItem(
                        icon = R.drawable.stylus_pencil_40px,
                        label = "Pencil",
                        selected = currentPen == Pen.PENCIL,
                        onClick = {
                            onPenSelect(Pen.PENCIL)
                            showMenu = false
                        }
                    )

                    // Brush
                    DrawingToolMenuItem(
                        icon = R.drawable.stylus_brush_40px,
                        label = "Brush",
                        selected = currentPen == Pen.BRUSH,
                        onClick = {
                            onPenSelect(Pen.BRUSH)
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawingToolMenuItem(
    icon: Int,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // E-Ink: Selected = inverted colors (black bg, white content)
    val backgroundColor = if (selected) EInkTheme.MenuItem.selectedBackground else EInkTheme.PopupMenu.background
    val contentColor = when {
        selected -> EInkTheme.MenuItem.selectedTextColor
        enabled -> EInkTheme.MenuItem.textColor
        else -> EInkTheme.MenuItem.textColorDisabled
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .then(if (enabled) Modifier.noRippleClickable { onClick() } else Modifier)
            .background(backgroundColor)
            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.MenuItem.iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = label,
            modifier = Modifier.size(EInkTheme.MenuItem.iconSize),
            tint = contentColor
        )
        Text(
            text = label,
            fontSize = EInkTheme.MenuItem.fontSize,
            modifier = Modifier.weight(1f),
            color = contentColor
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = contentColor,
                modifier = Modifier.size(EInkTheme.Global.iconSizeSmall)
            )
        }
    }
}
