package com.nikhil.yt.branding

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.nikhil.yt.constants.AppIconConfigFetchedAtKey
import com.nikhil.yt.constants.AppIconConfigJsonKey
import com.nikhil.yt.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the branding config an admin publishes from the dashboard (see
 * [AppIconConfig]) and exposes it to the rest of the app as a single
 * [StateFlow], so every screen that renders app branding (About screen,
 * splash, notifications) reflects the same, currently-active config without
 * each needing its own fetch/cache logic.
 *
 * The remote endpoint is expected to serve a single JSON document shaped
 * like [AppIconConfig] (admin-dashboard-owned; not defined by this repo).
 * Network failures, malformed JSON, and a config that hasn't been set up
 * yet all fall back to [AppIconConfig.EMPTY] — every consumer already
 * treats a null per-slot asset as "use the bundled default", so there is no
 * failure mode here that can leave the app without an icon.
 */
@Singleton
class AppIconRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }

    private val _config = MutableStateFlow(AppIconConfig.EMPTY)
    val config: StateFlow<AppIconConfig> = _config.asStateFlow()

    companion object {
        private const val TAG = "AppIconRepository"

        // Owned/hosted by the admin dashboard — not part of this repo. The
        // dashboard's upload flow validates each asset against the sizes in
        // AppIconSlot before publishing here, so anything this endpoint
        // returns is expected to already satisfy AppIconAsset.meetsMinimumSize.
        private const val REMOTE_CONFIG_URL = "https://admin.velune.app/api/branding/icon-config"

        // Re-check for a new config at most this often; the cached value from
        // DataStore is used for every launch in between so branding renders
        // immediately instead of waiting on a network round trip.
        private const val MIN_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6h
    }

    /** Loads the cached config (instant) then refreshes from the network if it's stale. */
    suspend fun initialize() {
        loadCached()
        val lastFetched = context.dataStore.data.map { it[AppIconConfigFetchedAtKey] ?: 0L }.first()
        if (System.currentTimeMillis() - lastFetched >= MIN_REFRESH_INTERVAL_MS) {
            refresh()
        }
    }

    private suspend fun loadCached() {
        val cachedJson = context.dataStore.data.map { it[AppIconConfigJsonKey] }.first()
        if (cachedJson.isNullOrBlank()) return
        runCatching { json.decodeFromString(AppIconConfig.serializer(), cachedJson) }
            .onSuccess { _config.value = it }
            .onFailure { Timber.tag(TAG).w(it, "Cached branding config was invalid, ignoring") }
    }

    /** Forces a network fetch — call from the admin-facing "publish now" action or pull-to-refresh, if wired up. */
    suspend fun refresh(): Result<AppIconConfig> {
        val response = runCatching { client.get(REMOTE_CONFIG_URL) }
        val body = response.getOrNull()?.let { runCatching { it.bodyAsText() }.getOrNull() }
        if (body.isNullOrBlank()) {
            Timber.tag(TAG).d("No branding config fetched, keeping current config")
            return Result.failure(response.exceptionOrNull() ?: IllegalStateException("Empty branding response"))
        }

        return runCatching { json.decodeFromString(AppIconConfig.serializer(), body) }
            .onSuccess { parsed ->
                _config.value = parsed
                context.dataStore.edit {
                    it[AppIconConfigJsonKey] = body
                    it[AppIconConfigFetchedAtKey] = System.currentTimeMillis()
                }
                Timber.tag(TAG).d("Applied branding config v${parsed.version}")
            }
            .onFailure { Timber.tag(TAG).w(it, "Malformed branding config, keeping current config") }
    }

    fun urlFor(slot: AppIconSlot): String? = _config.value.assetFor(slot)?.url
}
