/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 *
 * Turntable-style widget. Reimplemented on androidx.glance to match Echo
 * Music's visual concept: circular album art filling a square widget, a
 * floating play/pause button, and a prev/next pill. Verified against Echo's
 * actual EchoMusicWidgetManager/TurntableWidgetReceiver source: there is no
 * rotation animation or physics in their implementation either (getCircularBitmap
 * is a one-time crop, not a spin) — so this is a faithful reimplementation of
 * what Echo actually ships, not a cut-down approximation of it.
 *
 * Reuses the same widgetTitleKey / widgetArtPathKey / widgetIsPlayingKey
 * preference keys already populated by updateVeluneWidgetState in
 * VeluneWidget.kt, so it needs no separate data pipeline.
 */

package com.nikhil.yt.ui.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.unit.ColorProvider
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.nikhil.yt.R
import com.nikhil.yt.playback.MusicService

class TurntableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TurntableWidget()
}

class TurntableWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            TurntableWidgetUi()
        }
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun TurntableWidgetUi() {
        val prefs = currentState<Preferences>()
        val context = LocalContext.current

        val isPlaying = prefs[widgetIsPlayingKey] ?: false
        val artPath = prefs[widgetArtPathKey]
        val artBitmap = artPath?.let { BitmapFactory.decodeFile(it) }
        val imageProvider = if (artBitmap != null) ImageProvider(artBitmap)
            else ImageProvider(R.drawable.ic_velune_concept)

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_white

        val openAppIntent = actionStartActivity(
            ComponentName(context.packageName, "com.nikhil.yt.MainActivity")
        )

        fun mediaServiceIntent(mediaAction: String) = actionStartService(
            Intent(context, MusicService::class.java).apply { action = mediaAction },
            isForegroundService = true,
        )

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .padding(4.dp),
        ) {
            // Circular album art, filling the widget. cornerRadius greater than
            // half the smaller dimension renders as a full circle.
            Image(
                provider = imageProvider,
                contentDescription = "Album Art",
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(999.dp)
                    .clickable(openAppIntent),
            )

            // Prev/Next pill, top-end — matches Echo's layout position.
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(top = 16.dp, end = 4.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                Row(
                    modifier = GlanceModifier
                        .cornerRadius(18.dp)
                        .background(ColorProvider(ComposeColor(0x99000000.toInt()))),
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(32.dp)
                            .clickable(mediaServiceIntent("com.nikhil.yt.ACTION_PREV")),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_skip_previous),
                            contentDescription = "Previous",
                            colorFilter = ColorFilter.tint(ColorProvider(ComposeColor.White)),
                            modifier = GlanceModifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(2.dp))
                    Box(
                        modifier = GlanceModifier
                            .size(32.dp)
                            .clickable(mediaServiceIntent("com.nikhil.yt.ACTION_NEXT")),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_skip_next),
                            contentDescription = "Next",
                            colorFilter = ColorFilter.tint(ColorProvider(ComposeColor.White)),
                            modifier = GlanceModifier.size(18.dp),
                        )
                    }
                }
            }

            // Floating play/pause button, bottom-start — matches Echo's layout position.
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(bottom = 8.dp, start = 8.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(36.dp)
                        .cornerRadius(18.dp)
                        .background(ColorProvider(ComposeColor.White))
                        .clickable(mediaServiceIntent("com.nikhil.yt.ACTION_PLAY_PAUSE")),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(playPauseIcon),
                        contentDescription = "Play/Pause",
                        colorFilter = ColorFilter.tint(ColorProvider(ComposeColor.Black)),
                        modifier = GlanceModifier.size(20.dp),
                    )
                }
            }
        }
    }
}
