/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikhil.yt.constants.LibraryMode

data class SidebarItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val action: () -> Unit,
)

@Composable
fun LibrarySidebar(
    visible: Boolean,
    onDismiss: () -> Unit,
    currentMode: LibraryMode,
    onModeChange: (LibraryMode) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary

    val items = listOf(
        SidebarItem(
            icon = Icons.Outlined.LibraryMusic,
            label = "Browse",
            action = { onModeChange(LibraryMode.BROWSE); onDismiss() },
        ),
        SidebarItem(
            icon = Icons.Outlined.FavoriteBorder,
            label = "Favourites",
            action = { onNavigate("auto_playlist/liked"); onDismiss() },
        ),
        SidebarItem(
            icon = Icons.Outlined.CloudDownload,
            label = "Downloads",
            action = { onNavigate("cache_playlist/downloaded"); onDismiss() },
        ),
        SidebarItem(
            icon = Icons.Outlined.History,
            label = "Recently Played",
            action = { onNavigate("history"); onDismiss() },
        ),
        SidebarItem(
            icon = Icons.Outlined.TrendingUp,
            label = "Most Played",
            action = { onNavigate("top_playlist/Top"); onDismiss() },
        ),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onDismiss),
            )
        }

        // Panel — icon-only, content-width, no title
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(64.dp)
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .border(
                        0.5.dp,
                        primary.copy(alpha = 0.22f),
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    val isActive = item.label == "Browse" && currentMode == LibraryMode.BROWSE
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) primary.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(onClick = item.action),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isActive) primary else primary.copy(alpha = 0.60f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
