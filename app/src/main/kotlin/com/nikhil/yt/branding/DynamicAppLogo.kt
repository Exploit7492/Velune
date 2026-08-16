package com.nikhil.yt.branding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.nikhil.yt.di.AppIconEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Renders whichever icon/logo an admin has published for [slot] via the
 * branding dashboard, falling back to [fallback] (a bundled drawable) when
 * no override is configured for that slot, the config hasn't loaded yet, or
 * the remote asset fails to load — so this composable can never render
 * blank where a static `Image(painterResource(...))` used to be.
 *
 * [tint] is only applied to the bundled [fallback], never to a remote
 * asset — admin-uploaded artwork is shown as-authored, not recolored.
 *
 * Every place in the app that shows the app's own icon (About screen,
 * drawer/header branding, splash) should use this instead of a hardcoded
 * `R.drawable`/`R.mipmap` reference, so a single admin upload reflects
 * everywhere at once instead of each screen needing its own remote-image
 * wiring. See [AppIconConfig] for what "everywhere" covers today vs. what's
 * preview-only pending a true launcher-icon-swap feature.
 */
@Composable
fun DynamicAppLogo(
    slot: AppIconSlot,
    fallback: Painter,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val repository = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, AppIconEntryPoint::class.java)
            .appIconRepository()
    }
    val config by repository.config.collectAsState()

    LaunchedEffect(Unit) { repository.initialize() }

    val remoteUrl = config.assetFor(slot)?.url

    if (remoteUrl != null) {
        AsyncImage(
            model = remoteUrl,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            // If the admin-hosted asset fails to load (bad URL, offline,
            // host down), fall back to the bundled drawable rather than
            // rendering a broken-image placeholder.
            error = fallback,
            fallback = fallback,
        )
    } else {
        Image(
            painter = fallback,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize(),
            colorFilter = tint?.let { ColorFilter.tint(it) },
        )
    }
}

/** Convenience overload taking a drawable resource id instead of a [Painter]. */
@Composable
fun DynamicAppLogo(
    slot: AppIconSlot,
    fallbackResId: Int,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null,
    contentDescription: String? = null,
) {
    DynamicAppLogo(
        slot = slot,
        fallback = painterResource(fallbackResId),
        modifier = modifier,
        tint = tint,
        contentDescription = contentDescription,
    )
}
