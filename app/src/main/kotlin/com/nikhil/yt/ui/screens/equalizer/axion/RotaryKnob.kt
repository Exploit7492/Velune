package com.nikhil.yt.ui.screens.equalizer.axion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A drag-to-rotate knob for a single continuous value, in the style Poweramp/
 * Wavelet/Neutron use for their master-bus controls (preamp, bass boost,
 * balance) — a sweep arc, an indicator dot, and vertical-drag-to-adjust (the
 * usual mobile convention for knobs, since horizontal drag is ambiguous near
 * the top/bottom of a circle).
 *
 * [value] is expected already normalized to 0f..1f; callers map it to/from
 * their own range (e.g. dB, -1..1 balance) — this keeps the knob itself
 * unit-agnostic and reusable.
 */
@Composable
fun RotaryKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    valueLabel: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainerLow

    val currentOnChange by rememberUpdatedState(onValueChange)
    var dragStartValue by remember { mutableFloatStateOf(0f) }
    var dragAccumPx by remember { mutableFloatStateOf(0f) }

    // Knob sweeps 270° (from -135° to +135°, i.e. leaving a 90° gap at the
    // bottom) — the standard hardware-knob sweep range.
    val sweepDegrees = 270f
    val startDegrees = -225f // -135° measured from 3 o'clock, i.e. bottom-left

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = modifier
                .width(size)
                .aspectRatio(1f)
                .padding(4.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        dragStartValue = value
                        dragAccumPx = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            change.consume()
                            // Dragging up increases the value, down decreases —
                            // a fixed pixel-to-value sensitivity rather than
                            // absolute angle, so the knob doesn't jump when you
                            // first touch it partway around the sweep.
                            dragAccumPx += (change.previousPosition.y - change.position.y)
                            val delta = dragAccumPx / 220f
                            val newValue = (dragStartValue + delta).coerceIn(0f, 1f)
                            currentOnChange(newValue)
                        }
                    }
                }
        ) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = this.size.width / 2f * 0.82f
            val strokeWidth = this.size.width * 0.09f

            // Track
            drawArc(
                color = outline.copy(alpha = 0.25f),
                startAngle = startDegrees,
                sweepAngle = sweepDegrees,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - radius, cy - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            // Fill up to current value
            drawArc(
                color = if (enabled) accentColor else outline.copy(alpha = 0.4f),
                startAngle = startDegrees,
                sweepAngle = sweepDegrees * value.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - radius, cy - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )
            // Knob body
            val bodyR = radius * 0.62f
            drawCircle(surfaceContainer, bodyR, androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(outline.copy(alpha = 0.3f), bodyR, androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(1.dp.toPx()))

            // Indicator dot at current angle
            val angleRad = (startDegrees + sweepDegrees * value.coerceIn(0f, 1f)) * PI / 180.0
            val dotR = radius * 0.9f
            val dotX = cx + dotR * cos(angleRad).toFloat()
            val dotY = cy + dotR * sin(angleRad).toFloat()
            drawCircle(if (enabled) accentColor else onSurface.copy(alpha = 0.4f), strokeWidth * 0.55f, androidx.compose.ui.geometry.Offset(dotX, dotY))
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) onSurface else onSurface.copy(alpha = 0.4f),
        )
        Text(
            text = valueLabel,
            fontSize = 10.sp,
            color = if (enabled) onSurface.copy(alpha = 0.7f) else onSurface.copy(alpha = 0.3f),
        )
    }
}
