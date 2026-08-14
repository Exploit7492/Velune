package com.nikhil.yt.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.nikhil.yt.LocalPlayerConnection
import com.nikhil.yt.R

/**
 * Button that opens the [AudioDeviceBottomSheet] to manage cast / audio-output devices.
 *
 * @param asMenuItem when true, renders as a full-width row (icon + label) suitable for
 * embedding inside a [Material3MenuGroup] item; otherwise renders as a plain icon button.
 */
@Composable
fun CastButton(
    asMenuItem: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    val playerConnection = LocalPlayerConnection.current
    val service = playerConnection?.service
    val castHandler = remember(service) {
        try { service?.castConnectionHandler } catch (_: Exception) { null }
    }
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }

    if (asMenuItem) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable { showSheet = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.cast),
                contentDescription = null,
                tint = if (isCasting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.cast),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    } else {
        Icon(
            painter = painterResource(R.drawable.cast),
            contentDescription = stringResource(R.string.cast),
            tint = if (isCasting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = modifier.clickable { showSheet = true },
        )
    }

    if (showSheet) {
        AudioDeviceBottomSheet(onDismiss = { showSheet = false })
    }
}
