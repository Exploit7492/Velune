package com.nikhil.yt.campaign

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nikhil.yt.BuildConfig
import com.nikhil.yt.utils.dataStore
import com.nikhil.yt.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant

private val CampaignAccessTokenKey = stringPreferencesKey("campaign_admin_access_token")
private val CampaignRefreshTokenKey = stringPreferencesKey("campaign_admin_refresh_token")
private val CampaignTokenExpiryKey = stringPreferencesKey("campaign_admin_token_expiry")

/**
 * Everything needed to actually run campaigns from inside the app: signing
 * in as the admin, and full create/update/delete on the `campaigns` table.
 *
 * Deliberately a **separate class** from [CampaignRepository], not an
 * extension of it. [CampaignRepository] is documented as anon-key-only —
 * read-only plus one narrow atomic RPC, by design, so that class's own
 * doc comment can keep truthfully saying "anon gets zero direct write
 * grants." This class is the one place in the app that holds a signed-in
 * session and can actually write to the table, and it says so plainly.
 *
 * **Why Supabase Auth instead of embedding the service-role key**: the
 * service-role key bypasses Row Level Security entirely. Baking it into
 * the app would mean anyone who decompiles the APK gets unrestricted
 * read/write access to the whole database, not just campaigns — a real
 * security hole, not a theoretical one. Signing in as a real Supabase
 * Auth user instead means writes are scoped by RLS the same way anon
 * reads are, just to a different role (`authenticated`) — see
 * campaign_schema.sql's admin policies and their own caveat about what
 * "authenticated" is scoped to for a single-admin project like this one.
 *
 * Session tokens are stored via DataStore, same mechanism (and same
 * "not full OS keystore-grade encryption, just not committed to source"
 * caveat) as every other credential this app stores client-side — see
 * OpenRouterApiKey elsewhere for the established precedent.
 */
class CampaignAdminRepository(private val context: Context) {

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()
    private val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    private fun isConfigured() = supabaseUrl.isNotBlank() && anonKey.isNotBlank()

    suspend fun isSignedIn(): Boolean = withContext(Dispatchers.IO) {
        context.dataStore.get(CampaignAccessTokenKey, "").isNotBlank()
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        try {
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=password")
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = try {
                        JSONObject(body).optString("error_description", "Sign-in failed")
                    } catch (e: Exception) {
                        "Sign-in failed (HTTP ${response.code})"
                    }
                    return@withContext Result.failure(Exception(message))
                }
                storeSession(JSONObject(body))
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "signIn failed")
            Result.failure(e)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        com.nikhil.yt.utils.PreferenceStore.launchEdit(context.dataStore) {
            remove(CampaignAccessTokenKey)
            remove(CampaignRefreshTokenKey)
            remove(CampaignTokenExpiryKey)
        }
    }

    private suspend fun storeSession(body: JSONObject) {
        val accessToken = body.optString("access_token", "")
        val refreshToken = body.optString("refresh_token", "")
        val expiresIn = body.optLong("expires_in", 3600L)
        val expiryInstant = Instant.now().plusSeconds(expiresIn).toString()
        com.nikhil.yt.utils.PreferenceStore.launchEdit(context.dataStore) {
            this[CampaignAccessTokenKey] = accessToken
            this[CampaignRefreshTokenKey] = refreshToken
            this[CampaignTokenExpiryKey] = expiryInstant
        }
    }

    /** Returns a valid access token, transparently refreshing it first if
     * it's expired or close to it. Null if there's no session at all —
     * callers should treat that as "not signed in," not retry. */
    private suspend fun validAccessToken(): String? = withContext(Dispatchers.IO) {
        val accessToken = context.dataStore.get(CampaignAccessTokenKey, "")
        val refreshToken = context.dataStore.get(CampaignRefreshTokenKey, "")
        val expiryRaw = context.dataStore.get(CampaignTokenExpiryKey, "")
        if (accessToken.isBlank()) return@withContext null

        val expiry = try {
            Instant.parse(expiryRaw)
        } catch (e: Exception) {
            Instant.EPOCH
        }
        // Refresh a minute early rather than right at the deadline.
        if (Instant.now().isBefore(expiry.minusSeconds(60))) return@withContext accessToken
        if (refreshToken.isBlank()) return@withContext null

        try {
            val payload = JSONObject().apply { put("refresh_token", refreshToken) }
            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=refresh_token")
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = JSONObject(response.body?.string().orEmpty())
                storeSession(body)
                body.optString("access_token", null)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Token refresh failed")
            null
        }
    }

    /** Every row in the table, regardless of date window or active state
     * — the anon-scoped [CampaignRepository] can only ever see currently-
     * live campaigns by design (RLS), so admin management needs its own,
     * separately-authenticated read path to see scheduled/ended/paused
     * ones too. */
    suspend fun fetchAllCampaigns(): Result<List<AdminCampaignRow>> = withContext(Dispatchers.IO) {
        val token = validAccessToken() ?: return@withContext Result.failure(NotSignedInException())
        try {
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/campaigns?order=created_at.desc")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val array = org.json.JSONArray(response.body?.string().orEmpty())
                val rows = ArrayList<AdminCampaignRow>(array.length())
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    rows.add(AdminCampaignRow.fromJson(obj))
                }
                Result.success(rows)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "fetchAllCampaigns failed")
            Result.failure(e)
        }
    }

    suspend fun createCampaign(input: CampaignInput): Result<Unit> =
        writeCampaign(method = "POST", path = "/rest/v1/campaigns", input = input)

    suspend fun updateCampaign(id: String, input: CampaignInput): Result<Unit> =
        writeCampaign(method = "PATCH", path = "/rest/v1/campaigns?id=eq.$id", input = input)

    suspend fun deleteCampaign(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = validAccessToken() ?: return@withContext Result.failure(NotSignedInException())
        try {
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/campaigns?id=eq.$id")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) Result.failure(Exception("HTTP ${response.code}")) else Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "deleteCampaign failed")
            Result.failure(e)
        }
    }

    /** Independent of [createCampaign]/[updateCampaign] — a one-field
     * pause/resume toggle a campaign list can call directly without
     * opening the full edit form. */
    suspend fun setActive(id: String, active: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val token = validAccessToken() ?: return@withContext Result.failure(NotSignedInException())
        try {
            val payload = JSONObject().apply { put("active", active) }
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/campaigns?id=eq.$id")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .patch(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) Result.failure(Exception("HTTP ${response.code}")) else Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "setActive failed")
            Result.failure(e)
        }
    }

    private suspend fun writeCampaign(method: String, path: String, input: CampaignInput): Result<Unit> =
        withContext(Dispatchers.IO) {
            val token = validAccessToken() ?: return@withContext Result.failure(NotSignedInException())
            try {
                val payload = input.toJson()
                val body = payload.toString().toRequestBody(jsonMediaType)
                val requestBuilder = Request.Builder()
                    .url("$supabaseUrl$path")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                val request = when (method) {
                    "POST" -> requestBuilder.post(body)
                    "PATCH" -> requestBuilder.patch(body)
                    else -> throw IllegalArgumentException("Unsupported method $method")
                }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string().orEmpty()
                        Timber.tag(TAG).w("writeCampaign HTTP ${response.code}: $errorBody")
                        Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                    } else {
                        Result.success(Unit)
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "writeCampaign failed")
                Result.failure(e)
            }
        }

    class NotSignedInException : Exception("Not signed in")

    companion object {
        private const val TAG = "CampaignAdminRepository"
    }
}

