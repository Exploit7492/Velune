package com.nikhil.yt.ui.screens.spotifyimport

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.R
import com.nikhil.yt.spotify.SpotifyAuth
import com.nikhil.yt.spotifyimport.SpotifyImportState
import com.nikhil.yt.spotifyimport.SpotifyImportViewModel
import com.nikhil.yt.spotifyimport.SpotifyImportViewModelFactory
import com.nikhil.yt.ui.component.PlaylistGridItem
import com.nikhil.yt.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val viewModel: SpotifyImportViewModel = viewModel(
        factory = SpotifyImportViewModelFactory(context)
    )
    val state by viewModel.state.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    var showWebView by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf<com.nikhil.yt.spotify.models.SpotifyPlaylist?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
                )
            )

            when (state) {
                is SpotifyImportState.Idle -> {
                    if (!isLoggedIn) {
                        SpotifyLoginPrompt(
                            onLogin = { showWebView = true }
                        )
                    } else {
                        SpotifyLoggedInPrompt(
                            onLoadPlaylists = { viewModel.loadPlaylists() },
                            onLogout = { viewModel.logout() }
                        )
                    }
                }
                is SpotifyImportState.LoadingPlaylists -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is SpotifyImportState.PlaylistsLoaded -> {
                    val loaded = state as SpotifyImportState.PlaylistsLoaded
                    SpotifyPlaylistList(
                        playlists = loaded.playlists,
                        savedTracksCount = loaded.savedTracksCount,
                        onImportPlaylist = { showImportConfirm = it },
                        onImportSavedTracks = {
                            viewModel.importSavedTracks()
                        },
                        onRefresh = { viewModel.loadPlaylists() }
                    )
                }
                is SpotifyImportState.Importing -> {
                    val importing = state as SpotifyImportState.Importing
                    SpotifyImportProgress(
                        playlistName = importing.playlist?.name ?: "Liked Songs",
                        current = importing.current,
                        total = importing.total,
                        matched = importing.matched
                    )
                }
                is SpotifyImportState.ImportComplete -> {
                    val complete = state as SpotifyImportState.ImportComplete
                    SpotifyImportComplete(
                        playlistName = complete.playlist?.name ?: "Liked Songs",
                        matchedCount = complete.matchedCount,
                        totalCount = complete.totalCount,
                        onPlayAll = {
                            val videoIds = complete.results.mapNotNull { it.second?.id }
                            if (videoIds.isNotEmpty()) {
                                // Navigate to player with queue
                                navController.navigate("player")
                            }
                        },
                        onDismiss = { viewModel.reset() }
                    )
                }
                is SpotifyImportState.Error -> {
                    val error = state as SpotifyImportState.Error
                    SpotifyImportError(
                        message = error.message,
                        onRetry = {
                            viewModel.dismissError()
                            viewModel.loadPlaylists()
                        },
                        onDismiss = { viewModel.dismissError() }
                    )
                }
            }
        }

        // WebView overlay for login
        AnimatedVisibility(visible = showWebView) {
            SpotifyLoginWebView(
                onCookieExtracted = { cookie ->
                    viewModel.setSpDcCookie(cookie)
                    showWebView = false
                    viewModel.loadPlaylists()
                },
                onClose = { showWebView = false }
            )
        }

        // Import confirmation dialog
        showImportConfirm?.let { playlist ->
            AlertDialog(
                onDismissRequest = { showImportConfirm = null },
                title = { Text("Import Playlist") },
                text = { Text("Import \"${playlist.name}\" (${playlist.tracks?.total ?: 0} tracks) from Spotify?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showImportConfirm = null
                            viewModel.importPlaylist(playlist)
                        }
                    ) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportConfirm = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.spotify_import)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

@Composable
private fun SpotifyLoginPrompt(onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.music_note),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.spotify_import_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.spotify_import_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onLogin) {
            Text(stringResource(R.string.login_with_spotify))
        }
    }
}

@Composable
private fun SpotifyLoggedInPrompt(
    onLoadPlaylists: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.spotify_logged_in),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onLoadPlaylists) {
                Text(stringResource(R.string.load_playlists))
            }
            TextButton(onClick = onLogout) {
                Text(stringResource(R.string.logout))
            }
        }
    }
}

@Composable
private fun SpotifyPlaylistList(
    playlists: List<com.nikhil.yt.spotify.models.SpotifyPlaylist>,
    savedTracksCount: Int,
    onImportPlaylist: (com.nikhil.yt.spotify.models.SpotifyPlaylist) -> Unit,
    onImportSavedTracks: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.your_playlists),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.refresh))
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onImportSavedTracks)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.favorite),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.liked_songs),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$savedTracksCount tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        items(playlists) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onImportPlaylist(playlist) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val imageUrl = playlist.images.firstOrNull()?.url
                if (imageUrl != null) {
                    androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(imageUrl),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.library_music),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlist.tracks?.total ?: 0} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SpotifyImportProgress(
    playlistName: String,
    current: Int,
    total: Int,
    matched: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Importing \"$playlistName\"",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) current.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$current / $total tracks searched",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$matched matched on YouTube",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SpotifyImportComplete(
    playlistName: String,
    matchedCount: Int,
    totalCount: Int,
    onPlayAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.check_circle),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Import Complete",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "\"$playlistName\"",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$matchedCount of $totalCount tracks matched",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onPlayAll) {
            Text(stringResource(R.string.play_imported))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.done))
        }
    }
}

@Composable
private fun SpotifyImportError(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.error),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.import_failed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.try_again))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.done))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SpotifyLoginWebView(
    onCookieExtracted: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                WebView(it).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.removeAllCookies(null)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("https://open.spotify.com/")) {
                                extractSpDcCookie { cookie ->
                                    if (cookie.isNotBlank()) {
                                        onCookieExtracted(cookie)
                                    }
                                }
                                return true
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url?.startsWith("https://open.spotify.com/") == true) {
                                extractSpDcCookie { cookie ->
                                    if (cookie.isNotBlank()) {
                                        onCookieExtracted(cookie)
                                    }
                                }
                            }
                        }

                        private fun extractSpDcCookie(callback: (String) -> Unit) {
                            cookieManager.getCookie("https://open.spotify.com/")?.let { cookies ->
                                val spDc = cookies.split("; ")
                                    .find { it.startsWith("sp_dc=") }
                                    ?.removePrefix("sp_dc=")
                                if (!spDc.isNullOrBlank()) {
                                    callback(spDc)
                                }
                            }
                        }
                    }

                    loadUrl(SpotifyAuth.LOGIN_URL)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.close),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
