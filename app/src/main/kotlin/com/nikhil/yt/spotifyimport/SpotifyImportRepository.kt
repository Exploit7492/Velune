package com.nikhil.yt.spotifyimport

import com.nikhil.yt.spotify.models.*

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.spotify.Spotify
import com.nikhil.yt.spotify.SpotifyMapper
import com.nikhil.yt.spotify.models.SpotifyPlaylist
import com.nikhil.yt.spotify.models.SpotifyPlaylistTrack
import com.nikhil.yt.spotify.models.SpotifySavedTrack
import com.nikhil.yt.spotify.models.SpotifyTrack
import com.nikhil.yt.utils.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SpotifyImportRepository(private val context: Context) {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val importedPlaylistsKey = stringPreferencesKey("spotify_imported_playlists_v1")

    /**
     * Fetches the current user's playlists and liked songs count from Spotify.
     */
    suspend fun fetchPlaylists(): Result<Pair<List<SpotifyPlaylist>, Int>> = runCatching {
        val user = Spotify.getCurrentUser()
        val playlists = Spotify.getUserPlaylists(limit = 50)
        val savedTracks = Spotify.getSavedTracks(limit = 1)
        val savedCount = savedTracks.total
        playlists.items to savedCount
    }

    /**
     * Imports a specific Spotify playlist, searching each track on YouTube.
     */
    suspend fun importPlaylist(
        playlist: SpotifyPlaylist,
        onProgress: (current: Int, total: Int, matched: Int) -> Unit,
    ): Result<List<Pair<SpotifyTrack?, SongItem?>>> = runCatching {
        val tracks = Spotify.getAllPlaylistTracks(playlist.id)
        val results = SpotifyMapper.mapPlaylistTracks(tracks) { current, total ->
            val matchedSoFar = results.count { it.second != null }
            onProgress(current, total, matchedSoFar)
        }
        saveImportedPlaylist(playlist, results)
        results
    }

    /**
     * Imports the user's Liked Songs (saved tracks).
     */
    suspend fun importSavedTracks(
        onProgress: (current: Int, total: Int, matched: Int) -> Unit,
    ): Result<List<Pair<SpotifyTrack?, SongItem?>>> = runCatching {
        val tracks = Spotify.getAllSavedTracks()
        val playlistTracks = tracks.map { SpotifyPlaylistTrack(track = it.track) }
        val results = SpotifyMapper.mapPlaylistTracks(playlistTracks) { current, total ->
            val matchedSoFar = results.count { it.second != null }
            onProgress(current, total, matchedSoFar)
        }
        saveImportedPlaylist(
            SpotifyPlaylist(
                id = "saved_tracks",
                name = "Liked Songs",
                tracks = null,
            ),
            results
        )
        results
    }

    /**
     * Returns previously imported playlists from DataStore.
     */
    fun getImportedPlaylists(): Flow<List<ImportedPlaylist>> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[importedPlaylistsKey] ?: "[]"
            try {
                json.decodeFromString<List<ImportedPlaylist>>(raw)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Clears all imported playlist history.
     */
    suspend fun clearHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(importedPlaylistsKey)
        }
    }

    private suspend fun saveImportedPlaylist(
        playlist: SpotifyPlaylist,
        results: List<Pair<SpotifyTrack?, SongItem?>>,
    ) {
        val videoIds = results.mapNotNull { it.second?.id }
        val imported = ImportedPlaylist(
            spotifyId = playlist.id,
            name = SpotifyMapper.mapPlaylistName(playlist),
            trackCount = results.size,
            matchedCount = results.count { it.second != null },
            youtubeVideoIds = videoIds,
        )

        context.dataStore.edit { prefs ->
            val current = try {
                json.decodeFromString<List<ImportedPlaylist>>(prefs[importedPlaylistsKey] ?: "[]")
            } catch (e: Exception) {
                emptyList()
            }
            val updated = listOf(imported) + current.filter { it.spotifyId != playlist.id }.take(49)
            prefs[importedPlaylistsKey] = json.encodeToString(updated)
        }
    }
}
