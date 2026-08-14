package com.nikhil.yt.spotifyimport

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.spotify.Spotify
import com.nikhil.yt.spotify.models.SpotifyPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpotifyImportViewModel(context: Context) : ViewModel() {

    private val repository = SpotifyImportRepository(context)

    private val _state = MutableStateFlow<SpotifyImportState>(SpotifyImportState.Idle)
    val state: StateFlow<SpotifyImportState> = _state.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        checkLoginStatus()
    }

    fun checkLoginStatus() {
        _isLoggedIn.value = false
    }

    fun setSpDcCookie(cookie: String) {
        viewModelScope.launch {
            Spotify.setSpDcCookie(cookie)
                .onSuccess {
                    _isLoggedIn.value = cookie.isNotBlank()
                }
                .onFailure { error ->
                    _isLoggedIn.value = false
                    _state.value = SpotifyImportState.Error(
                        error.message ?: "Failed to sign in to Spotify"
                    )
                }
        }
    }

    fun logout() {
        Spotify.clearAuth()
        _isLoggedIn.value = false
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            _state.value = SpotifyImportState.LoadingPlaylists
            repository.fetchPlaylists()
                .onSuccess { (playlists, savedCount) ->
                    _state.value = SpotifyImportState.PlaylistsLoaded(
                        playlists = playlists,
                        savedTracksCount = savedCount,
                    )
                }
                .onFailure { error ->
                    _state.value = SpotifyImportState.Error(
                        error.message ?: "Failed to load playlists"
                    )
                }
        }
    }

    fun importPlaylist(playlist: SpotifyPlaylist) {
        viewModelScope.launch {
            _state.value = SpotifyImportState.Importing(
                playlist = playlist,
                current = 0,
                total = playlist.tracks?.total ?: 0,
                matched = 0,
            )

            repository.importPlaylist(playlist) { current, total, matched ->
                _state.value = SpotifyImportState.Importing(
                    playlist = playlist,
                    current = current,
                    total = total,
                    matched = matched,
                )
            }.onSuccess { results ->
                _state.value = SpotifyImportState.ImportComplete(
                    playlist = playlist,
                    results = results,
                    matchedCount = results.count { it.second != null },
                    totalCount = results.size,
                )
            }.onFailure { error ->
                _state.value = SpotifyImportState.Error(
                    error.message ?: "Import failed"
                )
            }
        }
    }

    fun importSavedTracks() {
        viewModelScope.launch {
            _state.value = SpotifyImportState.Importing(
                playlist = null,
                current = 0,
                total = 0,
                matched = 0,
            )

            repository.importSavedTracks { current, total, matched ->
                _state.value = SpotifyImportState.Importing(
                    playlist = null,
                    current = current,
                    total = total,
                    matched = matched,
                )
            }.onSuccess { results ->
                _state.value = SpotifyImportState.ImportComplete(
                    playlist = null,
                    results = results,
                    matchedCount = results.count { it.second != null },
                    totalCount = results.size,
                )
            }.onFailure { error ->
                _state.value = SpotifyImportState.Error(
                    error.message ?: "Import failed"
                )
            }
        }
    }

    fun dismissError() {
        if (_state.value is SpotifyImportState.Error) {
            _state.value = SpotifyImportState.Idle
        }
    }

    fun reset() {
        _state.value = SpotifyImportState.Idle
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}

class SpotifyImportViewModelFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return SpotifyImportViewModel(context.applicationContext) as T
    }
}
