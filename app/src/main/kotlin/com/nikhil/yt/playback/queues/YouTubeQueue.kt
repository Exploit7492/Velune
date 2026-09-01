/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.playback.queues

import androidx.media3.common.MediaItem
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.innertube.models.WatchEndpoint
import com.nikhil.yt.extensions.toMediaItem
import com.nikhil.yt.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
    // Task 59 Part 2b-b -- set only by the genre-tile-originated call
    // site in YouTubeBrowseScreen.kt (via radio()'s own new parameter
    // below); every other existing caller of this class/factory is
    // unaffected, defaults to null, same fail-closed behavior as
    // before this part.
    override val genre: String? = null,
) : Queue {
    private var continuation: String? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val nextResult =
            withContext(IO) {
                YouTube.next(endpoint, continuation).getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        return Queue.Status(
            title = nextResult.title,
            items = nextResult.items.map { it.toMediaItem() },
            mediaItemIndex = nextResult.currentIndex ?: 0,
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        val nextResult =
            withContext(IO) {
                YouTube.next(endpoint, continuation).getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        return nextResult.items.map { it.toMediaItem() }
    }

    companion object {
        fun radio(song: MediaMetadata, genre: String? = null) =
            YouTubeQueue(WatchEndpoint(song.id), song, genre = genre)
    }
}
