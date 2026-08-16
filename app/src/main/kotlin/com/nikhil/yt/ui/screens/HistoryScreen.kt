/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerConnection
import com.nikhil.yt.R
import com.nikhil.yt.extensions.toMediaItem
import com.nikhil.yt.playback.queues.ListQueue
import com.nikhil.yt.ui.component.SongListItem
import com.nikhil.yt.viewmodels.HistoryGroup
import com.nikhil.yt.viewmodels.HistoryPeriod
import com.nikhil.yt.viewmodels.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController, viewModel: HistoryViewModel = hiltViewModel()) {
    val historyGroups by viewModel.historyGroups.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val currentMediaMetadata by (playerConnection?.mediaMetadata ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val isPlaying by (playerConnection?.isPlaying ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()

    var showClearConfirm by remember { mutableStateOf(false) }

    val allSongs = remember(historyGroups) { historyGroups.flatMap { it.events } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    if (allSongs.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_history))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (historyGroups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.history),
                        null,
                        Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.no_listen_history),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.no_listen_history_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                historyGroups.forEach { group ->
                    item(key = "header_${group.period}") {
                        Text(
                            text = periodLabel(group.period),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(group.events, key = { it.event.id }) { event ->
                        SongListItem(
                            song = event.song,
                            isActive = currentMediaMetadata?.id == event.song.id,
                            isPlaying = isPlaying && currentMediaMetadata?.id == event.song.id,
                            trailingContent = {
                                IconButton(onClick = { viewModel.removeEvent(event) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.remove),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                val index = allSongs.indexOfFirst { it.event.id == event.event.id }
                                val ordered = if (index >= 0) allSongs.drop(index) + allSongs.take(index) else allSongs
                                playerConnection?.playQueue(
                                    ListQueue(
                                        title = "History",
                                        items = ordered.map { it.song.toMediaItem() },
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_history_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearConfirm = false
                }) {
                    Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun periodLabel(period: HistoryPeriod): String = when (period) {
    HistoryPeriod.TODAY -> stringResource(R.string.today)
    HistoryPeriod.YESTERDAY -> stringResource(R.string.yesterday)
    HistoryPeriod.THIS_WEEK -> stringResource(R.string.this_week)
    HistoryPeriod.THIS_MONTH -> stringResource(R.string.this_month)
    HistoryPeriod.EARLIER -> stringResource(R.string.earlier)
}
