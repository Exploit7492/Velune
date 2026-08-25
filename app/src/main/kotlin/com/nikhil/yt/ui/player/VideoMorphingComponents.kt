/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.nikhil.yt.R
import com.nikhil.yt.ui.component.shimmer.ShimmerHost

// Crossfade duration between the thumbnail and the video surface, ported
// from mavins' song/video toggle: a single shared progress value drives
// both layers' opacity in lockstep (rather than two independently-timed
// fades, which can visibly flash or gap if their timing ever drifts even
// slightly relative to each other).
private const val VIDEO_CROSSFADE_DURATION_MS = 300

@Composable
fun VideoMorphingThumbnail(
    thumbnailUrl: String?,
    isVideoMode: Boolean,
    videoPlayer: Player?,
    modifier: Modifier = Modifier
) {
    // Single progress value, 0 = fully Song, 1 = fully Video, animated
    // over VIDEO_CROSSFADE_DURATION_MS in either direction. Callers now
    // always pass the same `videoPlayer` reference regardless of
    // isVideoMode (see Player.kt's call sites) specifically so this fade
    // has something to render *out of* on the way back to Song, not just
    // fade *into* on the way to Video — losing the player reference the
    // instant isVideoMode flips would cut the fade-out short.
    val videoAlpha by animateFloatAsState(
        targetValue = if (isVideoMode) 1f else 0f,
        animationSpec = tween(durationMillis = VIDEO_CROSSFADE_DURATION_MS),
        label = "videoCrossfade"
    )

    // Tracks the slave video player's own buffering state (distinct from
    // isLoadingVideo in Player.kt, which only covers the rare live-resolve
    // fallback in toggleVideo() — this covers the much more common case of
    // ExoPlayer itself buffering a track that's already resolved, e.g. a
    // slow network on the initial video switch or after a seek). Listener
    // is only attached while there's actually a video player to observe;
    // state resets to false whenever the player reference changes so a
    // stale "buffering" from a previous track can't linger.
    var isBuffering by remember(videoPlayer) { mutableStateOf(false) }
    DisposableEffect(videoPlayer) {
        if (videoPlayer == null) {
            onDispose {}
        } else {
            isBuffering = videoPlayer.playbackState == Player.STATE_BUFFERING
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                }
            }
            videoPlayer.addListener(listener)
            onDispose { videoPlayer.removeListener(listener) }
        }
    }

    Box(modifier = modifier) {
        // Thumbnail: always composed (so the fade has something under
        // the video at all times), fading out as the video fades in.
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = "Album Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - videoAlpha },
            error = painterResource(R.drawable.ic_velune_concept),
            fallback = painterResource(R.drawable.ic_velune_concept)
        )

        // Video surface: only actually composed while relevant (either
        // currently in video mode, or still fading out of it) — avoids
        // keeping a PlayerView attached indefinitely once fully faded
        // back to Song.
        if (videoPlayer != null && (isVideoMode || videoAlpha > 0f)) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = videoPlayer
                        useController = false
                        // RESIZE_MODE_FIT letterboxes/pillarboxes the video to preserve its
                        // aspect ratio within the frame, which leaves the cover art behind
                        // it visible in the gaps whenever the video's aspect ratio doesn't
                        // match the frame — the video is meant to fully take over the frame
                        // the same way the cover art (ContentScale.Crop) does, not float in
                        // the middle of it. ZOOM crops to fill instead, matching that.
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        // Was SHOW_BUFFERING_WHEN_PLAYING, which drew Media3's own default
                        // spinner directly on the PlayerView. Replaced with the app's own
                        // skeleton loader (below) instead, matching the shimmer treatment
                        // used everywhere else loading state is shown in this app (see
                        // ShimmerHost/GridItemPlaceHolder) — never letting the built-in
                        // spinner draw is what actually removes it, disabling it here isn't
                        // enough on its own if this stays SHOW_BUFFERING_WHEN_PLAYING.
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        visibility = android.view.View.VISIBLE
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isClickable = false
                        isFocusable = false
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = videoAlpha },
                update = { view ->
                    view.player = videoPlayer
                    view.visibility = android.view.View.VISIBLE
                    view.bringToFront()
                }
            )

            // Skeleton loader: only while actually in Video mode (not during
            // the fade-out back to Song, where the stale last frame — not a
            // loading state — is what should be visible) and only once the
            // crossfade has progressed enough to be seeing the video layer
            // at all, so it doesn't flash on top of the thumbnail underneath
            // it. Same alpha as the video layer so it crossfades with it
            // rather than popping in/out abruptly.
            if (isVideoMode && isBuffering) {
                ShimmerHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = videoAlpha }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
        }
    }
}

