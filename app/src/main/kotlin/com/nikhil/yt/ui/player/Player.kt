/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.player

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.material3.IconButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.nikhil.yt.LocalDownloadUtil
import com.nikhil.yt.LocalPlayerConnection
import com.nikhil.yt.R
import com.nikhil.yt.constants.DarkModeKey
import com.nikhil.yt.constants.DisableBlurKey
import com.nikhil.yt.constants.PlayerBackgroundStyle
import com.nikhil.yt.constants.PlayerBackgroundStyleKey
import com.nikhil.yt.constants.PlayerButtonsStyle
import com.nikhil.yt.constants.PlayerButtonsStyleKey
import com.nikhil.yt.constants.PlayerCustomBlurKey
import com.nikhil.yt.constants.PlayerCustomBrightnessKey
import com.nikhil.yt.constants.PlayerCustomContrastKey
import com.nikhil.yt.constants.PlayerCustomImageUriKey
import com.nikhil.yt.constants.PlayerDesignStyle
import com.nikhil.yt.constants.PlayerDesignStyleKey
import com.nikhil.yt.constants.PlayerHorizontalPadding
import com.nikhil.yt.constants.QueuePeekHeight
import com.nikhil.yt.constants.SliderStyle
import com.nikhil.yt.constants.SliderStyleKey
import com.nikhil.yt.constants.UseNewMiniPlayerDesignKey
import com.nikhil.yt.extensions.metadata
import com.nikhil.yt.extensions.togglePlayPause
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.nikhil.yt.innertube.toHighResThumbnail
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.innertube.models.YouTubeClient
import okhttp3.OkHttpClient
import com.nikhil.yt.models.MediaMetadata
import com.nikhil.yt.ui.component.BottomSheet
import com.nikhil.yt.ui.component.BottomSheetState
import com.nikhil.yt.ui.component.LocalBottomSheetPageState
import com.nikhil.yt.ui.component.LocalMenuState
import com.nikhil.yt.ui.component.rememberBottomSheetState
import com.nikhil.yt.ui.menu.PlayerMenu
import com.nikhil.yt.ui.screens.settings.DarkMode
import com.nikhil.yt.ui.theme.PlayerColorExtractor
import com.nikhil.yt.ui.utils.ShowMediaInfo
import com.nikhil.yt.utils.rememberEnumPreference
import com.nikhil.yt.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ─── Import video morphing component ───────────────────────────────────────────
import com.nikhil.yt.ui.player.VideoMorphingThumbnail

// ─── Muted slave video / master audio soft-sync tuning ──────────────────────────
// See the LaunchedEffect(isVideoMode) sync-correction block below for how these
// are used. Kept file-scope (not inline magic numbers) so they're easy to find
// and retune together without hunting through the sync loop's body.
private const val SOFT_SYNC_CHECK_INTERVAL_MS = 400L
private const val SOFT_DRIFT_DEAD_ZONE_MS = 60
private const val MAX_SOFT_DRIFT_MS = 1500
private const val SOFT_SYNC_RAMP_MS = 800f
private const val SOFT_SYNC_MAX_RATE = 0.06f

