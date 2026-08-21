/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikhil.yt.ui.utils.formatCompactCount
import kotlin.math.max

// Design + animation ported directly from mavins' player screen's
// `playCountPill` + `AnimatedCounter` — pill styling, easing curve, and
// duration all matched to the original rather than approximated. Mavins
// uses a headset icon here rather than an eye, reading as "listens" for a
// music app rather than a literal video-view metric, even though the
// underlying figure (YouTube's view count) is the same either way — kept
// that choice as-is rather than swapping in an eye icon, since it's a
// deliberate framing decision in the original, not an arbitrary one. The
// original also renders the bare formatted number with no "views"/
// "listens" text label — the icon alone carries the meaning.
private const val COUNT_UP_DURATION_MS = 3500

@Composable
fun ViewCountPill(
    target: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (target == null) {
            // Skeleton placeholder — shown while the count is still
            // resolving. Callers are expected to not render this pill at
            // all once it's known there's nothing to show (e.g. local
            // media — see Player.kt's isCurrentSongLocal gate), so an
            // indefinitely-stuck skeleton here should only ever be a
            // brief transient state for streamed tracks.
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(10.dp)
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Headphones,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            AnimatedCountText(target = target)
        }
    }
}

/**
 * Counts up from 1 to [target] over [COUNT_UP_DURATION_MS] (3.5s, matched
 * to mavins' AnimatedCounter exactly), eased with the same ease-out-quad
 * curve (`1 - (1-t)^2`) so it decelerates into the final number rather
 * than landing on it linearly. Re-triggers whenever [target] changes (a
 * new track), same as mavins resetting its `counterTarget` state per
 * track.
 */
@Composable
private fun AnimatedCountText(target: Int) {
    val progress = remember(target) { Animatable(0f) }

    LaunchedEffect(target) {
        if (target <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = COUNT_UP_DURATION_MS, easing = EaseOutQuad),
        )
    }

    // max(1, floor(eased * target)) — matches mavins' Math.max(1, Math.floor(...)) exactly.
    val displayed = if (target <= 0) 1 else max(1, (progress.value * target).toInt())

    Text(
        text = formatCompactCount(displayed.toLong()),
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
    )
}

// 1 - (1-t)^2 — same ease-out-quadratic mavins' AnimatedCounter uses.
private val EaseOutQuad = Easing { t -> 1f - (1f - t) * (1f - t) }
