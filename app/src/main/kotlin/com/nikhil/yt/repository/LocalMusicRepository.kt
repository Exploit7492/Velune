/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.nikhil.yt.db.LocalMusicDao
import com.nikhil.yt.db.entities.LocalFolderEntity
import com.nikhil.yt.db.entities.LocalTrackEntity
import com.nikhil.yt.utils.LocalTrackMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localMusicDao: LocalMusicDao,
) {
    private val audioProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.YEAR,
    )

    private val audioSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    fun hasAudioPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    fun watchedFoldersFlow(): Flow<List<LocalFolderEntity>> =
        localMusicDao.watchedFoldersFlow()

    fun allFoldersFlow(): Flow<List<LocalFolderEntity>> =
        localMusicDao.allFoldersFlow()

    fun tracksByFolderFlow(folderId: String): Flow<List<LocalTrackEntity>> =
        localMusicDao.tracksByFolderFlow(folderId)

    suspend fun getTracksByFolder(folderId: String): List<LocalTrackEntity> =
        localMusicDao.getTracksByFolder(folderId)

    suspend fun addWatchedFolder(folderId: String) {
        localMusicDao.setFolderWatched(folderId, true)
        scanFolder(folderId)
    }

    suspend fun removeWatchedFolder(folderId: String) {
        localMusicDao.setFolderWatched(folderId, false)
        localMusicDao.deleteTracksByFolder(folderId)
    }

    suspend fun renameWatchedFolder(folderId: String, newName: String) {
        localMusicDao.renameFolder(folderId, newName)
    }

    suspend fun getFolderById(folderId: String): LocalFolderEntity? =
        localMusicDao.getFolderById(folderId)

    suspend fun deleteFolderFromDevice(folderId: String) {
        localMusicDao.deleteFolder(folderId)
        localMusicDao.deleteTracksByFolder(folderId)
    }

    suspend fun scanAllWatchedFolders() {
        val watched = localMusicDao.getWatchedFolders()
        watched.forEach { scanFolder(it.id) }
    }

    suspend fun scanFolder(folderId: String) = withContext(Dispatchers.IO) {
        try {
            val folder = localMusicDao.getAllFolders().find { it.id == folderId } ?: return@withContext
            val tracks = queryTracksInFolder(folder.path)
            localMusicDao.replaceTracksForFolder(folderId, tracks)
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan folder $folderId")
        }
    }

    suspend fun refreshAvailableAlbums() = withContext(Dispatchers.IO) {
        try {
            val albums = queryMediaStoreAlbums()
            localMusicDao.insertOrReplaceFolders(albums)
            val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            localMusicDao.deleteOldUnwatchedFolders(cutoff)
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh available albums")
        }
    }

    private suspend fun queryMediaStoreAlbums(): List<LocalFolderEntity> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, LocalFolderEntity>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
            ),
            audioSelection,
            null,
            null
        )?.use { cursor ->
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val albumId = cursor.getLong(albumIdCol).toString()
                val albumName = cursor.getString(albumCol) ?: "Unknown Album"
                val filePath = cursor.getString(dataCol) ?: continue
                val parentPath = filePath.substringBeforeLast("/")

                if (folders.containsKey(albumId)) continue

                val artworkUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId.toLong()
                ).toString()

                folders[albumId] = LocalFolderEntity(
                    id = albumId,
                    name = albumName,
                    path = parentPath,
                    artworkUri = artworkUri,
                    isWatched = false,
                    dateAdded = System.currentTimeMillis(),
                    lastScan = System.currentTimeMillis(),
                )
            }
        }

        folders.values.toList()
    }

    private suspend fun queryTracksInFolder(folderPath: String): List<LocalTrackEntity> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<LocalTrackEntity>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val selection = "$audioSelection AND ${MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath/%")

        context.contentResolver.query(
            uri,
            audioProjection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol).toString()
                val albumId = cursor.getLong(albumIdCol)
                val filePath = cursor.getString(dataCol) ?: continue

                val contentUri = ContentUris.withAppendedId(uri, cursor.getLong(idCol)).toString()
                val artworkUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()

                val (enrichedTitle, enrichedArtist) = LocalTrackMetadata.enrich(
                    cursor.getString(titleCol), cursor.getString(artistCol), filePath
                )

                tracks.add(
                    LocalTrackEntity(
                        id = id,
                        folderId = albumId.toString(),
                        title = enrichedTitle,
                        artist = enrichedArtist,
                        album = cursor.getString(albumCol) ?: "Unknown Album",
                        duration = cursor.getLong(durationCol),
                        fileUri = contentUri,
                        artworkUri = artworkUri,
                        trackNumber = cursor.getInt(trackCol),
                        dateAdded = cursor.getLong(dateAddedCol) * 1000,
                        dateModified = cursor.getLong(dateModifiedCol) * 1000,
                    )
                )
            }
        }

        tracks
    }
}
