package com.nikhil.yt.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class LocalMediaStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class AlbumInfo(val id: String, val title: String, val artworkUri: String?, val assetCount: Int)
    data class TrackInfo(val id: String, val title: String, val artist: String, val album: String,
        val albumId: String, val duration: Long, val fileUri: String, val lastModified: Long)

    suspend fun getAvailableAlbums(): List<AlbumInfo> = withContext(Dispatchers.IO) {
        val albums = mutableMapOf<String, AlbumInfo>()
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, "${MediaStore.Audio.Media.ALBUM} ASC")?.use { cursor ->
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val albumId = cursor.getString(albumIdCol) ?: continue
                val data = cursor.getString(dataCol) ?: continue
                val folderPath = data.substringBeforeLast("/", "")
                val folderName = folderPath.substringAfterLast("/", cursor.getString(albumCol) ?: "Unknown")
                val key = "$folderPath|$albumId"

                albums[key] = albums[key]?.let { it.copy(assetCount = it.assetCount + 1) }
                    ?: AlbumInfo(key, folderName, null, 1)
            }
        }
        albums.values.sortedBy { it.title }
    }

    suspend fun getTracksForAlbum(albumId: String): List<TrackInfo> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<TrackInfo>()
        val parts = albumId.split("|", limit = 2)
        val folderPath = if (parts.size == 2) parts[0] else albumId

        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DATE_MODIFIED)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, arrayOf("$folderPath/%"), sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idCol) ?: continue
                val data = cursor.getString(dataCol) ?: continue
                val (enrichedTitle, enrichedArtist) = LocalTrackMetadata.enrich(
                    cursor.getString(titleCol), cursor.getString(artistCol), data
                )
                tracks.add(TrackInfo(
                    id = id, title = enrichedTitle,
                    artist = enrichedArtist,
                    album = cursor.getString(albumCol) ?: "Unknown Album",
                    albumId = albumId, duration = cursor.getLong(durationCol),
                    fileUri = data, lastModified = cursor.getLong(dateModCol) * 1000
                ))
            }
        }
        tracks
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}
