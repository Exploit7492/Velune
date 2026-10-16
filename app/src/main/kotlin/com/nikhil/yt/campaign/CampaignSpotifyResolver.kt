/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.campaign

import com.nikhil.yt.innertube.models.SongItem
import com.nikhil.yt.spotify.SpotifyMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Resolves a Spotify campaign link to a playable YouTube video id, at
 * runtime — the Spotify counterpart to [CampaignUrlResolver]'s YouTube
 * branch.
 *
 * Why this exists: the seed data is now repaired, so every stored row has
 * a real track link *and* a pre-resolved `resolved_song_id`. This path is
 * for the case the data repair cannot cover — a campaign created from the
 * web promote form with a Spotify URL that was never resolved ahead of
 * time. Without it such a row is dropped by the queue RPC (correctly: it
 * has no `resolved_song_id`), so the advertiser's link never plays.
 *
 * Metadata comes from Spotify's **public embed page**, which needs no
 * credential and is the same source the offline repair used:
 *  - a `track` link exposes the track's own title/artists;
 *  - an `artist` or `album` link exposes a `trackList`, whose first entry
 *    is the entity's headline track.
 * The title/artist is then handed to [SpotifyMapper]'s existing YouTube
 * search — no second matcher, so runtime and offline resolution agree.
 */
object CampaignSpotifyResolver {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** A resolved Spotify entity reduced to what playback needs. */
    data class Track(val title: String, val artist: String)

    /**
     * Resolves [target] to a YouTube [SongItem], or null if the entity
     * cannot be read or has no confident YouTube match. Null is the safe
     * answer: the caller drops the row rather than pointing a paid
     * campaign at a stranger's video.
     */
    suspend fun resolve(target: SpotifyLink.Target): SongItem? {
        val track = readTrack(target) ?: return null
        if (track.title.isBlank()) return null
        return SpotifyMapper.findYouTubeMatch(track.title, track.artist)
    }

    /**
     * Reads the headline track (title + artist) for a Spotify entity.
     * Visible for testing the JSON reduction without a network call.
     */
    suspend fun readTrack(target: SpotifyLink.Target): Track? {
        val embedEntity = fetchEmbedEntity(SpotifyLink.embedUrl(target))
        if (embedEntity != null) {
            trackFromEntity(embedEntity)?.let { return it }
        }
        // oEmbed exposes only a title, no artist — enough to search on
        // for a track link, and a worse-but-nonempty answer than nothing
        // for the artist/album case if the embed page's shape ever
        // changes. Deliberately last, never first.
        val fallbackTitle = fetchOEmbedTitle(SpotifyLink.oEmbedUrl(target))
        return fallbackTitle?.takeIf { it.isNotBlank() }?.let { Track(it, "") }
    }

    /**
     * Reduces a Spotify embed `entity` object to its headline track.
     * Pure (no I/O) so the shape handling is testable directly.
     */
    fun trackFromEntity(entity: JSONObject): Track? {
        val trackList = entity.optJSONArray("trackList")
        if (trackList != null && trackList.length() > 0) {
            val first = trackList.optJSONObject(0) ?: return null
            val title = first.optString("title").normalized()
            val artist = first.optString("subtitle").normalized()
            if (title.isNotBlank()) return Track(title, artist)
        }

        val title = entity.optString("name").ifBlank { entity.optString("title") }.normalized()
        if (title.isBlank()) return null
        return Track(title, artistNames(entity))
    }

    private fun artistNames(entity: JSONObject): String {
        val artists = entity.optJSONArray("artists") ?: return ""
        return (0 until artists.length())
            .mapNotNull { artists.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
            .joinToString(", ") { it.normalized() }
    }

    /**
     * Spotify separates a multi-artist `subtitle` with a **non-breaking**
     * space (U+00A0), not a regular one — observed live, e.g.
     * `"Carla's Dreams,\u00a0Delia"`. Left as-is it defeats any
     * `", "`-based artist split downstream, so it is normalised at the
     * point of reading. Collapsing runs also guards the search query
     * against stray doubled spaces.
     */
    private fun String.normalized(): String =
        replace('\u00A0', ' ').replace('\u202F', ' ').trim().replace(Regex("\\s+"), " ")

    private suspend fun fetchEmbedEntity(url: String): JSONObject? {
        val html = fetch(url) ?: return null
        return try {
            JSONObject(extractNextData(html))
                .optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("state")
                ?.optJSONObject("data")
                ?.optJSONObject("entity")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Unreadable Spotify embed JSON")
            null
        }
    }

    /** The `__NEXT_DATA__` JSON blob every Spotify embed page carries. */
    private fun extractNextData(html: String): String? {
        val marker = html.indexOf("id=\"__NEXT_DATA__\"")
        if (marker < 0) return null
        val open = html.indexOf('>', marker)
        if (open < 0) return null
        val close = html.indexOf("</script>", open)
        if (close < 0) return null
        return html.substring(open + 1, close)
    }

    private suspend fun fetchOEmbedTitle(url: String): String? {
        val body = fetch(url) ?: return null
        return try {
            JSONObject(body).optString("title").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Unreadable Spotify oEmbed JSON")
            null
        }
    }

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "en")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("Spotify fetch %s -> HTTP %d", url, response.code)
                    null
                } else {
                    response.body?.string()
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Spotify fetch failed: $url")
            null
        }
    }

    private const val TAG = "CampaignSpotifyResolver"
}
