/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nikhil.yt.db.entities.LocalFolderEntity
import com.nikhil.yt.db.entities.LocalTrackEntity

// Format duration from ms to mm:ss
fun formatDuration(ms: Long): String {
    val sec = (ms / 1000).toInt()
    val m = sec / 60
    val s = (sec % 60).toString().padStart(2, '0')
    return "$m:$s"
}

@Composable
fun FolderCard(
    folder: LocalFolderEntity,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = if (isSelected) primary.copy(alpha = 0.55f) else primary.copy(alpha = 0.12f)
    val bgColor = if (isSelected) primary.copy(alpha = 0.10f) else surface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .width(2.5.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(primary.copy(alpha = 0.55f))
        )

        if (selectionMode) {
            // Selection circle
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
                    .background(if (isSelected) primary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            // Folder icon
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${folder.trackCount} ${if (folder.trackCount == 1) "track" else "tracks"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
fun TrackRow(
    track: LocalTrackEntity,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val size = 46.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Artwork
        if (track.artworkUri != null) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.38f)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = track.title,
                color = if (isPlaying) primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(if (track.artist == "Unknown Artist") "Upcoming Artist" else track.artist)
                    if (track.duration > 0) append(" · ${formatDuration(track.duration)}")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }

        if (isPlaying) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .border(0.5.dp, primary.copy(alpha = 0.28f), CircleShape)
                    .background(primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = "Playing",
                    tint = primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun QuickPill(
    icon: @Composable () -> Unit,
    label: String,
    sub: String,
    onClick: () -> Unit,
    badge: Int? = null,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, primary.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .background(surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 14.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = sub,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }

        if (badge != null && badge > 0) {
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.5.dp, primary.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                    .background(primary.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badge > 999) "999+" else badge.toString(),
                    color = primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun LocalEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 18.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.08f))
                .border(1.dp, primary.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun SelectionBottomBar(
    selectedCount: Int,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    visible: Boolean,
) {
    val primary = MaterialTheme.colorScheme.primary

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = selectedCount > 0, onClick = onRemove)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (selectedCount > 0)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.04f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Remove",
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Remove",
                    color = if (selectedCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = selectedCount > 0, onClick = onDelete)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (selectedCount > 0)
                                Color(0xFFFF3B30).copy(alpha = 0.10f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.04f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Delete",
                        tint = if (selectedCount > 0) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Delete",
                    color = if (selectedCount > 0) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
