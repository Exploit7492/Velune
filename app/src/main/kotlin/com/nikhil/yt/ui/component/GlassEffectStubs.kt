package com.nikhil.yt.ui.component

import androidx.compose.foundation.background
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/**
 * Config for the "Liquid Glass" translucent-blur UI treatment. [globalEnabled]
 * is the master switch (mirrors the user's Settings toggle); the more specific
 * flags let individual surfaces (nav bar, sheets, etc.) opt out even when the
 * feature is globally on.
 */
data class GlassEffectConfig(
    val enabled: Boolean = false,
    val globalEnabled: Boolean = false,
    val navBarEnabled: Boolean = true,
    val tintColor: Color = Color.White,
    val textColor: Color = Color.White,
    val blurRadius: androidx.compose.ui.unit.Dp = 20.dp,
    val tintAlpha: Float = 0.15f,
)

val LocalGlassEffectConfig = staticCompositionLocalOf { GlassEffectConfig() }

/**
 * Applies a translucent blurred-glass background to the modified element.
 * Falls back to a plain semi-transparent tint on API levels where a real
 * backdrop blur isn't available.
 */
fun Modifier.liquidGlass(
    config: GlassEffectConfig,
    shape: Shape = RectangleShape,
): Modifier =
    if (!config.enabled && !config.globalEnabled) {
        this
    } else {
        this
            .then(
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Modifier.blur(radius = config.blurRadius)
                } else {
                    Modifier
                },
            )
            .background(
                color = config.tintColor.copy(alpha = config.tintAlpha).compositeOver(Color.Transparent),
                shape = shape,
            )
    }
