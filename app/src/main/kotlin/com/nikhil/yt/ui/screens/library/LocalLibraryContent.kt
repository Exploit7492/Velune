/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nikhil.yt.db.entities.LocalFolderEntity
import com.nikhil.yt.ui.screens.library.components.FolderCard
import com.nikhil.yt.ui.screens.library.components.LocalEmptyState
import com.nikhil.yt.ui.screens.library.components.QuickPill
import com.nikhil.yt.ui.screens.library.components.SelectionBottomBar
import com.nikhil.yt.viewmodels.LocalLibraryViewModel

@Composable
fun LocalLibraryContent(
    navController: NavController,
    viewModel: LocalLibraryViewModel = hiltViewModel(),
    onShowFolderBrowser: () -> Unit,
) {
    val watchedFolders by viewModel.watchedFolders.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { viewModel.refreshFolders() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
            if (watchedFolders.isEmpty()) {
                item {
                    LocalEmptyState(
                        title = "No Local Music",
                        subtitle = "Add folders to see your local tracks here.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(watchedFolders, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        isSelected = selectedIds.contains(folder.id),
                        selectionMode = selectionMode,
                        onClick = {
                            if (selectionMode) {
                                viewModel.toggleSelect(folder.id)
                            } else {
                                viewModel.selectFolder(folder)
                            }
                        },
                        onLongPress = {
                            if (!selectionMode) {
                                viewModel.enterSelectionMode(folder.id)
                            }
                        },
                    )
                }
            }
        }

        // Selection bottom bar
        }

        SelectionBottomBar(
            selectedCount = selectedCount,
            onRemove = { viewModel.removeSelectedFolders() },
            onDelete = { /* Stage 4: implement device deletion */ },
            visible = selectionMode,
        )
    }
}

@Composable
fun BrowseLibraryContent(
    navController: NavController,
    onShowFolderBrowser: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
    ) {
        item {
            QuickPill(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = "Favourites",
                sub = "Songs you've liked",
                onClick = { /* navController.navigate("favorites") */ },
            )
        }
        item {
            QuickPill(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = "Downloads",
                sub = "Offline listening",
                onClick = { /* navController.navigate("downloads") */ },
            )
        }
        item {
            QuickPill(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = "Recently Played",
                sub = "Jump back in",
                onClick = { /* navController.navigate("recentlyPlayed") */ },
            )
        }
        item {
            QuickPill(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = "Most Played",
                sub = "Your top tracks",
                onClick = { /* navController.navigate("mostPlayed") */ },
            )
        }
        item {
            QuickPill(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = "Local Music",
                sub = "Files on this device",
                onClick = onShowFolderBrowser,
            )
        }
    }
}
