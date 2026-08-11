package com.nikhil.yt.spotify

import com.nikhil.yt.spotify.models.SpotifyImage
import com.nikhil.yt.spotify.models.SpotifyInternalToken
import com.nikhil.yt.spotify.models.SpotifyPaging
import com.nikhil.yt.spotify.models.SpotifyPlaylist
import com.nikhil.yt.spotify.models.SpotifyPlaylistTrack
import com.nikhil.yt.spotify.models.SpotifySavedTrack
import com.nikhil.yt.spotify.models.SpotifySimpleAlbum
import com.nikhil.yt.spotify.models.SpotifySimpleArtist
import com.nikhil.yt.spotify.models.SpotifyToken
import com.nikhil.yt.spotify.models.SpotifyTrack
import com.nikhil.yt.spotify.models.SpotifyUser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

object Spotify {
    private const val API_BASE = "https://api.spotify.com/v1"
    private const val GQL_ENDPOINT = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val MAX_RETRIES = 3
    private const val INITIAL_RETRY_DELAY_MS = 1000L

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        defaultRequest {
            header("Accept", "application/json")
            header("Accept-Language", "en")
            header("User-Agent", SpotifyAuth.USER_AGENT)
        }
        HttpResponseValidator {
            validateResponse { response ->
                if (!response.status.isSuccess()) {
                    val body = try {
                        response.bodyAsText()
                    } catch (_: Exception) {
                        ""
                    }
                    throw SpotifyException(response.status.value, body)
                }
            }
        }
    }

    private val tokenMutex = Mutex()
    private var currentToken: SpotifyInternalToken? = null
    private var spDcCookie: String = ""

    /**
     * Sets the sp_dc cookie obtained from WebView login.
     */
    fun setSpDcCookie(cookie: String) {
        spDcCookie = cookie
        currentToken = null
    }

    /**
     * Gets a valid access token, refreshing if necessary.
     */
    private suspend fun getAccessToken(): String = tokenMutex.withLock {
        val existing = currentToken
        if (existing != null && existing.accessTokenExpirationTimestampMs > System.currentTimeMillis() + 60_000) {
            return existing.accessToken
        }
        if (spDcCookie.isBlank()) {
            throw SpotifyException(401, "Not authenticated. Please log in with Spotify.")
        }
        val result = SpotifyAuth.fetchAccessToken(spDcCookie)
        val token = result.getOrElse { error ->
            throw SpotifyException(401, "Failed to refresh token: ${error.message}")
        }
        currentToken = token
        return token.accessToken
    }

    // ==================== REST API ====================

    suspend fun getCurrentUser(): SpotifyUser = withRetry {
        client.get("$API_BASE/me") {
            bearerAuth(getAccessToken())
        }.body()
    }

    suspend fun getUserPlaylists(limit: Int = 50, offset: Int = 0): SpotifyPaging<SpotifyPlaylist> = withRetry {
        client.get("$API_BASE/me/playlists") {
            bearerAuth(getAccessToken())
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()
    }

    suspend fun getPlaylist(playlistId: String): SpotifyPlaylist = withRetry {
        client.get("$API_BASE/playlists/$playlistId") {
            bearerAuth(getAccessToken())
        }.body()
    }

    suspend fun getPlaylistTracks(
        playlistId: String,
        limit: Int = 100,
        offset: Int = 0
    ): SpotifyPaging<SpotifyPlaylistTrack> = withRetry {
        client.get("$API_BASE/playlists/$playlistId/tracks") {
            bearerAuth(getAccessToken())
            parameter("limit", limit)
            parameter("offset", offset)
            parameter("fields", "items(track(id,name,artists(id,name),album(id,name,images),duration_ms,explicit,uri)),next,total,offset,limit")
        }.body()
    }

    suspend fun getSavedTracks(limit: Int = 50, offset: Int = 0): SpotifyPaging<SpotifySavedTrack> = withRetry {
        client.get("$API_BASE/me/tracks") {
            bearerAuth(getAccessToken())
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()
    }

    suspend fun getAllPlaylistTracks(playlistId: String): List<SpotifyPlaylistTrack> {
        val allTracks = mutableListOf<SpotifyPlaylistTrack>()
        var offset = 0
        while (true) {
            val page = getPlaylistTracks(playlistId, limit = 100, offset = offset)
            allTracks.addAll(page.items)
            if (page.next == null || page.items.isEmpty()) break
            offset += page.items.size
        }
        return allTracks
    }

    suspend fun getAllSavedTracks(): List<SpotifySavedTrack> {
        val allTracks = mutableListOf<SpotifySavedTrack>()
        var offset = 0
        while (true) {
            val page = getSavedTracks(limit = 50, offset = offset)
            allTracks.addAll(page.items)
            if (page.next == null || page.items.isEmpty()) break
            offset += page.items.size
        }
        return allTracks
    }

    // ==================== GraphQL API (fallback) ====================

    suspend fun getPlaylistTracksGraphQL(playlistId: String): List<SpotifyPlaylistTrack> = withRetry {
        val query = """
            query getPlaylist(${"$"}uri: String!) {
                playlistV2(uri: ${"$"}uri) {
                    content {
                        __typename
                        ... on PlaylistItemsPage {
                            totalCount
                            pagingInfo {
                                offset
                                limit
                            }
                            items {
                                ... on PlaylistTrack {
                                    trackV2 {
                                        ... on Track {
                                            uri
                                            name
                                            artists {
                                                items {
                                                    profile {
                                                        name
                                                    }
                                                    uri
                                                }
                                            }
                                            albumOfTrack {
                                                uri
                                                name
                                                coverArt {
                                                    sources {
                                                        url
                                                        width
                                                        height
                                                    }
                                                }
                                            }
                                            duration {
                                                totalMilliseconds
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val response = client.post(GQL_ENDPOINT) {
            bearerAuth(getAccessToken())
            contentType(ContentType.Application.Json)
            setBody(
                GraphQLRequest(
                    operationName = "getPlaylist",
                    query = query,
                    variables = mapOf("uri" to "spotify:playlist:$playlistId")
                )
            )
        }.body<JsonObject>()

        parsePlaylistTracksFromGraphQL(response)
    }

    private fun parsePlaylistTracksFromGraphQL(response: JsonObject): List<SpotifyPlaylistTrack> {
        val data = response["data"]?.jsonObject ?: return emptyList()
        val playlistV2 = data["playlistV2"]?.jsonObject ?: return emptyList()
        val content = playlistV2["content"]?.jsonObject ?: return emptyList()
        val items = content["items"]?.jsonArray ?: return emptyList()

        return items.mapNotNull { item ->
            val trackV2 = item.jsonObject["trackV2"]?.jsonObject ?: return@mapNotNull null
            val trackData = trackV2["data"]?.jsonObject ?: trackV2

            val name = trackData["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val uri = trackData["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val id = uri.removePrefix("spotify:track:")

            val artists = trackData["artists"]?.jsonObject?.get("items")?.jsonArray?.map { artistItem ->
                val artistData = artistItem.jsonObject
                val artistName = artistData["profile"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                    ?: artistData["name"]?.jsonPrimitive?.content
                    ?: ""
                SpotifySimpleArtist(name = artistName)
            } ?: emptyList()

            val albumData = trackData["albumOfTrack"]?.jsonObject
            val album = if (albumData != null) {
                val albumName = albumData["name"]?.jsonPrimitive?.content ?: ""
                val coverArt = albumData["coverArt"]?.jsonObject?.get("sources")?.jsonArray
                val images = coverArt?.map { source ->
                    val url = source.jsonObject["url"]?.jsonPrimitive?.content ?: ""
                    val width = source.jsonObject["width"]?.jsonPrimitive?.content?.toIntOrNull()
                    val height = source.jsonObject["height"]?.jsonPrimitive?.content?.toIntOrNull()
                    SpotifyImage(url = url, width = width, height = height)
                } ?: emptyList()
                SpotifySimpleAlbum(id = "", name = albumName, images = images)
            } else null

            val durationMs = trackData["duration"]?.jsonObject?.get("totalMilliseconds")?.jsonPrimitive?.content?.toIntOrNull() ?: 0

            SpotifyPlaylistTrack(
                track = SpotifyTrack(
                    id = id,
                    name = name,
                    artists = artists,
                    album = album,
                    durationMs = durationMs,
                    uri = uri
                )
            )
        }
    }

    // ==================== Retry logic ====================

    private suspend inline fun <T> withRetry(block: () -> T): T {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                return block()
            } catch (e: SpotifyException) {
                lastException = e
                if (e.code == 401 || e.code == 403) {
                    currentToken = null
                }
                if (attempt < MAX_RETRIES - 1) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl attempt)
                    kotlinx.coroutines.delay(delayMs)
                }
            } catch (e: ClientRequestException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    kotlinx.coroutines.delay(INITIAL_RETRY_DELAY_MS * (1 shl attempt))
                }
            } catch (e: ServerResponseException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    kotlinx.coroutines.delay(INITIAL_RETRY_DELAY_MS * (1 shl attempt))
                }
            } catch (e: HttpRequestTimeoutException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    kotlinx.coroutines.delay(INITIAL_RETRY_DELAY_MS * (1 shl attempt))
                }
            } catch (e: IOException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    kotlinx.coroutines.delay(INITIAL_RETRY_DELAY_MS * (1 shl attempt))
                }
            }
        }
        throw lastException ?: SpotifyException(500, "Unknown error after $MAX_RETRIES retries")
    }

    // ==================== Data classes ====================

    @Serializable
    private data class GraphQLRequest(
        val operationName: String,
        val query: String,
        val variables: Map<String, String>
    )

    class SpotifyException(val code: Int, override val message: String) : Exception(message)
}
