/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.extensions
import com.nikhil.yt.models.MediaMetadata.Artist
import com.nikhil.yt.models.MediaMetadata.Album

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.nikhil.yt.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import com.nikhil.yt.db.entities.Song
import com.nikhil.yt.models.MediaMetadata
import com.nikhil.yt.models.toMediaMetadata

const val ExtraIsMusicVideo = "com.nikhil.yt.extra.IS_MUSIC_VIDEO"

// The campaign a promoted item came from, carried on the item itself.
// `get_next_campaign_for_queue_slot` (migration 083) returns the whole
// campaign row when it picks one for a queue slot, and this is where that
// row lands: the injected MediaItem is the only object the playback
// service, the notification and the player screen all share, so putting
// the campaign's own numbers on it means each of them can render the
// campaign without a second request — and without having to guess which
// campaign a bare video id belongs to.
const val ExtraCampaignId = "com.nikhil.yt.extra.CAMPAIGN_ID"
const val ExtraCampaignTotalStreams = "com.nikhil.yt.extra.CAMPAIGN_TOTAL_STREAMS"
const val ExtraCampaignGenres = "com.nikhil.yt.extra.CAMPAIGN_GENRES"
const val ExtraCampaignStage = "com.nikhil.yt.extra.CAMPAIGN_STAGE"
const val ExtraCampaignCoverUrl = "com.nikhil.yt.extra.CAMPAIGN_COVER_URL"

/**
 * Attach the campaign a promoted item was served for, so the enriched
 * fields the RPC returned travel with the item through the queue instead
 * of being dropped at injection time.
 *
 * Additive and idempotent: the existing extras bundle is copied and
 * extended, so `ExtraIsMusicVideo` and anything else already set stays.
 */
fun MediaItem.withCampaign(
    campaignId: String,
    totalStreams: Long,
    genres: List<String>,
    stage: String,
    coverUrl: String?,
): MediaItem {
    val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply {
        putString(ExtraCampaignId, campaignId)
        putLong(ExtraCampaignTotalStreams, totalStreams)
        putStringArray(ExtraCampaignGenres, genres.toTypedArray())
        putString(ExtraCampaignStage, stage)
        if (coverUrl != null) putString(ExtraCampaignCoverUrl, coverUrl)
    }
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
}

/** The campaign id this item was injected for, or `null` for an ordinary item. */
val MediaItem.campaignId: String?
    get() = mediaMetadata.extras?.getString(ExtraCampaignId)

/** The campaign's delivered stream total at injection time, or `null`. */
val MediaItem.campaignTotalStreams: Long?
    get() = mediaMetadata.extras?.takeIf { it.containsKey(ExtraCampaignTotalStreams) }
        ?.getLong(ExtraCampaignTotalStreams)

/**
 * The campaign a promoted item was injected for, in one value, or `null`
 * for an ordinary item.
 *
 * Reads only what the server sent in the queue-slot response — nothing is
 * inferred from the video id, so an ordinary track that happens to be
 * promoted elsewhere still reads as an ordinary track here.
 */
data class InjectedCampaignInfo(
    val campaignId: String,
    val totalStreams: Long,
    val genres: List<String>,
    val stage: String,
    val coverUrl: String?,
)

val MediaItem.campaignInfo: InjectedCampaignInfo?
    get() {
        val extras = mediaMetadata.extras ?: return null
        val id = extras.getString(ExtraCampaignId)?.takeIf { it.isNotBlank() } ?: return null
        return InjectedCampaignInfo(
            campaignId = id,
            totalStreams = extras.getLong(ExtraCampaignTotalStreams),
            genres = extras.getStringArray(ExtraCampaignGenres)?.filter { it.isNotBlank() }.orEmpty(),
            stage = extras.getString(ExtraCampaignStage).orEmpty().ifBlank { "planting" },
            coverUrl = extras.getString(ExtraCampaignCoverUrl)?.takeIf { it.isNotBlank() },
        )
    }


val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata

fun Song.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(song.id)
        .setUri(song.id)
        .setCustomCacheKey(song.id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(song.title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(song.thumbnailUrl?.toUri())
                .setAlbumTitle(song.albumName)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { putBoolean(ExtraIsMusicVideo, false) })
                .build(),
        ).build()

fun SongItem.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCustomCacheKey(id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(thumbnail.toUri())
                .setAlbumTitle(album?.name)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { putBoolean(ExtraIsMusicVideo, isMusicVideo()) })
                .build(),
        ).build()

fun MediaMetadata.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCustomCacheKey(id)
        .setTag(this)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(thumbnailUrl?.toUri())
                .setAlbumTitle(album?.title)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { putBoolean(ExtraIsMusicVideo, false) })
                .build(),
        ).build()

private fun SongItem.isMusicVideo(): Boolean {
    val musicVideoType = endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
    return musicVideoType == MUSIC_VIDEO_TYPE_OMV || musicVideoType == MUSIC_VIDEO_TYPE_UGC
}

fun com.nikhil.yt.db.entities.LocalTrackEntity.toMediaItem(): MediaItem {
    val metadata = com.nikhil.yt.models.MediaMetadata(
        id = id,
        title = title,
        artists = listOf(com.nikhil.yt.models.MediaMetadata.Artist(name = artist, id = "")),
        duration = (duration / 1000L).toInt(),
        thumbnailUrl = artworkUri,
        album = com.nikhil.yt.models.MediaMetadata.Album(title = album, id = folderId),
    )
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(fileUri)
        .setCustomCacheKey(id)
        .setTag(metadata)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artworkUri?.toUri())
                .setAlbumTitle(album)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .build()
        ).build()
}
