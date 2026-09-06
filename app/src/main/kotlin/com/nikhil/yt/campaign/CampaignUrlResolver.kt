/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.campaign

import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.models.toMediaMetadata
import timber.log.Timber

/**
 * Turns a campaign row's raw `source_url` into an actually-playable
 * [CampaignCard] — extracting the YouTube video id from the URL, then
 * resolving real, current title/artist/thumbnail metadata for it via the
 * same [YouTube.queue] call the rest of the app uses to resolve a song by
 * id.
 *
 * Resolving live rather than trusting stored text is deliberate: a
 * campaign row only needs a URL — nothing else can go stale.
 */
object CampaignUrlResolver {

    // YouTube video ids are always 11 characters from this alphabet.
    private val VIDEO_ID_REGEX = Regex("[A-Za-z0-9_-]{11}")

    private val URL_PATTERNS = listOf(
        // Bug fixed here: this used to be
        // `watch\?.*[?&]v=([A-Za-z0-9_-]{11})` -- the literal `\?` right
        // after `watch` already consumes the URL's one `?`, so the
        // `[?&]` immediately before `v=` could only ever match when
        // `v=` was NOT the first query parameter (e.g.
        // `?feature=share&v=ID`). Every real YouTube Music share link
        // puts `v=` first (`?v=ID&si=...`) -- confirmed against two real
        // campaign rows in production, both silently failing extraction
        // this way despite being genuine, well-formed
        // `music.youtube.com/watch?v=...` URLs. Fixed by allowing zero
        // or more other `key=value&` pairs ahead of `v=`, so `v=` being
        // first (the common case), or not first (the rare case the old
        // pattern accidentally only supported), both match.
        Regex("""(?:music\.)?youtube\.com/watch\?(?:[^&]*&)*v=([A-Za-z0-9_-]{11})"""),
        Regex("""(?:music\.)?youtube\.com/shorts/([A-Za-z0-9_-]{11})"""),
        Regex("""(?:music\.)?youtube\.com/live/([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
    )

    /**
     * Extracts the video id from a YouTube URL in any of the shapes real
     * users actually paste/share. Falls back to treating the whole string
     * as a bare id if it's already exactly 11 valid characters.
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
     * null if the URL can't be parsed or the video can't be resolved.
     */
    suspend fun resolve(row: CampaignRow): CampaignCard? {
        val videoId = row.resolvedSongId?.takeIf { it.isNotBlank() }
            ?: extractVideoId(row.sourceUrl)
        if (videoId == null) {
            Timber.tag(TAG).w("Could not extract video id from campaign ${'$'}{row.id}'s source_url")
            return null
        }

        return try {
            val result = YouTube.queue(listOf(videoId)).getOrNull()
            val metadata = result?.firstOrNull()?.toMediaMetadata()
            if (metadata == null) {
                Timber.tag(TAG).w("Could not resolve metadata for campaign ${'$'}{row.id} (video $videoId)")
                return null
            }
            CampaignCard(
                id = row.id,
                songId = videoId,
                trackId = row.trackId ?: "",
                artistId = row.artistId ?: "",
                title = metadata.title,
                artist = metadata.artists.joinToString(", ") { it.name },
                thumbnailUrl = metadata.thumbnailUrl.orEmpty(),
                totalStreams = row.totalStreams,
                trendingScore = row.trendingScore,
                geographicTier = row.geographicTier,
                currentStage = row.currentStage,
                certified = row.certified,
                isLive = row.isLive,
                playCount = row.playCount,
                ctaLabel = row.ctaLabel,
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to resolve campaign ${'$'}{row.id} (video $videoId)")
            null
        }
    }

    private const val TAG = "CampaignUrlResolver"
}

/** Raw row shape as parsed from the campaigns table's JSON response,
 * before resolution — see CampaignRepository.parseTrendingRows. */
data class CampaignRow(
    val id: String,
    val sourceUrl: String,
    val resolvedSongId: String?,
    val trackId: String? = null,
    val artistId: String? = null,
    val totalStreams: Long = 0L,
    val trendingScore: Double = 0.0,
    val geographicTier: String = "local",
    val currentStage: String = "planting",
    val certified: Boolean = false,
    val isLive: Boolean = false,
    val playCount: Long = 0L,
    val ctaLabel: String = "Play",
)
