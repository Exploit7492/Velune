package com.nikhil.yt.ui.screens.equalizer.axion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import kotlin.math.abs

/**
 * A slim, bipolar vertical gain fader for one EQ band — the "channel strip"
 * look real hardware mixers and graphic-EQ views (Neutron's fixed-band
 * view, Poweramp's graphic tab) use, instead of a full-width Material
 * `Slider` rotated 90°.
 *
 * This deliberately replaces the old `EqBandSlider` (removed when Advanced
 * folded into Master — see git history), which was a rotated stock
 * `Slider` at ~56dp wide with a full free-floating thumb. That read as
 * "big" for a bank of 10 side-by-side controls, because a Material
 * `Slider`'s hit target, thumb, and track are all sized for being the only
 * interactive element on a row, not one of ten packed edge-to-edge. This
 * is a fully custom `Canvas` draw instead: [width] can go much narrower
 * (the default is under half the old control's width) because there's no
 * Material touch-target minimum fighting the visual size, and the track
 * fill is *bipolar* — it grows from the 0dB centerline outward toward
 * boost or cut, the way a real console fader's LED meter or a graphic
 * EQ's fill typically reads, rather than filling from one fixed end the
 * way a plain `Slider` does. That center-anchored fill is also what makes
 * "how far this band has been pushed" visually legible at a glance across
 * a whole 10-band row, which a same-direction fill doesn't give you.
 *
 * Visually matches [RotaryKnob]'s established neon language rather than
 * inventing a second style for the same screen: a layered wide-dim +
 * narrow-bright pass for the "glowing line" fill (the same trick
 * `drawNeonArc` uses for the knob's arc, just applied to a vertical
 * capsule instead of an arc), a small glowing cap at the thumb position
 * (the same halo-of-fading-circles trick as `drawNeonDot`), and an
 * under-glow wash whose opacity tracks how far the value has moved from
 * its center/default — RotaryKnob's "light that goes with what it's
 * controlling" effect, applied here to "how far this band is pushed"
 * instead of "how far this knob is turned."
 *
 * Dragging maps the touch position directly to a value (tap anywhere on
 * the track jumps the fader there, then follows the finger 1:1) rather
 * than RotaryKnob's delta-based dragging — a vertical fader's value
 * already has a natural 1:1 spatial correspondence to where you touch it,
 * the way every real fader (hardware or software) works, unlike a knob's
 * rotation which doesn't map cleanly to an absolute touch point.
 */
