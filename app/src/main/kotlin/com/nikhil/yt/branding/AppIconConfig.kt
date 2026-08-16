package com.nikhil.yt.branding

import kotlinx.serialization.Serializable

/**
 * Every place the app's icon/logo can show up, each with the exact pixel
 * size and format an admin needs to upload for it to render correctly.
 *
 * IMPORTANT platform constraint (documented here so it isn't rediscovered
 * the hard way later): Android's home-screen launcher icon can only be
 * changed at runtime by toggling between a fixed set of `<activity-alias>`
 * entries that were already bundled into the APK at build time — an app
 * cannot fetch an arbitrary image from a server and make the OS use it as
 * the launcher icon the way it can for an in-app logo. [LAUNCHER],
 * [LAUNCHER_FOREGROUND], [LAUNCHER_BACKGROUND] and [LAUNCHER_MONOCHROME]
 * are included here so the admin dashboard has one place to manage every
 * icon asset and so a future "true" launcher-icon-swap feature (built on
 * activity-aliases) has a config shape ready to consume — but today they
 * drive in-app previews (this screen, share cards, the About screen) only.
 * [NOTIFICATION] is the same story: the status bar requires a bundled,
 * single-color silhouette resource, so this slot only affects in-app
 * notification previews and the expanded media-notification's large icon,
 * never the actual status bar glyph.
 * [IN_APP_LOGO] and [SPLASH] are the two slots that *do* apply immediately,
 * everywhere, at runtime, since they're just images rendered inside Compose.
 */
@Serializable
enum class AppIconSlot(
    val label: String,
    val recommendedPx: Int,
    val minPx: Int,
    val requiresTransparency: Boolean,
    val format: String,
) {
    /** Square, flattened launcher icon (legacy, pre-adaptive-icon devices). Preview only — see class doc. */
    LAUNCHER(label = "Launcher icon", recommendedPx = 512, minPx = 192, requiresTransparency = false, format = "PNG/WEBP"),

    /** Adaptive icon foreground layer, transparent background, safe-zone content inside the center ~66%. Preview only. */
    LAUNCHER_FOREGROUND(label = "Adaptive foreground", recommendedPx = 432, minPx = 288, requiresTransparency = true, format = "PNG/WEBP"),

    /** Adaptive icon background layer, opaque, no transparency needed. Preview only. */
    LAUNCHER_BACKGROUND(label = "Adaptive background", recommendedPx = 432, minPx = 288, requiresTransparency = false, format = "PNG/WEBP"),

    /** Android 13+ themed/monochrome icon — single-channel silhouette, alpha carries the shape. Preview only. */
    LAUNCHER_MONOCHROME(label = "Monochrome icon", recommendedPx = 432, minPx = 288, requiresTransparency = true, format = "PNG"),

    /** Large icon shown on the expanded media-style playback notification. Status bar glyph itself stays a bundled vector. */
    NOTIFICATION(label = "Notification large icon", recommendedPx = 256, minPx = 128, requiresTransparency = true, format = "PNG"),

    /** Rendered live: About screen, drawer/header branding, share cards. */
    IN_APP_LOGO(label = "In-app logo", recommendedPx = 512, minPx = 256, requiresTransparency = true, format = "PNG/WEBP"),

    /** Rendered live: cold-start splash screen. */
    SPLASH(label = "Splash logo", recommendedPx = 768, minPx = 384, requiresTransparency = true, format = "PNG/WEBP"),
}

/**
 * One uploaded asset for a given [slot]. [width]/[height] are the actual
 * dimensions of what was uploaded, so the client can warn (never silently
 * upscale/distort) if an admin uploads something under [AppIconSlot.minPx].
 */
@Serializable
data class AppIconAsset(
    val slot: AppIconSlot,
    val url: String,
    val width: Int,
    val height: Int,
    val checksum: String? = null,
) {
    val meetsMinimumSize: Boolean get() = width >= slot.minPx && height >= slot.minPx
}

/**
 * The full remote branding config an admin dashboard publishes. Every field
 * is nullable/defaulted so a partial config (e.g. only [inAppLogo] set)
 * degrades to built-in defaults per-slot instead of failing to parse or
 * leaving other slots blank — a malformed or partially-filled-out remote
 * payload should never be able to crash the app or blank out branding that
 * was already working.
 */
@Serializable
data class AppIconConfig(
    val version: Int = 1,
    val updatedAtEpochMs: Long = 0L,
    val launcher: AppIconAsset? = null,
    val launcherForeground: AppIconAsset? = null,
    val launcherBackground: AppIconAsset? = null,
    val launcherMonochrome: AppIconAsset? = null,
    val notification: AppIconAsset? = null,
    val inAppLogo: AppIconAsset? = null,
    val splash: AppIconAsset? = null,
) {
    fun assetFor(slot: AppIconSlot): AppIconAsset? = when (slot) {
        AppIconSlot.LAUNCHER -> launcher
        AppIconSlot.LAUNCHER_FOREGROUND -> launcherForeground
        AppIconSlot.LAUNCHER_BACKGROUND -> launcherBackground
        AppIconSlot.LAUNCHER_MONOCHROME -> launcherMonochrome
        AppIconSlot.NOTIFICATION -> notification
        AppIconSlot.IN_APP_LOGO -> inAppLogo
        AppIconSlot.SPLASH -> splash
    }

    companion object {
        val EMPTY = AppIconConfig()
    }
}
