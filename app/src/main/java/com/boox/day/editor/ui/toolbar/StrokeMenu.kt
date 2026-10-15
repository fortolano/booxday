package com.boox.day.editor.ui.toolbar

import com.boox.day.ui.LocalNestedPopupOffset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.boox.day.data.datastore.AppSettings
import com.boox.day.data.datastore.BUTTON_SIZE
import com.boox.day.data.datastore.GlobalAppSettings
import com.boox.day.editor.utils.PenSetting
import com.boox.day.ui.convertDpToPixel
import com.boox.day.ui.noRippleClickable
import com.boox.day.ui.theme.EInkTheme
import kotlin.math.roundToInt


@Composable
fun StrokeMenu(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    onClose: () -> Unit,
    sizeOptions: List<Pair<Int, Float>>,
    colorOptions: List<Color>,
) {
    val context = LocalContext.current

    Popup(
        offset = IntOffset(0, convertDpToPixel(EInkTheme.PopupMenu.offsetY, context).toInt()) + LocalNestedPopupOffset.current,
        onDismissRequest = { onClose() },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        alignment = Alignment.TopCenter
    ) {
        StrokeMenuContent(
            value = value,
            onChange = onChange,
            sizeOptions = sizeOptions,
            colorOptions = colorOptions,
            modifier = Modifier
                .background(EInkTheme.PopupMenu.background)
                .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
        )
    }
}

/**
 * The pen color and size pickers, shared by the stroke menu popup and the toolbar menu (≡) when the
 * pen settings are not on the bar (Oct 2026).
 */
@Composable
fun StrokeMenuContent(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    sizeOptions: List<Pair<Int, Float>>,
    colorOptions: List<Color>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(IntrinsicSize.Min) // match the widest child (ColorPicker Row)
            .padding(horizontal = 10.dp)
            .padding(top = 10.dp, bottom = 4.dp)
    ) {
        val listOfColors = if (GlobalAppSettings.current.monochromeMode) listOf(
            Color.Black,
            Color.DarkGray,
            Color.Gray,
            Color.LightGray
        )
        else colorOptions

        val widthOfPicker = (44 * listOfColors.size.coerceAtLeast(5))
        val heightOfPicker = 40

        val isBottom =
            GlobalAppSettings.current.toolbarPosition == AppSettings.Position.Bottom

        if (isBottom) {
            // Show size first, then colors
            StrokeSizePicker(
                value = value,
                onChange = onChange,
                sizeOptions = sizeOptions,
                widthOfPicker = widthOfPicker,
                heightOfPicker = heightOfPicker
            )

            Spacer(Modifier.height(16.dp))

            ColorPicker(
                value = value,
                onChange = onChange,
                colorOptions = listOfColors,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                embedded = true
            )
        } else {
            // Original order: colors first, then size
            ColorPicker(
                value = value,
                onChange = onChange,
                colorOptions = listOfColors,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                embedded = true
            )

            Spacer(Modifier.height(16.dp))

            StrokeSizePicker(
                value = value,
                onChange = onChange,
                sizeOptions = sizeOptions,
                widthOfPicker = widthOfPicker,
                heightOfPicker = heightOfPicker
            )
        }
    }
}

@Composable
fun ColumnScope.StrokeSizePicker(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    sizeOptions: List<Pair<Int, Float>>,
    widthOfPicker: Int,
    heightOfPicker: Int = 40
) {

    val sizes = sizeOptions.map { it.second }
    DiscreteThicknessSlider(
        value = value.strokeSize,
        onValueChange = { newSize ->
            onChange(
                PenSetting(
                    strokeSize = newSize,
                    color = value.color
                )
            )
        },
        values = sizes,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .width(widthOfPicker.dp)   // shorter than full width
            .height(heightOfPicker.dp)   // compact height
            .padding(horizontal = 10.dp, vertical = 2.dp),
        sliderColor = Color(value.color)
    )
}


