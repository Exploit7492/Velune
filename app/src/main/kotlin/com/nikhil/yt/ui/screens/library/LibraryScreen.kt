/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nikhil.yt.LocalDatabase
import com.nikhil.yt.R
import com.nikhil.yt.constants.ChipSortTypeKey
import com.nikhil.yt.constants.DisableBlurKey
import com.nikhil.yt.constants.LibraryFilter
import com.nikhil.yt.constants.LibraryMode
import com.nikhil.yt.constants.PlaylistTagsFilterKey
import com.nikhil.yt.constants.ShowTagsInLibraryKey
import com.nikhil.yt.ui.component.ChipsRow
import com.nikhil.yt.ui.component.TagsFilterChips
import com.nikhil.yt.utils.rememberEnumPreference
import com.nikhil.yt.utils.rememberPreference
import com.nikhil.yt.viewmodels.LocalLibraryViewModel

@Composable
fun LibraryScreen(navController: NavController) {
    val viewModel: LocalLibraryViewModel = hiltViewModel()
    val libraryMode by viewModel.libraryMode.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()

    val database = LocalDatabase.current
    val (disableBlur) = rememberPreference(DisableBlurKey, true)

    // Mode toggle header
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with mode toggle
        if (!selectionMode) {
            LibraryModeHeader(
                mode = libraryMode,
                onModeChange = { viewModel.setLibraryMode(it) },
                onAddClick = { /* Stage 3: open folder browser */ },
                onMenuClick = { /* Stage 4: open sidebar */ },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            SelectionHeader(
                selectedCount = selectedCount,
                onClear = { viewModel.exitSelectionMode() },
                onSelectAll = { /* TODO */ },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Content area
        AnimatedContent(
            targetState = libraryMode,
            transitionSpec = {
                if (targetState == LibraryMode.LOCAL) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(
                        slideOutHorizontally { -it } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(
                        slideOutHorizontally { it } + fadeOut()
                    )
                }
            },
            label = "library_mode",
        ) { mode ->
            when (mode) {
                LibraryMode.BROWSE -> {
                    BrowseLibraryContent(
                        navController = navController,
                        onShowFolderBrowser = { viewModel.setLibraryMode(LibraryMode.LOCAL) },
                    )
                }
                LibraryMode.LOCAL -> {
                    LocalLibraryContent(
                        navController = navController,
                        onShowFolderBrowser = { /* Stage 3: open folder browser */ },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryModeHeader(
    mode: LibraryMode,
    onModeChange: (LibraryMode) -> Unit,
    onAddClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (mode == LibraryMode.LOCAL) "LOCAL" else "BROWSE",
                color = primary.copy(alpha = 0.65f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
            )
            Text(
                text = if (mode == LibraryMode.LOCAL) "Local Library" else "Browse",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            )
        }

        // Mode toggle pills
        Row(
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(0.5.dp, primary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModePill(
                label = "BROWSE",
                active = mode == LibraryMode.BROWSE,
                onClick = { onModeChange(LibraryMode.BROWSE) },
            )
            ModePill(
                label = "LOCAL",
                active = mode == LibraryMode.LOCAL,
                onClick = { onModeChange(LibraryMode.LOCAL) },
            )
        }

        if (mode == LibraryMode.LOCAL) {
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(primary.copy(alpha = 0.10f))
                    .border(0.5.dp, primary.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add folder",
                    tint = primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(primary.copy(alpha = 0.05f))
                .border(0.5.dp, primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ModePill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(if (active) primary.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = if (selectedCount > 0) "$selectedCount selected" else "Select folders",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSelectAll) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // TODO: all-selected checkmark
            }
        }
    }
}
