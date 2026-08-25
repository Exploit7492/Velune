/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.campaign

import androidx.media3.common.MediaItem
import com.nikhil.yt.BuildConfig
import com.nikhil.yt.extensions.toMediaItem
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Reads campaign data from the new `track_campaigns` system via Supabase
 * RPCs. The old `campaigns` table has been replaced by `track_campaigns`
 * which links to the existing `tracks` and `users` tables.
 *
 * This class:
 * 1. Calls `get_trending_campaigns` RPC for active campaigns
 * 2. Fetches track details from the `tracks` table to get YouTube video IDs
 * 3. Resolves YouTube metadata to playable [CampaignCard] objects
 * 4. Resolves [MediaItem]s for queue injection via [CampaignInjectedQueue]
 *
 * All Supabase credentials come from [BuildConfig] (compiled from CI secrets
 * or local.properties). Blank credentials = graceful degradation (empty lists).
 */
class CampaignRepository {

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    private fun config(): Pair<String, String>? {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/')
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        if (url.isBlank() || anonKey.isBlank()) return null
        return url to anonKey
    }

    /**
     * Fetches active, trending campaigns from the `track_campaigns` table
     * via the `get_trending_campaigns` RPC. For each campaign, fetches the
     * associated track from the `tracks` table to extract the YouTube video
     * ID needed for playback.
     *
     * Returns empty list on any failure — never blocks the UI.
     */
    suspend fun fetchActiveCampaigns(
        limit: Int = 10,
        countryCode: String? = null,
        genre: String? = null,
    ): List<CampaignCard> = withContext(Dispatchers.IO) {
        val (url, anonKey) = config() ?: return@withContext emptyList()

        try {
            // Step 1: Get trending campaigns from RPC
            val request = Request.Builder()
                .url("$url/rest/v1/rpc/get_trending_campaigns")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .post(
                    JSONObject().apply {
                        put("p_limit", limit)
                        put("p_country_code", countryCode ?: JSONObject.NULL)
                        put("p_genre", genre ?: JSONObject.NULL)
                    }.toString().toRequestBody(jsonMediaType)
                )
                .build()

            val campaigns = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("get_trending_campaigns RPC: HTTP ${response.code}")
                    return@use emptyList<TrendingCampaignRow>()
                }
                parseTrendingCampaigns(response.body?.string().orEmpty())
            }

            if (campaigns.isEmpty()) return@withContext emptyList()

            // Step 2: For each campaign, fetch track details to get YouTube ID
            campaigns.mapNotNull { row ->
                val trackDetails = fetchTrackDetails(url, anonKey, row.trackId)
                val songId = extractYouTubeId(trackDetails)
                if (songId == null) {
                    Timber.tag(TAG).w("Could not extract YouTube ID for track ${row.trackId}")
                    return@mapNotNull null
                }

                // Step 3: Resolve live YouTube metadata
                val metadata = try {
                    val result = YouTube.queue(listOf(songId)).getOrNull()
                    result?.firstOrNull()?.toMediaMetadata()
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "YouTube resolution failed for $songId")
                    null
                }

