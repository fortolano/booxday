package com.boox.day.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.boox.day.speech.VoiceInputState
import kotlin.math.sin

/**
 * Canvas-based waveform visualization for voice input.
 *
 * Design principles:
 * 1. Simple black lines on white - e-ink friendly
 * 2. Smooth interpolation - not jumpy
 * 3. Different animations for different states:
 *    - IDLE: Flat line
 *    - LISTENING: Subtle breathing animation
 *    - SPEAKING: Dynamic bars responding to RMS level
 *    - PROCESSING: Smooth wave animation (thinking)
 *    - ERROR: Static centered line
 */
@Composable
fun WaveformCanvas(
    rmsLevel: Float,
    state: VoiceInputState,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    barColor: Color = Color.Black
) {
    // Store recent RMS values for smooth visualization
    val bars = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } } }

    // Animated progress for breathing/processing effects
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    // Update bars when RMS changes (for speaking state)
    LaunchedEffect(rmsLevel, state) {
        when (state) {
            is VoiceInputState.Speaking,
            is VoiceInputState.SilenceDetected -> {
                // Shift bars left, add new RMS value on right
                if (bars.isNotEmpty()) {
                    bars.removeAt(0)
                    bars.add(rmsLevel)
                }
            }
            is VoiceInputState.Listening -> {
                // Gentle decay when waiting for speech
                if (bars.isNotEmpty()) {
                    bars.removeAt(0)
                    bars.add(rmsLevel * 0.3f) // Lower amplitude for listening
                }
            }
            else -> {
                // For other states, gradually decay to zero
                if (bars.isNotEmpty() && bars.any { it > 0.01f }) {
                    bars.removeAt(0)
                    bars.add(0f)
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2
        val barWidth = canvasWidth / barCount
        val barSpacing = barWidth * 0.3f
        val actualBarWidth = barWidth - barSpacing

        when (state) {
            is VoiceInputState.Idle -> {
                // Flat line
                drawLine(
                    color = barColor.copy(alpha = 0.3f),
                    start = Offset(0f, centerY),
                    end = Offset(canvasWidth, centerY),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
            }

            is VoiceInputState.Listening -> {
                // Subtle breathing animation
                bars.forEachIndexed { index, _ ->
                    val phase = (index.toFloat() / barCount) * 2 * Math.PI.toFloat()
                    val breathingAmplitude = (sin(animatedProgress * 2 * Math.PI.toFloat() + phase) + 1) / 2
                    val barHeight = 4f + breathingAmplitude * 12f // Small breathing effect

                    val x = index * barWidth + barWidth / 2

                    drawLine(
                        color = barColor.copy(alpha = 0.5f),
                        start = Offset(x, centerY - barHeight / 2),
                        end = Offset(x, centerY + barHeight / 2),
                        strokeWidth = actualBarWidth.coerceAtMost(4f),
                        cap = StrokeCap.Round
                    )
                }
            }

            is VoiceInputState.Speaking,
            is VoiceInputState.SilenceDetected -> {
                // Dynamic bars responding to voice
                bars.forEachIndexed { index, level ->
                    // Add some variance for visual interest
                    val variance = sin(index * 0.5f + animatedProgress * 6f) * 0.1f
                    val adjustedLevel = (level + variance).coerceIn(0f, 1f)

                    // Minimum bar height for visibility
                    val minHeight = 4f
                    val maxHeight = canvasHeight * 0.8f
                    val barHeight = minHeight + adjustedLevel * (maxHeight - minHeight)

                    val x = index * barWidth + barWidth / 2

                    drawLine(
                        color = barColor,
                        start = Offset(x, centerY - barHeight / 2),
                        end = Offset(x, centerY + barHeight / 2),
                        strokeWidth = actualBarWidth.coerceAtMost(6f),
                        cap = StrokeCap.Round
                    )
                }
            }

            is VoiceInputState.Processing,
            is VoiceInputState.AutoComplete -> {
                // Smooth wave animation (AI is thinking)
                bars.forEachIndexed { index, _ ->
                    val phase = (index.toFloat() / barCount) * 2 * Math.PI.toFloat()
                    val waveProgress = animatedProgress * 4 * Math.PI.toFloat()
                    val waveAmplitude = (sin(waveProgress + phase) + 1) / 2

                    val barHeight = 8f + waveAmplitude * 30f

                    val x = index * barWidth + barWidth / 2

                    drawLine(
                        color = barColor.copy(alpha = 0.7f),
                        start = Offset(x, centerY - barHeight / 2),
                        end = Offset(x, centerY + barHeight / 2),
                        strokeWidth = actualBarWidth.coerceAtMost(5f),
                        cap = StrokeCap.Round
                    )
                }
            }

            is VoiceInputState.Paused -> {
                // Paused indicator - static with middle dip
                bars.forEachIndexed { index, _ ->
                    val distFromCenter = kotlin.math.abs(index - barCount / 2f) / (barCount / 2f)
                    val barHeight = 6f + (1f - distFromCenter) * 20f

                    val x = index * barWidth + barWidth / 2

                    drawLine(
                        color = barColor.copy(alpha = 0.4f),
                        start = Offset(x, centerY - barHeight / 2),
                        end = Offset(x, centerY + barHeight / 2),
                        strokeWidth = actualBarWidth.coerceAtMost(4f),
                        cap = StrokeCap.Round
                    )
                }
            }

            is VoiceInputState.Error -> {
                // Error state - X pattern or flat line
                drawLine(
                    color = Color.DarkGray,
                    start = Offset(canvasWidth * 0.3f, centerY),
                    end = Offset(canvasWidth * 0.7f, centerY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Compact waveform for smaller spaces (e.g., button indicators).
 */
@Composable
fun WaveformIndicator(
    rmsLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color.Black
) {
    val infiniteTransition = rememberInfiniteTransition(label = "indicator")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "indicator_progress"
    )

    Canvas(modifier = modifier.height(24.dp)) {
        val centerY = size.height / 2
        val barCount = 5
        val barWidth = size.width / barCount
        val spacing = barWidth * 0.2f

        repeat(barCount) { index ->
            val phase = index * 0.5f
            val height = if (isActive) {
                // Active: respond to RMS with animation
                val variance = sin(animatedProgress * 2 * Math.PI.toFloat() + phase) * 0.2f
                val level = (rmsLevel + variance).coerceIn(0.2f, 1f)
                size.height * 0.3f + level * size.height * 0.5f
            } else {
                // Inactive: small static bars
                size.height * 0.3f
            }

            val x = index * barWidth + barWidth / 2

            drawLine(
                color = barColor.copy(alpha = if (isActive) 1f else 0.4f),
                start = Offset(x, centerY - height / 2),
                end = Offset(x, centerY + height / 2),
                strokeWidth = (barWidth - spacing).coerceAtMost(4f),
                cap = StrokeCap.Round
            )
        }
    }
}
