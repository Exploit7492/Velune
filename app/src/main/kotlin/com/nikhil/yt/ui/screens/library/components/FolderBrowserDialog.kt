/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.nikhil.yt.db.entities.LocalFolderEntity
import com.nikhil.yt.viewmodels.LocalLibraryViewModel

@Composable
fun FolderBrowserDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: LocalLibraryViewModel = hiltViewModel(),
) {
    val allFolders by viewModel.allFolders.collectAsState()
    val watchedIds by viewModel.watchedFolders.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val watchedSet = remember(watchedIds) { watchedIds.map { it.id }.toSet() }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Refresh available albums when dialog opens
    LaunchedEffect(visible) {
        if (visible) {
            viewModel.refreshFolders()
            selectedIds = emptySet()
        }
    }

    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = 24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "Add Music Folders",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (selectedIds.isEmpty()) "Add" else "Add ${selectedIds.size}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = selectedIds.isNotEmpty()) {
                            selectedIds.forEach { id ->
                                viewModel.addWatchedFolder(id)
                            }
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (isScanning) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            } else if (allFolders.isEmpty()) {
                LocalEmptyState(
                    title = "No folders found",
                    subtitle = "Pull down to refresh and scan for music folders",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(allFolders, key = { it.id }) { folder ->
                        val isWatched = watchedSet.contains(folder.id)
                        val isSelected = selectedIds.contains(folder.id)

                        FolderBrowserRow(
                            folder = folder,
                            isWatched = isWatched,
                            isSelected = isSelected,
                            onToggle = {
                                if (isWatched) return@FolderBrowserRow
                                selectedIds = if (isSelected) {
                                    selectedIds - folder.id
                                } else {
                                    selectedIds + folder.id
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderBrowserRow(
    folder: LocalFolderEntity,
    isWatched: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isWatched -> primary.copy(alpha = 0.05f)
                    isSelected -> primary.copy(alpha = 0.10f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .border(
                0.5.dp,
                when {
                    isSelected -> primary.copy(alpha = 0.50f)
                    else -> primary.copy(alpha = 0.12f)
                },
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = !isWatched, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isWatched) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Added",
                    tint = primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(
                        1.5.dp,
                        if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        CircleShape
                    )
                    .background(if (isSelected) primary else androidx.compose.ui.graphics.Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.Black,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                color = if (isWatched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = "Album folder",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }

        if (isWatched) {
            Text(
                text = "Added",
                color = primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