                CampaignCard(
                    id = row.campaignId,
                    songId = songId,
                    trackId = row.trackId,
                    artistId = row.artistId,
                    title = metadata?.title ?: row.trackTitle,
                    artist = metadata?.artists?.joinToString(", ") { it.name } ?: row.artistName,
                    thumbnailUrl = metadata?.thumbnailUrl ?: row.coverUrl,
                    totalStreams = row.totalStreams,
                    trendingScore = row.trendingScore,
                    geographicTier = row.geographicTier,
                    currentStage = row.currentStage,
                    certified = false, // New schema doesn't have this; derive from stage if needed
                    isLive = false,    // New schema doesn't have this
                    playCount = row.totalStreams,
                    ctaLabel = when (row.currentStage) {
                        "planting" -> "Discover"
                        "germination" -> "Trending"
                        "root_system" -> "Hot"
                        "branching" -> "Viral"
                        "full_bloom" -> "Charting"
                        else -> "Play"
                    },
                )
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "fetchActiveCampaigns failed")
            emptyList()
        }
    }

    /**
     * Resolves active campaigns directly to [MediaItem]s for queue injection.
     * This skips the intermediate [CampaignCard] step and is used by
     * [CampaignInjectedQueue] to inject campaign songs into the playback queue.
     */
    suspend fun fetchActiveCampaignMediaItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val campaigns = fetchActiveCampaigns(limit = 10)
        if (campaigns.isEmpty()) return@withContext emptyList()

        campaigns.mapNotNull { campaign ->
            try {
                val result = YouTube.queue(listOf(campaign.songId)).getOrNull()
                val metadata = result?.firstOrNull()?.toMediaMetadata()
                metadata?.toMediaItem()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to resolve campaign ${campaign.id} to MediaItem")
                null
            }
        }
    }

    /**
     * Records one real playback start against a campaign. Called by Velune
     * when a user (real or seed — we don't distinguish here) plays a
     * campaign track. The RPC handles all internal logic internally.
     */
    suspend fun recordPlay(
        campaignId: String,
        userId: String,
        listenDurationSeconds: Int = 0,
        countryCode: String? = null,
        isFullListen: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val (url, anonKey) = config() ?: return@withContext
        try {
            val payload = JSONObject().apply {
                put("p_campaign_id", campaignId)
                put("p_user_id", userId)
                put("p_listen_duration_seconds", listenDurationSeconds)
                put("p_country_code", countryCode ?: JSONObject.NULL)
                put("p_is_full_listen", isFullListen)
            }
            val request = Request.Builder()
                .url("$url/rest/v1/rpc/record_campaign_stream")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("record_campaign_stream RPC: HTTP ${response.code} for campaign $campaignId")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "recordPlay failed for campaign $campaignId")
        }
    }

    // ── Internal helpers ──────────────────────────────────────────

    private fun parseTrendingCampaigns(body: String): List<TrendingCampaignRow> {
        val array = try {
            JSONArray(body)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Malformed get_trending_campaigns response")
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            TrendingCampaignRow(
                campaignId = obj.optString("campaign_id", ""),
                trackId = obj.optString("track_id", ""),
                artistId = obj.optString("artist_id", ""),
                artistName = obj.optString("artist_name", ""),
                trackTitle = obj.optString("track_title", ""),
                coverUrl = obj.optString("cover_url", ""),
                totalStreams = obj.optLong("total_streams", 0L),
                trendingScore = obj.optDouble("trending_score", 0.0),
                geographicTier = obj.optString("geographic_tier", "local"),
                currentStage = obj.optString("current_stage", "planting"),
            )
        }
    }

    private fun fetchTrackDetails(url: String, anonKey: String, trackId: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url("$url/rest/v1/tracks?id=eq.$trackId&select=*")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val array = JSONArray(body)
                if (array.length() > 0) array.getJSONObject(0) else null
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "fetchTrackDetails failed for track $trackId")
            null
        }
    }

    private fun extractYouTubeId(trackDetails: JSONObject?): String? {
        if (trackDetails == null) return null

        // Try audio_url first
        trackDetails.optString("audio_url", "").takeIf { it.isNotBlank() }?.let { url ->
            extractFromUrl(url)?.let { return it }
        }

        // Try video_url
        trackDetails.optString("video_url", "").takeIf { it.isNotBlank() }?.let { url ->
            extractFromUrl(url)?.let { return it }
        }

        // Try metadata_json.youtube_id
        trackDetails.optJSONObject("metadata_json")?.optString("youtube_id", "")
            ?.takeIf { it.isNotBlank() }?.let { return it }

        // Try metadata_json.video_id
        trackDetails.optJSONObject("metadata_json")?.optString("video_id", "")
            ?.takeIf { it.isNotBlank() }?.let { return it }

        // Last resort: if the track id itself is 11 chars and alphanumeric, treat as bare YouTube ID
        val trackId = trackDetails.optString("id", "")
        if (VIDEO_ID_REGEX.matches(trackId)) return trackId

        return null
    }

    private fun extractFromUrl(url: String): String? {
        val trimmed = url.trim()
        for (pattern in URL_PATTERNS) {
            pattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return if (VIDEO_ID_REGEX.matches(trimmed)) trimmed else null
    }

    companion object {
        private const val TAG = "CampaignRepository"
        private val VIDEO_ID_REGEX = Regex("[A-Za-z0-9_-]{11}")
        private val URL_PATTERNS = listOf(
            Regex("""(?:music\.)?youtube\.com/watch\?.*[?&]v=([A-Za-z0-9_-]{11})"""),
            Regex("""(?:music\.)?youtube\.com/shorts/([A-Za-z0-9_-]{11})"""),
            Regex("""(?:music\.)?youtube\.com/live/([A-Za-z0-9_-]{11})"""),
            Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        )
    }
}

/**
 * Raw row from `get_trending_campaigns` RPC response.
 */
private data class TrendingCampaignRow(
    val campaignId: String,
    val trackId: String,
    val artistId: String,
    val artistName: String,
    val trackTitle: String,
    val coverUrl: String,
    val totalStreams: Long,
    val trendingScore: Double,
    val geographicTier: String,
    val currentStage: String,
)