@Composable
fun NeonVerticalFader(
    valueDb: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    width: Dp = 22.dp,
    trackHeight: Dp = 128.dp,
    range: ClosedFloatingPointRange<Float> = -12f..12f,
) {
    val outline = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val currentOnChange by rememberUpdatedState(onValueChange)

    val clamped = valueDb.coerceIn(range.start, range.endInclusive)
    val span = range.endInclusive - range.start
    // Fraction from the BOTTOM of the track (0f) to the TOP (1f) — inverted
    // relative to a raw y pixel coordinate, since y grows downward but a
    // fader's value grows upward.
    val valueFraction = (clamped - range.start) / span
    val centerFraction = (0f - range.start) / span // where 0dB sits, e.g. 0.5 for a symmetric range

    val dimColor = outline.copy(alpha = if (enabled) 0.4f else 0.25f)
    val glowColor = if (enabled) accentColor else dimColor

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "%+.1f".format(clamped),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) onSurface.copy(alpha = 0.85f) else onSurface.copy(alpha = 0.35f),
        )
        Canvas(
            modifier = modifier
                .width(width)
                .height(trackHeight)
                .pointerInput(enabled, range) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun applyAt(y: Float) {
                            val f = (1f - (y / size.height)).coerceIn(0f, 1f)
                            currentOnChange(range.start + f * span)
                        }
                        applyAt(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            change.consume()
                            applyAt(change.position.y)
                        }
                    }
                }
        ) {
            val trackWidth = this.size.width * 0.34f
            val cx = this.size.width / 2f
            val h = this.size.height
            val zeroY = h * (1f - centerFraction)
            val valueY = h * (1f - valueFraction)

            // Under-glow: soft wash behind the whole fader, brighter the
            // further the value sits from 0dB — same "light tied to how
            // far this is pushed" effect as RotaryKnob's under-glow, just
            // driven by distance-from-center instead of distance-from-zero.
            if (enabled) {
                val pushed = abs(valueFraction - centerFraction) / maxOf(centerFraction, 1f - centerFraction)
                val glowAlpha = 0.08f + 0.22f * pushed
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = glowAlpha), accentColor.copy(alpha = 0f)),
                        center = Offset(cx, valueY),
                        radius = this.size.width * 1.8f,
                    ),
                    radius = this.size.width * 1.8f,
                    center = Offset(cx, valueY),
                )
            }

            // Track background — a thin rounded capsule spanning the full
            // height, unfilled.
            drawRoundRect(
                color = outline.copy(alpha = 0.16f),
                topLeft = Offset(cx - trackWidth / 2f, 0f),
                size = Size(trackWidth, h),
                cornerRadius = CornerRadius(trackWidth / 2f),
            )

            // Centerline tick — a short horizontal mark at 0dB so the fill
            // below always has a visible reference point, even at rest.
            drawLine(
                color = outline.copy(alpha = 0.35f),
                start = Offset(cx - trackWidth * 1.4f, zeroY),
                end = Offset(cx + trackWidth * 1.4f, zeroY),
                strokeWidth = 1.dp.toPx(),
            )

            // Bipolar fill — from the 0dB centerline out to the current
            // value, drawn with the same layered wide-dim/narrow-bright
            // passes RotaryKnob's drawNeonArc uses, just as a vertical
            // capsule instead of an arc.
            val fillTop = minOf(valueY, zeroY)
            val fillBottom = maxOf(valueY, zeroY)
            if (fillBottom - fillTop > 0.5f) {
                drawNeonFillCapsule(
                    top = fillTop,
                    bottom = fillBottom,
                    centerX = cx,
                    baseWidth = trackWidth,
                    color = glowColor,
                    glowing = enabled,
                )
            }

            // Thumb — a small glowing cap at the current value, same
            // halo-of-fading-circles trick as RotaryKnob's drawNeonDot.
            drawNeonFaderCap(
                center = Offset(cx, valueY),
                halfWidth = this.size.width * 0.46f,
                color = glowColor,
                glowing = enabled,
            )
        }
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (enabled) onSurface.copy(alpha = 0.6f) else onSurface.copy(alpha = 0.3f),
        )
    }
}

/** Layered-stroke "neon capsule" fill between [top] and [bottom], centered on [centerX]. */
private fun DrawScope.drawNeonFillCapsule(
    top: Float,
    bottom: Float,
    centerX: Float,
    baseWidth: Float,
    color: Color,
    glowing: Boolean,
) {
    fun capsule(w: Float, alpha: Float) {
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(centerX - w / 2f, top),
            size = Size(w, bottom - top),
            cornerRadius = CornerRadius(w / 2f),
        )
    }
    if (glowing) {
        capsule(baseWidth * 2.4f, 0.16f)
        capsule(baseWidth * 1.5f, 0.30f)
    }
    capsule(baseWidth, if (glowing) 0.9f else 0.5f)
}

/** A small glowing horizontal "cap" — the fader thumb — with a soft halo behind it. */
private fun DrawScope.drawNeonFaderCap(
    center: Offset,
    halfWidth: Float,
    color: Color,
    glowing: Boolean,
) {
    val capHeight = halfWidth * 0.55f
    if (glowing) {
        drawCircle(color.copy(alpha = 0.14f), halfWidth * 1.5f, center)
    }
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - halfWidth, center.y - capHeight / 2f),
        size = Size(halfWidth * 2f, capHeight),
        cornerRadius = CornerRadius(capHeight / 2f),
        style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round),
    )
    drawCircle(Color.White.copy(alpha = if (glowing) 0.55f else 0.2f), capHeight * 0.28f, center)
}
