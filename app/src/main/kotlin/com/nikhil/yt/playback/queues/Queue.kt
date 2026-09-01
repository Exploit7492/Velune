/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.playback.queues

import androidx.media3.common.MediaItem
import com.nikhil.yt.extensions.ExtraIsMusicVideo
import com.nikhil.yt.extensions.metadata
import com.nikhil.yt.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    // Task 59 Part 2b-b (handover.md) -- carries genre context from a
    // genre-tile-originated browse tap through to MusicService's
    // campaign-injection wrapping site, without threading a new
    // parameter through playQueue()'s own signature at every one of
    // its many call sites across this app. A Kotlin interface property
    // with a default implementation -- every existing Queue
    // implementer (ListQueue, YouTubeQueue, LocalMixQueue,
    // LocalAlbumRadio, YouTubeAlbumRadio, EmptyQueue,
    // CampaignInjectedQueue) inherits `null` for free, zero changes
    // required to any of them except the one (YouTubeQueue) that a
    // genre-tile tap can actually construct. `null` here is the
    // correct fail-closed default for every queue not explicitly
    // genre-tagged -- exactly Task 59's own standing rule, satisfied
    // structurally rather than by a caller remembering to check.
    val genre: String? get() = null

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                copy(
                    items = items.filterExplicit(),
                )
            } else {
                this
            }
        fun filterVideo(enabled: Boolean = true) =
            if (enabled) {
                copy(
                    items = items.filterVideo(),
                )
            } else {
                this
            }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideo(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.mediaMetadata.extras?.getBoolean(ExtraIsMusicVideo, false) == true
        }
    } else {
        this
    }
