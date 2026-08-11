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
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Artist video canvas provider.
 * Fetches artist video backgrounds.
 */
object ArtistVideoCanvasProvider {
    private const val BASE_URL = "https://artwork-archivetune.koiiverse.cloud/"

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
    private data class ArtistVideoResponse(
        val url: String? = null,
        val videoUrl: String? = null,
        val static: String? = null,
        val animated: String? = null,
    )

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val ttlMs = 60_000L

    suspend fun getBySongArtist(song: String, artist: String): CanvasArtwork? {
        val key = cacheKey(song, artist)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val response = runCatching {
            client.get(BASE_URL) {
                parameter("q", "$song $artist")
                parameter("artist", artist)
                parameter("song", song)
            }
        }.getOrNull()

        val value = when (response?.status) {
            HttpStatusCode.OK -> {
                val body = runCatching { response.body<ArtistVideoResponse>() }.getOrNull()
                body?.let {
                    CanvasArtwork(
                        name = song,
                        artist = artist,
                        static = it.static,
                        animated = it.animated,
                        videoUrl = it.videoUrl ?: it.url
                    )
                }
            }
            else -> null
        }

        cache[key] = CacheEntry(value, System.currentTimeMillis() + ttlMs)
        return value
    }

    private fun cacheKey(song: String, artist: String): String {
        return "av|${song.trim().lowercase(Locale.ROOT)}|${artist.trim().lowercase(Locale.ROOT)}"
    }
}