/** Everything a create/edit form needs to submit — a human-entered subset
 * of the table's columns. `sourceUrl`/`startDate`/`endDate` are the only
 * required fields, matching "you only insert a URL, then start/end dates"
 * — everything else defaults sensibly. */
data class CampaignInput(
    val sourceUrl: String,
    val startDate: Instant,
    val endDate: Instant,
    val certified: Boolean = false,
    val isLive: Boolean = false,
    val active: Boolean = true,
    val ctaLabel: String = "Play",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("source_url", sourceUrl)
        put("start_date", startDate.toString())
        put("end_date", endDate.toString())
        put("certified", certified)
        put("is_live", isLive)
        put("active", active)
        put("cta_label", ctaLabel)
    }
}

/** One row as seen by the admin — every column, not just the ones a
 * player card needs, and without the anon path's date-window filtering.
 * Intentionally its own type, not a subclass of [CampaignRow] (a data
 * class — Kotlin doesn't allow those to be extended anyway): the admin
 * view and the resolved-for-playback view have different enough shapes
 * (dates and `active` matter here; `resolvedSongId` doesn't) that sharing
 * a type would mean one or the other carries fields that don't apply. */
data class AdminCampaignRow(
    val id: String,
    val sourceUrl: String,
    val startDate: Instant,
    val endDate: Instant,
    val certified: Boolean,
    val isLive: Boolean,
    val active: Boolean,
    val playCount: Long,
) {
    companion object {
        fun fromJson(obj: JSONObject): AdminCampaignRow = AdminCampaignRow(
            id = obj.optString("id", ""),
            sourceUrl = obj.optString("source_url", ""),
            startDate = runCatching { Instant.parse(obj.optString("start_date")) }.getOrDefault(Instant.EPOCH),
            endDate = runCatching { Instant.parse(obj.optString("end_date")) }.getOrDefault(Instant.EPOCH),
            certified = obj.optBoolean("certified", false),
            isLive = obj.optBoolean("is_live", false),
            active = obj.optBoolean("active", true),
            playCount = obj.optLong("play_count", 0L),
        )
    }

    /** Where this row currently stands relative to its own date window —
     * purely a display concept, computed fresh each time, never stored. */
    fun status(now: Instant = Instant.now()): CampaignStatus = when {
        !active -> CampaignStatus.PAUSED
        now.isBefore(startDate) -> CampaignStatus.SCHEDULED
        now.isAfter(endDate) -> CampaignStatus.ENDED
        else -> CampaignStatus.LIVE
    }
}

enum class CampaignStatus { LIVE, SCHEDULED, ENDED, PAUSED }
