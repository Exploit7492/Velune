/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.nikhil.yt.innertube.models.*
import com.nikhil.yt.LocalDatabase
import com.nikhil.yt.LocalPlayerConnection
import com.nikhil.yt.R
import com.nikhil.yt.extensions.togglePlayPause
import com.nikhil.yt.models.toMediaMetadata
import com.nikhil.yt.playback.queues.YouTubeQueue
import com.nikhil.yt.ui.component.LocalMenuState
import com.nikhil.yt.ui.component.YouTubeListItem
import com.nikhil.yt.ui.component.RecognizeMusicFab
import com.nikhil.yt.ui.component.YouTubeGridItem
import com.nikhil.yt.ui.menu.*
import com.nikhil.yt.viewmodels.OnlineSearchSuggestionViewModel
import com.nikhil.yt.viewmodels.ExploreViewModel
import com.nikhil.yt.viewmodels.ChartsViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    chartsViewModel: ChartsViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()

    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val viewState by viewModel.viewState.collectAsState()

    val explorePage by exploreViewModel.explorePage.collectAsState()
    val chartsPage by chartsViewModel.chartsPage.collectAsState()

    val lazyListState = rememberLazyListState()

    // Load prepopulated content on arrival
    LaunchedEffect(Unit) {
        if (explorePage == null) {
            exploreViewModel.loadExplore()
        }
        if (chartsPage == null) {
            chartsViewModel.loadCharts()
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        viewModel.query.value = query
    }
    Box(modifier = Modifier.fillMaxSize()) {

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        ),
        modifier = Modifier
            .fillMaxSize()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        // ─── PREPOPULATED CONTENT (when no query typed yet) ─────────────────
        if (query.isBlank() && viewState.history.isEmpty() && viewState.suggestions.isEmpty()) {
            // Trending / Charts section
            chartsPage?.sections?.firstOrNull { it.chartType == com.nikhil.yt.innertube.pages.ChartsPage.ChartType.TRENDING }?.let { section ->
                item(key = "trending_header") {
                    SectionHeader(
                        title = "🔥 Trending",
                        pureBlack = pureBlack
                    )
                }
                item(key = "trending_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(section.items.take(10), key = { "trend_${it.id}" }) { item ->
                            when (item) {
                                is SongItem -> TrendingCard(
                                    item = item,
                                    isPlaying = isPlaying && mediaMetadata?.id == item.id,
                                    onClick = {
                                        if (item.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubeQueue.radio(item.toMediaMetadata())
                                            )
                                            onDismiss()
                                        }
                                    },
                                    pureBlack = pureBlack
                                )
                                else -> {}
                            }
                        }
                    }
                }
            }

            // New Releases section
            explorePage?.newReleaseAlbums?.takeIf { it.isNotEmpty() }?.let { albums ->
                item(key = "newreleases_header") {
                    SectionHeader(
                        title = stringResource(R.string.new_release_albums),
                        onClick = { /* navController.navigate("new_releases") REMOVED: route mismatch */ },
                        pureBlack = pureBlack
                    )
                }
                item(key = "newreleases_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(albums.distinctBy { it.id }, key = { "nr_${it.id}" }) { album ->
                            YouTubeGridItem(
                                item = album,
                                isActive = mediaMetadata?.album?.id == album.id,
                                isPlaying = isPlaying,
                                coroutineScope = coroutineScope,
                                modifier = Modifier
                                    .width(160.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        navController.navigate("album/${album.id}")
                                        onDismiss()
                                    }
                            )
                        }
                    }
                }
            }

            // Moods & Genres section
            explorePage?.moodAndGenres?.takeIf { it.isNotEmpty() }?.let { moods ->
                item(key = "moods_header") {
                    SectionHeader(
                        title = stringResource(R.string.mood_and_genres),
                        onClick = { navController.navigate("mood_and_genres") },
                        pureBlack = pureBlack
                    )
                }
                item(key = "moods_grid") {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        moods.take(8).forEach { mood ->
                            MoodGenreChip(
                                title = mood.title,
                                stripeColor = mood.stripeColor,
                                onClick = {
                                    navController.navigate("browse/${mood.endpoint.browseId}?params=${mood.endpoint.params ?: ""}")
                                    onDismiss()
                                },
                                pureBlack = pureBlack,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ─── SEARCH HISTORY ─────────────────────────────────────────────────
        items(viewState.history, key = { "history_${it.query}" }) { history ->
            SuggestionItem(
                query = history.query,
                online = false,
                onClick = {
                    onSearch(history.query)
                    onDismiss()
                },
                onDelete = {
                    database.query {
                        delete(history)
                    }
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(history.query, TextRange(history.query.length)))
                },
                modifier = Modifier.animateItem(),
                pureBlack = pureBlack
            )
        }

        // ─── SEARCH SUGGESTIONS ─────────────────────────────────────────────
        items(viewState.suggestions, key = { "suggestion_$it" }) { query ->
            SuggestionItem(
                query = query,
                online = true,
                onClick = {
                    onSearch(query)
                    onDismiss()
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(query, TextRange(query.length)))
                },
                modifier = Modifier.animateItem(),
                pureBlack = pureBlack
            )
        }

        // ─── TOP RESULTS FROM SUGGESTIONS ───────────────────────────────────
        if (viewState.items.isNotEmpty() && viewState.history.size + viewState.suggestions.size > 0) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.top_results),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pureBlack) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ─── SEARCH RESULT ITEMS ────────────────────────────────────────────
        items(viewState.items.distinctBy { it.id }, key = { "item_${it.id}" }) { item ->
            YouTubeListItem(
                item = item,
                isActive = when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                },
                isPlaying = isPlaying,
                trailingContent = {
                    IconButton(
                        onClick = {
                            menuState.show {
                                when (item) {
                                    is SongItem -> YouTubeSongMenu(
                                        song = item,
                                        navController = navController,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                    is AlbumItem -> YouTubeAlbumMenu(
                                        albumItem = item,
                                        navController = navController,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                    is ArtistItem -> YouTubeArtistMenu(
                                        artist = item,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                    is PlaylistItem -> YouTubePlaylistMenu(
                                        playlist = item,
                                        coroutineScope = scope,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue.radio(item.toMediaMetadata())
                                        )
                                        onDismiss()
                                    }
                                }
                                is AlbumItem -> {
                                    navController.navigate("album/${item.id}")
                                    onDismiss()
                                }
                                is ArtistItem -> {
                                    navController.navigate("artist/${item.id}")
                                    onDismiss()
                                }
                                is PlaylistItem -> {
                                    navController.navigate("online_playlist/${item.id}")
                                    onDismiss()
                                }
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                when (item) {
                                    is SongItem -> YouTubeSongMenu(
                                        song = item,
                                        navController = navController,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                    is AlbumItem -> YouTubeAlbumMenu(
                                        albumItem = item,
                                        navController = navController,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                    is ArtistItem -> YouTubeArtistMenu(
                                        artist = item,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                    is PlaylistItem -> YouTubePlaylistMenu(
                                        playlist = item,
                                        coroutineScope = coroutineScope,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    )
                    .animateItem()
            )
        }
    }

    RecognizeMusicFab(
        onClick = { navController.navigate("recognition") },
        modifier = Modifier.align(Alignment.BottomEnd)
    )
    }
}

// ─── SECTION HEADER ─────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(
    title: String,
    onClick: (() -> Unit)? = null,
    pureBlack: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (pureBlack) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = if (pureBlack) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── TRENDING CARD ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrendingCard(
    item: SongItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    pureBlack: Boolean
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (pureBlack) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_velune_concept),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.3f
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (pureBlack) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = item.artists.firstOrNull()?.name ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = if (pureBlack) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── MOOD & GENRE CHIP ──────────────────────────────────────────────────────
@Composable
private fun MoodGenreChip(
    title: String,
    stripeColor: Long,
    onClick: () -> Unit,
    pureBlack: Boolean,
    modifier: Modifier = Modifier
) {
    val color = androidx.compose.ui.graphics.Color(stripeColor.toInt() or 0xFF000000.toInt())
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (pureBlack) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (pureBlack) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit,
    pureBlack: Boolean
) {
    val iconContainerColor = if (pureBlack) {
        Color.White.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }

    val iconTint = if (pureBlack) {
        Color.White.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = iconContainerColor,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                painterResource(if (online) R.drawable.search else R.drawable.history),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = query,
            style = MaterialTheme.typography.bodyLarge,
            color = if (pureBlack) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (!online) {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    tint = if (pureBlack) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        IconButton(onClick = onFillTextField) {
            Icon(
                painter = painterResource(R.drawable.arrow_top_left),
                contentDescription = null,
                tint = if (pureBlack) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
