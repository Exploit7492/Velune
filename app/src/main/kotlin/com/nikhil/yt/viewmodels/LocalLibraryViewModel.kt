/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.constants.LibraryMode
import com.nikhil.yt.constants.LocalLibraryModeKey
import com.nikhil.yt.constants.LocalSortKey
import com.nikhil.yt.constants.LocalSortsKey
import com.nikhil.yt.constants.SortDir
import com.nikhil.yt.constants.SortEntry
import com.nikhil.yt.db.entities.LocalFolderEntity
import com.nikhil.yt.db.entities.LocalTrackEntity
import com.nikhil.yt.repository.LocalMusicRepository
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.get
import com.nikhil.yt.utils.set
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LocalLibraryViewModel @Inject constructor(
    application: Application,
    private val repository: LocalMusicRepository,
) : AndroidViewModel(application) {

    private val dataStore = application.dataStore

    // Permission
    private val _permissionGranted = MutableStateFlow(repository.hasAudioPermission())
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    fun checkPermission() {
        _permissionGranted.value = repository.hasAudioPermission()
    }

    // Library Mode (BROWSE / LOCAL)
    val libraryMode: StateFlow<LibraryMode> = dataStore.data
        .map { prefs ->
            when (prefs[LocalLibraryModeKey]) {
                "LOCAL" -> LibraryMode.LOCAL
                else -> LibraryMode.BROWSE
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, LibraryMode.BROWSE)

    fun setLibraryMode(mode: LibraryMode) {
        viewModelScope.launch {
            dataStore.set(LocalLibraryModeKey, mode.name)
        }
    }

    // Folders
    val watchedFolders: StateFlow<List<LocalFolderEntity>> =
        repository.watchedFoldersFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allFolders: StateFlow<List<LocalFolderEntity>> =
        repository.allFoldersFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isRefreshingFolder = MutableStateFlow(false)
    val isRefreshingFolder: StateFlow<Boolean> = _isRefreshingFolder.asStateFlow()

    // Selected Folder Detail
    private val _selectedFolder = MutableStateFlow<LocalFolderEntity?>(null)
    val selectedFolder: StateFlow<LocalFolderEntity?> = _selectedFolder.asStateFlow()

    private val _folderTracks = MutableStateFlow<List<LocalTrackEntity>>(emptyList())
    val folderTracks: StateFlow<List<LocalTrackEntity>> = _folderTracks.asStateFlow()

    private var folderTracksJob: kotlinx.coroutines.Job? = null

    // Sorting
    val sorts: StateFlow<List<SortEntry>> = dataStore.data
        .map { prefs ->
            parseSortsJson(prefs[LocalSortsKey] ?: "[]")
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Search
    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selection Mode
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val selectedCount: StateFlow<Int> = _selectedIds.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // Displayed Tracks (sorted + filtered)
    val displayedTracks: StateFlow<List<LocalTrackEntity>> = combine(
        _folderTracks,
        sorts,
        _searchQuery
    ) { tracks, activeSorts, query ->
        var result = tracks.toList()

        if (activeSorts.isNotEmpty()) {
            result = applySorts(result, activeSorts)
        }

        if (query.isNotBlank()) {
            val q = query.lowercase()
            result = result.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q)
            }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Actions
    fun refreshFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                repository.refreshAvailableAlbums()
                repository.scanAllWatchedFolders()
            } catch (e: Exception) {
                Timber.e(e, "refreshFolders failed")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun selectFolder(folder: LocalFolderEntity) {
        _selectedFolder.value = folder
        folderTracksJob?.cancel()
        folderTracksJob = viewModelScope.launch(Dispatchers.IO) {
            repository.tracksByFolderFlow(folder.id).collect { tracks ->
                _folderTracks.value = tracks
            }
        }
    }

    /** Re-scans just the currently open folder (e.g. from a pull-to-refresh gesture). */
    fun refreshSelectedFolder() {
        val folder = _selectedFolder.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingFolder.value = true
            try {
                repository.scanFolder(folder.id)
            } catch (e: Exception) {
                Timber.e(e, "refreshSelectedFolder failed")
            } finally {
                _isRefreshingFolder.value = false
            }
        }
    }

    fun clearSelectedFolder() {
        folderTracksJob?.cancel()
        folderTracksJob = null
        _selectedFolder.value = null
        _folderTracks.value = emptyList()
        _searchQuery.value = ""
        _searchActive.value = false
    }

    fun toggleSearch() {
        _searchActive.value = !_searchActive.value
        if (!_searchActive.value) {
            _searchQuery.value = ""
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addSort(key: LocalSortKey) {
        viewModelScope.launch {
            val current = sorts.first().toMutableList()
            if (current.none { it.key == key }) {
                current.add(SortEntry(key, SortDir.ASC))
                saveSorts(current)
            }
        }
    }

    fun removeSort(key: LocalSortKey) {
        viewModelScope.launch {
            val current = sorts.first().filter { it.key != key }
            saveSorts(current)
        }
    }

    fun toggleSortDir(key: LocalSortKey) {
        viewModelScope.launch {
            val current = sorts.first().map {
                if (it.key == key) it.copy(dir = if (it.dir == SortDir.ASC) SortDir.DESC else SortDir.ASC)
                else it
            }
            saveSorts(current)
        }
    }

    fun clearSorts() {
        viewModelScope.launch {
            saveSorts(emptyList())
        }
    }

    fun toggleSort(key: LocalSortKey) {
        viewModelScope.launch {
            val current = sorts.first()
            if (current.any { it.key == key }) {
                removeSort(key)
            } else {
                addSort(key)
                // Default date sorts to DESC (newest first)
                if (key == LocalSortKey.DATE_ADDED || key == LocalSortKey.DATE_MODIFIED) {
                    toggleSortDir(key)
                }
            }
        }
    }

    fun selectAllWatched(folderIds: List<String>) {
        _selectedIds.value = folderIds.toSet()
    }

    private suspend fun saveSorts(sorts: List<SortEntry>) {
        val json = JSONArray().apply {
            sorts.forEach { s ->
                put(JSONObject().apply {
                    put("key", s.key.name)
                    put("dir", s.dir.name)
                })
            }
        }
        dataStore.edit { it[LocalSortsKey] = json.toString( })
    }

    private fun parseSortsJson(json: String): List<SortEntry> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                SortEntry(
                    key = LocalSortKey.valueOf(obj.getString("key")),
                    dir = SortDir.valueOf(obj.getString("dir"))
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun enterSelectionMode(folderId: String) {
        _selectionMode.value = true
        _selectedIds.value = setOf(folderId)
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelect(folderId: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (contains(folderId)) remove(folderId) else add(folderId)
        }.also {
            if (it.isEmpty()) _selectionMode.value = false
        }
    }

    fun selectAll(folderIds: List<String>) {
        _selectedIds.value = folderIds.toSet()
    }

    fun removeSelectedFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedIds.value.forEach { id ->
                repository.removeWatchedFolder(id)
            }
            withContext(Dispatchers.Main) {
                exitSelectionMode()
            }
        }
    }

    fun deleteSelectedFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedIds.value.forEach { id ->
                repository.deleteFolderFromDevice(id)
            }
            withContext(Dispatchers.Main) {
                exitSelectionMode()
            }
        }
    }

    fun addWatchedFolder(folderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val wasEmpty = watchedFolders.value.isEmpty()
            repository.addWatchedFolder(folderId)
            if (wasEmpty) {
                setLibraryMode(LibraryMode.LOCAL)
            }
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameWatchedFolder(folderId, newName)
        }
    }

    // Sort Engine
    private fun applySorts(
        tracks: List<LocalTrackEntity>,
        sorts: List<SortEntry>
    ): List<LocalTrackEntity> {
        if (sorts.isEmpty()) return tracks

        val UNKNOWN = "\uFFFF"

        return tracks.sortedWith { a, b ->
            for ((key, dir) in sorts) {
                val cmp = when (key) {
                    LocalSortKey.NAME -> compareValues(
                        a.title.ifBlank { UNKNOWN },
                        b.title.ifBlank { UNKNOWN }
                    )
                    LocalSortKey.ARTIST -> compareValues(
                        a.artist.takeIf { it != "Unknown Artist" } ?: UNKNOWN,
                        b.artist.takeIf { it != "Unknown Artist" } ?: UNKNOWN
                    )
                    LocalSortKey.ALBUM -> compareValues(a.album, b.album)
                    LocalSortKey.FILENAME -> compareValues(
                        a.fileUri.substringAfterLast("/"),
                        b.fileUri.substringAfterLast("/")
                    )
                    LocalSortKey.FOLDER -> compareValues(a.folderId, b.folderId)
                    LocalSortKey.DURATION -> compareValues(a.duration, b.duration)
                    LocalSortKey.TRACK_NUMBER -> compareValues(a.trackNumber, b.trackNumber)
                    LocalSortKey.DATE_ADDED -> compareValues(a.dateAdded, b.dateAdded)
                    LocalSortKey.DATE_MODIFIED -> compareValues(a.dateModified, b.dateModified)
                    LocalSortKey.YEAR -> compareValues(
                        extractYear(a.dateModified), extractYear(b.dateModified)
                    )
                    else -> 0
                }
                val finalCmp = if (dir == SortDir.DESC) -cmp else cmp
                if (finalCmp != 0) return@sortedWith finalCmp
            }
            0
        }
    }

    private fun extractYear(timestamp: Long): Int {
        return java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            .get(java.util.Calendar.YEAR)
    }
}
