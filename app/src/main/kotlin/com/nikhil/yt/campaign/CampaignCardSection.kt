package com.nikhil.yt.campaign

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nikhil.yt.R
import kotlinx.coroutines.launch

/**
 * Below this real play count, the card shows a "New" pill instead of the
 * number — a small real count (1, 4, 7...) reads as empty/unsuccessful
 * rather than honest, the same reason SoundCloud/Bandcamp hide raw counts
 * early on. This changes what's *displayed*, never what's *stored* — the
 * real count keeps accumulating underneath either way, see
 * CampaignRepository.
 */
private const val COUNT_DISPLAY_THRESHOLD = 10L

private fun formatPlayCount(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

/**
 * The home screen's promoted-content banner — one real, currently-live
 * campaign at a time, horizontally swipeable if more than one is active.
 * Deliberately compact (a rectangle banner, not a tall feature card): this
 * sits at the very top of Home, above Trending/Popular, so it needs to
 * read at a glance and get out of the way, not compete with the rest of
 * the page for space.
 *
 * Renders nothing at all if there are no live campaigns (table empty, or
 * everything in it has ended/not started/been paused) or Supabase hasn't
 * been configured yet — see [CampaignRepository]'s empty-list behavior in
 * both cases. No placeholder/skeleton card either: an empty promo slot
 * isn't something worth occupying space to announce.
 *
 * ## Play Recording
 * Every tap on a campaign card records a play via [CampaignRepository.recordPlay]
 * before starting playback. This is a fire-and-forget call — the user
 * experience is never blocked by the network round-trip.
 */
@Composable
fun CampaignCardSection(
    repository: CampaignRepository,
    onCampaignClick: (CampaignCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    var campaigns by remember { mutableStateOf<List<CampaignCard>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        campaigns = repository.fetchActiveCampaigns()
    }

    AnimatedVisibility(visible = campaigns.isNotEmpty()) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(campaigns, key = { it.id }) { campaign ->
                CampaignBanner(
                    campaign = campaign,
                    onClick = {
                        // Record the play fire-and-forget before starting playback
                        scope.launch {
                            repository.recordPlay(campaign.id)
                        }
                        onCampaignClick(campaign)
                    },
                )
            }
        }
    }
}

@Composable
private fun CampaignBanner(
    campaign: CampaignCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val accent = Color(0xFFD4AF37) // warm gold — matches the badge art's own palette
    val liveRed = Color(0xFFE53935)

    Row(
        modifier = modifier
            .width(300.dp)
            .height(72.dp) // small rectangle banner, not a feature card
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.surfaceContainer,
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AsyncImage(
                model = campaign.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            // Moderation badge — shown only when a human reviewer actually
            // approved this campaign, see CampaignCard.certified's doc.
            if (campaign.certified) {
                Image(
                    painter = painterResource(
                        if (isDark) R.drawable.campaign_badge_dark else R.drawable.campaign_badge_light
                    ),
                    contentDescription = "Reviewed pick",
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = campaign.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (campaign.isLive) {
                    Spacer(Modifier.width(6.dp))
                    LiveBadge(color = liveRed)
                }
            }
            Text(
                text = campaign.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            if (campaign.playCount >= COUNT_DISPLAY_THRESHOLD) {
                Text(
                    text = "${formatPlayCount(campaign.playCount)} plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** A small "LIVE" pill with a pulsing red dot — a UI animation, not a
 * data claim; the claim itself is [CampaignCard.isLive], set truthfully
 * by whoever created the campaign. */
@Composable
private fun LiveBadge(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "liveDotPulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liveDotAlpha",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha)),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}
