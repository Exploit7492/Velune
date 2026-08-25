package com.nikhil.yt.ui.component

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nikhil.yt.ui.component.backdrop.Backdrop
import com.nikhil.yt.ui.component.backdrop.drawBackdrop
import com.nikhil.yt.ui.component.backdrop.effects.blur
import com.nikhil.yt.ui.component.backdrop.effects.colorControls
import com.nikhil.yt.ui.component.backdrop.effects.lens
import com.nikhil.yt.ui.component.backdrop.highlight.Highlight
import com.nikhil.yt.ui.component.backdrop.shadow.Shadow

/**
 * User-configurable parameters of the liquid glass effect, sourced from the
 * `LiquidGlass*` DataStore preferences (see [com.nikhil.yt.ui.screens.settings.GlassEffectSettings])
 * and distributed through [LocalGlassEffectConfig]. Ported from the equivalent
 * wiring in the echo-music sibling project, adapted onto Velune's existing
 * (previously unused) preference keys.
 */
@Stable
data class GlassEffectConfig(
    val globalEnabled: Boolean = false,
    val vibrancy: Float = 1f,
    /** Blur in dp applied to glass surfaces. */
    val blurRadius: Float = 8f,
    /** 0..1, mapped to 0..[LENS_MAX_DP] dp of lens refraction height. */
    val lensHeight: Float = 0.5f,
    /** 0..1, mapped to 0..[LENS_MAX_DP] dp of lens refraction amount. */
    val lensAmount: Float = 0.5f,
    val chromaticAberration: Boolean = true,
    val depthEffect: Boolean = true,
    /** [Color.Unspecified] means adaptive: light glass on light theme, dark on dark. */
    val surfaceTintColor: Color = Color.Unspecified,
    val surfaceOpacity: Float = 0.4f,
    val textColor: Color = Color.Unspecified,
    val playerEnabled: Boolean = true,
    val miniPlayerEnabled: Boolean = true,
    val navBarEnabled: Boolean = true,
) {
    /**
     * Whether the glass effect should be rendered for [component], taking the master
     * switch and the per-component switch into account.
     */
    fun isEnabledFor(component: GlassComponent): Boolean =
        globalEnabled && when (component) {
            GlassComponent.PLAYER -> playerEnabled
            GlassComponent.MINI_PLAYER -> miniPlayerEnabled
            GlassComponent.NAV_BAR -> navBarEnabled
        }
}

/** UI surfaces that can individually opt in or out of the liquid glass effect. */
enum class GlassComponent {
    PLAYER,
    MINI_PLAYER,
    NAV_BAR,
}

/** Maximum lens refraction in dp when the 0..1 preference sliders are at 1. */
internal const val LENS_MAX_DP = 48f

/** The full screen player uses a heavier blur multiplier than the nav bar/mini player pills. */
internal const val PLAYER_BLUR_MULTIPLIER = 4f

internal const val MIN_GLASS_RESOLUTION_SCALE = 0.33f
internal const val FULL_QUALITY_BLUR_DP = 8f

/**
 * Resolution fraction at which a glass surface records and processes its backdrop.
 * More blur masks more upscaling, so heavier blur can render at a lower resolution;
 * with no blur the surface stays at full resolution so it remains crisp.
 */
fun glassResolutionScale(blurRadiusDp: Float): Float {
    val t = (blurRadiusDp / FULL_QUALITY_BLUR_DP).coerceIn(0f, 1f)
    return 1f - t * (1f - MIN_GLASS_RESOLUTION_SCALE)
}

/**
 * The backdrop blur pipeline requires [android.graphics.RenderEffect] on a
 * [android.graphics.RenderNode], available from Android 12 (API 31).
 */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/**
 * Maps the user-facing vibrancy preference (0..2, default 1) to a saturation multiplier.
 */
fun glassSaturation(vibrancy: Float): Float = 1f + 0.5f * vibrancy.coerceIn(0f, 2f)

val LocalGlassEffectConfig = staticCompositionLocalOf { GlassEffectConfig() }

/** The backdrop content (app UI) that glass surfaces sample from. Provided once in MainActivity. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop].
 *
 * Applies the configured vibrancy, blur and lens refraction effects, then draws the
 * surface tint (theme-adaptive unless the user picked a color). Returns the receiver
 * unchanged on devices without RenderEffect support.
 *
 * [applyEdgeEffects] controls the lens refraction / specular highlight / drop shadow
 * edge treatment. It should be false for large surfaces (e.g. the full screen player)
 * where the rim would render as a stray band of light.
 *
 * [blurRadiusDp] overrides the configured blur — the full screen player passes a
 * heavier value ([PLAYER_BLUR_MULTIPLIER]x) than the nav bar/mini player pills.
 *
 * [shape] is restricted to [CornerBasedShape] because the lens effect throws for any
 * other shape type.
 */
@Composable
fun Modifier.liquidGlass(
    config: GlassEffectConfig,
    shape: CornerBasedShape = RoundedCornerShape(0.dp),
    applyEdgeEffects: Boolean = true,
    blurRadiusDp: Float = config.blurRadius,
): Modifier {
    if (!isGlassSupported()) return this
    val backdrop = LocalAppBackdrop.current
    val density = LocalDensity.current
    val resolutionScale = glassResolutionScale(blurRadiusDp)
    val blurPx = with(density) { blurRadiusDp.dp.toPx() } * resolutionScale
    val saturation = glassSaturation(config.vibrancy)
    val lensHeightPx = with(density) { (config.lensHeight * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val lensAmountPx = with(density) { (config.lensAmount * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val surfaceTintColor = if (config.surfaceTintColor.isSpecified) {
        config.surfaceTintColor
    } else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (saturation != 1f) {
                colorControls(saturation = saturation)
            }
            if (blurPx > 0f) {
                blur(blurPx)
            }
            if (applyEdgeEffects &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (lensHeightPx > 0f || lensAmountPx > 0f)
            ) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = config.depthEffect,
                    chromaticAberration = config.chromaticAberration,
                )
            }
        },
        highlight = if (applyEdgeEffects) ({ Highlight.Default }) else null,
        shadow = if (applyEdgeEffects) ({ Shadow.Default }) else null,
        onDrawSurface = {
            if (config.surfaceOpacity > 0f) {
                drawRect(
                    color = surfaceTintColor.copy(alpha = config.surfaceOpacity),
                    size = size,
                )
            }
        },
        backdropScale = resolutionScale,
    )
}
