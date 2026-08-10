package com.nikhil.yt.viewmodels.local

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.data.local.LocalMusicStore
import com.nikhil.yt.db.DatabaseDao
import com.nikhil.yt.db.entities.local.LocalAlbumEntity
import com.nikhil.yt.utils.LocalMediaStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context

@HiltViewModel
class LocalLibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localMusicStore: LocalMusicStore,
    private val mediaStoreManager: LocalMediaStoreManager,
    private val databaseDao: DatabaseDao
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val albums: List<LocalAlbumEntity> = emptyList(),
        val availableAlbums: List<LocalMediaStoreManager.AlbumInfo> = emptyList(),
        val hasPermission: Boolean = false,
        val defaultView: String = "browse",
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            localMusicStore.defaultView.collect { _uiState.value = _uiState.value.copy(defaultView = it) }
        }
        viewModelScope.launch {
            databaseDao.localMusicDao.getAllAlbums().collect { _uiState.value = _uiState.value.copy(albums = it) }
        }
        checkPermission()
    }

    fun checkPermission() {
        _uiState.value = _uiState.value.copy(
            hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun setDefaultView(view: String) { viewModelScope.launch { localMusicStore.setDefaultView(view) } }

    fun loadAvailableAlbums() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                _uiState.value = _uiState.value.copy(availableAlbums = mediaStoreManager.getAvailableAlbums(), isLoading = false)
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(error = e.message, isLoading = false) }
        }
    }

    fun addWatchedFolder(albumInfo: LocalMediaStoreManager.AlbumInfo) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            localMusicStore.addWatchedFolder(LocalMusicStore.WatchedFolder(
                albumInfo.id, albumInfo.id, albumInfo.title, now, now, albumInfo.assetCount))
            databaseDao.localMusicDao.insertAlbum(LocalAlbumEntity(
                albumInfo.id, albumInfo.title, albumInfo.artworkUri, albumInfo.assetCount, now, now))
            scanTracks(albumInfo.id)
        }
    }

    fun removeWatchedFolder(albumId: String) {
        viewModelScope.launch {
            localMusicStore.removeWatchedFolder(albumId)
            databaseDao.localMusicDao.deleteAlbumWithTracks(albumId)
        }
    }

    fun renameWatchedFolder(albumId: String, newName: String) {
        viewModelScope.launch {
            localMusicStore.renameWatchedFolder(albumId, newName)
            databaseDao.localMusicDao.renameAlbum(albumId, newName)
        }
    }

    private fun scanTracks(albumId: String) {
        viewModelScope.launch {
            try {
                val tracks = mediaStoreManager.getTracksForAlbum(albumId)
                val now = System.currentTimeMillis()
                databaseDao.localMusicDao.insertTracks(tracks.map {
                    com.nikhil.yt.db.entities.local.LocalTrackEntity(
                        it.id, albumId, it.title, it.artist, it.album, it.duration,
                        it.artworkUri, it.fileUri, it.lastModified, now)
                })
                databaseDao.localMusicDao.updateAlbumTrackCount(albumId, tracks.size, now)
            } catch (_: Exception) {}
        }
    }
}
