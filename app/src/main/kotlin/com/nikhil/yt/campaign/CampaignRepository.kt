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

class CampaignRepository {

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    private fun config(): Pair<String, String>? {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/')
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        if (url.isBlank() || anonKey.isBlank()) return null
        return url to anonKey
    }

    suspend fun fetchActiveCampaigns(limit: Int = 10): List<CampaignCard> = withContext(Dispatchers.IO) {
        val (url, anonKey) = config() ?: return@withContext emptyList()
        try {
            val request = Request.Builder()
                .url(
                    "$url/rest/v1/track_campaigns" +
                        "?is_active=eq.true" +
                        "&is_paused=eq.false" +
                        "&select=id,source_url,resolved_song_id,total_streams,current_stage" +
                        "&order=created_at.desc" +
                        "&limit=$limit"
                )
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("fetchActiveCampaigns: HTTP ${response.code}")
                    return@use emptyList()
                }
                val body = response.body?.string().orEmpty()
                parseCampaignRows(body).mapNotNull { row ->
                    CampaignUrlResolver.resolve(row)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "fetchActiveCampaigns failed")
            emptyList()
        }
    }

    suspend fun fetchActiveCampaignMediaItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val campaigns = fetchActiveCampaigns(limit = 10)
        if (campaigns.isEmpty()) return@withContext emptyList()

        campaigns.mapNotNull { campaign ->
            try {
                val result = YouTube.queue(listOf(campaign.songId)).getOrNull()
                val metadata = result?.firstOrNull()?.toMediaMetadata()
                metadata?.toMediaItem()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to resolve campaign ${campaign.id}")
                null
            }
        }
    }

    suspend fun recordPlay(campaignId: String) = withContext(Dispatchers.IO) {
        val (url, anonKey) = config() ?: return@withContext
        try {
            val payload = JSONObject().apply {
                put("campaign_id_input", campaignId)
            }
            val request = Request.Builder()
                .url("$url/rest/v1/rpc/increment_campaign_play")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("recordPlay: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "recordPlay failed")
        }
    }

    private fun parseCampaignRows(body: String): List<CampaignRow> {
        val array = try {
            JSONArray(body)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Malformed response")
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            val row = array.optJSONObject(i) ?: return@mapNotNull null
            val id = row.optString("id", "")
            val sourceUrl = row.optString("source_url", "")
            if (id.isBlank() || sourceUrl.isBlank()) return@mapNotNull null
            CampaignRow(
                id = id,
                sourceUrl = sourceUrl,
                resolvedSongId = row.optString("resolved_song_id", "").takeIf { it.isNotBlank() },
                certified = false,
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
