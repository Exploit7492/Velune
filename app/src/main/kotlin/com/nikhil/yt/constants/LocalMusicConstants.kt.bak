/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

enum class LibraryMode {
    BROWSE,
    LOCAL
}

enum class LocalSortKey {
    NAME,
    ARTIST,
    ALBUM,
    FILENAME,
    FOLDER,
    YEAR,
    DURATION,
    TRACK_NUMBER,
    DATE_ADDED,
    DATE_MODIFIED,
}

enum class SortDir {
    ASC,
    DESC
}

data class SortEntry(
    val key: LocalSortKey,
    val dir: SortDir
)

val LocalLibraryModeKey = stringPreferencesKey("local_library_mode")
val LocalSortsKey = stringPreferencesKey("local_sorts_json")
val LocalSearchActiveKey = booleanPreferencesKey("local_search_active")