@Composable
private fun ColorPicker(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    colorOptions: List<Color>,
    modifier: Modifier = Modifier,
    embedded: Boolean = false
) {
    val rowModifier = if (embedded) {
        modifier.height(IntrinsicSize.Max)
    } else {
        modifier
            .background(EInkTheme.PopupMenu.background)
            .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor)
            .height(IntrinsicSize.Max)
    }

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(EInkTheme.ColorPicker.swatchSpacing)
    ) {
        colorOptions.forEach { color ->
            val isSelected = color == Color(value.color)
            val label = colorName(color)
            Column(
                modifier = Modifier
                    .width(EInkTheme.ColorPicker.columnWidth)
                    .noRippleClickable {
                        onChange(
                            PenSetting(
                                strokeSize = value.strokeSize,
                                color = android.graphics.Color.argb(
                                    (color.alpha * 255).toInt(),
                                    (color.red * 255).toInt(),
                                    (color.green * 255).toInt(),
                                    (color.blue * 255).toInt()
                                )
                            )
                        )
                    }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(EInkTheme.ColorPicker.swatchSizeCompact)
                        .background(color, CircleShape)
                        .border(
                            width = if (isSelected) EInkTheme.ColorPicker.selectedBorderWidth else EInkTheme.ColorPicker.swatchBorderWidth,
                            color = if (isSelected) EInkTheme.ColorPicker.swatchBorderColor else EInkTheme.Colors.darkGray,
                            shape = CircleShape
                        )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    fontSize = EInkTheme.Typography.labelSmall,
                    color = EInkTheme.Colors.primary,
                    maxLines = 1
                )
            }
        }
    }

    if (!embedded) {
        Spacer(Modifier.height(4.dp))
    }

}

private fun colorName(color: Color): String {
    return when (color) {
        Color.Black -> "Black"
        Color.DarkGray -> "Dark"
        Color.Gray -> "Gray"
        Color.LightGray -> "Light"
        Color.Red -> "Red"
        Color.Green -> "Green"
        Color.Blue -> "Blue"
        Color.Cyan -> "Cyan"
        Color.Magenta -> "Magenta"
        Color.Yellow -> "Yellow"
        else -> "Color"
    }
}

// Size picker with icons
@Composable
private fun ThicknessPicker(
    value: PenSetting,
    onChange: (setting: PenSetting) -> Unit,
    sizeOptions: List<Pair<Int, Float>>,
    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier
            .background(EInkTheme.PopupMenu.background)
            .border(EInkTheme.PopupMenu.borderWidth, EInkTheme.PopupMenu.borderColor),
        horizontalArrangement = Arrangement.Center
    ) {
        sizeOptions.forEach { (iconId, size) ->
            ToolbarButton(
                iconId = iconId,
                contentDescription = "size $size",
                isSelected = value.strokeSize == size,
                onSelect = {
                    onChange(
                        PenSetting(
                            strokeSize = size, color = value.color
                        )
                    )
                },
                modifier = Modifier
            )
        }
    }
}

/**
 * Discrete slider:
 * - Track is a wedge: thin on the left, gradually thicker to the right.
 * - Tap or drag to change; snapping to nearest integer value between min and max.
 * - Single down arrow/rect thumb for clarity (high contrast).
 * - Takes a List<Float> of suggested values; indicators are shown above these.
 * - All integer values between min and max are selectable by dragging/tapping.
 */
