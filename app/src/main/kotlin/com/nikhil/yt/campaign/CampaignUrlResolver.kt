package com.nikhil.yt.campaign

import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.models.toMediaMetadata
import timber.log.Timber

/**
 * Turns a campaign row's raw `source_url` into an actually-playable
 * [CampaignCard] — extracting the YouTube video id from the URL, then
 * resolving real, current title/artist/thumbnail metadata for it via the
 * same [YouTube.queue] call the rest of the app uses to resolve a song by
 * id (see PlayerMenu.kt/ListenTogetherManager.kt for other call sites of
 * the identical pattern).
 *
 * Resolving live rather than trusting stored text is deliberate: a
 * campaign row only needs a URL, a start date, and an end date — nothing
 * else has to be typed in, and nothing here can go stale the way a
 * manually-copied title or thumbnail URL could if the source video is
 * retitled or the campaign sits unedited for weeks.
 */
object CampaignUrlResolver {

    // YouTube video ids are always 11 characters from this alphabet.
    private val VIDEO_ID_REGEX = Regex("[A-Za-z0-9_-]{11}")

    private val URL_PATTERNS = listOf(
        Regex("""(?:music\.)?youtube\.com/watch\?.*[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""(?:music\.)?youtube\.com/shorts/([A-Za-z0-9_-]{11})"""),
        Regex("""(?:music\.)?youtube\.com/live/([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
    )

    /**
     * Extracts the video id from a YouTube URL in any of the shapes real
     * users actually paste/share (watch, shorts, live, youtu.be short
     * links — with or without extra query params like a playlist or
     * timestamp). Falls back to treating the whole string as a bare id if
     * it's already exactly 11 valid characters. Returns null for anything
     * that isn't recognizably one of those.
     */
    fun extractVideoId(sourceUrl: String): String? {
        val trimmed = sourceUrl.trim()
        for (pattern in URL_PATTERNS) {
            pattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return if (VIDEO_ID_REGEX.matches(trimmed)) trimmed else null
    }

    /**
     * Resolves one campaign row into a playable [CampaignCard]. Returns
     * null (not a placeholder/broken card) if the URL can't be parsed or
     * the video can't be resolved right now (removed, region-locked,
     * transient network failure) — an unresolvable campaign should just
     * not show up this fetch, never render as a broken card.
     */
    suspend fun resolve(row: CampaignRow): CampaignCard? {
        val videoId = row.resolvedSongId?.takeIf { it.isNotBlank() }
            ?: extractVideoId(row.sourceUrl)
        if (videoId == null) {
            Timber.tag(TAG).w("Could not extract a video id from campaign ${row.id}'s source_url")
            return null
        }

        return try {
            val result = YouTube.queue(listOf(videoId)).getOrNull()
            val metadata = result?.firstOrNull()?.toMediaMetadata()
            if (metadata == null) {
                Timber.tag(TAG).w("Could not resolve metadata for campaign ${row.id} (video $videoId)")
                return null
            }
            CampaignCard(
                id = row.id,
                songId = videoId,
                title = metadata.title,
                artist = metadata.artists.joinToString(", ") { it.name },
                thumbnailUrl = metadata.thumbnailUrl.orEmpty(),
                certified = row.certified,
                isLive = row.isLive,
                playCount = row.playCount,
                ctaLabel = row.ctaLabel,
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to resolve campaign ${row.id} (video $videoId)")
            null
        }
    }

    private const val TAG = "CampaignUrlResolver"
}

/** Raw row shape as parsed from the campaigns table's JSON response,
 * before resolution — see CampaignRepository.parseCampaignRows. */
data class CampaignRow(
    val id: String,
    val sourceUrl: String,
    val resolvedSongId: String?,
    val certified: Boolean,
    val isLive: Boolean,
    val playCount: Long,
    val ctaLabel: String,
)
