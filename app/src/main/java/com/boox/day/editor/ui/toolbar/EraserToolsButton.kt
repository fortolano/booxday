package com.boox.day.editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Divider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.R
import com.boox.day.data.datastore.BUTTON_SIZE
import com.boox.day.editor.state.Mode
import com.boox.day.editor.utils.Eraser
import com.boox.day.ui.convertDpToPixel
import com.boox.day.ui.theme.EInkTheme

/**
 * Eraser tools dropdown button.
 * Focused on eraser-specific options only:
 * - Eraser modes (Standard, Stroke)
 * - Scribble-to-erase toggle
 *
 * Page operations (Clear Page, Delete Page) moved to Hamburger menu.
 * Refresh Canvas moved to Hamburger menu.
 */
@Composable
fun EraserToolsButton(
    currentEraser: Eraser,
    currentMode: Mode,
    scribbleToErase: Boolean,
    onEraserSelect: (Eraser) -> Unit,
    onScribbleToggle: (Boolean) -> Unit,
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
            iconId = if (currentEraser == Eraser.PEN) R.drawable.ink_eraser_40px
                     else R.drawable.remove_selection_40px,
            isSelected = isActive,
            onSelect = {
                if (currentMode == Mode.Erase) {
                    // Already erasing: toggle eraser menu
                    showMenu = !showMenu
                } else {
                    // Switch back to erase mode with existing eraser type
                    onEraserSelect(currentEraser)
                }
            },
            hasDropdown = true,
            contentDescription = "Eraser tools",
            iconPadding = 10  // Increased padding for smaller icon appearance
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
                    // Standard Eraser (PEN mode)
                    EraserMenuItem(
                        icon = R.drawable.ink_eraser_40px,
                        label = "Standard Eraser",
                        selected = currentEraser == Eraser.PEN && currentMode == Mode.Erase,
                        onClick = {
                            onEraserSelect(Eraser.PEN)
                            showMenu = false
                        }
                    )

                    // Stroke Eraser (SELECT mode)
                    EraserMenuItem(
                        icon = R.drawable.remove_selection_40px,
                        label = "Stroke Eraser",
                        selected = currentEraser == Eraser.SELECT && currentMode == Mode.Erase,
                        onClick = {
                            onEraserSelect(Eraser.SELECT)
                            showMenu = false
                        }
                    )

                    Divider(color = EInkTheme.Divider.subtleColor, thickness = EInkTheme.Divider.subtleThickness)

                    // Scribble-to-erase toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(EInkTheme.MenuItem.height)
                            .clickable { onScribbleToggle(!scribbleToErase) }
                            .padding(horizontal = EInkTheme.MenuItem.horizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EInkTheme.Global.paddingSmall)
                    ) {
                        Checkbox(
                            checked = scribbleToErase,
                            onCheckedChange = { onScribbleToggle(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = EInkTheme.Checkbox.checkedBackground,
                                uncheckedColor = EInkTheme.Colors.darkGray
                            )
                        )
                        Text(
                            text = "Scribble to Erase",
                            fontSize = EInkTheme.MenuItem.fontSize,
                            color = EInkTheme.MenuItem.textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EraserMenuItem(
    icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // E-Ink: Selected = inverted colors (black bg, white content)
    val backgroundColor = if (selected) EInkTheme.MenuItem.selectedBackground else EInkTheme.PopupMenu.background
    val contentColor = if (selected) EInkTheme.MenuItem.selectedTextColor else EInkTheme.MenuItem.textColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EInkTheme.MenuItem.height)
            .clickable { onClick() }
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
