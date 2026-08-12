package com.nikhil.yt.canvas.providers

import io.ktor.client.statement.bodyAsText

import com.nikhil.yt.canvas.CanvasArtwork
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Apple Music canvas provider.
 * Fetches Apple Music-style canvas videos using a token extracted from
 * beta.music.apple.com. The token is refreshed periodically.
 */
object AppleMusicCanvasProvider {
    private const val TOKEN_URL = "https://beta.music.apple.com/"
    private const val API_BASE = "https://amp-api.music.apple.com/v1/catalog/us/search"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)
            expectSuccess = false
        }
    }

    @Serializable
    private data class AppleMusicTokenResponse(
        val token: String? = null
    )

    @Serializable
    private data class AppleMusicSearchResponse(
        val results: AppleMusicResults? = null
    )

    @Serializable
    private data class AppleMusicResults(
        val songs: AppleMusicSongs? = null
    )

    @Serializable
    private data class AppleMusicSongs(
        val data: List<AppleMusicSong> = emptyList()
    )

    @Serializable
    private data class AppleMusicSong(
        val id: String = "",
        val attributes: AppleMusicAttributes? = null,
        val relationships: AppleMusicRelationships? = null,
    )

    @Serializable
    private data class AppleMusicAttributes(
        val name: String = "",
        val artistName: String = "",
        val artwork: AppleMusicArtwork? = null,
        val previews: List<AppleMusicPreview> = emptyList(),
    )

    @Serializable
    private data class AppleMusicArtwork(
        val url: String = "",
        val width: Int? = null,
        val height: Int? = null,
    )

    @Serializable
    private data class AppleMusicPreview(
        val url: String = "",
    )

    @Serializable
    private data class AppleMusicRelationships(
        val albums: AppleMusicAlbums? = null,
    )

    @Serializable
    private data class AppleMusicAlbums(
        val data: List<AppleMusicAlbum> = emptyList(),
    )

    @Serializable
    private data class AppleMusicAlbum(
        val id: String = "",
        val attributes: AppleMusicAttributes? = null,
    )

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val ttlMs = 60_000L

    private var currentToken: String? = null
    private var tokenExpiresAt: Long = 0
    private val tokenTtlMs = 300_000L

    suspend fun getBySongArtist(song: String, artist: String): CanvasArtwork? {
        val key = cacheKey(song, artist)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val token = getToken() ?: return null

        val response = runCatching {
            client.get(API_BASE) {
                header("Authorization", "Bearer $token")
                header("Origin", "https://music.apple.com")
                parameter("term", "$song $artist")
                parameter("types", "songs")
                parameter("limit", "5")
                parameter("with", "lyrics")
            }
        }.getOrNull()

        val value = when (response?.status) {
            HttpStatusCode.OK -> {
                val body = runCatching { response.body<AppleMusicSearchResponse>() }.getOrNull()
                parseResponse(body, song, artist)
            }
            else -> null
        }

        cache[key] = CacheEntry(value, System.currentTimeMillis() + ttlMs)
        return value
    }

    private suspend fun getToken(): String? {
        if (currentToken != null && tokenExpiresAt > System.currentTimeMillis()) {
            return currentToken
        }

        val response = runCatching {
            client.get(TOKEN_URL) {
                header("Accept", "text/html")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
        }.getOrNull()

        val body = response?.bodyAsText() ?: return null

        // Extract token from the HTML page
        // The token is in a script tag or meta tag
        val token = extractTokenFromHtml(body)
        if (token != null) {
            currentToken = token
            tokenExpiresAt = System.currentTimeMillis() + tokenTtlMs
        }
        return token
    }

    private fun extractTokenFromHtml(html: String): String? {
        // Try to find the token in various patterns
        val patterns = listOf(
            Regex("""token\s*[:=]\s*["']([A-Za-z0-9\-_]+)["']"""),
            Regex("""accessToken\s*[:=]\s*["']([A-Za-z0-9\-_]+)["']"""),
            Regex("""media-api-token["']\s*content=["']([A-Za-z0-9\-_]+)["']"""),
        )

        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        // Fallback: look for a long JWT-like string
        val jwtPattern = Regex("""eyJ[A-Za-z0-9\-_]+\.eyJ[A-Za-z0-9\-_]+\.[A-Za-z0-9\-_]+""")
        val jwtMatch = jwtPattern.find(html)
        return jwtMatch?.value
    }

    private fun parseResponse(
        response: AppleMusicSearchResponse?,
        song: String,
        artist: String,
    ): CanvasArtwork? {
        val songs = response?.results?.songs?.data ?: return null

        val normalizedSong = song.trim().lowercase(Locale.ROOT)
        val normalizedArtist = artist.trim().lowercase(Locale.ROOT)

        // Find best match
        val match = songs.firstOrNull { item ->
            val attr = item.attributes ?: return@firstOrNull false
            val itemSong = attr.name.trim().lowercase(Locale.ROOT)
            val itemArtist = attr.artistName.trim().lowercase(Locale.ROOT)
            itemSong == normalizedSong || itemArtist == normalizedArtist
        } ?: songs.firstOrNull()

        val attr = match?.attributes ?: return null
        val preview = attr.previews.firstOrNull()?.url
        val artwork = attr.artwork?.url?.replace("{w}", "640")?.replace("{h}", "640")

        return CanvasArtwork(
            name = attr.name,
            artist = attr.artistName,
            static = artwork,
            videoUrl = preview,
        )
    }

    private fun cacheKey(song: String, artist: String): String {
        return "apple|${song.trim().lowercase(Locale.ROOT)}|${artist.trim().lowercase(Locale.ROOT)}"
    }
}
