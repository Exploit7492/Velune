package com.nikhil.yt.campaign

import androidx.media3.common.MediaItem
import com.nikhil.yt.extensions.toMediaItem
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.models.toMediaMetadata
import com.nikhil.yt.BuildConfig
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
 * Reads/writes the `campaigns` table over Supabase's REST API (PostgREST) —
 * plain OkHttp + org.json, matching how the rest of this app talks to
 * third-party JSON APIs (see AiRecommendationHelper), rather than pulling in
 * the full supabase-kt SDK for what's a handful of simple calls.
 *
 * A campaign row only ever needs a URL, a start date, and an end date —
 * [CampaignUrlResolver] resolves the rest (title/artist/thumbnail) live
 * from YouTube. There is deliberately no method here that writes a number
 * or a piece of metadata this class invented; [CampaignCard.playCount] is
 * the real `campaigns.play_count` column, moved only by the atomic
 * `increment_campaign_play` RPC (see the migration SQL this feature ships
 * with) once per genuine playback start. If a future change wants to add
 * a projected/estimated/seeded field to this pipeline anywhere, that's a
 * sign to stop and reconsider the feature, not extend this class.
 *
 * Credentials ([BuildConfig.SUPABASE_URL]/[BuildConfig.SUPABASE_ANON_KEY])
 * are compiled in at build time — sourced from `local.properties` for a
 * local dev build or CI secrets for a release build (see
 * `app/build.gradle.kts`), never typed into the app at runtime. This is
 * deliberately different from the per-user AI API keys elsewhere in this
 * app (OpenRouterApiKey etc., which are DataStore/Settings-backed): the
 * campaigns table is a single app-owned backend every install talks to,
 * not a credential each user brings their own copy of. Embedding a
 * Supabase *anon* key in a client build is the standard, documented way
 * to use it from a mobile app — it's meant to be public; row level
 * security in campaign_schema.sql is what actually gates access, not
 * secrecy of this key. Until real values are supplied at build time,
 * every method here simply returns an empty result / no-ops rather than
 * throwing, so the app works fine and the campaign card just doesn't
 * render.
 *
 * Visibility (which rows even come back from Supabase at all) is enforced
 * server-side twice over — see campaign_schema.sql's RLS policy and the
 * RPC's own WHERE clause — so an expired or paused campaign can't leak
 * through even if this class's own query below were ever wrong. The
 * `active=eq.true` filter and date bounds in [fetchActiveCampaigns] are
 * defense in depth on top of that, not the only thing standing between an
 * expired campaign and a user seeing it.
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
     * Currently-live campaigns (active AND inside their date window),
     * newest first, each resolved to real, current YouTube metadata.
     * Returns an empty list — never an error, never a placeholder card —
     * if Supabase isn't configured, if no campaign is currently live, or
     * on any network/parse failure; a broken promo fetch should never
     * block the rest of Home from rendering.
     */
    suspend fun fetchActiveCampaigns(limit: Int = 10): List<CampaignCard> = withContext(Dispatchers.IO) {
        val (url, anonKey) = config() ?: return@withContext emptyList()
        try {
            val nowIso = java.time.Instant.now().toString()
            // active=true and inside [start_date, end_date] — the RLS
            // policy already guarantees this server-side; these query
            // params exist so a mis-set clock skew or a future looser RLS
            // change doesn't silently start showing expired campaigns
            // without at least this layer noticing too.
            val request = Request.Builder()
                .url(
                    "$url/rest/v1/campaigns" +
                        "?active=eq.true" +
                        "&start_date=lte.$nowIso" +
                        "&end_date=gte.$nowIso" +
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
                    return@withContext emptyList()
                }
                val body = response.body?.string().orEmpty()
                val rows = parseCampaignRows(body)
                rows.mapNotNull { row -> CampaignUrlResolver.resolve(row) }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "fetchActiveCampaigns failed")
            emptyList()
        }
    }

    /**
     * Records one real playback start against a campaign's real play
     * count, via an atomic server-side RPC — not a client-side
     * read-then-write, which would race under concurrent plays across
     * devices. Silently no-ops if Supabase isn't configured or the call
     * fails; a missed increment shouldn't ever interrupt actual playback,
     * which is why this is fire-and-forget from the caller's perspective
     * (see HomeScreen's call site — it launches this on a background
     * scope and doesn't await it).
     */
    suspend fun recordPlay(campaignId: String) = withContext(Dispatchers.IO) {
        val (url, anonKey) = config() ?: return@withContext
        try {
            val payload = JSONObject().apply { put("campaign_id_input", campaignId) }
            val request = Request.Builder()
                .url("$url/rest/v1/rpc/increment_campaign_play")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("recordPlay: HTTP ${response.code} for campaign $campaignId")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "recordPlay failed for campaign $campaignId")
        }
    }

    private fun parseCampaignRows(body: String): List<CampaignRow> {
        val array = try {
            JSONArray(body)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Malformed campaigns response")
            return emptyList()
        }
        val result = ArrayList<CampaignRow>(array.length())
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val id = row.optString("id", "")
            val sourceUrl = row.optString("source_url", "")
            if (id.isBlank() || sourceUrl.isBlank()) continue
            result.add(
                CampaignRow(
                    id = id,
                    sourceUrl = sourceUrl,
                    resolvedSongId = row.optString("resolved_song_id", "").takeIf { it.isNotBlank() },
                    certified = row.optBoolean("certified", false),
                    isLive = row.optBoolean("is_live", false),
                    playCount = row.optLong("play_count", 0L),
                    ctaLabel = row.optString("cta_label", "Play"),
                )
            )
        }
        return result
    }


    /**
     * Fetches active campaigns and resolves each to a playable [MediaItem].
     * Used by [CampaignInjectedQueue] to inject campaign songs into the
     * playback queue every 5th position.
     *
     * Returns empty list if Supabase isn't configured, no campaigns are live,
     * or on any failure — queue injection gracefully degrades to pass-through.
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

    companion object {
        private const val TAG = "CampaignRepository"
    }
}