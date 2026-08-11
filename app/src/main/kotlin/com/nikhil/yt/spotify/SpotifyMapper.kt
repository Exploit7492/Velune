package com.nikhil.yt.spotify

import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.spotify.models.SpotifyPlaylist
import com.nikhil.yt.spotify.models.SpotifyPlaylistTrack
import com.nikhil.yt.spotify.models.SpotifySavedTrack
import com.nikhil.yt.spotify.models.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

object SpotifyMapper {

    private const val SEARCH_DELAY_MS = 300L
    private const val MAX_CONCURRENT_SEARCHES = 3

    /**
     * Maps a list of Spotify playlist tracks to Velune SongItems by searching YouTube.
     * Returns a list of pairs: (original Spotify track, matched SongItem or null).
     */
    suspend fun mapPlaylistTracks(
        tracks: List<SpotifyPlaylistTrack>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Pair<SpotifyTrack?, SongItem?>> = withContext(Dispatchers.IO) {
        val validTracks = tracks.mapNotNull { it.track }
        val results = mutableListOf<Pair<SpotifyTrack?, SongItem?>>()

        validTracks.chunked(MAX_CONCURRENT_SEARCHES).forEachIndexed { chunkIndex, chunk ->
            val chunkResults = coroutineScope {
                chunk.map { track ->
                    async {
                        val songItem = searchYouTube(track)
                        track to songItem
                    }
                }.awaitAll()
            }
            results.addAll(chunkResults)
            onProgress(
                minOf((chunkIndex + 1) * MAX_CONCURRENT_SEARCHES, validTracks.size),
                validTracks.size
            )
            delay(SEARCH_DELAY_MS)
        }

        results
    }

    /**
     * Maps saved tracks (Liked Songs) to Velune SongItems.
     */
    suspend fun mapSavedTracks(
        tracks: List<SpotifySavedTrack>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Pair<SpotifyTrack?, SongItem?>> {
        return mapPlaylistTracks(
            tracks.map { SpotifyPlaylistTrack(track = it.track) },
            onProgress
        )
    }

    /**
     * Searches YouTube for the best match of a Spotify track.
     */
    private suspend fun searchYouTube(track: SpotifyTrack): SongItem? {
        val query = buildSearchQuery(track)
        return try {
            val searchResults = YouTube.search(query)
            searchResults.getOrNull()?.firstOrNull()
        } catch (e: Exception) {
            Timber.tag("SpotifyMapper").e(e, "Search failed for: $query")
            null
        }
    }

    /**
     * Builds a YouTube search query from a Spotify track.
     * Prefers: "artist - title" format with album context.
     */
    private fun buildSearchQuery(track: SpotifyTrack): String {
        val artistNames = track.artists.joinToString(" ") { it.name }
        val title = track.name
        val album = track.album?.name

        return buildString {
            append(artistNames)
            append(" - ")
            append(title)
            if (!album.isNullOrBlank() && album != title) {
                append(" ")
                append(album)
            }
        }
    }

    /**
     * Creates a Velune playlist name from a Spotify playlist.
     */
    fun mapPlaylistName(playlist: SpotifyPlaylist): String {
        return "Spotify: ${playlist.name}"
    }

    /**
     * Maps a Spotify playlist to a list of YouTube video IDs for direct queue creation.
     */
    suspend fun mapToVideoIds(
        tracks: List<SpotifyPlaylistTrack>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): List<String> {
        val mapped = mapPlaylistTracks(tracks, onProgress)
        return mapped.mapNotNull { (_, songItem) ->
            songItem?.id
        }
    }
}
