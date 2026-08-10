package com.nikhil.yt.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.localMusicDataStore: DataStore<Preferences> by preferencesDataStore(name = "local_music_store")

@Singleton
class LocalMusicStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.localMusicDataStore

    companion object {
        val DEFAULT_VIEW = stringPreferencesKey("default_view")
        val WATCHED_FOLDERS = stringPreferencesKey("watched_folders")
    }

    data class WatchedFolder(
        val id: String, val path: String, val name: String,
        val dateAdded: Long, val lastScan: Long, val trackCount: Int
    )

    val defaultView: Flow<String> = dataStore.data.map { it[DEFAULT_VIEW] ?: "browse" }
    val watchedFolders: Flow<List<WatchedFolder>> = dataStore.data.map { parseFolders(it[WATCHED_FOLDERS] ?: "[]") }

    suspend fun setDefaultView(view: String) { dataStore.edit { it[DEFAULT_VIEW] = view } }
    suspend fun addWatchedFolder(folder: WatchedFolder) {
        dataStore.edit { prefs ->
            val current = parseFolders(prefs[WATCHED_FOLDERS] ?: "[]")
            if (current.none { it.id == folder.id }) {
                prefs[WATCHED_FOLDERS] = serializeFolders(current + folder)
            }
        }
    }
    suspend fun removeWatchedFolder(folderId: String) {
        dataStore.edit { prefs ->
            prefs[WATCHED_FOLDERS] = serializeFolders(parseFolders(prefs[WATCHED_FOLDERS] ?: "[]").filter { it.id != folderId })
        }
    }
    suspend fun renameWatchedFolder(folderId: String, newName: String) {
        dataStore.edit { prefs ->
            prefs[WATCHED_FOLDERS] = serializeFolders(parseFolders(prefs[WATCHED_FOLDERS] ?: "[]").map {
                if (it.id == folderId) it.copy(name = newName) else it
            })
        }
    }

    private fun parseFolders(json: String): List<WatchedFolder> {
        return try {
            if (json == "[]" || json.isBlank()) return emptyList()
            val regex = """\{"id":"([^"]+)","path":"([^"]*)","name":"([^"]+)","dateAdded":(\d+),"lastScan":(\d+),"trackCount":(\d+)\}""".toRegex()
            regex.findAll(json).map { m ->
                WatchedFolder(m.groupValues[1], m.groupValues[2], m.groupValues[3],
                    m.groupValues[4].toLong(), m.groupValues[5].toLong(), m.groupValues[6].toInt())
            }.toList()
        } catch (e: Exception) { emptyList() }
    }

    private fun serializeFolders(folders: List<WatchedFolder>): String {
        if (folders.isEmpty()) return "[]"
        return "[" + folders.joinToString(",") {
            """{"id":"${it.id}","path":"${it.path}","name":"${it.name}","dateAdded":${it.dateAdded},"lastScan":${it.lastScan},"trackCount":${it.trackCount}}"""
        } + "]"
    }
}
