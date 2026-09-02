/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.playback.queues

import androidx.media3.common.MediaItem
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.innertube.models.WatchEndpoint
import com.nikhil.yt.db.entities.AlbumWithSongs
import com.nikhil.yt.extensions.toMediaItem
import com.nikhil.yt.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class LocalAlbumRadio(
    private val albumWithSongs: AlbumWithSongs,
    private val startIndex: Int = 0,
    // Task 59 Round 16, Part B-ii -- same treatment Round 12 gave
    // YouTubeQueue, now also applied here. Set only by a genre-tile-
    // originated song tap in AlbumScreen.kt; every other existing
    // caller of this class is unaffected, defaults to null, same
    // fail-closed behavior the Queue interface's own default already
    // provides.
    override val genre: String? = null,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private lateinit var playlistId: String
    private val endpoint: WatchEndpoint
        get() = WatchEndpoint(
            playlistId = playlistId,
            params = "wAEB"
        )

    private var continuation: String? = null
    private var firstTimeLoaded: Boolean = false

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        Queue.Status(
            title = albumWithSongs.album.title,
            items = albumWithSongs.songs.map { it.toMediaItem() },
            mediaItemIndex = startIndex
        )
    }

    override fun hasNextPage(): Boolean = !firstTimeLoaded || continuation != null

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        if (!firstTimeLoaded) {
            playlistId = YouTube.album(albumWithSongs.album.id).getOrThrow().album.playlistId
            val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
            continuation = nextResult.continuation
            firstTimeLoaded = true
            return@withContext nextResult.items.subList(
                albumWithSongs.songs.size,
                nextResult.items.size
            ).map { it.toMediaItem() }
        }
        val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
        continuation = nextResult.continuation
        nextResult.items.map { it.toMediaItem() }
    }
}
