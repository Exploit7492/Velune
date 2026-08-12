package com.nikhil.yt.applecanvas

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

internal object AppleMusicTokenProvider {
    private const val TOKEN_URL = "https://beta.music.apple.com/"
    private const val TOKEN_TTL_MS = 300_000L

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(ContentEncoding) { gzip(); deflate() }
            expectSuccess = false
        }
    }

    private var currentToken: String? = null
    private var tokenExpiresAt: Long = 0

    suspend fun getToken(): String? {
        if (currentToken != null && tokenExpiresAt > System.currentTimeMillis()) return currentToken

        val response = runCatching {
            client.get(TOKEN_URL) {
                header("Accept", "text/html")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
        }.getOrNull()

        val body = response?.let { runCatching { it.bodyAsText() }.getOrNull() } ?: return null
        val token = extractTokenFromHtml(body)
        if (token != null) {
            currentToken = token
            tokenExpiresAt = System.currentTimeMillis() + TOKEN_TTL_MS
        }
        return token
    }

    private fun extractTokenFromHtml(html: String): String? {
        val patterns = listOf(
            Regex("""token\s*[:=]\s*["']([A-Za-z0-9\-_]+)["']"""),
            Regex("""accessToken\s*[:=]\s*["']([A-Za-z0-9\-_]+)["']"""),
            Regex("""media-api-token["']\s*content=["']([A-Za-z0-9\-_]+)["']"""),
        )
        for (p in patterns) p.find(html)?.let { return it.groupValues[1] }
        return Regex("""eyJ[A-Za-z0-9\-_]+\.eyJ[A-Za-z0-9\-_]+\.[A-Za-z0-9\-_]+""").find(html)?.value
    }
}
