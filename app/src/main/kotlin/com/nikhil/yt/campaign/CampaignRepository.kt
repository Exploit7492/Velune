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
import java.util.UUID

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
     * Fetch trending campaigns via the get_trending_campaigns RPC.
     * Returns resolved [CampaignCard] objects ready for UI display.
     */
    suspend fun fetchActiveCampaigns(
        limit: Int = 10,
        countryCode: String? = null,
        genre: String? = null
    ): List<CampaignCard> = withContext(Dispatchers.IO) {
        val (url, anonKey) = config() ?: return@withContext emptyList()
        try {
            val request = Request.Builder()
                .url(
                    "$url/rest/v1/rpc/get_trending_campaigns" +
                        "?p_limit=$limit" +
                        (countryCode?.let { "&p_country_code=$it" } ?: "") +
                        (genre?.let { "&p_genre=$it" } ?: "")
                )
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("fetchActiveCampaigns: HTTP ${'$'}{response.code}")
                    return@use emptyList()
                }
                val body = response.body?.string().orEmpty()
                parseTrendingRows(body).mapNotNull { row ->
                    CampaignUrlResolver.resolve(row)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "fetchActiveCampaigns failed")
            emptyList()
        }
    }

    /**
     * Fetch active campaigns as raw MediaItems for queue injection.
     * Used by CampaignInjectedQueue to get playable campaign songs.
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
                Timber.tag(TAG).e(e, "Failed to resolve media item for ${'$'}{campaign.songId}")
                null
            }
        }
    }

    /**
     * Record a campaign stream play via the record_campaign_stream RPC.
     * Called whenever a campaign song is played (full or partial listen).
     *
     * @param campaignId The campaign UUID
     * @param userId The listener's user UUID (real user or seed)
     * @param listenDurationSeconds How many seconds the user listened
     * @param countryCode ISO country code of the listener
     * @param isFullListen Whether the user listened to the full track
     */
    suspend fun recordCampaignStream(
        campaignId: String,
        userId: String? = null,
        listenDurationSeconds: Int = 0,
        countryCode: String? = null,
        isFullListen: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val (url, anonKey) = config() ?: return@withContext
        try {
            val payload = JSONObject().apply {
                put("p_campaign_id", campaignId)
                put("p_user_id", userId ?: UUID.randomUUID().toString())
                put("p_listen_duration_seconds", listenDurationSeconds)
                put("p_country_code", countryCode ?: "unknown")
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
                    Timber.tag(TAG).w("recordCampaignStream: HTTP ${'$'}{response.code}")
                } else {
                    Timber.tag(TAG).d("Recorded stream for campaign $campaignId")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "recordCampaignStream failed for $campaignId")
        }
    }

    /**
     * Legacy increment wrapper — redirects to the new RPC.
     * Kept for backward compatibility with existing call sites.
     */
    suspend fun recordPlay(campaignId: String, userId: String? = null, countryCode: String? = null) {
        recordCampaignStream(campaignId = campaignId, userId = userId, countryCode = countryCode)
    }

    private fun parseTrendingRows(body: String): List<CampaignRow> {
        val array = try {
            JSONArray(body)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Malformed trending response")
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            val row = array.optJSONObject(i) ?: return@mapNotNull null
            val campaignId = row.optString("campaign_id", "")
            val sourceUrl = row.optString("source_url", "")
            if (campaignId.isBlank()) return@mapNotNull null

            CampaignRow(
                id = campaignId,
                sourceUrl = sourceUrl.ifBlank { "https://youtube.com/watch?v=${row.optString("resolved_song_id", "")}" },
                resolvedSongId = row.optString("resolved_song_id", "").takeIf { it.isNotBlank() },
                trackId = row.optString("track_id", "").takeIf { it.isNotBlank() },
                artistId = row.optString("artist_id", "").takeIf { it.isNotBlank() },
                totalStreams = row.optLong("total_streams", 0L),
                trendingScore = row.optDouble("trending_score", 0.0),
                geographicTier = row.optString("geographic_tier", "local"),
                currentStage = row.optString("current_stage", "planting"),
                certified = when (row.optString("current_stage", "planting")) {
                    "branching", "full_bloom" -> true
                    else -> false
                },
                isLive = false,
                playCount = row.optLong("total_streams", 0L),
                ctaLabel = when (row.optString("current_stage", "planting")) {
                    "planting" -> "Discover"
                    "germination" -> "Trending"
                    "root_system" -> "Hot"
                    "branching" -> "Viral"
                    "full_bloom" -> "Charting"
                    else -> "Play"
                },
            )
        }
    }

    companion object {
        private const val TAG = "CampaignRepository"
    }
}
