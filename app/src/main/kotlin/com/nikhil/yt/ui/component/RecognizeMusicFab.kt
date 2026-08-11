package com.nikhil.yt.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nikhil.yt.R

@Composable
fun RecognizeMusicFab(
    onClick: () -> Unit,
    isListening: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    FloatingActionButton(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp
        ),
        modifier = modifier
            .padding(end = 20.dp, bottom = 84.dp)
            .size(56.dp)
            .then(
                if (isListening) {
                    Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                } else Modifier
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.music_note),
            contentDescription = "Recognize music",
            modifier = Modifier.size(28.dp)
        )
    }
}
