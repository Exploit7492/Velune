/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.constants.LocalSortKey
import com.nikhil.yt.db.entities.LocalTrackEntity
import com.nikhil.yt.ui.screens.library.components.InlineFilterRow
import com.nikhil.yt.ui.screens.library.components.LocalEmptyState
import com.nikhil.yt.ui.screens.library.components.SearchBar
import com.nikhil.yt.ui.screens.library.components.SortPanel
import com.nikhil.yt.ui.screens.library.components.TrackRow
import com.nikhil.yt.ui.screens.library.components.formatDuration
import com.nikhil.yt.viewmodels.LocalLibraryViewModel

@Composable
fun FolderDetailScreen(
    viewModel: LocalLibraryViewModel,
    onBack: () -> Unit,
    onPlayTrack: (LocalTrackEntity, List<LocalTrackEntity>) -> Unit,
    currentTrackId: String? = null,
) {
    val folder by viewModel.selectedFolder.collectAsState()
    val tracks by viewModel.displayedTracks.collectAsState()
    val allTracks by viewModel.folderTracks.collectAsState()
    val sorts by viewModel.sorts.collectAsState()
    val searchActive by viewModel.searchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshingFolder by viewModel.isRefreshingFolder.collectAsState()

    var sortPanelVisible by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary

    if (folder == null) {
        onBack()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ─────────────────────────────────────────────────────
            // This screen fully replaces LibraryScreen's content while a folder is
            // selected (see LibraryScreen's FolderDetailPane), so it needs the same
            // top inset LibraryScreen's own header uses — otherwise this header sits
            // where the main app header/search bar overlay is, instead of below it.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = folder!!.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = "${allTracks.size} ${if (allTracks.size == 1) "track" else "tracks"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }

                // Search toggle
                //
                // A plain IconButton (Material3's own, not this app's custom
                // ResizableIconButton wrapper) always enforces a minimum
                // ~48dp touch/ripple target via minimumInteractiveComponentSize(),
                // regardless of the smaller .size(32.dp) requested here for the
                // visible box — that's Material3's accessibility floor, not a
                // bug in itself. The bug was in the SPACING: with only 4dp
                // between two 32dp visual boxes, their invisible ~48dp touch/
                // ripple regions overlap by roughly 12dp, so tapping near the
                // boundary (or even just the ripple animation on tap) visibly
                // bleeds from one button into the other — that's what read as
                // "the icons overlap." Fix is spacing, not shrinking the touch
                // target further (that would hurt accessibility, not help).
                IconButton(
                    onClick = { viewModel.toggleSearch() },
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (searchActive) primary.copy(alpha = 0.14f) else primary.copy(alpha = 0.06f)
                        )
                        .border(
                            0.5.dp,
                            if (searchActive) primary.copy(alpha = 0.5f) else primary.copy(alpha = 0.22f),
                            RoundedCornerShape(9.dp)
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = if (searchActive) primary else primary.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Sort toggle — same spacing reasoning as the Search toggle above.
                IconButton(
                    onClick = { sortPanelVisible = true },
                    modifier = Modifier
                        .padding(end = if (sorts.isNotEmpty()) 8.dp else 0.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (sorts.isNotEmpty()) primary.copy(alpha = 0.14f) else primary.copy(alpha = 0.06f)
                        )
                        .border(
                            0.5.dp,
                            if (sorts.isNotEmpty()) primary.copy(alpha = 0.5f) else primary.copy(alpha = 0.22f),
                            RoundedCornerShape(9.dp)
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Sort",
                        tint = if (sorts.isNotEmpty()) primary else primary.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Active sort count badge
                if (sorts.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = sorts.size.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }

            // ── Search Bar ───────────────────────────────────────────────────
            if (searchActive) {
                SearchBar(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                )
            }

            // ── Inline Sort Pills ──────────────────────────────────────────
            if (sorts.isNotEmpty()) {
                InlineFilterRow(
                    sorts = sorts,
                    onRemove = { viewModel.removeSort(it) },
                    onToggleDir = { viewModel.toggleSortDir(it) },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // ── Divider ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 4.dp, bottom = 2.dp)
                    .size(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            )

            // ── Track List ───────────────────────────────────────────────────
            PullToRefreshBox(
                isRefreshing = isRefreshingFolder,
                onRefresh = { viewModel.refreshSelectedFolder() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (tracks.isEmpty()) {
                    LocalEmptyState(
                        title = if (searchQuery.isNotBlank()) "No matches" else "No tracks",
                        subtitle = if (searchQuery.isNotBlank()) "Try a different search" else "Pull down to refresh",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(tracks, key = { it.id }) { track ->
                            TrackRow(
                                track = track,
                                isPlaying = currentTrackId == track.id,
                                onClick = {
                                    val playlist = buildPlaylist(tracks, track)
                                    onPlayTrack(track, playlist)
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── Sort Panel Overlay ─────────────────────────────────────────────
        SortPanel(
            visible = sortPanelVisible,
            sorts = sorts,
            onDismiss = { sortPanelVisible = false },
            onToggle = { key ->
                viewModel.toggleSort(key)
                sortPanelVisible = false
            },
        )
    }
}

/**
 * Build a playlist starting from the selected track,
 * followed by the remaining tracks in the current sort order.
 */
private fun buildPlaylist(
    allTracks: List<LocalTrackEntity>,
    selectedTrack: LocalTrackEntity,
): List<LocalTrackEntity> {
    val index = allTracks.indexOfFirst { it.id == selectedTrack.id }
    if (index == -1) return listOf(selectedTrack)
    val fromSelected = allTracks.subList(index, allTracks.size)
    val beforeSelected = allTracks.subList(0, index)
    return fromSelected + beforeSelected
}
