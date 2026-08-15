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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.LocalPlayerConnection
import com.nikhil.yt.constants.LibraryMode
import com.nikhil.yt.playback.queues.ListQueue
import com.nikhil.yt.ui.screens.library.components.FolderBrowserDialog
import com.nikhil.yt.ui.screens.library.components.LibrarySidebar
import com.nikhil.yt.ui.screens.library.components.LocalMusicPermissionGate
import com.nikhil.yt.ui.screens.library.components.PermissionDeniedScreen
import com.nikhil.yt.viewmodels.LocalLibraryViewModel
import com.nikhil.yt.extensions.toMediaItem
import com.nikhil.yt.ui.component.RecognizeMusicFab

@Composable
fun LibraryScreen(navController: NavController) {
    val viewModel: LocalLibraryViewModel = hiltViewModel()
    val libraryMode by viewModel.libraryMode.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()
    val permissionGranted by viewModel.permissionGranted.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    var showSidebar by remember { mutableStateOf(false) }
    var showFolderBrowser by remember { mutableStateOf(false) }
    var showPermissionDenied by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = selectionMode || selectedFolder != null || showSidebar) {
        when {
            selectionMode -> viewModel.exitSelectionMode()
            selectedFolder != null -> viewModel.clearSelectedFolder()
            showSidebar -> showSidebar = false
        }
    }

    if (!permissionGranted) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        // Re-check permission whenever the user comes back to this screen
        // (e.g. after granting it from the system Settings app).
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    viewModel.checkPermission()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        if (showPermissionDenied) {
            PermissionDeniedScreen(
                onOpenSettings = {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                },
                onCancel = { showPermissionDenied = false },
            )
        } else {
            LocalMusicPermissionGate(
                onGranted = { viewModel.checkPermission() },
                onRequest = { showPermissionDenied = true },
            )
        }
        return
    }

    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isExpanded = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    @Composable
    fun FolderDetailPane() {
        FolderDetailScreen(
            viewModel = viewModel,
            onBack = { viewModel.clearSelectedFolder() },
            onPlayTrack = { track, allTracks ->
                playerConnection?.let { connection ->
                    val index = allTracks.indexOfFirst { it.id == track.id }
                    val playlist = if (index >= 0) {
                        allTracks.drop(index) + allTracks.take(index)
                    } else {
                        allTracks
                    }
                    connection.playQueue(
                        ListQueue(
                            title = selectedFolder!!.name,
                            items = playlist.map { it.toMediaItem() }
                        )
                    )
                }
            },
            currentTrackId = playerConnection?.player?.currentMediaItem?.mediaId,
        )
    }

    if (selectedFolder != null && !isExpanded) {
        FolderDetailPane()
        return
    }

    @Composable
    fun LibraryListPane(modifier: Modifier = Modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier.then(
                Modifier
                    .fillMaxSize()
                    // Was a hardcoded `AppBarHeight + 8.dp`, which is missing the status bar
                    // component. The real floating global header (logo + search/settings)
                    // that overlays this screen is `statusBarHeight + AppBarHeight` tall
                    // (see the blur backdrop height in MainActivity), and that's exactly
                    // what LocalPlayerAwareWindowInsets already bakes in for every other
                    // top-level screen (Home, Stats). Library was the odd one out, so its
                    // header row rendered too high and sat partly under the global header —
                    // which also meant the global header's higher z-order buttons could
                    // intercept touches meant for this screen in that overlapping band.
                    .padding(top = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding() + 8.dp)
                    .pointerInput(libraryMode) {
                        // Reserve a strip along the left edge for the OS back gesture. Without
                        // this, any rightward swipe anywhere on the screen — including one
                        // starting at the very edge, which is exactly the system "swipe back"
                        // gesture — got consumed here to toggle Browse/Local instead of ever
                        // reaching the back dispatcher, so Library had no way to navigate back.
                        val edgeZonePx = 32.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (down.position.x < edgeZonePx) return@awaitEachGesture

                            var totalDrag = 0f
                            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                                totalDrag += over
                                change.consume()
                            }
                            if (drag != null) {
                                horizontalDrag(drag.id) { change ->
                                    totalDrag += change.positionChange().x
                                    change.consume()
                                }
                                val threshold = 120f
                                when {
                                    totalDrag < -threshold -> showSidebar = true
                                    totalDrag > threshold && !showSidebar ->
                                        viewModel.setLibraryMode(
                                            if (libraryMode == LibraryMode.BROWSE) LibraryMode.LOCAL else LibraryMode.BROWSE
                                        )
                                }
                            }
                        }
                    },
            )
        ) {
            if (!selectionMode) {
                LibraryModeHeader(
                    mode = libraryMode,
                    onModeChange = { viewModel.setLibraryMode(it) },
                    onAddClick = { showFolderBrowser = true },
                    onMenuClick = { showSidebar = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SelectionHeader(
                    selectedCount = selectedCount,
                    onClear = { viewModel.exitSelectionMode() },
                    onSelectAll = { viewModel.selectAll(viewModel.watchedFolders.value.map { it.id }) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

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
                            onShowFolderBrowser = { showFolderBrowser = true },
                        )
                    }
                    LibraryMode.LOCAL -> {
                        LocalLibraryContent(
                            navController = navController,
                            viewModel = viewModel,
                            onShowFolderBrowser = { showFolderBrowser = true },
                        )
                    }
                }
            }
        }
        RecognizeMusicFab(
            onClick = { navController.navigate("recognition") },
            modifier = Modifier.align(Alignment.BottomEnd)
        )
        }

    }

    if (isExpanded && selectedFolder != null) {
        Row(modifier = Modifier.fillMaxSize()) {
            LibraryListPane(modifier = Modifier.weight(0.4f))
            Box(modifier = Modifier.weight(0.6f)) {
                FolderDetailPane()
            }
        }
    } else {
        LibraryListPane()
    }

        FolderBrowserDialog(
        visible = showFolderBrowser,
        onDismiss = { showFolderBrowser = false },
        viewModel = viewModel,
    )

    LibrarySidebar(
        visible = showSidebar,
        onDismiss = { showSidebar = false },
        currentMode = libraryMode,
        onModeChange = { viewModel.setLibraryMode(it) },
        onNavigate = { route ->
            showSidebar = false
            navController.navigate(route)
        },
    )
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
        modifier = modifier.padding(horizontal = 18.dp, vertical = 10.dp),
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
            .background(if (active) primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
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
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }

        Text(
            text = if (selectedCount > 0) "$selectedCount selected" else "Select folders",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                .clickable(onClick = onSelectAll),
            contentAlignment = Alignment.Center,
        ) { }
    }
}
