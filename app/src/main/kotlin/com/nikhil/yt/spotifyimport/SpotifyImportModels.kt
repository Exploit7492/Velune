package com.nikhil.yt.spotifyimport

import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.spotify.models.SpotifyPlaylist
import com.nikhil.yt.spotify.models.SpotifyTrack

sealed class SpotifyImportState {
    data object Idle : SpotifyImportState()
    data object LoadingPlaylists : SpotifyImportState()
    data class PlaylistsLoaded(
        val playlists: List<SpotifyPlaylist>,
        val savedTracksCount: Int,
    ) : SpotifyImportState()
    data class Importing(
        val playlist: SpotifyPlaylist?,
        val current: Int,
        val total: Int,
        val matched: Int,
    ) : SpotifyImportState()
    data class ImportComplete(
        val playlist: SpotifyPlaylist?,
        val results: List<Pair<SpotifyTrack?, SongItem?>>,
        val matchedCount: Int,
        val totalCount: Int,
    ) : SpotifyImportState()
    data class Error(val message: String) : SpotifyImportState()
}

data class ImportedPlaylist(
    val spotifyId: String,
    val name: String,
    val trackCount: Int,
    val matchedCount: Int,
    val youtubeVideoIds: List<String>,
    val importedAt: Long = System.currentTimeMillis(),
)
