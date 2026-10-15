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
    // genre-tile tap can actually construct.
    //
    // `null` means "this queue has no genre context" -- search results,
    // albums, playlists, artist radio, autoplay. The RPC reads it as
    // "any eligible campaign", which is correct: campaign injection is
    // a monetization slot that belongs on every queue (§33).
    //
    // It does NOT mean "fail closed". §H was exactly that conflation: a
    // genre-tile queue whose tile had no reviewed mapping also sent
    // `null`, so the RPC widened to the whole catalogue and an R&B tile
    // could be served Hip-Hop. A genre-tile queue must carry a concrete
    // term; see [com.nikhil.yt.campaign.GenreTileMappingCache.resolveTile]
    // for how the client guarantees that, and migration 046 for the
    // server side, which fails closed on a term it cannot resolve.
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
