package com.nikhil.yt.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RotationalKnob(
    value: Float, // 0.0 to 1.0
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    label: String = "",
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    val angle = remember(value) { -135f + (value * 270f) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = modifier
                .size(size)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val pos = change.position
                        val dx = pos.x - center.x
                        val dy = pos.y - center.y
                        var theta = atan2(dy, dx) * (180f / PI.toFloat())
                        // Normalize to 0-270 range starting from -135
                        theta = (theta + 135f + 360f) % 360f
                        if (theta > 270f) theta = 270f
                        onValueChange((theta / 270f).coerceIn(0f, 1f))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                center = Offset(size.toPx() / 2, size.toPx() / 2)
                val radius = size.toPx() / 2 - 8f
                val strokeWidth = 6f

                // Background arc
                drawArc(
                    color = trackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )

                // Value arc
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * value,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )

                // Knob indicator
                rotate(degrees = angle, pivot = center) {
                    drawLine(
                        color = color,
                        start = center,
                        end = Offset(
                            center.x + (radius - strokeWidth) * cos(0f),
                            center.y + (radius - strokeWidth) * sin(0f)
                        ),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }

                // Center dot
                drawCircle(color = color.copy(alpha = 0.3f), radius = 4f, center = center)
            }
        }

        if (label.isNotBlank()) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
