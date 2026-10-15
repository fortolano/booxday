package com.boox.day.ui.components

import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.boox.day.ui.theme.EInkTheme
import kotlinx.coroutines.delay

/**
 * A composable that displays a fun message and rotates to a new one at regular intervals.
 *
 * Used in AI processing dialogs to entertain users during wait times.
 * Follows the same LaunchedEffect + while(true) + delay pattern as EinkLoadingDots.
 *
 * @param messageProvider Lambda that returns a new random message (e.g., AiFeedbackMessages::getRandomGeneralMessage)
 * @param intervalMs Time between message rotations in milliseconds (default from EInkTheme.AiProcessingFeedback)
 * @param modifier Modifier for the Text composable
 * @param fontSize Font size for the message text (default from EInkTheme.AiProcessingFeedback)
 * @param fontWeight Font weight for the message text
 * @param color Text color
 * @param textAlign Text alignment
 */
@Composable
fun RotatingFunMessage(
    messageProvider: () -> String,
    intervalMs: Long = EInkTheme.AiProcessingFeedback.funMessageIntervalMs,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = EInkTheme.AiProcessingFeedback.funMessageFontSize,
    fontWeight: FontWeight = EInkTheme.AiProcessingFeedback.funMessageFontWeight,
    color: Color = EInkTheme.AiProcessingFeedback.funMessageColor,
    textAlign: TextAlign = TextAlign.Center
) {
    // Initial message - shown immediately
    var currentMessage by remember { mutableStateOf(messageProvider()) }

    // Rotation effect - cycles through messages at the specified interval
    LaunchedEffect(Unit) {
        while (true) {
            delay(intervalMs)
            currentMessage = messageProvider()
        }
    }

    Text(
        text = currentMessage,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        textAlign = textAlign,
        modifier = modifier
    )
}
