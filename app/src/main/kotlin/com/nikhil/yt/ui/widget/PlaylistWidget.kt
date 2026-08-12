/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 *
 * Playlist widget. Conceptually inspired by Echo Music's PlaylistWidgetManager,
 * but reimplemented from scratch on androidx.glance instead of ported: Echo's
 * version is hand-rolled RemoteViews + manual Bitmap/Canvas drawing (~700 lines)
 * and does not transplant onto Glance's Compose-based model. Scope here is
 * intentionally smaller than Echo's — up to 4 recently-updated playlists,
 * cover art, tap-to-open, manual refresh — with room to grow later.
 */

package com.nikhil.yt.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.unit.ColorProvider
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nikhil.yt.MainActivity
import com.nikhil.yt.R
import com.nikhil.yt.db.entities.Playlist
import com.nikhil.yt.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

const val EXTRA_OPEN_LOCAL_PLAYLIST_ID = "com.nikhil.yt.EXTRA_OPEN_LOCAL_PLAYLIST_ID"

private const val MAX_PLAYLIST_WIDGET_ITEMS = 4
private const val PLAYLIST_WIDGET_ART_SIZE = 160
private const val FIELD_DELIMITER = "\u0001"
private const val ITEM_DELIMITER = "\u0002"

val playlistWidgetItemsKey = stringPreferencesKey("playlist_widget_items")

private data class PlaylistWidgetItem(
    val id: String,
    val name: String,
    val songCount: Int,
    val artPath: String?,
)

private fun encodePlaylistWidgetItems(items: List<PlaylistWidgetItem>): String =
    items.joinToString(ITEM_DELIMITER) { item ->
        listOf(item.id, item.name, item.songCount.toString(), item.artPath ?: "")
            .joinToString(FIELD_DELIMITER)
    }

private fun decodePlaylistWidgetItems(raw: String): List<PlaylistWidgetItem> {
    if (raw.isBlank()) return emptyList()
    return raw.split(ITEM_DELIMITER).mapNotNull { entry ->
        val fields = entry.split(FIELD_DELIMITER)
        if (fields.size < 4) return@mapNotNull null
        PlaylistWidgetItem(
            id = fields[0],
            name = fields[1],
            songCount = fields[2].toIntOrNull() ?: 0,
            artPath = fields[3].ifBlank { null },
        )
    }
}

class PlaylistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaylistWidget()
}

class RefreshPlaylistWidgetCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlaylistWidget().update(context, glanceId)
    }
}

class PlaylistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = loadPlaylistWidgetItems(context)
        updateAppWidgetState(context, id) { prefs ->
            prefs[playlistWidgetItemsKey] = encodePlaylistWidgetItems(items)
        }
        provideContent {
            PlaylistWidgetUi()
        }
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun PlaylistWidgetUi() {
        val prefs = currentState<Preferences>()
        val context = LocalContext.current
        val items = decodePlaylistWidgetItems(prefs[playlistWidgetItemsKey] ?: "")

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(24.dp)
                .background(ColorProvider(ComposeColor(0xFF1E1E1E)))
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Playlists",
                    style = TextStyle(
                        color = ColorProvider(ComposeColor.White),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Box(
                    modifier = GlanceModifier
                        .size(28.dp)
                        .clickable(actionRunCallback<RefreshPlaylistWidgetCallback>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.sync),
                        contentDescription = "Refresh",
                        colorFilter = ColorFilter.tint(ColorProvider(ComposeColor.White)),
                        modifier = GlanceModifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (items.isEmpty()) {
                Text(
                    text = "No playlists yet",
                    style = TextStyle(color = ColorProvider(ComposeColor.Gray), fontSize = 13.sp),
                )
            } else {
                items.forEach { item ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable(
                                actionStartActivity(openPlaylistIntent(context, item.id))
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val artBitmap = item.artPath?.let { BitmapFactory.decodeFile(it) }
                        Image(
                            provider = if (artBitmap != null) ImageProvider(artBitmap)
                                else ImageProvider(R.drawable.queue_music),
                            contentDescription = item.name,
                            modifier = GlanceModifier.size(40.dp).cornerRadius(6.dp),
                        )
                        Column(
                            modifier = GlanceModifier.padding(start = 10.dp).defaultWeight()
                        ) {
                            Text(
                                text = item.name,
                                style = TextStyle(
                                    color = ColorProvider(ComposeColor.White),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 1,
                            )
                            Text(
                                text = "${item.songCount} songs",
                                style = TextStyle(color = ColorProvider(ComposeColor.Gray), fontSize = 11.sp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openPlaylistIntent(context: Context, playlistId: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(EXTRA_OPEN_LOCAL_PLAYLIST_ID, playlistId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

private suspend fun loadPlaylistWidgetItems(context: Context): List<PlaylistWidgetItem> =
    withContext(Dispatchers.IO) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            val database = entryPoint.musicDatabase()
            val playlists = database.playlistsByUpdatedDateAsc()
                .first()
                .takeLast(MAX_PLAYLIST_WIDGET_ITEMS)
                .reversed()

            playlists.map { playlist ->
                PlaylistWidgetItem(
                    id = playlist.playlist.id,
                    name = playlist.playlist.name,
                    songCount = playlist.songCount,
                    artPath = cachePlaylistArt(context, playlist),
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

private fun cachePlaylistArt(context: Context, playlist: Playlist): String? {
    val thumbnailUrl = playlist.thumbnails.firstOrNull() ?: return null
    val file = File(context.cacheDir, "widget_playlist_art_${playlist.playlist.id}.png")
    if (file.exists()) return file.absolutePath
    return try {
        val connection = URL(thumbnailUrl).openConnection()
        connection.connect()
        val bitmap = BitmapFactory.decodeStream(connection.getInputStream())
        val scaled = Bitmap.createScaledBitmap(bitmap, PLAYLIST_WIDGET_ART_SIZE, PLAYLIST_WIDGET_ART_SIZE, true)
        FileOutputStream(file).use { out -> scaled.compress(Bitmap.CompressFormat.PNG, 100, out) }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}
