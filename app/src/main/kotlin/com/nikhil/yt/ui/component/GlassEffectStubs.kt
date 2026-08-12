package com.nikhil.yt.ui.component
import androidx.compose.runtime.Composable; import androidx.compose.runtime.staticCompositionLocalOf
data class GlassEffectConfig(val enabled: Boolean = false)
val LocalGlassEffectConfig = staticCompositionLocalOf { GlassEffectConfig() }
@Composable fun liquidGlass(enabled: Boolean = LocalGlassEffectConfig.current.enabled, content: @Composable () -> Unit) { content() }
