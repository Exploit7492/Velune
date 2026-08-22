package com.nikhil.yt

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.pawns.ndk.PawnsCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PawnsManager private constructor(private val context: Context) {

    companion object {
        // Used as the shared fallback whenever no per-user key has been
        // stored yet (see PawnsBootReceiver.kt and MainActivity.kt's
        // consent flow) — previously a live key hardcoded directly here,
        // committed to this public repo's git history. Sourced from
        // BuildConfig now, same as every other credential in this app —
        // see app/build.gradle.kts's PAWNS_API_KEY comment. Not `const`:
        // a const val must be a compile-time literal, and BuildConfig
        // fields (generated per build from local.properties/CI secrets)
        // aren't literals from Kotlin's perspective even though they're
        // final at the Java bytecode level.
        val DEFAULT_API_KEY: String = com.nikhil.yt.BuildConfig.PAWNS_API_KEY
        private const val TAG = "PawnsManager"
        private const val PREFS_NAME = "pawns_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_CONSENT_GIVEN = "consent_given"

        @Volatile
        private var instance: PawnsManager? = null

        fun getInstance(context: Context): PawnsManager {
            return instance ?: synchronized(this) {
                instance ?: PawnsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    // State flows for UI observation
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _isConsentGiven = MutableStateFlow(prefs.getBoolean(KEY_CONSENT_GIVEN, false))
    val isConsentGiven: StateFlow<Boolean> = _isConsentGiven

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private var initialized = false

    // ─── INITIALIZE ──────────────────────────────────────────────────────────────
    fun initialize(apiKey: String): Boolean {
        return try {
            Log.d(TAG, "Initializing Pawns SDK with API key")
            // Store API key for boot receiver
            prefs.edit().putString(KEY_API_KEY, apiKey).apply()

            // Build and initialize the SDK (PawnsCore is the entry point from .aar)
            // Note: PawnsCore.Initialize(apiKey, deviceName) - we pass empty string for deviceName
            PawnsCore.Initialize(apiKey, "")

            initialized = true

            // Restore prior consent decision
            val consentGiven = prefs.getBoolean(KEY_CONSENT_GIVEN, false)
            _isConsentGiven.value = consentGiven

            if (consentGiven) {
                // If consent was previously given, start sharing automatically
                PawnsCore.StartMainRoutine("", object : PawnsCore.Callback {
                    override fun onCallback(message: String) {
                        Log.d(TAG, "Pawns callback: $message")
                    }
                })
                _isRunning.value = true
                Log.d(TAG, "Consent previously granted — sharing resumed")
            } else {
                Log.d(TAG, "No prior consent on record — sharing left off")
            }

            true
        } catch (e: Throwable) {
            _lastError.value = e.message
            Log.e(TAG, "Initialization failed: ${e.message}", e)
            false
        }
    }

    // ─── OPT IN ──────────────────────────────────────────────────────────────────
    fun optIn(): Boolean {
        return try {
            Log.d(TAG, "Opting in - granting consent")
            prefs.edit().putBoolean(KEY_CONSENT_GIVEN, true).apply()
            _isConsentGiven.value = true

            // Tell the SDK consent is given
            PawnsCore.StartMainRoutine("", object : PawnsCore.Callback {
                override fun onCallback(message: String) {
                    Log.d(TAG, "Pawns callback after opt-in: $message")
                }
            })

            _isRunning.value = true
            true
        } catch (e: Throwable) {
            _lastError.value = e.message
            Log.e(TAG, "OptIn failed: ${e.message}", e)
            false
        }
    }

    // ─── OPT OUT ─────────────────────────────────────────────────────────────────
    fun optOut(): Boolean {
        return try {
            Log.d(TAG, "Opting out - revoking consent")
            PawnsCore.StopMainRoutine()
            prefs.edit().putBoolean(KEY_CONSENT_GIVEN, false).apply()
            _isConsentGiven.value = false
            _isRunning.value = false
            true
        } catch (e: Throwable) {
            _lastError.value = e.message
            Log.e(TAG, "OptOut failed: ${e.message}", e)
            false
        }
    }

    // ─── START ──────────────────────────────────────────────────────────────────
    fun start(): Boolean {
        return try {
            Log.d(TAG, "Starting Pawns sharing")
            PawnsCore.StartMainRoutine("", object : PawnsCore.Callback {
                override fun onCallback(message: String) {
                    Log.d(TAG, "Pawns callback on start: $message")
                }
            })
            _isRunning.value = true
            true
        } catch (e: Throwable) {
            _lastError.value = e.message
            Log.e(TAG, "Start failed: ${e.message}", e)
            false
        }
    }

    // ─── STOP ───────────────────────────────────────────────────────────────────
    fun stop(): Boolean {
        return try {
            Log.d(TAG, "Stopping Pawns sharing")
            PawnsCore.StopMainRoutine()
            _isRunning.value = false
            true
        } catch (e: Throwable) {
            _lastError.value = e.message
            Log.e(TAG, "Stop failed: ${e.message}", e)
            false
        }
    }

    // ─── GET STATUS ─────────────────────────────────────────────────────────────
    fun getStatus(): Map<String, Any?> {
        return mapOf(
            "isRunning" to _isRunning.value,
            "isConsentGiven" to _isConsentGiven.value,
            "initialized" to initialized,
            "lastError" to _lastError.value
        )
    }

    // ─── GET LAST ERROR ────────────────────────────────────────────────────────
    fun getLastError(): String? = _lastError.value

    // ─── IS SDK INITIALIZED ────────────────────────────────────────────────────
    fun isInitialized(): Boolean = initialized

    // ─── CLEAR STORED DATA ─────────────────────────────────────────────────────
    fun clearAllData() {
        prefs.edit().clear().apply()
        _isConsentGiven.value = false
        _isRunning.value = false
        _lastError.value = null
    }

    // ─── GET STORED API KEY (for boot receiver) ──────────────────────────────
    fun getStoredApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    // ─── GET STORED CONSENT (for boot receiver) ──────────────────────────────
    fun getStoredConsent(): Boolean = prefs.getBoolean(KEY_CONSENT_GIVEN, false)
}
