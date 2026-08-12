/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A continuous smooth wavy slider, distinct from [SquigglySlider].
 *
 * Where [SquigglySlider] flattens into a straight line at the play position and only
 * squiggles ahead of it, [WavySlider] draws one unbroken smooth sine wave across the
 * entire track, with the active portion drawn in the accent color and the remainder
 * drawn in a dimmed tone. This mirrors the "wavy" progress style used by some modern
 * media players where the whole track undulates continuously while media is playing.
 */
@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = true,
) {
    val activeColor = colors.activeTrackColor
    val inactiveColor = colors.inactiveTrackColor

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(value) }

    val currentValue = if (isDragging) dragPosition else value
    val duration = valueRange.endInclusive - valueRange.start

    val infiniteTransition = rememberInfiniteTransition(label = "WavySliderPhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavySliderPhaseAnim"
    )

    val waveLength = 36f
    val amplitude = if (isPlaying && !isDragging) 5f else 0f
    val strokeWidth = 5.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(valueRange) {
                            detectTapGestures { offset ->
                                val newPosition = (offset.x / size.width) * duration
                                val mapped = valueRange.start + newPosition.coerceIn(0f, duration)
                                onValueChange(mapped)
                                onValueChangeFinished?.invoke()
                            }
                        }
                        .pointerInput(valueRange) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    val newPosition = (offset.x / size.width) * duration
                                    dragPosition = valueRange.start + newPosition.coerceIn(0f, duration)
                                    onValueChange(dragPosition)
                                },
                                onDragEnd = {
                                    isDragging = false
                                    onValueChangeFinished?.invoke()
                                },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val newPosition = (change.position.x / size.width) * duration
                                    dragPosition = valueRange.start + newPosition.coerceIn(0f, duration)
                                    onValueChange(dragPosition)
                                }
                            )
                        }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            val strokePx = strokeWidth.toPx()
            val progress = if (duration > 0f) {
                ((currentValue - valueRange.start) / duration).coerceIn(0f, 1f)
            } else 0f
            val width = size.width
            val centerY = size.height / 2f
            val activeX = width * progress
            val phaseOffsetPx = phase * waveLength

            fun buildWavePath(): Path {
                val path = Path()
                var x = -waveLength
                path.moveTo(x, centerY)
                while (x <= width + waveLength) {
                    val nextX = x + waveLength / 2f
                    val cycle = ((x + phaseOffsetPx) / waveLength)
                    val sign = if (cycle.toInt() % 2 == 0) 1f else -1f
                    path.cubicTo(
                        x + waveLength / 4f, centerY + sign * amplitude,
                        nextX - waveLength / 4f, centerY + sign * amplitude,
                        nextX, centerY
                    )
                    x = nextX
                }
                return path
            }

            val wavePath = buildWavePath()

            clipRect(left = 0f, top = 0f, right = activeX, bottom = size.height) {
                drawPath(path = wavePath, color = activeColor, style = Stroke(width = strokePx, cap = StrokeCap.Round))
            }
            clipRect(left = activeX, top = 0f, right = width, bottom = size.height) {
                drawPath(path = wavePath, color = inactiveColor, style = Stroke(width = strokePx, cap = StrokeCap.Round))
            }

            drawCircle(color = activeColor, radius = strokePx, center = Offset(activeX, centerY))
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.clipRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit
) = androidx.compose.ui.graphics.drawscope.clipRect(left, top, right, bottom, block = block)
