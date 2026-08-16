/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.nikhil.yt.ui.component.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nikhil.yt.LocalPlayerAwareWindowInsets
import com.nikhil.yt.R
import com.nikhil.yt.constants.NotificationChannelsKey
import com.nikhil.yt.ui.component.PreferenceGroupTitle
import com.nikhil.yt.ui.component.TextFieldDialog
import com.nikhil.yt.ui.utils.backToMain
import com.nikhil.yt.utils.rememberPreference

private const val MAX_CHANNELS = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationChannelsSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (channelsRaw, onChannelsRawChange) = rememberPreference(
        key = NotificationChannelsKey,
        defaultValue = "",
    )
    val channels = remember(channelsRaw) {
        channelsRaw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun persist(updated: List<String>) {
        onChannelsRawChange(updated.joinToString("\n"))
    }

    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        PreferenceGroupTitle(title = stringResource(R.string.notification_channels))
        Text(
            text = stringResource(R.string.notification_channels_desc, MAX_CHANNELS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (channels.isEmpty()) {
            Text(
                text = stringResource(R.string.no_channels_added),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            channels.forEachIndexed { index, url ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    IconButton(
                        onClick = { persist(channels.toMutableList().apply { removeAt(index) }) },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.remove),
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = { showAddDialog = true },
            enabled = channels.size < MAX_CHANNELS,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                if (channels.size < MAX_CHANNELS) stringResource(R.string.add_channel)
                else stringResource(R.string.channel_limit_reached, MAX_CHANNELS)
            )
        }
    }

    if (showAddDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.add_channel)) },
            placeholder = { Text(stringResource(R.string.channel_url_placeholder)) },
            isInputValid = { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) },
            onDone = { url ->
                val trimmed = url.trim()
                if (trimmed.isNotBlank() && channels.size < MAX_CHANNELS && trimmed !in channels) {
                    persist(channels + trimmed)
                }
            },
            onDismiss = { showAddDialog = false },
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.notification_channels)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}
