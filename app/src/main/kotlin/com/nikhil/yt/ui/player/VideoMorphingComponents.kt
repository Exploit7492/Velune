/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.nikhil.yt.R
import com.nikhil.yt.playback.PlayerConnection

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
        if (!isVideoMode || !hasVideoTrack || player == null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(R.drawable.ic_velune_concept),
                fallback = painterResource(R.drawable.ic_velune_concept)
            )
        }

        if (hasVideoTrack && isVideoMode && player != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    view.bringToFront()
                }
            )

            DisposableEffect(Unit) {
                onDispose { /* player is managed by PlayerConnection */ }
            }
        }
    }
}
