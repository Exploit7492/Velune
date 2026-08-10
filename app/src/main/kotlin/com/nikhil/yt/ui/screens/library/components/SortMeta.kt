/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.library.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Title
import androidx.compose.ui.graphics.vector.ImageVector
import com.nikhil.yt.constants.LocalSortKey

// NOTE: These icons require androidx.compose.material:material-icons-extended.
// If unavailable, replace any missing icon with Icons.Outlined.Sort.

data class SortMeta(val label: String, val icon: ImageVector)

val SORT_META: Map<LocalSortKey, SortMeta> = mapOf(
    LocalSortKey.NAME to SortMeta("Name", Icons.Outlined.Title),
    LocalSortKey.ARTIST to SortMeta("Artist", Icons.Outlined.Person),
    LocalSortKey.ALBUM to SortMeta("Album", Icons.Outlined.AddCircle),      // fallback icon
    LocalSortKey.FILENAME to SortMeta("Filename", Icons.Outlined.Description),
    LocalSortKey.FOLDER to SortMeta("Folder", Icons.Outlined.Folder),
    LocalSortKey.YEAR to SortMeta("Year", Icons.Outlined.CalendarToday),
    LocalSortKey.DURATION to SortMeta("Duration", Icons.Outlined.Timer),
    LocalSortKey.TRACK_NUMBER to SortMeta("Track #", Icons.Outlined.FormatListNumbered),
    LocalSortKey.DATE_ADDED to SortMeta("Added", Icons.Outlined.AddCircle),
    LocalSortKey.DATE_MODIFIED to SortMeta("Modified", Icons.Outlined.Edit),
)
