/*
 * Velune - by Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.ui.screens.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
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
import com.nikhil.yt.constants.LocalSortKey
import com.nikhil.yt.constants.SortDir
import com.nikhil.yt.constants.SortEntry

@Composable
fun InlineFilterRow(
    sorts: List<SortEntry>,
    onRemove: (LocalSortKey) -> Unit,
    onToggleDir: (LocalSortKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            Box(
                modifier = Modifier
                    .padding(start = 14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        0.5.dp,
                        if (sorts.isEmpty()) primary.copy(alpha = 0.45f) else primary.copy(alpha = 0.18f),
                        RoundedCornerShape(20.dp)
                    )
                    .background(if (sorts.isEmpty()) primary.copy(alpha = 0.14f) else primary.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "All",
                    color = if (sorts.isEmpty()) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        items(sorts, key = { it.key.name }) { entry ->
            val meta = SORT_META[entry.key] ?: return@items
            val priority = sorts.indexOf(entry) + 1

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(0.5.dp, primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .background(primary.copy(alpha = 0.09f))
                    .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Priority badge
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = priority.toString(),
                        color = primary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                // Icon
                Icon(
                    imageVector = meta.icon,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(12.dp),
                )

                // Label
                Text(
                    text = meta.label,
                    color = primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )

                // Direction arrow
                Icon(
                    imageVector = if (entry.dir == SortDir.ASC) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                    contentDescription = null,
                    tint = primary.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .clickable { onToggleDir(entry.key) }
                        .padding(2.dp),
                )

                // Remove
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable { onRemove(entry.key) }
                        .padding(2.dp),
                )
            }
        }
    }
}
