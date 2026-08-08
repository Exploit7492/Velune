/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nikhil.yt.R
import com.nikhil.yt.playback.PlayerConnection

/**
 * A thumbnail that morphs into a video player when video mode is enabled.
 * Shows cover art when video is off, or when no video track is available.
 */
@Composable
fun VideoMorphingThumbnail(
    thumbnailUrl: String?,
    isVideoMode: Boolean,
    hasVideoTrack: Boolean,
    playerConnection: PlayerConnection?,
    modifier: Modifier = Modifier
) {
    val player = playerConnection?.player

    Box(modifier = modifier) {
        // Cover Art (visible when video mode is OFF or no video track)
        if (!isVideoMode || !hasVideoTrack || player == null) {
            AsyncImage(
                model = ImageRequest.Builder(playerConnection?.context ?: androidx.compose.ui.platform.LocalContext.current)
                    .data(thumbnailUrl)
                    .crossfade(true)
                    .fallback(R.drawable.ic_velune_concept)
                    .error(R.drawable.ic_velune_concept)
                    .build(),
                contentDescription = "Album Art",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Video Player (visible when video mode is ON and video track exists)
        if (hasVideoTrack && isVideoMode && player != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = false
                        resizeMode = PlayerView.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        visibility = android.view.View.VISIBLE
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (view.player != player) {
                        view.player = player
                    }
                    view.visibility = android.view.View.VISIBLE
                    view.resizeMode = PlayerView.RESIZE_MODE_FIT
                    view.bringToFront()
                }
            )

            // No need to release the player – it is managed elsewhere.
            DisposableEffect(Unit) {
                onDispose { /* player is managed by PlayerConnection */ }
            }
        }
    }
}
