package com.nikhil.yt.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Note: SpotifyImage lives in SpotifyUser.kt, SpotifyTrack/SpotifySimpleArtist/SpotifySimpleAlbum
// in SpotifyTrack.kt, and SpotifyPlaylist/SpotifyPlaylistTrack/SpotifyPlaylistTracksRef in
// SpotifyPlaylist.kt — those are the single canonical definitions, merged from what used to be
// two competing copies. This file holds the remaining, non-duplicated Spotify GraphQL models.

@Serializable
data class SpotifyFollowers(
    @SerialName("total") val total: Int = 0,
)

@Serializable
data class SpotifyArtist(
    val id: String = "",
    val name: String = "",
    val images: List<SpotifyImage> = emptyList(),
    val followers: SpotifyFollowers? = null,
    val uri: String = "",
    val popularity: Int? = null,
    val type: String = "artist",
)

@Serializable
data class SpotifyAlbum(
    val id: String = "",
    val name: String = "",
    @SerialName("album_type") val albumType: String? = null,
    val artists: List<SpotifySimpleArtist> = emptyList(),
    val images: List<SpotifyImage> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("total_tracks") val totalTracks: Int = 0,
    val tracks: SpotifyPaging<SpotifyTrack>? = null,
    val uri: String = "",
)

@Serializable
data class SpotifySearchResult(
    val albums: SpotifyPaging<SpotifyAlbum>? = null,
    val artists: SpotifyPaging<SpotifyArtist>? = null,
    val tracks: SpotifyPaging<SpotifyTrack>? = null,
    val playlists: SpotifyPaging<SpotifyPlaylist>? = null,
)

@Serializable
data class SpotifyRecommendations(
    val tracks: List<SpotifyTrack> = emptyList(),
)

@Serializable
data class SpotifyRecommendationSeed(
    @SerialName("initialPoolSize") val initialPoolSize: Int = 0,
    val id: String = "",
    val type: String = "",
)

/**
 * A single entry in the user's Spotify "Your Library" listing, which mixes
 * playlists and folders in one feed. Sealed so callers can exhaustively
 * branch on what came back from the GQL `libraryV3` query.
 */
sealed class SpotifyLibraryItem {
    data class Playlist(val playlist: SpotifyPlaylist) : SpotifyLibraryItem()
    data class Folder(val folder: SpotifyLibraryFolder) : SpotifyLibraryItem()
}

@Serializable
data class SpotifyLibraryFolder(
    val id: String = "",
    val name: String = "",
    val uri: String = "",
    val totalChildren: Int = 0,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class SpotifyHomeFeed(
    val greeting: String? = null,
    val sections: List<SpotifyHomeFeedSection> = emptyList(),
)

@Serializable
data class SpotifyHomeFeedSection(
    val sectionUri: String = "",
    val title: String? = null,
    val typename: String = "",
    val totalCount: Int = 0,
    val items: List<SpotifyHomeFeedItem> = emptyList(),
)

/**
 * A single card in the Spotify home feed. Sealed over the three renderer
 * shapes Spotify's `home` GQL query actually returns (playlists, albums,
 * artists) rather than one flattened, mostly-null data class.
 */
@Serializable
sealed class SpotifyHomeFeedItem {
    abstract val uri: String
    abstract val id: String
    abstract val name: String
    abstract val imageUrl: String?

    @Serializable
    data class Playlist(
        override val uri: String,
        override val id: String,
        override val name: String,
        val description: String? = null,
        val format: String? = null,
        val totalCount: Int = 0,
        override val imageUrl: String? = null,
        val extractedColorHex: String? = null,
        val ownerName: String? = null,
        val madeForUsername: String? = null,
    ) : SpotifyHomeFeedItem()

    @Serializable
    data class Album(
        override val uri: String,
        override val id: String,
        override val name: String,
        val albumType: String? = null,
        val artists: List<SpotifySimpleArtist> = emptyList(),
        override val imageUrl: String? = null,
    ) : SpotifyHomeFeedItem()

    @Serializable
    data class Artist(
        override val uri: String,
        override val id: String,
        override val name: String,
        override val imageUrl: String? = null,
    ) : SpotifyHomeFeedItem()
}