@Composable
private fun DiscreteThicknessSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    values: List<Float>,
    modifier: Modifier = Modifier,
    sliderColor: Color = Color.Black,
) {
    if (values.size < 2) return // Require at least two values to define a range

    // Use only sorted, distinct values
    val displayValues = values.distinct().sorted()
    val minVal = displayValues.first().toInt()
    val maxVal = displayValues.last().toInt()


    // Map each integer to a normalized position [0,1] along the slider
    fun fractionForInt(v: Int): Float = (v - minVal).toFloat() / (maxVal - minVal).toFloat()

    // For the indicators only: map the suggested values to fractions
    val indicatorFractions = displayValues.map { v ->
        val intV = v.toInt().coerceIn(minVal, maxVal)
        fractionForInt(intV)
    }

    // Clamp and snap to nearest integer on user interaction
    fun snapToNearestInt(xFraction: Float): Float {
        val clamped = xFraction.coerceIn(0f, 1f)
        val rawValue = minVal + ((maxVal - minVal) * clamped)
        return rawValue.roundToInt().coerceIn(minVal, maxVal).toFloat()
    }

    // Thumb's proportional position
    val thumbInt = value.roundToInt().coerceIn(minVal, maxVal)
    val thumbFraction = fractionForInt(thumbInt)

    Box(
        modifier = modifier
            .pointerInput(displayValues) {
                detectTapGestures { offset ->
                    val w = size.width.coerceAtLeast(1)
                    onValueChange(snapToNearestInt(offset.x / w))
                }
            }
            .pointerInput(displayValues) {
                detectDragGestures(onDrag = { change, _ ->
                    val w = size.width.coerceAtLeast(1)
                    onValueChange(snapToNearestInt(change.position.x / w))
                })
            }) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f

            // Wedge parameters (thin -> thick)
            val minTrack = 6f
            val baseMaxTrack = (h * 0.75f).coerceAtLeast(minTrack + 4f)
            val maxTrack = (baseMaxTrack * 1.5f).coerceAtMost(h * 0.95f)

            fun thicknessAtX(x: Float): Float {
                val frac = if (w > 0) (x / w).coerceIn(0f, 1f) else 0f
                return minTrack + (maxTrack - minTrack) * frac
            }

            // Wedge track path with left thin, right thick (bottom edge kept centered)
            val trackPath = Path().apply {
                moveTo(0f, centerY - minTrack / 2f) // top-left
                lineTo(w, centerY - maxTrack / 2f)  // top-right
                lineTo(w, centerY)                  // bottom-right
                lineTo(0f, centerY)                 // bottom-left
                close()
            }

            // Outline first (white halo), then fill, then crisp black outline
            drawPath(
                path = trackPath,
                color = Color.White,
                style = Stroke(
                    width = 6f,
                    cap = StrokeCap.Butt,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = trackPath,
                color = sliderColor,
                style = Fill
            )
            drawPath(
                path = trackPath,
                color = Color.Black,
                style = Stroke(
                    width = 2f,
                    cap = StrokeCap.Butt,
                    join = StrokeJoin.Round
                )
            )

            // Rounded end hints
            val leftR = minTrack / 4f
            val rightR = maxTrack / 4f
            drawCircle(sliderColor, radius = leftR, center = Offset(2f, centerY))
            drawCircle(sliderColor, radius = rightR, center = Offset(w - 3f, centerY - rightR))

            // Ticks (indicators) along the top for the suggested values only
            val tickGap = 6f
            val tickHeight = 12f
            for (frac in indicatorFractions) {
                val x = frac * w
                val t = thicknessAtX(x)
                val topY = (centerY - t / 2f)
                val startY = (topY - tickGap - tickHeight).coerceAtLeast(0f)
                val endY = (topY - tickGap).coerceAtLeast(0f)
                drawLine(
                    color = Color.Black,
                    start = Offset(x, startY),
                    end = Offset(x, endY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // Thumb position and prominence
            val thumbX = thumbFraction * w
            val currentThickness = thicknessAtX(thumbX)

            // Highly visible vertical rounded thumb (white border + black body)
            val thumbW = 10f
            val thumbH = (currentThickness + 14f).coerceAtMost(h * 0.9f) / 2
            val thumbRadius = 6f
            val thumbOuterPadding = 3f

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(
                    thumbX - thumbW / 2f - thumbOuterPadding, centerY - thumbH - thumbOuterPadding
                ),
                size = Size(
                    thumbW + thumbOuterPadding * 2f, thumbH + thumbOuterPadding * 2f
                ),
                cornerRadius = CornerRadius(thumbRadius, thumbRadius)
            )
            drawRoundRect(
                color = sliderColor,
                topLeft = Offset(thumbX - thumbW / 2f, centerY - thumbH),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbRadius, thumbRadius)
            )

            // Down arrow pointing to current position for visibility
            val arrowWidth = 30f
            val arrowHeight = 30f
            val downArrow = Path().apply {
                val tipY = (centerY + 6f).coerceAtMost(h)
                moveTo(thumbX, tipY) // tip (upwards)
                lineTo(thumbX - arrowWidth / 2f, (tipY + arrowHeight).coerceAtMost(h))
                lineTo(thumbX + arrowWidth / 2f, (tipY + arrowHeight).coerceAtMost(h))
                close()
            }
            drawPath(downArrow, color = sliderColor)
        }
    }
}
