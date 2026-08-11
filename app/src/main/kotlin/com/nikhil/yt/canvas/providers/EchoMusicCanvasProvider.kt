package com.nikhil.yt.canvas.providers

import com.nikhil.yt.canvas.CanvasArtwork
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Echo Music canvas provider.
 * Fetches canvas videos from the Echo Music community endpoint.
 */
object EchoMusicCanvasProvider {
    private const val BASE_URL = "https://canvas.echomusic.fun/canvas.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 18_000
                socketTimeoutMillis = 18_000
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
    private data class EchoCanvasManifest(
        val items: List<EchoCanvasItem> = emptyList()
    )

    @Serializable
    private data class EchoCanvasItem(
        val song: String,
        val artist: String,
        val url: String
    )

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val ttlMs = 60_000L
    private var manifest: EchoCanvasManifest? = null
    private var manifestLoadedAt: Long = 0
    private val manifestTtlMs = 300_000L

    suspend fun getBySongArtist(song: String, artist: String): CanvasArtwork? {
        val key = cacheKey(song, artist)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val currentManifest = loadManifest()
        val normalizedSong = song.trim().lowercase(Locale.ROOT)
        val normalizedArtist = artist.trim().lowercase(Locale.ROOT)

        val match = currentManifest?.items?.find { item ->
            item.song.trim().lowercase(Locale.ROOT) == normalizedSong &&
            item.artist.trim().lowercase(Locale.ROOT) == normalizedArtist
        }

        val value = match?.let {
            CanvasArtwork(
                name = it.song,
                artist = it.artist,
                videoUrl = it.url
            )
        }

        cache[key] = CacheEntry(value, System.currentTimeMillis() + ttlMs)
        return value
    }

    private suspend fun loadManifest(): EchoCanvasManifest? {
        if (manifest != null && manifestLoadedAt + manifestTtlMs > System.currentTimeMillis()) {
            return manifest
        }
        val response = runCatching {
            client.get(BASE_URL)
        }.getOrNull()
        val loaded = when (response?.status) {
            HttpStatusCode.OK -> runCatching { response.body<EchoCanvasManifest>() }.getOrNull()
            else -> null
        }
        manifest = loaded
        manifestLoadedAt = System.currentTimeMillis()
        return loaded
    }

    private fun cacheKey(song: String, artist: String): String {
        return "echo|${song.trim().lowercase(Locale.ROOT)}|${artist.trim().lowercase(Locale.ROOT)}"
    }
}
