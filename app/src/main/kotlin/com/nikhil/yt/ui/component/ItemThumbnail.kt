package com.nikhil.yt.ui.component
import androidx.compose.foundation.background; import androidx.compose.foundation.layout.Box; import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape; import androidx.compose.material3.MaterialTheme; import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment; import androidx.compose.ui.Modifier; import androidx.compose.ui.draw.clip; import androidx.compose.ui.unit.Dp; import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
@Composable
fun ItemThumbnail(thumbnailUrl: String?, modifier: Modifier = Modifier, size: Dp = 48.dp, placeholder: @Composable (() -> Unit)? = null) {
    Box(modifier = modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (thumbnailUrl != null) AsyncImage(model = thumbnailUrl, contentDescription = null, modifier = Modifier.matchParentSize()) else placeholder?.invoke()
    }
}
