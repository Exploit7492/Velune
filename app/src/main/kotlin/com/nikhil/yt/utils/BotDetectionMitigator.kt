package com.nikhil.yt.utils

import com.nikhil.yt.extensions.dataStore


<<<<<<< HEAD
package com.nikhil.yt.utils
=======
>>>>>>> 05c038de80d0d71942953c5ec6e9c9f6690bb624

import androidx.datastore.preferences.core.edit
import com.nikhil.yt.innertube.YouTube
import com.nikhil.yt.constants.VisitorDataKey
import com.nikhil.yt.utils.cipher.CipherDeobfuscator
import com.nikhil.yt.utils.PlaybackLogManager
import com.nikhil.yt.utils.PlaybackLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger


object BotDetectionMitigator {
    private const val TAG = "BotDetectionMitigator"

    private val failureCount = AtomicInteger(0)

    
    
    private val GEO_ERROR_SIGNATURES = listOf(
        "not available in your country",
        "not available in your region",
        "not available in this country",
        "not available in this region",
        "geo-restricted",
        "GEO_RESTRICTED",
        "NOT_AVAILABLE_IN_THIS_COUNTRY",
        "only available in certain countries",
        "country restriction",
        "region restriction",
    )

    
    private val BOT_ERROR_SIGNATURES = listOf(
        "Sign in to confirm",
        "confirm you're not a bot",
        "automated queries",
        "Error 2000",
        "403",
        "This content isn't available on this device",
    )

    
    fun notifyPlaybackFailure(isLoggedIn: Boolean, errorMessage: String? = null): Boolean {
        if (isLoggedIn) return false
        if (isGeoError(errorMessage)) return false

        failureCount.incrementAndGet()
        return true
    }

    
    fun notifyPlaybackSuccess() {
        failureCount.set(0)
    }

    
    suspend fun rotateGuestSession() {
        Timber.tag(TAG).i("Rotating guest session to bypass bot detection...")
        PlaybackLogManager.log(
            PlaybackLogLevel.BOT, 
            "Rotating guest session", 
            "Bypassing bot detection by refreshing visitorData (locale preserved)"
        )
        
        withContext(Dispatchers.IO) {
            
            val currentLocale = YouTube.locale

            
            YouTube.visitorData = null
            
            YouTube.visitorData = YouTube.visitorData().onSuccess { newData ->
            YouTube.visitorData().onSuccess { newData ->
                Timber.tag(TAG).i("New visitorData obtained successfully for region ${currentLocale.gl}.")
                YouTube.visitorData = newData
                CipherDeobfuscator.appContext?.dataStore?.edit { settings ->
                    settings[VisitorDataKey] = newData
                }
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to refresh visitorData during rotation")
                YouTube.locale = currentLocale
            }
        
        failureCount.set(0)
    }

    
    fun isGeoError(message: String?): Boolean {
        if (message == null) return false
        val lower = message.lowercase()
        return GEO_ERROR_SIGNATURES.any { lower.contains(it.lowercase()) }
    }

    
    fun isBotDetectionError(message: String?): Boolean {
        if (message == null) return false
        val lower = message.lowercase()
        return BOT_ERROR_SIGNATURES.any { lower.contains(it.lowercase()) }
    }

    fun reset() {
        failureCount.set(0)
    }
}
