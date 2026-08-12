package com.nikhil.yt.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

data class DraggableLyricsProviderItem(
    val id: String,
    val name: String,
    val icon: Painter,
)

@Composable
fun DraggableLyricsProviderList(
    items: List<DraggableLyricsProviderItem>,
    onItemsReordered: (List<DraggableLyricsProviderItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(items, key = { it.id }) { item ->
            val index = items.indexOf(item)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Row {
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val reordered = items.toMutableList()
                                    val moved = reordered.removeAt(index)
                                    reordered.add(index - 1, moved)
                                    onItemsReordered(reordered)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Move up",
                            )
                        }
                        IconButton(
                            onClick = {
                                if (index < items.lastIndex) {
                                    val reordered = items.toMutableList()
                                    val moved = reordered.removeAt(index)
                                    reordered.add(index + 1, moved)
                                    onItemsReordered(reordered)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Move down",
                            )
                        }
                    }
                }
            }
        }
    }
}
