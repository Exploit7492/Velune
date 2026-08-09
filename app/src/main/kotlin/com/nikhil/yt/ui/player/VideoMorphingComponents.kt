/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.nikhil.yt.R

@Composable
fun VideoMorphingThumbnail(
    thumbnailUrl: String?,
    isVideoMode: Boolean,
    videoPlayer: Player?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Always show thumbnail (visible when not in video mode or while video loads)
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = "Album Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            error = painterResource(R.drawable.ic_velune_concept),
            fallback = painterResource(R.drawable.ic_velune_concept)
        )

        // When in video mode and slave player is ready, show video surface on top
        if (isVideoMode && videoPlayer != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = videoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        visibility = android.view.View.VISIBLE
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isClickable = false
                        isFocusable = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.player = videoPlayer
                    view.visibility = android.view.View.VISIBLE
                    view.bringToFront()
                }
            )
        }
    }
}
