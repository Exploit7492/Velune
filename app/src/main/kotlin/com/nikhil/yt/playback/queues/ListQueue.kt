/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.playback.queues

import androidx.media3.common.MediaItem
import com.nikhil.yt.models.MediaMetadata

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
    // Task 59 Round 16, Part B-ii -- same treatment Round 12 gave
    // YouTubeQueue. Set only by a genre-tile-originated song tap in
    // AlbumScreen.kt/ArtistScreen.kt/OnlinePlaylistScreen.kt; every
    // other existing caller of this class is unaffected, defaults to
    // null, same fail-closed behavior the Queue interface's own
    // default already provides.
    override val genre: String? = null,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus() = Queue.Status(title, items, startIndex, position)

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()
}
