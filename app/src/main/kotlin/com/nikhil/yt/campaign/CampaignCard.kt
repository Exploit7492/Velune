package com.nikhil.yt.campaign

/**
 * A single promoted-content banner. Everything on this class is either
 * something a human typed into the campaigns table, a real count Velune
 * measured, or metadata resolved live from YouTube — there is no
 * generated/projected/simulated field anywhere on it. If a future change
 * wants to add one, that's a signal to stop and reconsider the feature,
 * not extend this class — see CampaignRepository.kt's class doc.
 */
data class CampaignCard(
    val id: String,
    /** Extracted from the campaign row's source_url by
     * [CampaignUrlResolver] — the id actually used to queue playback. */
    val songId: String,
    /** Resolved live from YouTube at fetch time (CampaignUrlResolver),
     * never typed into the table directly — one source of truth, and it
     * can never go stale the way a manually-copied title/thumbnail could. */
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    /**
     * True only if a human reviewer approved this campaign for the
     * promoted slot — a moderation flag, not a claim about the artist's
     * identity, label status, or any measured popularity.
     */
    val certified: Boolean,
    /**
     * True only if a human truthfully marked this as pointing at a
     * genuine YouTube livestream. Velune has no Radio/Podcast/Show
     * content types — songs and videos from YouTube are the only content
     * this app plays, and a video is the only one of those that can ever
     * really be "live." This is never inferred or defaulted to true;
     * whoever creates the campaign row is asserting a fact about their
     * own content, same honesty bar as [certified]. Rendered as the red
     * LIVE badge — see CampaignBanner's doc.
     */
    val isLive: Boolean,
    /** Real play count, incremented server-side once per actual playback
     * start via the increment_campaign_play RPC — never client-computed. */
    val playCount: Long,
    val ctaLabel: String,
)
