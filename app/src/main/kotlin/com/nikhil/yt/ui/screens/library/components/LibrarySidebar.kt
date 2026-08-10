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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
            action = { onNavigate("favorites"); onDismiss() },
        ),
        SidebarItem(
            icon = Icons.Outlined.CloudDownload,
            label = "Downloads",
            action = { onNavigate("downloads"); onDismiss() },
        ),
        SidebarItem(
            icon = Icons.Outlined.History,
            label = "Recently Played",
            action = { onNavigate("recentlyPlayed"); onDismiss() },
        ),
        SidebarItem(
            icon = Icons.Outlined.TrendingUp,
            label = "Most Played",
            action = { onNavigate("mostPlayed"); onDismiss() },
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

        // Panel
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(220.dp)
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .border(
                        0.5.dp,
                        primary.copy(alpha = 0.22f),
                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 20.dp, horizontal = 14.dp),
            ) {
                Text(
                    text = "Library",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp, start = 6.dp),
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    items(items) { item ->
                        val isActive = item.label == "Browse" && currentMode == LibraryMode.BROWSE

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable(onClick = item.action)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(primary.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (isActive) primary else primary.copy(alpha = 0.70f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = item.label,
                                color = if (isActive) primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
