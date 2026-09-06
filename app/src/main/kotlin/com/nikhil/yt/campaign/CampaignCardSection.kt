package com.nikhil.yt.campaign

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * feature card): it sits directly below the home category chips
 * (`ChipsRow` in `HomeScreen.kt`) and above Quick Picks/Trending, so
 * it needs to read at a glance and get out of the way, not compete
 * with the rest of the page for space.
 *
 * Feeds from [CampaignRepository.fetchLiveCampaignsForBanner] (Task
 * 59 Part 3a), not [CampaignRepository.fetchActiveCampaigns] — the
 * former returns every currently-live campaign with zero ranking
 * (this surface's whole point), display metadata already resolved
 * server-side, and deliberately zeroed `playCount`/`trendingScore` at
 * the data layer (Part 3a's own doc comment) — enforcing "never reveal
 * the live count" one layer below this composable, not just by this
 * file choosing not to render it. Same rule for `ctaLabel`: the
 * banner's own row parser hardcodes it to `"Play"` on purpose — see
 * [CampaignRepository.fetchLiveCampaignsForBanner]'s doc comment for
 * why the stage-based Discover/Trending/Hot/Viral/Charting ladder
 * must never reach this surface. HANDOVER_CAMPAIGN.md §32's visual
 * redesign below still renders [CampaignCard.ctaLabel] verbatim (as a
 * generic call-to-action, currently always "Play") — it must NOT be
 * changed to read [CampaignCard.currentStage]/[CampaignCard.geographicTier]
 * to make that label "richer"; both stay unread by this file, same as
 * before the redesign.
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
 *
 * ## Visual redesign (HANDOVER_CAMPAIGN.md §32)
 * [CampaignBanner] below was rebuilt as a cinematic glass-panel card:
 * the campaign's own thumbnail, blurred and darkened, fills the whole
 * card as ambient art (a self-contained frosted-glass look, built from
 * a blurred [AsyncImage] + gradient scrim + translucent border — see
 * that composable's own doc comment for why this deliberately does
 * NOT use this app's existing `Modifier.liquidGlass` backdrop-blur
 * system, even though one already exists in
 * `com.nikhil.yt.ui.component.GlassEffectStubs`). Layout contract is
 * unchanged: still a fixed-size rectangle banner (only modestly taller
 * than before, 92.dp vs. the previous 72.dp, to fit the new CTA glass
 * chip without crowding the text), still a single item, still renders
 * nothing when there's no live campaign.
 */
@Composable
fun CampaignCardSection(
    repository: CampaignRepository,
    onCampaignClick: (CampaignCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    var campaigns by remember { mutableStateOf<List<CampaignCard>>(emptyList()) }

    LaunchedEffect(Unit) {
        campaigns = repository.fetchLiveCampaignsForBanner()
    }

    val carouselState = rememberCampaignCarouselState(campaigns)
    val campaign = carouselState.current

    AnimatedVisibility(visible = campaign != null) {
        if (campaign != null) {
            CampaignBanner(
                campaign = campaign,
                onClick = { onCampaignClick(campaign) },
                modifier = modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/**
 * The card itself — see [CampaignCardSection]'s "Visual redesign"
 * section for the brief. Three stacked layers give the glass-panel
 * effect, all self-contained to this card (no sampling of the real
 * screen content behind it):
 *
 * 1. The campaign's own [CampaignCard.thumbnailUrl], scaled to fill
 *    the card and blurred ([Modifier.blur], RenderEffect-backed —
 *    a documented no-op below API 31, so the layer just renders
 *    sharp instead; never crashes, never blank, on `minSdk = 26`).
 *    This is deliberately a *second*, separate [AsyncImage] from the
 *    sharp foreground thumbnail below, not the same composable reused
 *    — Coil caches the underlying bitmap by URL, so this costs no
 *    extra network fetch.
 * 2. A diagonal dark scrim (near-black, low luminance) so light text
 *    stays legible over whatever the art actually looks like, plus a
 *    hairline gradient border (bright top-left corner fading to
 *    transparent) — the classic glassmorphism edge highlight, drawn
 *    directly rather than via the backdrop-blur pipeline (see below).
 * 3. The actual content: sharp thumbnail, "Promoted" label (now the
 *    same gold, `Color(0xFFD4AF37)`, as `Player.kt`'s own full-screen
 *    "Promoted" label — previously a different blue here; unifying
 *    the two "Promoted" surfaces' color was a deliberate part of this
 *    redesign, not an accidental collision), title/artist in
 *    high-contrast white (forced, not `MaterialTheme.colorScheme.*` —
 *    those tokens assume a plain surface-color background, which this
 *    card no longer has now that real art fills it), the existing
 *    pulsing [LiveBadge], and a small circular glass "play" chip
 *    rendering [CampaignCard.ctaLabel]'s icon as a generic CTA
 *    affordance (see this file's top doc comment for why the label's
 *    *text* is deliberately never derived from stage/tier data).
 *
 * Why not this app's own `Modifier.liquidGlass` (`GlassEffectStubs.kt`)
 * for the frosted-panel look: that system does true backdrop-sampling
 * blur — it blurs whatever's actually rendered *behind* the composable
 * in the real screen (via [com.nikhil.yt.LocalAppBackdrop]), which is
 * the right tool for a floating overlay that stays put while other
 * content scrolls underneath it (`GlassMiniPlayer.kt`, the nav bar).
 * This card is the opposite: it's an inline `LazyColumn` item in
 * `HomeScreen.kt`, scrolling *with* everything else — there's nothing
 * meaningfully "behind" it to sample, and it would also make this
 * card's look depend on `GlassEffectConfig.globalEnabled` (default
 * `false`) and the user's blur/vibrancy sliders, i.e. most users would
 * get a plain, non-glass card by default for a surface that's supposed
 * to always look premium. Blurring the card's own art instead sidesteps
 * both problems and needs no new per-component entry in
 * [com.nikhil.yt.ui.component.GlassComponent] (which would also mean
 * touching the Settings screen — out of scope for this pass).
 */
@Composable
private fun CampaignBanner(
    campaign: CampaignCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val liveRed = Color(0xFFE53935)
    val premiumGold = Color(0xFFD4AF37)
    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .width(320.dp)
            .height(92.dp) // still a compact banner, not a feature card — see class doc
            .clip(cardShape)
            .clickable(onClick = onClick),
    ) {
        // Layer 1 — ambient art: the campaign's own thumbnail, blurred
        // and stretched to fill the card. Deliberately a second
        // AsyncImage (Coil dedupes by URL, so no extra network cost).
        AsyncImage(
            model = campaign.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(26.dp),
        )

        // Layer 2 — cinematic scrim + glass edge highlight.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.78f),
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.62f),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.38f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                    shape = cardShape,
                ),
        )

        // Layer 3 — foreground content.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(
                    model = campaign.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(12.dp),
                        ),
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

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                // "Promoted" indicator — same gold as Player.kt's own
                // full-screen "Promoted" label. HANDOVER_CAMPAIGN.md
                // §33: a 🔥 sits right after the word itself (part of
                // the same Text so it never wraps onto its own line),
                // purely a visual accent — it is not read from
                // CampaignCard.currentStage/trendingScore, same "never
                // derive this label from stage/tier data" rule as the
                // rest of this card.
                Text(
                    text = "Promoted \uD83D\uDD25",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    fontWeight = FontWeight.Bold,
                    color = premiumGold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = campaign.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = campaign.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // HANDOVER_CAMPAIGN.md §33: the trailing glass "play" CTA
            // chip is removed outright, not just hidden — the whole
            // card is already a single tap target (see the outer Box's
            // own .clickable above), so a second play affordance next
            // to it was redundant, not a distinct action.
        }

        // HANDOVER_CAMPAIGN.md §33: LIVE pill moved off the title row
        // and onto the card itself, top-right corner, so it reads as a
        // card-level status flag rather than crowding the title text —
        // and shrunk (see LiveBadge's own `compact` param) to match its
        // new, smaller corner placement. Added last among this Box's
        // children so it draws above Layer 3's content, not beneath it.
        if (campaign.isLive) {
            LiveBadge(
                color = liveRed,
                compact = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
            )
        }
    }
}

/** A small "LIVE" pill with a pulsing red dot — a UI animation, not a
 * data claim; the claim itself is [CampaignCard.isLive], set truthfully
 * by whoever created the campaign. Restyled for legibility over real
 * art (higher-contrast chip background) as part of HANDOVER_CAMPAIGN.md
 * §32 — the pulse mechanic itself is untouched.
 *
 * [compact] (HANDOVER_CAMPAIGN.md §33) shrinks the pill for its new
 * top-right corner placement on [CampaignBanner] — smaller dot, tighter
 * padding, explicit 8.sp text instead of `labelSmall` (which reads too
 * large at this corner size). Defaults to `false` so nothing else that
 * might ever reuse this composable changes size unless it opts in. */
@Composable
private fun LiveBadge(color: Color, modifier: Modifier = Modifier, compact: Boolean = false) {
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
    val dotSize = if (compact) 4.dp else 6.dp
    val horizontalPadding = if (compact) 4.dp else 5.dp
    val verticalPadding = if (compact) 0.5.dp else 1.dp
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.5f))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = alpha)),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "LIVE",
            style = if (compact) {
                MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp)
            } else {
                MaterialTheme.typography.labelSmall
            },
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}
