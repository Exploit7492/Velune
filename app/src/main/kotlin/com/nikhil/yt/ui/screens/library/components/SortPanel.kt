/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikhil.yt.constants.LocalSortKey
import com.nikhil.yt.constants.SortDir
import com.nikhil.yt.constants.SortEntry

@Composable
fun SortPanel(
    visible: Boolean,
    sorts: List<SortEntry>,
    onDismiss: () -> Unit,
    onToggle: (LocalSortKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val primary = MaterialTheme.colorScheme.primary
    var showLabels by remember { mutableStateOf(false) }
    val activeKeys = remember(sorts) { sorts.map { it.key }.toSet() }

    // Backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopEnd,
    ) {
        // Panel
        Column(
            modifier = modifier
                .padding(top = 80.dp, end = 10.dp)
                .width(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(0.5.dp, primary.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(14.dp),
        ) {
            // Header with eye button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            0.5.dp,
                            if (showLabels) primary.copy(alpha = 0.4f) else primary.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .background(if (showLabels) primary.copy(alpha = 0.12f) else primary.copy(alpha = 0.05f))
                        .clickable { showLabels = !showLabels },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (showLabels) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = "Toggle labels",
                        tint = if (showLabels) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Left accent line
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(2.dp)
                    .size(2.dp)
            )

            // Sort grid
            val keys = LocalSortKey.entries
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(keys, key = { it.name }) { key ->
                    val meta = SORT_META[key] ?: return@items
                    val isActive = activeKeys.contains(key)
                    val entry = sorts.find { it.key == key }
                    val priority = sorts.indexOfFirst { it.key == key } + 1

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onToggle(key) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Priority badge
                            if (isActive && priority > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = priority.toString(),
                                        color = androidx.compose.ui.graphics.Color.Black,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }

                            Icon(
                                imageVector = meta.icon,
                                contentDescription = meta.label,
                                tint = if (isActive) primary else primary.copy(alpha = 0.55f),
                                modifier = Modifier.size(if (showLabels) 16.dp else 20.dp),
                            )
                        }

                        if (showLabels) {
                            Text(
                                text = meta.label,
                                color = if (isActive) primary else primary.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }

                        // Direction arrow
                        if (isActive && entry != null) {
                            Icon(
                                imageVector = if (entry.dir == SortDir.ASC) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                                contentDescription = null,
                                tint = primary.copy(alpha = 0.75f),
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
