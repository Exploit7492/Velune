package com.nikhil.yt.viewmodels.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikhil.yt.db.DatabaseDao
import com.nikhil.yt.db.entities.local.LocalTrackEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderDetailViewModel @Inject constructor(private val databaseDao: DatabaseDao) : ViewModel() {

    data class SortEntry(val key: SortKey, val dir: SortDir)
    enum class SortKey { NAME, ARTIST, ALBUM, DURATION, DATE_ADDED, DATE_MODIFIED }
    enum class SortDir { ASC, DESC }

    private val _tracks = MutableStateFlow<List<LocalTrackEntity>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _sorts = MutableStateFlow<List<SortEntry>>(emptyList())

    val tracks: StateFlow<List<LocalTrackEntity>> = combine(_tracks, _searchQuery, _sorts) { list, query, sorts ->
        var result = list
        val q = query.trim().lowercase()
        if (q.isNotBlank()) result = result.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        sorts.forEach { sort ->
            result = when (sort.key) {
                SortKey.NAME -> if (sort.dir == SortDir.ASC) result.sortedBy { it.title } else result.sortedByDescending { it.title }
                SortKey.ARTIST -> if (sort.dir == SortDir.ASC) result.sortedBy { it.artist } else result.sortedByDescending { it.artist }
                SortKey.ALBUM -> if (sort.dir == SortDir.ASC) result.sortedBy { it.album } else result.sortedByDescending { it.album }
                SortKey.DURATION -> if (sort.dir == SortDir.ASC) result.sortedBy { it.duration } else result.sortedByDescending { it.duration }
                SortKey.DATE_ADDED -> if (sort.dir == SortDir.ASC) result.sortedBy { it.addedToLibrary } else result.sortedByDescending { it.addedToLibrary }
                SortKey.DATE_MODIFIED -> if (sort.dir == SortDir.ASC) result.sortedBy { it.lastModified } else result.sortedByDescending { it.lastModified }
            }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun loadTracks(albumId: String) {
        viewModelScope.launch {
            databaseDao.localMusicDao.getTracksByAlbum(albumId).collect { _tracks.value = it }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun addSort(key: SortKey) { if (_sorts.value.none { it.key == key }) _sorts.value += SortEntry(key, SortDir.ASC) }
    fun removeSort(key: SortKey) { _sorts.value = _sorts.value.filter { it.key != key } }
    fun toggleSortDir(key: SortKey) { _sorts.value = _sorts.value.map { if (it.key == key) it.copy(dir = if (it.dir == SortDir.ASC) SortDir.DESC else SortDir.ASC) else it } }
    fun clearSorts() { _sorts.value = emptyList() }
    val sorts: StateFlow<List<SortEntry>> = _sorts.asStateFlow()
}
