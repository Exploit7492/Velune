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
import java.net.URLEncoder
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
                        (countryCode?.let { "&p_country_code=${URLEncoder.encode(it, "UTF-8")}" } ?: "") +
                        (genre?.let { "&p_genre=${URLEncoder.encode(it, "UTF-8")}" } ?: "")
                )
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("fetchActiveCampaigns: HTTP ${response.code}")
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
     * Fetch ALL currently-live campaigns for the home banner, via
     * `get_live_campaigns_for_banner()` (migration 025, Task 59 Part 3
     * — see handover.md, Mavins-web repo). **Not** the same function
     * [fetchActiveCampaigns] above calls — that one
     * (`get_trending_campaigns`) is a scored/limited/single-winner
     * function still used by the queue-slot mechanic (Part 1/2's own
     * concern, deliberately untouched by Part 3); this one returns the
     * complete, unranked set of live campaigns, no `LIMIT`, no score
     * ordering, matching this surface's own "no competition, all is
     * accommodated for" spec.
     *
     * **Deliberately does NOT call [CampaignUrlResolver.resolve] (no
     * live YouTube metadata round-trip per row)** — that resolver
     * exists because [fetchActiveCampaigns]'s own RPC never returns
     * display metadata at all, only ids. `get_live_campaigns_for_banner`
     * is different: it already joins and returns `artist_name`/
     * `track_title`/`cover_url` directly from the DB (see the
     * migration's own `SELECT`), specifically so this surface — which
     * must show *every* live campaign, not a capped top-N — doesn't
     * need one YouTube API call per row just to render a title and
     * thumbnail. [CampaignCard.songId] is still computed here (needed
     * for actual playback once a card is tapped), just not resolved
     * eagerly against YouTube for every row up front. This matches
     * [CampaignCard]'s own field doc comments, which already describe
     * `title`/`artist`/`thumbnailUrl` as "resolved from YouTube **or
     * fallback to** track_title/artist_name/cover_url" — this function
     * is what actually uses that fallback path; nothing used it before
     * this session.
     *
     * `totalStreams`/`trendingScore`/`playCount` are always `0` here —
     * not omitted by accident: the underlying RPC doesn't return them
     * at all (by design, per that migration's own comment — this
     * surface must never expose a competitive/ranking signal). Part 3b
     * (Velune UI rebuild, not started) must not render these fields
     * for a banner card, matching the already-resolved "never reveal
     * the live count or any per-card competitive number" rule.
     * `ctaLabel` is left at its data-class default ("Play") rather
     * than the stage-based Discover/Trending/Hot/Viral/Charting
     * ladder [parseTrendingRows] below computes — Part 3's own spec
     * explicitly flagged that ladder as dead, ranking-adjacent data
     * that should never reach a UI, and this is the one place that
     * decision is actually enforced at the data layer, not left to the
     * UI layer to remember not to render it.
     *
     * `certified` reuses the exact same stage-based check
     * [parseTrendingRows] already uses (`branching`/`full_bloom` →
     * true) — Part 3's own spec explicitly said to keep this signal
     * (a real moderation/trust marker, not a competitive one) when
     * rebuilding the banner, not remove it along with the
     * ranking-adjacent fields above.
     *
     * @return every currently-live, unpaused, non-completed campaign,
     *   in the RPC's own stable-but-meaningless id order (row order is
     *   NOT a ranking signal — see the migration's own comment; the
     *   Part 3b UI owns shuffle/rotation order entirely). Empty list on
     *   any failure — same fail-soft posture as every other fetch in
     *   this file, never a thrown exception reaching the caller.
     */
    suspend fun fetchLiveCampaignsForBanner(): List<CampaignCard> = withContext(Dispatchers.IO) {
    val (url, anonKey) = config() ?: return@withContext emptyList()
    try {
        val request = Request.Builder()
            .url("$url/rest/v1/track_campaigns?select=id,source_url,artist_id,total_streams,current_stage&is_active=eq.true&is_paused=eq.false&current_stage=neq.completed&current_stage=neq.cancelled")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.tag(TAG).w("fetchLiveCampaignsForBanner: HTTP ${response.code}")
                return@use emptyList()
            }
            val body = response.body?.string().orEmpty()
            parseLiveBannerRows(body)
        }
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "fetchLiveCampaignsForBanner failed")
        emptyList()
    }
}

    private fun parseLiveBannerRows(body: String): List<CampaignCard> {
        val array = try {
            JSONArray(body)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Malformed live-banner response")
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            val row = array.optJSONObject(i) ?: return@mapNotNull null
            val campaignId = row.optString("id", "")
            if (campaignId.isBlank()) return@mapNotNull null

            val sourceUrl = row.optString("source_url", "")
            val resolvedSongId = row.optString("resolved_song_id", "").takeIf { it.isNotBlank() }
            // Same resolution order fetchNextCampaignForQueueSlot already
            // establishes: a stored resolved id wins if present, falls
            // back to extracting one from the raw source URL otherwise.
            // A null here (both fields empty/unparseable) means this row
            // can't be played if tapped -- excluded via mapNotNull below,
            // same "silently drop, don't crash the whole list over one
            // bad row" posture as parseTrendingRows.
            val songId = resolvedSongId ?: CampaignUrlResolver.extractVideoId(sourceUrl)
            if (songId == null) {
                Timber.tag(TAG).w("parseLiveBannerRows: could not extract a video id for campaign $campaignId")
                return@mapNotNull null
            }

            val currentStage = row.optString("current_stage", "planting")
            val trackTitle = row.optString("track_title", "").takeIf { it.isNotBlank() }
            val artistName = row.optString("artist_name", "").takeIf { it.isNotBlank() }
            val coverUrl = row.optString("cover_url", "")

            CampaignCard(
                id = campaignId,
                songId = songId,
                trackId = row.optString("track_id", "").takeIf { it.isNotBlank() } ?: "",
                artistId = row.optString("artist_id", "").takeIf { it.isNotBlank() } ?: "",
                // Fallback path CampaignCard's own field docs already
                // anticipated ("resolved from YouTube or fallback to
                // track_title/artist_name/cover_url") -- this is the
                // first call site to actually use it, per this
                // function's own header comment on why no live YouTube
                // resolution happens here.
                title = trackTitle ?: "Untitled",
                artist = artistName ?: "Unknown Artist",
                thumbnailUrl = coverUrl,
                totalStreams = 0L,
                trendingScore = 0.0,
                geographicTier = "local",
                currentStage = currentStage,
                certified = when (currentStage) {
                    "branching", "full_bloom" -> true
                    else -> false
                },
                isLive = true, // the RPC's own WHERE clause guarantees this
                playCount = 0L,
                ctaLabel = "Play",
            )
        }
    }

    /**
     * Fetch ONE campaign for a single queue-slot injection point, via
     * the genre-locked, fair-rotation `get_next_campaign_for_queue_slot`
     * RPC (migration 023, Task 59 Part 1/2a — see handover.md, Mavins-
     * web repo).
     *
     * Deliberately called once PER injection point by
     * [com.nikhil.yt.playback.queues.CampaignInjectedQueue] — never
     * pre-fetched as a batch and locally rotated. That per-call
     * discipline is what gives this function's own RPC its real,
     * cross-listener fairness meaning: the RPC does an atomic
     * "least-recently-served eligible campaign, marked served, in one
     * transaction" pick on every single call — pre-fetching several at
     * once up front would mark campaigns as served before they've
     * actually reached a played slot, corrupting that bookkeeping for
     * every other listener's concurrent queue.
     *
     * JSON POST body, not query-string params — the same safe pattern
     * [recordCampaignStream] below already established, and now also
     * what [fetchActiveCampaigns] above uses after this session's own
     * fix (that function previously string-interpolated `genre`/
     * `countryCode` directly into a URL without encoding — a real, live
     * bug for any value containing a URL-special character, "R&B"
     * being one of this app's own actual genres; fixed this session via
     * `URLEncoder.encode`, matching this codebase's own established
     * convention in `MainActivity.kt`/`DiscordOAuthRepository.kt`).
     * This function was written after that fix and never had the bug.
     *
     * @return a playable [MediaItem] for the selected campaign, or
     *   `null` if no eligible campaign exists for [genre] right now
     *   (a normal, expected outcome — a thin genre, or one where every
     *   eligible campaign was already served very recently — not an
     *   error) or if resolution failed for any reason.
     */
    suspend fun fetchNextCampaignForQueueSlot(genre: String): MediaItem? = withContext(Dispatchers.IO) {
        val (url, anonKey) = config() ?: return@withContext null
        try {
            val payload = JSONObject().apply {
                put("p_genre", genre)
            }
            val request = Request.Builder()
                .url("$url/rest/v1/rpc/get_next_campaign_for_queue_slot")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("fetchNextCampaignForQueueSlot: HTTP ${response.code}")
                    return@use null
                }
                val body = response.body?.string().orEmpty()
                val array = try {
                    JSONArray(body)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "fetchNextCampaignForQueueSlot: malformed response")
                    return@use null
                }
                // Empty array = no eligible campaign for this genre right
                // now (the RPC's own fail-closed/no-match behavior, per
                // migration 023) -- not an error, just "skip this slot."
                if (array.length() == 0) return@use null
                val row = array.optJSONObject(0) ?: return@use null

                val sourceUrl = row.optString("source_url", "")
                val resolvedSongId = row.optString("resolved_song_id", "").takeIf { it.isNotBlank() }
                // Same resolution order CampaignUrlResolver already
                // establishes for the trending-list path: a stored
                // resolved id wins if present, falls back to extracting
                // one from the raw source URL otherwise.
                val videoId = resolvedSongId ?: CampaignUrlResolver.extractVideoId(sourceUrl)
                if (videoId == null) {
                    Timber.tag(TAG).w("fetchNextCampaignForQueueSlot: could not extract a video id")
                    return@use null
                }

                val result = YouTube.queue(listOf(videoId)).getOrNull()
                val metadata = result?.firstOrNull()?.toMediaMetadata()
                metadata?.toMediaItem()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "fetchNextCampaignForQueueSlot failed for genre=$genre")
            null
        }
    }

    /**
     * Fetch the reviewed portion of `campaign_genre_tile_mapping`
     * (migration 024, Task 59 Part 2b-a — Mavins-web repo) as a
     * `tile_title -> mapped_genre_id` map, `mapped_genre_id` itself
     * possibly `null` for a tile an admin has confirmed is genuinely a
     * mood/non-genre (a deliberate, reviewed decision — see the
     * migration's own `is_reviewed` column comment for why that's a
     * different state from "not yet reviewed," and why this function
     * only ever reads `is_reviewed = true` rows: an unreviewed row's
     * `suggested_genre_id` is a machine guess, never live targeting
     * data, per Round 6's own core invariant — this function doesn't
     * even fetch that column).
     *
     * Direct Supabase REST read (`GET .../rest/v1/campaign_genre_tile_mapping`),
     * not a call through Mavins-web's own API — this table's RLS
     * explicitly grants public `SELECT` for exactly this purpose (the
     * migration's own comment: "Velune's own client-side cache...
     * reads this table directly"). Uses only [BuildConfig.SUPABASE_URL]
     * / [BuildConfig.SUPABASE_ANON_KEY], already configured for every
     * other call in this file — deliberately does NOT need a new
     * Mavins-web API host added to this app's build config, unlike
     * [ingestGenreTile] would (not built this session — see Task 59
     * Part 2b-b's own handover.md note for why that's a separate,
     * later piece of work with a real open question of its own: what
     * host Velune should call for a Next.js route that isn't Supabase
     * itself).
     *
     * Callers are expected to fetch this periodically and cache the
     * result client-side (the migration comment's own intended
     * pattern) rather than call it fresh on every single genre-tile
     * tap — not implemented here, since Part 2b-b (not this part) is
     * what actually calls this from a real tap, and can hold its own
     * opinion about caching/refresh cadence once it exists.
     *
     * @return an empty map on any failure (network, malformed
     *   response, missing config) — callers should treat that
     *   identically to "no confirmed mapping for this tile," the same
     *   fail-closed default this whole feature is built around, not as
     *   a distinguishable error state.
     */
    suspend fun fetchGenreTileMapping(): Map<String, String?> = withContext(Dispatchers.IO) {
        val (url, anonKey) = config() ?: return@withContext emptyMap()
        try {
            val request = Request.Builder()
                .url("$url/rest/v1/campaign_genre_tile_mapping?is_reviewed=eq.true&select=tile_title,mapped_genre_id")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("fetchGenreTileMapping: HTTP ${response.code}")
                    return@use emptyMap()
                }
                val body = response.body?.string().orEmpty()
                val array = try {
                    JSONArray(body)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "fetchGenreTileMapping: malformed response")
                    return@use emptyMap()
                }
                (0 until array.length()).mapNotNull { i ->
                    val row = array.optJSONObject(i) ?: return@mapNotNull null
                    val tileTitle = row.optString("tile_title", "").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    // org.json has no isNull-aware optString overload that
                    // distinguishes "key absent" from "key present, JSON
                    // null" the way we need here (a confirmed non-genre
                    // tile's mapped_genre_id is a real, meaningful JSON
                    // null, not a missing field) — check explicitly.
                    val mappedGenreId = if (row.isNull("mapped_genre_id")) {
                        null
                    } else {
                        row.optString("mapped_genre_id", "").takeIf { it.isNotBlank() }
                    }
                    tileTitle to mappedGenreId
                }.toMap()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "fetchGenreTileMapping failed")
            emptyMap()
        }
    }

    /**
     * Ingest an unknown genre-tile title into the Mavins-web
     * admin-review pipeline (`POST /api/campaigns/genre-tile-mapping/
     * ingest`, that repo's own route — see its header comment for the
     * full upsert-semantics contract this call relies on: a new title
     * inserts unreviewed with a suggested match, a repeat title just
     * bumps `seen_count`, an already-reviewed row is never touched).
     * Fire-and-forget by design, matching [recordCampaignStream]'s own
     * shape below — no return value, no error surfaced to the caller
     * or the user; this is telemetry that happens to seed a table, not
     * a request anything blocks on. Callers (Task 59 Part 2b-b,
     * the UI/nav chain, not yet built) are expected to call this only
     * for a tile title [fetchGenreTileMapping]'s own cached read doesn't
     * already have an entry for — this function itself doesn't check
     * that, since it has no access to that cache.
     *
     * Uses [BuildConfig.MAVINS_API_URL], not [BuildConfig.SUPABASE_URL]
     * — this hits Mavins-web's own Next.js route directly (which itself
     * writes to Supabase via its service-role admin client), not
     * Supabase's REST API the way every other function in this file
     * does. Different host, deliberately no `apikey`/`Authorization`
     * headers — that route is explicitly public with no auth (see its
     * own header comment for why), unlike every Supabase call above.
     */
    suspend fun ingestGenreTile(tileTitle: String) = withContext(Dispatchers.IO) {
        val host = BuildConfig.MAVINS_API_URL.trimEnd('/')
        if (host.isBlank()) {
            Timber.tag(TAG).w("ingestGenreTile: MAVINS_API_URL not configured")
            return@withContext
        }
        try {
            val payload = JSONObject().apply {
                put("tileTitle", tileTitle)
            }
            val request = Request.Builder()
                .url("$host/api/campaigns/genre-tile-mapping/ingest")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("ingestGenreTile: HTTP ${response.code}")
                } else {
                    Timber.tag(TAG).d("ingestGenreTile: ingested '$tileTitle'")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "ingestGenreTile failed for '$tileTitle'")
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
                Timber.tag(TAG).e(e, "Failed to resolve media item for ${campaign.songId}")
                null
            }
        }
    }

    /**
     * Task 49 Part b-b-i (handover.md, Mavins-web repo) — Kotlin
     * wrapper for the `ensure_device_listener` RPC (migration 028).
     * Idempotently upserts a minimal `public.users` row keyed on
     * [deviceId] (`role = 'listener'`, a synthetic non-deliverable
     * `.internal` email — see that migration's own header for why: a
     * device-ID listener never logs in and has no real email, by
     * design) so `listener_play_events.listener_id`'s foreign key
     * (Task 49 Part a) has a real row to point at for the overwhelming
     * common case — a real Velune listener, identified only by device
     * ID, no login.
     *
     * **This function alone does not fix anything — it is only called
     * from where Task 49 Part b-b-ii decides to call it (deferred,
     * not built in this same part), which must be the real, persisted
     * [deviceId] from `MusicService.kt`'s own
     * `getOrCreateCampaignDeviceId()`, specifically and only that
     * value — never a one-off fallback UUID
     * ([recordCampaignStream]'s own `userId ?: UUID.randomUUID()...`
     * default below is exactly the case that must NOT reach this
     * function, per migration 028's own explicit warning against
     * flooding `public.users` with a throwaway row per unmatched
     * play).** Deliberately not called from within this file at all —
     * that decision belongs to whichever call site in `MusicService.kt`
     * actually knows which kind of ID it's holding, this file has no
     * way to tell the two apart.
     *
     * @return the same [deviceId] echoed back (the RPC always returns
     *   its own input UUID on success — confirmed against migration
     *   028's own `RETURN p_device_id;` before relying on it, not
     *   assumed), or `null` if the call failed for any reason. A
     *   caller that gets `null` back should treat this the same as
     *   any other best-effort network call in this file — log and
     *   move on, never block playback on it.
     */
    suspend fun ensureDeviceListener(deviceId: String): String? = withContext(Dispatchers.IO) {
        val (url, anonKey) = config() ?: return@withContext null
        try {
            val payload = JSONObject().apply {
                put("p_device_id", deviceId)
            }
            val request = Request.Builder()
                .url("$url/rest/v1/rpc/ensure_device_listener")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("ensureDeviceListener: HTTP ${response.code}")
                    return@use null
                }
                // Postgres RETURNS UUID via PostgREST comes back as a
                // bare JSON string (not an array-of-objects the way
                // RETURNS TABLE functions elsewhere in this file do) —
                // e.g. "\"550e8400-...\"" -- strip the surrounding
                // quotes rather than reaching for a JSON array parser
                // that would fail on this shape.
                val body = response.body?.string().orEmpty().trim().trim('"')
                body.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "ensureDeviceListener failed for deviceId=$deviceId")
            null
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
                    Timber.tag(TAG).w("recordCampaignStream: HTTP ${response.code}")
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
     *
     * Task 60 (handover.md) — no longer has any call sites as of this
     * fix (confirmed via grep: both of its former callers,
     * `CampaignCardSection.kt` and `HomeScreen.kt`, were removed —
     * they were firing duplicate/broken writes for the same tap that
     * `MusicService.kt`'s own direct `recordCampaignStream()` call
     * already covers correctly). Left in place rather than deleted —
     * removing a whole function felt like a bigger, less reversible
     * call than this fix's own narrow scope needed, same reasoning
     * Task 59 Part 2a used for a similar now-orphaned function in this
     * same file. Flagging here as a real cleanup candidate for a
     * future pass, not silently leaving the doc comment's old
     * "kept for backward compatibility with existing call sites"
     * claim standing now that it's no longer true.
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
            val campaignId = row.optString("id", "")
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
