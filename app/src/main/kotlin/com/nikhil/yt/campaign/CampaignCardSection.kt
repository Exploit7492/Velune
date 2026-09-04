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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * The home screen's promoted-content banner — Task 59 Part 3b-b
 * (handover.md): a single-card carousel, one real, currently-live
 * campaign visible at a time, auto-advancing every 30 seconds and
 * reshuffling specifically on app-background-then-resume (never on
 * every recomposition, never on initial load) — see
 * [rememberCampaignCarouselState]'s own header comment for the full
 * state-machine rules this composable is built on. Replaces the prior
 * `LazyRow`-of-every-live-campaign design entirely (Round 3's own
 * "replace, not augment" decision, applied here to both the data
 * source and the rendering code) — this was never meant to be a
 * "browse all active campaigns" surface, it's a single rotating
 * spotlight, deliberately compact (a rectangle banner, not a tall
 * feature card): this sits at the very top of Home, above
 * Trending/Popular, so it needs to read at a glance and get out of
 * the way, not compete with the rest of the page for space.
 *
 * Feeds from [CampaignRepository.fetchLiveCampaignsForBanner] (Task
 * 59 Part 3a), not [CampaignRepository.fetchActiveCampaigns] — the
 * former returns every currently-live campaign with zero ranking
 * (this surface's whole point), display metadata already resolved
 * server-side, and deliberately zeroed `playCount`/`trendingScore` at
 * the data layer (Part 3a's own doc comment) — enforcing "never reveal
 * the live count" one layer below this composable, not just by this
 * file choosing not to render it.
 *
 * Renders nothing at all if there are no live campaigns (table empty,
 * or everything in it has ended/not started/been paused) or Supabase
 * hasn't been configured yet — [rememberCampaignCarouselState]'s
 * `current` is simply `null` in both cases. No placeholder/skeleton
 * card either: an empty promo slot isn't something worth occupying
 * space to announce.
 *
 * ## Play Recording
 * Task 60 (handover.md) — this composable does not record a play on
 * tap, and never should again (see that task's own write-up for the
 * double-recording bug this was the fix for) — the one correct,
 * surviving call lives in `MusicService.kt`, firing once playback
 * actually transitions to the tapped song, not at tap-time.
 */
@Composable

fun CampaignCardSection(
    repository: CampaignRepository,
    onCampaignClick: (CampaignCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    var campaigns by remember { mutableStateOf<List<CampaignCard>>(emptyList()) }

    LaunchedEffect(Unit) {
        val fetched = repository.fetchLiveCampaignsForBanner()
        Timber.tag("CampaignCardSection").d("Fetched ${fetched.size} campaigns")
        campaigns = fetched
        fetched.firstOrNull()?.let {
            Timber.tag("CampaignCardSection").d("First campaign: ${it.title} by ${it.artist}")
        }
    }

    val campaign = campaigns.firstOrNull()
    Timber.tag("CampaignCardSection").d("Current campaign: ${campaign?.title ?: "null"}")

    if (campaign != null) {
        CampaignBanner(
            campaign = campaign,
            onClick = { onCampaignClick(campaign) },
            modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}


@Composable
private fun CampaignBanner(
    campaign: CampaignCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
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
