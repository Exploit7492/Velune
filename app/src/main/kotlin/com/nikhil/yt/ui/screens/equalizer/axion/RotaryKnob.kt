package com.nikhil.yt.ui.screens.equalizer.axion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A drag-to-rotate knob for a single continuous value, styled as a small
 * glowing instrument dial — a "neon puck" — rather than a flat Material
 * slider-in-a-circle. Three things carry the effect, all driven by the same
 * [value]/[accentColor] so nothing needs separate state:
 *
 * 1. **Value arc** is drawn in three overlaid passes (wide+dim, medium,
 *    thin+bright) instead of one stroke — the classic layered-stroke trick
 *    for a "glowing line" on a canvas that has no real bloom/blur pass.
 * 2. **Indicator** at the current angle gets the same treatment: several
 *    concentric circles fading out, so it reads as a light with a halo
 *    around it, and it visibly travels around the sweep as the knob turns.
 * 3. **Under-glow**: a soft radial wash sits behind the whole knob, its
 *    opacity tied to [value] — the knob visibly lights up from underneath
 *    as it's turned up, dims toward off. This is the "light under it that
 *    goes with what it's rotating to" effect.
 *
 * [value] is normalized to 0f..1f; callers map it to/from their own range.
 * The displayed angle is a spring-animated shadow of [value] (drag input
 * itself is immediate/unaffected) purely so programmatic changes — loading
 * a saved profile, an external reset — sweep into place instead of jumping,
 * which is what makes the dial read as a physical object instead of a
 * static readout.
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
    size: Dp = 64.dp,
) {
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainerLow

    val currentOnChange by rememberUpdatedState(onValueChange)
    var dragStartValue by remember { mutableFloatStateOf(0f) }
    var dragAccumPx by remember { mutableFloatStateOf(0f) }

    val clampedValue = value.coerceIn(0f, 1f)
    val displayValue by animateFloatAsState(
        targetValue = clampedValue,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "knobValue",
    )

    // Knob sweeps 270° (from -135° to +135°, i.e. leaving a 90° gap at the
    // bottom) — the standard hardware-knob sweep range.
    val sweepDegrees = 270f
    val startDegrees = -225f // -135° measured from 3 o'clock, i.e. bottom-left

    val dimColor = outline.copy(alpha = if (enabled) 0.4f else 0.25f)
    val glowColor = if (enabled) accentColor else dimColor

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
            val center = Offset(cx, cy)
            val radius = this.size.width / 2f * 0.82f
            val strokeWidth = this.size.width * 0.09f
            val angleRad = (startDegrees + sweepDegrees * displayValue) * PI / 180.0
            val dotX = cx + radius * 0.98f * cos(angleRad).toFloat()
            val dotY = cy + radius * 0.98f * sin(angleRad).toFloat()

            // 1. Under-glow: a soft wash behind everything, brighter the
            // further the knob is turned up — the "light under it" effect.
            // Drawn as a plain radial gradient rather than a blurred shape:
            // Canvas has no cheap portable blur, so a gradient that already
            // fades to transparent reads the same as a blurred solid circle
            // would, without an API-level-gated blur modifier.
            if (enabled) {
                val glowRadius = this.size.width * 0.62f
                val glowAlpha = 0.10f + 0.30f * displayValue
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = glowAlpha),
                            accentColor.copy(alpha = 0f),
                        ),
                        center = center,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = center,
                )
            }

            // Track (unfilled portion of the sweep)
            drawArc(
                color = outline.copy(alpha = 0.2f),
                startAngle = startDegrees,
                sweepAngle = sweepDegrees,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
            )

            // 2. Value arc — layered wide/dim -> thin/bright passes so it
            // reads as a glowing neon line rather than a flat stroke.
            drawNeonArc(
                center = center,
                radius = radius,
                startAngle = startDegrees,
                sweepAngle = sweepDegrees * displayValue,
                baseStrokeWidth = strokeWidth,
                color = glowColor,
                glowing = enabled,
            )

            // Knob body — a subtle radial gradient instead of a flat fill,
            // so it reads as a rounded physical puck rather than a disc.
            val bodyR = radius * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        surfaceContainer,
                        surfaceContainer.copy(alpha = 0.85f),
                    ),
                    center = Offset(cx - bodyR * 0.3f, cy - bodyR * 0.3f),
                    radius = bodyR * 1.6f,
                ),
                radius = bodyR,
                center = center,
            )
            // Thin neon rim around the body, dim when off, tinted when on.
            drawCircle(
                color = glowColor.copy(alpha = if (enabled) 0.5f else 0.25f),
                radius = bodyR,
                center = center,
                style = Stroke(1.dp.toPx()),
            )

            // 3. Indicator — a small glowing dot that travels with the
            // sweep, drawn as several fading concentric circles (halo) plus
            // a bright core, instead of one flat dot.
            drawNeonDot(
                position = Offset(dotX, dotY),
                coreRadius = strokeWidth * 0.5f,
                color = glowColor,
                glowing = enabled,
            )
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

/** Layered-stroke "neon line" arc: a wide dim glow pass under a thin bright core. */
private fun DrawScope.drawNeonArc(
    center: Offset,
    radius: Float,
    startAngle: Float,
    sweepAngle: Float,
    baseStrokeWidth: Float,
    color: Color,
    glowing: Boolean,
) {
    if (sweepAngle <= 0f) return
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    if (glowing) {
        drawArc(
            color = color.copy(alpha = 0.18f),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(baseStrokeWidth * 2.2f, cap = StrokeCap.Round),
        )
        drawArc(
            color = color.copy(alpha = 0.35f),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(baseStrokeWidth * 1.5f, cap = StrokeCap.Round),
        )
    }
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(baseStrokeWidth, cap = StrokeCap.Round),
    )
}

/** Layered-circle "glowing dot" — a halo of fading rings behind a bright core. */
private fun DrawScope.drawNeonDot(
    position: Offset,
    coreRadius: Float,
    color: Color,
    glowing: Boolean,
) {
    if (glowing) {
        drawCircle(color.copy(alpha = 0.12f), coreRadius * 3.2f, position)
        drawCircle(color.copy(alpha = 0.22f), coreRadius * 2.1f, position)
    }
    drawCircle(color, coreRadius, position)
    drawCircle(Color.White.copy(alpha = if (glowing) 0.55f else 0.2f), coreRadius * 0.4f, position)
}