// Small forward lead applied when seeking the (already-prepared) slave
// video player at the moment of an actual toggle-to-video tap, to roughly
// account for the gap between calling seekTo()/playWhenReady=true and the
// first frame actually rendering. 800ms, matching the value this was
// tuned at before the eager-pre-buffer change (see
// loadVideoForCurrentTrack/toggleVideo above) — kept as specified rather
// than the smaller guess an earlier pass here used, since pre-buffering
// changes how much of the old cold-start latency this constant needs to
// cover and that's not something this sandbox can measure directly. The
// soft-sync loop above will still correct any residual drift within a
// second or so either way; this only affects how close the very first
// frame lands on tap.
private const val VIDEO_TOGGLE_SEEK_LEAD_MS = 800L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    // Ensure master player never loads video (audio-only)
    LaunchedEffect(playerConnection.player) {
        val trackSelector = playerConnection.player.trackSelector
        if (trackSelector is DefaultTrackSelector) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            )
        }
    }

    // ─── Video morphing state ────────────────────────────────────────────────────
    var isVideoMode by remember { mutableStateOf(false) }
    var isLoadingVideo by remember { mutableStateOf(false) }
    // Video-availability discovery state, ported from mavins' song/video
    // toggle: null = not yet known (still resolving, or the track just
    // changed), true = confirmed available AND already prepared/buffered
    // (ready to switch instantly), false = confirmed no video for this
    // track. Lets the pill dim the Video label ahead of time instead of
    // only discovering unavailability after a tap, and lets toggleVideo()
    // below take the instant path once this is true.
    var hasVideo by remember { mutableStateOf<Boolean?>(null) }

    // The video stream URL resolved for the toggle always comes from the IOS
    // client (see YTPlayerUtils.resolveVideoStreamUrl), and googlevideo.com
    // rejects requests that don't carry that client's User-Agent — a bare
    // ExoPlayer.Builder() uses ExoPlayer's own default User-Agent instead, so
    // the request gets refused and the toggle looked like it "did nothing"
    // even after the URL itself resolved correctly. Same header setup
    // CanvasArtworkPlayer/MusicService already use for the same reason.
    val slaveOkHttpClient = remember {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                val isYouTubeMediaHost =
                    host.endsWith("googlevideo.com") ||
                        host.endsWith("googleusercontent.com") ||
                        host.endsWith("youtube.com") ||
                        host.endsWith("youtube-nocookie.com") ||
                        host.endsWith("ytimg.com")

                if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                chain.proceed(
                    request.newBuilder()
                        .header("User-Agent", YouTubeClient.IOS.userAgent)
                        .build()
                )
            }
            .build()
    }
    val slaveMediaSourceFactory = remember(slaveOkHttpClient) {
        DefaultMediaSourceFactory(
            DefaultDataSource.Factory(context, OkHttpDataSource.Factory(slaveOkHttpClient))
        )
    }

    // Slave video player (audio disabled, muted) — this only ever plays the
    // picture; the real audio always comes from playerConnection.player. Every
    // control (play/pause, next, previous, seek) is meant to keep driving the
    // master audio player exactly as it always has — the slave just follows it.
    val player = remember(context, slaveMediaSourceFactory) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(slaveMediaSourceFactory)
            .setTrackSelector(DefaultTrackSelector(context).apply {
                setParameters(buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                )
            })
            .build()
            .apply { volume = 0f }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    // Video (and the view-count pill, further below) only makes sense for
    // streamed YouTube tracks — a local file has no video stream or view
    // count to fetch at all, and blindly attempting the resolve for one
    // would just be a guaranteed-to-fail network call every time (a local
    // song's id isn't a YouTube video id). Gates both the eager pre-buffer
    // below and the Song/Video pill's visibility in the UI further down.
    val isCurrentSongLocal = currentSong?.song?.isLocal == true

    // Clears CampaignPlaybackTracker the moment whatever's actually
    // playing stops being the campaign that was tapped to start it — see
    // CampaignPlaybackTracker's doc. Placed here rather than inside
    // MetroPlayerContent so it fires regardless of which content variant
    // is rendering, and fires on every real track change, not just ones
    // that happen to recompose the content composable.
    androidx.compose.runtime.LaunchedEffect(mediaMetadata?.id) {
        com.nikhil.yt.campaign.CampaignPlaybackTracker.clearIfNot(mediaMetadata?.id)
    }

    // ─── View count pill state ───────────────────────────────────────────────────
    // Design ported from mavins' player screen: a small translucent pill
    // with an icon + an abbreviated, animated count-up number. Fetches
    // MediaInfo (already used elsewhere for the "Song Info" bottom sheet,
    // see ShowMediaInfo.kt) per track, since the player screen doesn't
    // otherwise have view-count data at all.
    var viewCountTarget by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(mediaMetadata?.id, isCurrentSongLocal) {
        viewCountTarget = null
        if (isCurrentSongLocal) return@LaunchedEffect
        val videoId = mediaMetadata?.id ?: return@LaunchedEffect
        val count = runCatching { YouTube.getMediaInfo(videoId).getOrNull()?.viewCount }
            .getOrNull()
        if (count != null && count > 0) viewCountTarget = count
    }

    suspend fun loadVideoForCurrentTrack(autoPlayIfAlreadyInVideoMode: Boolean) {
        val videoId = mediaMetadata?.id
        if (videoId == null || isCurrentSongLocal) {
            // Local media: no video stream exists to resolve, so don't
            // even try — confirmed-unavailable immediately rather than
            // going through a network call destined to fail.
            hasVideo = false
            if (isVideoMode) isVideoMode = false
            return
        }
        // Ported from mavins' song/video toggle: this is called
        // unconditionally as soon as a track becomes active (see the
        // LaunchedEffect below), not gated behind isVideoMode — that's
        // the actual mechanism behind the toggle feeling instant instead
        // of laggy. By the time the user taps "Video", the stream has
        // (usually) already been resolved and prepared, sitting there
        // paused/muted, ready to seek+play — see the fast path in
        // toggleVideo below.
        //
        // Trade-off, stated plainly: every track now pays the video
        // stream's resolve+buffer cost (a real network round-trip — see
        // YTPlayerUtils.resolveVideoStreamUrl; cached after the first
        // resolve, but not free the first time) even for users who never
        // touch the Video tab. That's the same trade mavins makes — it's
        // what "no lag on toggle" actually costs, not a free lunch.
        hasVideo = null
        isLoadingVideo = true
        val videoUrl = withContext(Dispatchers.IO) {
            try {
                com.nikhil.yt.utils.YTPlayerUtils.resolveVideoStreamUrl(videoId)
            } catch (e: Exception) { null }
        }
        isLoadingVideo = false
        if (videoUrl != null) {
            hasVideo = true
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
            player.prepare()
            player.seekTo(playerConnection.player.currentPosition)
            // Stays paused/muted until the user actually switches to
            // video (or, if a track change happened while already in
            // video mode, resumes right away to match) — pre-buffering
            // means "ready", not "playing".
            player.playWhenReady = autoPlayIfAlreadyInVideoMode && isPlaying
        } else {
            hasVideo = false
            if (isVideoMode) isVideoMode = false
            player.stop()
            player.clearMediaItems()
        }
    }

    // Song changed. If the user was already in video mode, load the *new*
    // track's video and stay in video mode instead of unconditionally dropping
    // back to audio — that unconditional drop was the reason pressing next/
    // previous while watching video silently kicked you back to audio-only:
    // every control that changes tracks (next/previous buttons anywhere in the
    // app, the notification, a headset button, the queue) changes
    // mediaMetadata.id the same way, so this one effect covers all of them.
    //
    // Now also always pre-buffers the video regardless of isVideoMode (see
    // loadVideoForCurrentTrack's doc above) — previously this only fired
    // for tracks the user was already watching in video mode, leaving
    // every other track's video to cold-start resolve at toggle time.
    // Keyed on isCurrentSongLocal too (not just the id) since currentSong
    // is its own async Flow that can briefly lag behind mediaMetadata
    // updating — this makes sure the local-media gate inside
    // loadVideoForCurrentTrack gets re-evaluated once it settles, not
    // just whatever it happened to read on the first composition after
    // an id change.
    LaunchedEffect(mediaMetadata?.id, isCurrentSongLocal) {
        player.stop()
        player.clearMediaItems()
        loadVideoForCurrentTrack(autoPlayIfAlreadyInVideoMode = isVideoMode)
    }

    // Prefetch the *next* queue item's video stream ahead of time, so
    // whenever the transition above actually happens, its call to
    // resolveVideoStreamUrl hits a cache hit (see that function's
    // cache-first check in YTPlayerUtils.kt) instead of a cold network
    // resolve — the actual cause of the reported "next-song video
    // hitch": until this was added, the current track's video only
    // ever got resolved *reactively*, after the transition had already
    // happened and the slave player had already been stopped/cleared,
    // so there was nothing to show until a fresh resolve completed.
    //
    // Keyed the same as the effect above so this re-fires every time
    // the current track (and therefore "next") changes — including
    // when the user skips ahead of a prefetch that hadn't finished yet,
    // which simply starts a new prefetch for the now-different next
    // item; the old one's result, if it ever lands, just sits harmlessly
    // in the cache unused rather than being raced against or cancelled
    // explicitly.
    //
    // Deliberately does NOT touch the local Room database to check
    // whether the next track is local media (unlike isCurrentSongLocal
    // above, which does) — that would mean threading a database
    // reference into this composable that doesn't otherwise need one.
    // Instead, a real YouTube video id is always exactly 11 characters
    // from a fixed alphanumeric alphabet (matching
    // CampaignUrlResolver.VIDEO_ID_REGEX's own definition of the same
    // fact, established when that class was built), while a local
    // track's mediaId is its own local database row id — essentially
    // certain not to coincidentally match that exact shape. Skipping
    // the prefetch on a false negative here just means the existing
    // reactive resolve still runs when that track actually becomes
    // current, same as today — this prefetch is a pure optimization,
    // never behavior-load-bearing.
    LaunchedEffect(mediaMetadata?.id, isCurrentSongLocal) {
        val nextIndex = playerConnection.player.nextMediaItemIndex
        if (nextIndex == androidx.media3.common.C.INDEX_UNSET) return@LaunchedEffect
        val nextId = playerConnection.player.getMediaItemAt(nextIndex).mediaId
        if (!Regex("[A-Za-z0-9_-]{11}").matches(nextId)) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching { com.nikhil.yt.utils.YTPlayerUtils.resolveVideoStreamUrl(nextId) }
        }
    }

    // Keep the muted slave's play/pause mirroring the master's — for *any*
    // control that changed it, not just this screen's own button. That's what
    // makes "press pause while in video mode -> video pauses too" work
    // regardless of whether pause came from this screen, the mini player, the
    // notification, or a headset button.
    LaunchedEffect(isPlaying, isVideoMode) {
        if (isVideoMode) {
            player.playWhenReady = isPlaying
        }
    }

    // Soft sync correction — the same approach YouTube's own clients (and
    // Netflix, Twitch, etc) use to keep a silent secondary video pipeline
    // glued to a master timeline: nudge *playback speed* by a tiny amount
    // rather than ever calling `seekTo` during normal playback. A `seekTo`
    // on a network-streamed video forces a re-buffer — a visible freeze —
    // which is what caused the "keeps hitching" complaint when this used to
    // hard-seek every 3s. A speed nudge has no re-buffer at all: the
    // decoder just renders frames a few percent faster or slower for a
    // moment, which is imperceptible on a muted video track (no pitch to
    // give it away, unlike doing this to audible audio).
    //
    // - Dead zone below SOFT_DRIFT_DEAD_ZONE_MS: do nothing. Sub-frame drift
    //   is inaudible/invisible and correcting it constantly would just be
    //   jitter for no benefit.
    // - Between the dead zone and MAX_SOFT_DRIFT_MS: proportional speed
    //   nudge, clamped to +/-SOFT_SYNC_MAX_RATE so it's never enough to be
    //   noticeable, and reset back to 1.0x the moment we're back inside the
    //   dead zone.
    // - Above MAX_SOFT_DRIFT_MS: something more than clock drift happened
    //   (a real stall, a dropped seek, etc) — a speed nudge would take too
    //   long to close that gap, so this is the one case that still falls
    //   back to a hard `seekTo`. This should be rare in practice; routine
    //   drift is handled entirely by the speed nudge above.
    LaunchedEffect(isVideoMode) {
        if (!isVideoMode) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(SOFT_SYNC_CHECK_INTERVAL_MS)
            if (!isVideoMode) break
            if (!isPlaying) continue // nothing to correct while paused

            val drift = playerConnection.player.currentPosition - player.currentPosition
            val absDrift = kotlin.math.abs(drift)

            when {
                absDrift > MAX_SOFT_DRIFT_MS -> {
                    player.seekTo(playerConnection.player.currentPosition)
                    player.setPlaybackParameters(PlaybackParameters(1f))
                }
                absDrift > SOFT_DRIFT_DEAD_ZONE_MS -> {
                    // Behind master (drift > 0) -> speed up slightly to catch up.
                    // Ahead of master (drift < 0) -> slow down slightly to fall back.
                    val correction = (drift.toFloat() / SOFT_SYNC_RAMP_MS) * SOFT_SYNC_MAX_RATE
                    val rate = 1f + correction.coerceIn(-SOFT_SYNC_MAX_RATE, SOFT_SYNC_MAX_RATE)
                    player.setPlaybackParameters(PlaybackParameters(rate))
                }
                else -> {
                    player.setPlaybackParameters(PlaybackParameters(1f))
                }
            }
        }
        // Leaving video mode (or the loop otherwise exiting) — always leave
        // the slave at normal speed so a stale nudge never lingers into the
        // next video load.
        player.setPlaybackParameters(PlaybackParameters(1f))
    }

    // Toggle function — manages slave video player.
    val toggleVideo: () -> Unit = {
        if (isVideoMode) {
            isVideoMode = false
            // Just pause, don't stop()/clearMediaItems() anymore — the
            // whole point of pre-buffering (loadVideoForCurrentTrack
            // above) is that leaving video mode shouldn't throw away an
            // already-loaded stream. Toggling back to Video a moment
            // later should be instant too, not a re-fetch from scratch.
            player.pause()
        } else if (hasVideo == true) {
            // Fast path — ported from mavins' song/video toggle: this is
            // the case that makes the toggle feel instant. The stream
            // was already resolved and prepared by
            // loadVideoForCurrentTrack the moment this track became
            // active, so switching now is just a seek + play on an
            // already-buffered player, no network round-trip in the way.
            isVideoMode = true
            player.seekTo(playerConnection.player.currentPosition + VIDEO_TOGGLE_SEEK_LEAD_MS)
            player.playWhenReady = isPlaying
        } else if (hasVideo == null) {
            // Rare edge case: the toggle was tapped within the first
            // moment or two of a new track starting, before this track's
            // eager pre-buffer (kicked off by the LaunchedEffect above)
            // finished resolving. Falls back to the old live-resolve-on-
            // tap path so the toggle still works — just without the
            // "instant" property this time. isLoadingVideo is now
            // actually wired into the pill UI below (it previously
            // wasn't rendered anywhere, so this path used to look like
            // the tap "did nothing" for however long the resolve took).
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                loadVideoForCurrentTrack(autoPlayIfAlreadyInVideoMode = false)
                if (hasVideo == true) {
                    isVideoMode = true
                    player.seekTo(playerConnection.player.currentPosition + VIDEO_TOGGLE_SEEK_LEAD_MS)
                    player.playWhenReady = isPlaying
                }
            }
        }
        // hasVideo == false: confirmed no video for this track — nothing
        // to switch to. The pill's Video label is dimmed and its click
        // handler disabled for this case below, so this branch should be
        // unreachable in practice; left as a no-op rather than an
        // uncovered `when` on principle.
    }

    val playerDesignStyle by rememberEnumPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.V3
    )

    val (useNewMiniPlayerDesign) = rememberPreference(
        UseNewMiniPlayerDesignKey,
        defaultValue = true
    )

    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.COLORING
    )

    val (playerCustomImageUri) = rememberPreference(PlayerCustomImageUriKey, "")
    val (playerCustomBlur) = rememberPreference(PlayerCustomBlurKey, 0f)
    val (playerCustomContrast) = rememberPreference(PlayerCustomContrastKey, 1f)
    val (playerCustomBrightness) = rememberPreference(PlayerCustomBrightnessKey, 1f)

    val (disableBlur) = rememberPreference(DisableBlurKey, true)
    val (showCodecOnPlayer) = rememberPreference(
        booleanPreferencesKey("show_codec_on_player"),
        false
    )

    val playerButtonsStyle by rememberEnumPreference(
        key = PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.SECONDARY
    )

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.ON)
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }
    when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
        else ->
            if (useDarkTheme)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onPrimary
    }
    val useBlackBackground =
        remember(isSystemInDarkTheme, darkTheme, pureBlack) {
            val useDarkTheme =
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            useDarkTheme && pureBlack
        }
    if (useNewMiniPlayerDesign) {
        if (useBlackBackground && state.value > state.collapsedBound) {
            val progress =
                ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
                    .coerceIn(0f, 1f)
            Color.Black.copy(alpha = progress)
        } else {
            val progress =
                ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
                    .coerceIn(0f, 1f)
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = progress)
        }
    } else {
        if (useBlackBackground) {
            lerp(MaterialTheme.colorScheme.surfaceContainer, Color.Black, state.progress)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    }

    val playbackState by playerConnection.playbackState.collectAsState()
    val currentSongLiked = currentSong?.song?.liked == true
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    playerConnection.service.playerVolume.collectAsState()

    val automix by playerConnection.service.automixItems.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()

    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.Circular)

    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    var duration by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.duration)
    }
    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }

    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null

    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }

    var previousThumbnailUrl by remember { mutableStateOf<String?>(null) }
    var previousGradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    val gradientColorsCache = remember { mutableMapOf<String, List<Color>>() }

    if (!canSkipNext && automix.isNotEmpty()) {
        playerConnection.service.addToQueueAutomix(automix[0], 0)
    }

    val defaultGradientColors =
        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()

    LaunchedEffect(mediaMetadata?.id) {
        val currentThumbnail = mediaMetadata?.thumbnailUrl
        if (currentThumbnail != previousThumbnailUrl) {
            previousThumbnailUrl = currentThumbnail
            previousGradientColors = gradientColors
        }
    }

    LaunchedEffect(mediaMetadata?.id, playerBackground) {
        if (playerBackground == PlayerBackgroundStyle.GRADIENT || playerBackground == PlayerBackgroundStyle.COLORING || playerBackground == PlayerBackgroundStyle.BLUR_GRADIENT || playerBackground == PlayerBackgroundStyle.GLOW || playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED || playerBackground == PlayerBackgroundStyle.APPLE_MUSIC || playerBackground == PlayerBackgroundStyle.LIVE_MESH || playerBackground == PlayerBackgroundStyle.LIQUID_GLASS) {
            val currentMetadata = mediaMetadata
            if (currentMetadata != null && currentMetadata.thumbnailUrl != null) {
                val cachedColors = gradientColorsCache[currentMetadata.id]
                if (cachedColors != null) {
                    gradientColors = cachedColors
                } else {
                    val request = ImageRequest.Builder(context)
                        .data(currentMetadata.thumbnailUrl)
                        .size(
                            PlayerColorExtractor.Config.IMAGE_SIZE,
                            PlayerColorExtractor.Config.IMAGE_SIZE
                        )
                        .allowHardware(false)
                        .build()

                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            context.imageLoader.execute(request)
                        }
                    }.getOrNull()

                    if (result != null) {
                        val bitmap = result.image?.toBitmap()
                        if (bitmap != null) {
                            val palette = withContext(Dispatchers.Default) {
                                Palette.from(bitmap)
                                    .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                    .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                    .generate()
                            }

                            val extractedColors = PlayerColorExtractor.extractGradientColors(
                                palette = palette,
                                fallbackColor = fallbackColor
                            )

                            gradientColorsCache[currentMetadata.id] = extractedColors
                            gradientColors = extractedColors
                        } else {
                            gradientColors = defaultGradientColors
                        }
                    } else {
                        gradientColors = defaultGradientColors
                    }
                }
            } else {
                gradientColors = emptyList()
            }
        } else {
            gradientColors = emptyList()
        }
    }

    state.expandedBound / 3

    val TextBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
            PlayerBackgroundStyle.BLUR -> Color.White
            PlayerBackgroundStyle.GRADIENT -> Color.White
            PlayerBackgroundStyle.COLORING -> Color.White
            PlayerBackgroundStyle.BLUR_GRADIENT -> Color.White
            PlayerBackgroundStyle.GLOW -> Color.White
            PlayerBackgroundStyle.GLOW_ANIMATED -> Color.White
            PlayerBackgroundStyle.CUSTOM -> Color.White
            PlayerBackgroundStyle.APPLE_MUSIC -> Color.White
            PlayerBackgroundStyle.LIVE_MESH -> Color.White
            PlayerBackgroundStyle.LIQUID_GLASS -> Color.White
        }

    val icBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
            PlayerBackgroundStyle.BLUR -> Color.Black
            PlayerBackgroundStyle.GRADIENT -> Color.Black
            PlayerBackgroundStyle.COLORING -> Color.Black
            PlayerBackgroundStyle.BLUR_GRADIENT -> Color.Black
            PlayerBackgroundStyle.GLOW -> Color.Black
            PlayerBackgroundStyle.GLOW_ANIMATED -> Color.Black
            PlayerBackgroundStyle.CUSTOM -> Color.Black
            PlayerBackgroundStyle.APPLE_MUSIC -> Color.Black
            PlayerBackgroundStyle.LIVE_MESH -> Color.Black
            PlayerBackgroundStyle.LIQUID_GLASS -> Color.Black
        }

    val (textButtonColor, iconButtonColor) = when (playerButtonsStyle) {
        PlayerButtonsStyle.DEFAULT -> Pair(TextBackgroundColor, icBackgroundColor)
        PlayerButtonsStyle.PRIMARY -> Pair(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
        PlayerButtonsStyle.TERTIARY -> Pair(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
        PlayerButtonsStyle.SECONDARY -> Pair(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary
        )
    }

    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd
        ) {
            playerConnection.service.sleepTimer.isActive
        }

    var sleepTimerTimeLeft by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }

    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    var sleepTimerValue by remember {
        mutableFloatStateOf(30f)
    }
    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.bedtime),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSleepTimerDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )

                    OutlinedIconButton(
                        onClick = {
                            showSleepTimerDialog = false
                            playerConnection.service.sleepTimer.start(-1)
                        },
                    ) {
                        Text(stringResource(R.string.end_of_song))
                    }
                }
            },
        )
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.player.currentPosition
                duration = playerConnection.player.duration
            }
        }
    }

    val dynamicQueuePeekHeight =
        if (showCodecOnPlayer) {
            88.dp
        } else {
            QueuePeekHeight
        }

    val dismissedBound =
        dynamicQueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = dismissedBound,
        expandedBound = state.expandedBound,
        collapsedBound = dismissedBound + 1.dp,
        initialAnchor = 1
    )

    val lyricsSheetState = rememberBottomSheetState(
        dismissedBound = 0.dp,
        expandedBound = state.expandedBound,
        collapsedBound = 0.dp,
        initialAnchor = 1
    )

    BackHandler(
        enabled =
            (!lyricsSheetState.isCollapsed && !lyricsSheetState.isDismissed) ||
                    (!queueSheetState.isCollapsed && !queueSheetState.isDismissed) ||
                    (!state.isCollapsed && !state.isDismissed)
    ) {
        when {
            !lyricsSheetState.isCollapsed && !lyricsSheetState.isDismissed -> lyricsSheetState.collapseSoft()
            !queueSheetState.isCollapsed && !queueSheetState.isDismissed -> queueSheetState.collapseSoft()
            !state.isCollapsed && !state.isDismissed -> state.collapseSoft()
        }
    }

    BottomSheet(
        state = state,
        modifier = modifier,
        backgroundColor = when (playerBackground) {
            PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> {
                val progress =
                    ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
                        .coerceIn(0f, 1f)

                val fadeProgress = if (progress < 0.2f) {
                    ((0.2f - progress) / 0.2f).coerceIn(0f, 1f)
                } else {
                    0f
                }

                MaterialTheme.colorScheme.surface.copy(alpha = 1f - fadeProgress)
            }

            else -> {
                val progress =
                    ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
                        .coerceIn(0f, 1f)

                val fadeProgress = if (progress < 0.2f) {
                    ((0.2f - progress) / 0.2f).coerceIn(0f, 1f)
                } else {
                    0f
                }

                if (useBlackBackground) {
                    Color.Black.copy(alpha = 1f - fadeProgress)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 1f - fadeProgress)
                }
            }
        },
        onDismiss = {
            playerConnection.service.stopAndClearPlayback()
        },
        collapsedContent = {
            MiniPlayer(
                position = position,
                duration = duration,
                pureBlack = pureBlack,
            )
        },
    ) {
        // Background must be drawn first: this content lambda is a BoxScope, and later
        // siblings paint over earlier ones at the same position. PlayerBackground is
        // Modifier.fillMaxSize(), so having it declared after the top bar (as it was)
        // painted directly over the equalizer icon and made it invisible.
        if (!state.isCollapsed) {
            PlayerBackground(
                playerBackground = playerBackground,
                mediaMetadata = mediaMetadata,
                gradientColors = gradientColors,
                disableBlur = disableBlur,
                playerCustomImageUri = playerCustomImageUri,
                playerCustomBlur = playerCustomBlur,
                playerCustomContrast = playerCustomContrast,
                playerCustomBrightness = playerCustomBrightness
            )
        }

        val onSliderValueChange: (Long) -> Unit = { sliderPosition = it }
        val onSliderValueChangeFinished: () -> Unit = {
            sliderPosition?.let {
                playerConnection.player.seekTo(it)
                if (isVideoMode) player.seekTo(it)
                position = it
            }
            sliderPosition = null
        }
        duration > 0L && duration != C.TIME_UNSET

        remember(queueWindows, currentWindowIndex) {
            queueWindows.getOrNull(currentWindowIndex + 1)?.mediaItem?.metadata
        }

        val enrichedMetadata = remember(mediaMetadata, currentSong) {
            val meta = mediaMetadata ?: return@remember null
            if (meta.album != null) return@remember meta
            val dbAlbum = currentSong?.album
            val dbAlbumId = currentSong?.song?.albumId
            when {
                dbAlbum != null -> meta.copy(
                    album = MediaMetadata.Album(id = dbAlbum.id, title = dbAlbum.title)
                )

                dbAlbumId != null -> meta.copy(
                    album = MediaMetadata.Album(
                        id = dbAlbumId,
                        title = currentSong?.song?.albumName.orEmpty()
                    )
                )

                else -> meta
            }
        }

        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            PlayerControlsContent(
                mediaMetadata = mediaMetadata,
                playerDesignStyle = playerDesignStyle,
                sliderStyle = sliderStyle,
                playbackState = playbackState,
                isPlaying = isPlaying,
                isLoading = isLoading,
                repeatMode = repeatMode,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                textButtonColor = textButtonColor,
                iconButtonColor = iconButtonColor,
                textBackgroundColor = TextBackgroundColor,
                icBackgroundColor = icBackgroundColor,
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                playerConnection = playerConnection,
                navController = navController,
                state = state,
                menuState = menuState,
                bottomSheetPageState = bottomSheetPageState,
                clipboardManager = clipboardManager,
                context = context,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished
            )
        }

        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                if (playerDesignStyle == PlayerDesignStyle.V5) {
                    enrichedMetadata?.let { metadata ->
                        MetroPlayerContent(
                            mediaMetadata = metadata,
                            sliderPosition = sliderPosition,
                            positionMs = position,
                            durationMs = duration,
                            textColor = TextBackgroundColor,
                            liked = currentSongLiked,
                            playerConnection = playerConnection,
                            onToggleLike = playerConnection::toggleLike,
                            onExpandQueue = queueSheetState::expandSoft,
                            onMenuClick = {
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = metadata,
                                        navController = navController,
                                        playerBottomSheetState = state,
                                        onShowDetailsDialog = {
                                            bottomSheetPageState.show { ShowMediaInfo(metadata.id) }
                                        },
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            },
                            context = context,
                            bottomPadding = dynamicQueuePeekHeight,
                            isVideoMode = isVideoMode,
                            videoPlayer = player, // always passed so the crossfade (see VideoMorphingComponents.kt) has a stable player reference to fade out from, not just fade in to
                            onToggleVideo = toggleVideo
                        )
                    }

                } else {
                    Row(
                        modifier =
                            Modifier
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                                .padding(bottom = queueSheetState.collapsedBound + 48.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f),
                        ) {
                            // Was Modifier.size(screenWidth * 0.4f.dp) — a small, fixed-size
                            // square floating in the middle of this half of the landscape
                            // Row, leaving the rest of its Box empty. Asked to cover edge to
                            // edge of its actual container instead (this Box, itself
                            // weight(1f) of the Row — i.e. the full available half of the
                            // screen), the same way VideoMorphingThumbnail's own internal
                            // AsyncImage/PlayerView already crop-to-fill (ContentScale.Crop /
                            // RESIZE_MODE_ZOOM) whatever size *they're* given — the gap was
                            // entirely at this call site's modifier, not inside that
                            // component. fillMaxSize() here is what actually closes it.
                            VideoMorphingThumbnail(
                                thumbnailUrl = mediaMetadata?.thumbnailUrl?.toHighResThumbnail(),
                                isVideoMode = isVideoMode,
                                videoPlayer = player, // always passed so the crossfade (see VideoMorphingComponents.kt) has a stable player reference to fade out from, not just fade in to
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(
                                            WindowInsetsSides.Top
                                        )
                                    ),
                        ) {
                            Spacer(Modifier.weight(1f))

                            enrichedMetadata?.let {
                                controlsContent(it)
                            }

                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            else -> {
                if (playerDesignStyle == PlayerDesignStyle.V5) {
                    enrichedMetadata?.let { metadata ->
                        MetroPlayerContent(
                            mediaMetadata = metadata,
                            sliderPosition = sliderPosition,
                            positionMs = position,
                            durationMs = duration,
                            textColor = TextBackgroundColor,
                            liked = currentSongLiked,
                            playerConnection = playerConnection,
                            onToggleLike = playerConnection::toggleLike,
                            onExpandQueue = queueSheetState::expandSoft,
                            onMenuClick = {
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = metadata,
                                        navController = navController,
                                        playerBottomSheetState = state,
                                        onShowDetailsDialog = {
                                            bottomSheetPageState.show { ShowMediaInfo(metadata.id) }
                                        },
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            },
                            context = context,
                            bottomPadding = dynamicQueuePeekHeight,
                            isVideoMode = isVideoMode,
                            videoPlayer = player, // always passed so the crossfade (see VideoMorphingComponents.kt) has a stable player reference to fade out from, not just fade in to
                            onToggleVideo = toggleVideo
                        )
                    }

                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                                .padding(bottom = queueSheetState.collapsedBound),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f),
                        ) {
                            // Was missing any size modifier at all — relied entirely on
                            // ambiguous constraint propagation from a weight(1f) Box inside a
                            // Column with no explicit width, which is not the same thing as
                            // actually filling this Box's real available area. Explicit
                            // fillMaxSize() (matching the fix applied to the landscape call
                            // site above) is what makes this cover edge to edge of its
                            // container instead of sizing to whatever ambiguous constraint
                            // happened to fall out of that chain.
                            VideoMorphingThumbnail(
                                thumbnailUrl = mediaMetadata?.thumbnailUrl?.toHighResThumbnail(),
                                isVideoMode = isVideoMode,
                                videoPlayer = player, // always passed so the crossfade (see VideoMorphingComponents.kt) has a stable player reference to fade out from, not just fade in to
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
                            )
                        }

                        enrichedMetadata?.let {
                            controlsContent(it)
                        }

                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }

        // ─── TOP BAR: SONG/VIDEO PILL (left) — EQUALIZER (right) ───────────────────
        // FIX: this Row and the `when (orientation)` block above it are both direct
        // siblings inside the same BoxScope, so whichever is declared later paints
        // over the one declared earlier at the same screen position (same rule the
        // PlayerBackground fix above already relies on). The main content block
        // (the full-bleed thumbnail/controls Column) was declared after this Row,
        // so it painted directly over the Song/Video pill and made it invisible —
        // the equalizer icon happened to survive because the thumbnail image is
        // centered and usually doesn't reach all the way to the very top edge, but
        // the pill sits flush left where the image reliably covers it. Moving this
        // Row to render after the main content — instead of moving the content, or
        // giving the content a top inset — keeps it painting on top without
        // affecting the content's own layout/sizing.
        //
        // Accent follows the same artwork-extracted palette (gradientColors) the
        // rest of the player uses, so the pill's selected state and the equalizer
        // icon re-tint themselves per song/artist instead of a fixed theme color.
        // gradientColors is only populated for background styles that use it
        // (GRADIENT/COLORING/GLOW/etc.) — for the others it's empty and this falls
        // back to the normal theme primary, so nothing changes there.
        val pillAccentColor = gradientColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
        val pillAccentContentColor = if (pillAccentColor.luminance() > 0.5f) Color.Black else Color.White

        // Glassy pill background — same self-contained backdrop-blur technique
        // GlassMiniPlayer uses (a synthetic backdrop tinted from the artist's own
        // accent color, blurred + given vibrancy), rather than a flat translucent
        // fill, so this reads as actual frosted glass instead of a plain chip.
        val pillSupportsBackdrop = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        val pillShape = RoundedCornerShape(14.dp)
        val density = LocalDensity.current
        val pillBackdrop = rememberCanvasBackdrop {
            drawRect(color = pillAccentColor.copy(alpha = 0.22f), size = size)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Pushed further down from the very top edge (was flush against it)
                // and off the horizontal edges a touch more to read as a floating
                // control rather than an edge-docked bar.
                .padding(
                    start = PlayerHorizontalPadding,
                    end = PlayerHorizontalPadding,
                    top = PlayerHorizontalPadding + 40.dp,
                    bottom = 8.dp,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pill switch (Song / Video) — top-left. Doesn't exist at all
            // for local device files: there's no video stream, and no
            // network endpoint that would ever resolve one for a local
            // song id, so a toggle that can only ever fail isn't a toggle
            // worth showing — matches isCurrentSongLocal's gate on the
            // eager pre-buffer itself (loadVideoForCurrentTrack above).
            // Spacer keeps the EQ icon pinned to the right edge via
            // SpaceBetween the same as when the pill is present, rather
            // than collapsing the Row down to a single centered/left
            // child once the pill is gone.
            if (isCurrentSongLocal) {
                Spacer(modifier = Modifier.size(1.dp))
            } else {
            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .then(
                        if (pillSupportsBackdrop) {
                            Modifier.drawBackdrop(
                                backdrop = pillBackdrop,
                                shape = { pillShape },
                                effects = {
                                    vibrancy()
                                    blur(with(density) { 14.dp.toPx() })
                                },
                                onDrawSurface = {
                                    drawRect(pillAccentColor.copy(alpha = 0.10f))
                                }
                            )
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        }
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.18f), pillShape)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Song button
                Surface(
                    modifier = Modifier
                        .clickable { if (isVideoMode) toggleVideo() }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = if (!isVideoMode) pillAccentColor else Color.Transparent,
                ) {
                    Text(
                        text = "Song",
                        color = if (!isVideoMode) pillAccentContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                // Video button
                // Dimmed + non-interactive once hasVideo is confirmed
                // false for this track (ported from mavins' toggle,
                // which does the same `!hasVideo && {opacity:0.3}`
                // treatment) — previously this only discovered
                // unavailability after a tap, silently reverting to Song
                // with no explanation. While hasVideo is still null
                // (resolving), the button stays fully interactive/
                // optimistic, since a tap in that window is handled by
                // toggleVideo's fallback path rather than blocked.
                val videoUnavailable = hasVideo == false
                Surface(
                    modifier = Modifier
                        .clickable(enabled = !videoUnavailable) { if (!isVideoMode) toggleVideo() }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = if (isVideoMode) pillAccentColor else Color.Transparent,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Video",
                            color = (if (isVideoMode) pillAccentContentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                                .copy(alpha = if (videoUnavailable) 0.3f else 1f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        // Rare-path feedback: only ever shown when a tap
                        // landed in the hasVideo==null resolving window
                        // and fell back to a live resolve (see
                        // toggleVideo) — the normal, pre-buffered path
                        // never sets this, so most users should never
                        // see it. Previously isLoadingVideo was tracked
                        // but never actually rendered anywhere, so that
                        // fallback path looked like the tap did nothing.
                        if (isLoadingVideo) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(8.dp),
                                strokeWidth = 1.dp,
                                color = if (isVideoMode) pillAccentContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            }

            // View count pill — center, between the Song/Video pill and
            // the EQ icon. Hidden for local media the same way the
            // Song/Video pill is (isCurrentSongLocal — no YouTube view
            // count exists for a local file), but shown for any streamed
            // track regardless of whether it has a usable video stream
            // (hasVideo/the toggle above), since a plain audio-only
            // YouTube track still has a real view count.
            if (!isCurrentSongLocal) {
                ViewCountPill(target = viewCountTarget)
            }

            // Equalizer — top-right. Opens the hybrid Axion equalizer (graphic/
            // circular + parametric/advanced modes, presets) directly, same
            // screen the overflow "..." menu's Equalizer entry should open too.
            // Collapse the full-player sheet in the same click as navigating —
            // otherwise the sheet stays expanded on top of the newly-navigated
            // screen (which renders underneath, just hidden) until the user
            // presses back and the sheet's own BackHandler collapses it, which
            // looked like the EQ screen didn't open until you hit back.
            //
            // FIX: this used to be a bare IconButton floating directly on the
            // player background — every other top-bar control (the Song/Video
            // switch above) sits inside the same glassy pill treatment, so the
            // EQ button looked inconsistent/unstyled next to it. Wrapped it in
            // the identical pill container (same shape, backdrop-blur/tint
            // fallback, and border) so it reads as a matching pair rather than
            // one chip + one bare icon.
            Row(
                modifier = Modifier
                    .clip(pillShape)
                    .then(
                        if (pillSupportsBackdrop) {
                            Modifier.drawBackdrop(
                                backdrop = pillBackdrop,
                                shape = { pillShape },
                                effects = {
                                    vibrancy()
                                    blur(with(density) { 14.dp.toPx() })
                                },
                                onDrawSurface = {
                                    drawRect(pillAccentColor.copy(alpha = 0.10f))
                                }
                            )
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        }
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.18f), pillShape)
            ) {
                IconButton(onClick = {
                    state.collapseSoft()
                    navController.navigate("eq/axion")
                }) {
                    // FIX: this used to tint the icon itself with
                    // pillAccentColor (the artwork-extracted accent that
                    // shifts per song/album and drives the pill's own
                    // animated backdrop) — which made the icon's color
                    // chase and blend into the shifting background behind
                    // it instead of reading clearly against it. The
                    // Song/Video pill next to this one never does that:
                    // its segment text stays a fixed color regardless of
                    // the accent (MaterialTheme.colorScheme.onSurfaceVariant
                    // when unselected, a fixed black/white computed once
                    // from luminance when selected) — the accent color is
                    // only ever meant to live in the pill's background
                    // surface, never applied directly to the content sitting
                    // on top of it. Fixed white to match that pattern and
                    // the explicit ask: this icon should stay put visually
                    // while the pill background around it keeps shifting.
                    Icon(
                        painter = painterResource(R.drawable.equalizer),
                        contentDescription = stringResource(R.string.equalizer),
                        tint = Color.White,
                    )
                }
            }
        }

        val queueOnBackgroundColor =
            if (useBlackBackground) Color.White else MaterialTheme.colorScheme.onSurface
        val queueSurfaceColor =
            if (useBlackBackground) Color.Black else MaterialTheme.colorScheme.surface

        val (_, _) = when (playerButtonsStyle) {
            PlayerButtonsStyle.DEFAULT -> Pair(queueOnBackgroundColor, queueSurfaceColor)
            PlayerButtonsStyle.PRIMARY -> Pair(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.onPrimary
            )
            PlayerButtonsStyle.TERTIARY -> Pair(
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.onTertiary
            )
            PlayerButtonsStyle.SECONDARY -> Pair(
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.onSecondary
            )
        }

        Queue(
            state = queueSheetState,
            playerBottomSheetState = state,
            navController = navController,
            backgroundColor =
                if (useBlackBackground) {
                    Color.Black
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            onBackgroundColor = queueOnBackgroundColor,
            TextBackgroundColor = TextBackgroundColor,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            onShowLyrics = { lyricsSheetState.expandSoft() },
            pureBlack = pureBlack,
        )

        mediaMetadata?.let { metadata ->
            BottomSheet(
                state = lyricsSheetState,
                backgroundColor = Color.Unspecified,
                onDismiss = { /* Optional dismiss action */ },
                collapsedContent = {
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(
                                alpha = lyricsSheetState.progress.coerceIn(0f, 1f)
                            )
                        )
                ) {
                    LyricsScreen(
                        mediaMetadata = metadata,
                        onBackClick = { lyricsSheetState.collapseSoft() },
                        navController = navController
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetroPlayerContent(
    mediaMetadata: MediaMetadata,
    sliderPosition: Long?,
    positionMs: Long,
    durationMs: Long,
    textColor: Color,
    liked: Boolean,
    playerConnection: com.nikhil.yt.playback.PlayerConnection,
    onToggleLike: () -> Unit,
    onExpandQueue: () -> Unit,
    onMenuClick: () -> Unit,
    context: Context,
    bottomPadding: androidx.compose.ui.unit.Dp,
    // Video parameters – used only for the thumbnail
    isVideoMode: Boolean,
    videoPlayer: Player?,
    onToggleVideo: () -> Unit // kept for compatibility, not used directly here
) {
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val isLoading = playbackState == STATE_BUFFERING
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
            .padding(bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.7f)
            )
            Text(
                text = mediaMetadata.album?.title ?: "Playing from Library",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            VideoMorphingThumbnail(
                thumbnailUrl = mediaMetadata.thumbnailUrl?.toHighResThumbnail(),
                isVideoMode = isVideoMode,
                videoPlayer = videoPlayer,
                // aspectRatio(1f) is correct for the Song-mode cover art (a
                // square is the intentional look there) but is exactly what
                // was capping the video at edge-to-edge horizontally only:
                // this Box is taller than it is wide (weight(1f) inside a
                // Column), so forcing a 1:1 square left empty space above
                // and below whenever a video was actually showing. Dropping
                // the constraint while isVideoMode is true lets the video
                // genuinely fill the full weight(1f) height, matching the
                // edge-to-edge-horizontally behavior that already worked.
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (isVideoMode) it else it.aspectRatio(1f) }
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = com.nikhil.yt.constants.PlayerHorizontalPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val trackedCampaign by com.nikhil.yt.campaign.CampaignPlaybackTracker.current.collectAsState()
                    val campaign = trackedCampaign?.takeIf { it.songId == mediaMetadata.id }
                    if (campaign != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            if (campaign.certified) {
                                Image(
                                    painter = painterResource(
                                        if (isSystemInDarkTheme()) R.drawable.campaign_badge_dark else R.drawable.campaign_badge_light
                                    ),
                                    contentDescription = "Reviewed pick",
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = "Promoted",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color(0xFFD4AF37),
                            )
                        }
                    }
                    Text(
                        text = mediaMetadata.title ?: "Unknown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = mediaMetadata.artists.joinToString { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))

                // ─── Share and Like buttons (no video toggle here) ──────────────
                Surface(
                    onClick = {
                        val intent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                            )
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, null))
                    },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = "Share",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = onToggleLike,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                            contentDescription = "Like",
                            tint = if (liked) MaterialTheme.colorScheme.error else Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val displayPositionMs = sliderPosition ?: positionMs
            StyledPlaybackSlider(
                sliderStyle = SliderStyle.Wavy,
                value = (displayPositionMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(
                    0f,
                    1f
                ),
                valueRange = 0f..1f,
                onValueChange = { fraction ->
                    val target = (durationMs * fraction).toLong()
                    playerConnection.player.seekTo(target)
                    if (isVideoMode) videoPlayer?.seekTo(target)
                },
                onValueChangeFinished = {},
                activeColor = textColor,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .offset(y = (-8).dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = com.nikhil.yt.utils.makeTimeString(displayPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
                Text(
                    text = if (durationMs != C.TIME_UNSET) com.nikhil.yt.utils.makeTimeString(
                        durationMs
                    ) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val playInteractionSource =
                androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isPlayPressed by playInteractionSource.collectIsPressedAsState()
            val sideButtonWidth by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (isPlayPressed) 48.dp else 64.dp,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.6f,
                    stiffness = 400f
                ),
                label = "SideWidth"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = playerConnection::seekToPrevious,
                    shape = RoundedCornerShape(50),
                    color = textColor.copy(alpha = 0.08f),
                    modifier = Modifier
                        .width(sideButtonWidth)
                        .height(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.skip_previous),
                            contentDescription = "Previous",
                            tint = textColor.copy(alpha = if (canSkipPrevious) 1f else 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Surface(
                    onClick = {
                        if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                            playerConnection.player.seekTo(
                                0,
                                0
                            )
                            playerConnection.player.playWhenReady = true
                        } else playerConnection.player.togglePlayPause()
                    },
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    interactionSource = playInteractionSource,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isLoading) com.nikhil.yt.ui.component.VeluneLoader(size = 24.dp)
                        else {
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPlaying) "Pause" else "Play",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Black
                            )
                        }
                    }
                }
                Surface(
                    onClick = playerConnection::seekToNext,
                    shape = RoundedCornerShape(50),
                    color = textColor.copy(alpha = 0.08f),
                    modifier = Modifier
                        .width(sideButtonWidth)
                        .height(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.skip_next),
                            contentDescription = "Next",
                            tint = textColor.copy(alpha = if (canSkipNext) 1f else 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
