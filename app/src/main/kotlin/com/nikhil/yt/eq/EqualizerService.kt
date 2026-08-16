package com.nikhil.yt.eq


import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.nikhil.yt.eq.audio.CustomEqualizerAudioProcessor
import com.nikhil.yt.eq.data.ParametricEQ
import com.nikhil.yt.eq.data.SavedEQProfile
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class EqualizerService @Inject constructor() {

    @SuppressLint("UnsafeOptInUsageError")
    private val audioProcessors = mutableListOf<CustomEqualizerAudioProcessor>()
    private var pendingProfile: SavedEQProfile? = null
    private var shouldDisable: Boolean = false

    // Master-bus controls (balance, bass boost) — independent of the per-band
    // profile above, same as the master bus on a real mixer sits above the
    // individual EQ channels. Remembered the same "pending" way profiles are,
    // so they survive a processor being torn down/recreated (e.g. track change).
    private var pendingBalance: Double = 0.0
    private var pendingBassBoostDb: Double = 0.0

    companion object {
        private const val TAG = "EqualizerService"
    }

    
    @OptIn(UnstableApi::class)
    fun addAudioProcessor(processor: CustomEqualizerAudioProcessor) {
        audioProcessors.add(processor)
        Timber.tag(TAG).d("Audio processor added. Total: ${audioProcessors.size}")

        
        if (shouldDisable) {
            processor.disable()
            
        } else if (pendingProfile != null) {
            val profile = pendingProfile!!
            applyProfileToProcessor(processor, profile)
            
        }
        processor.setBalance(pendingBalance)
        processor.setBassBoost(pendingBassBoostDb)
    }

    
    fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor) {
        audioProcessors.remove(processor)
    }

    /** -1.0 (full left) .. 0.0 (center) .. 1.0 (full right). */
    @OptIn(UnstableApi::class)
    fun setBalance(value: Double) {
        pendingBalance = value.coerceIn(-1.0, 1.0)
        audioProcessors.forEach { it.setBalance(pendingBalance) }
    }

    /** 0..12 dB shelf boost below ~120Hz, applied on top of whatever profile is active. */
    @OptIn(UnstableApi::class)
    fun setBassBoost(gainDb: Double) {
        pendingBassBoostDb = gainDb.coerceIn(0.0, 12.0)
        audioProcessors.forEach { it.setBassBoost(pendingBassBoostDb) }
    }

    
    @OptIn(UnstableApi::class)
    fun applyProfile(profile: SavedEQProfile): Result<Unit> {
        if (audioProcessors.isEmpty()) {
            Timber.tag(TAG)
                .w("No audio processors set yet. Storing profile as pending: ${profile.name}")
            pendingProfile = profile
            shouldDisable = false
            return Result.success(Unit)
        }

        pendingProfile = profile 
        shouldDisable = false
        
        var success = true
        var lastError: Exception? = null

        audioProcessors.forEach { processor ->
            try {
                applyProfileToProcessor(processor, profile)
            } catch (e: Exception) {
                success = false
                lastError = e
            }
        }

        return if (success) Result.success(Unit) else Result.failure(lastError ?: Exception("Unknown error"))
    }

    private fun applyProfileToProcessor(processor: CustomEqualizerAudioProcessor, profile: SavedEQProfile) {
        val parametricEQ = ParametricEQ(
            preamp = profile.preamp,
            bands = profile.bands
        )
        processor.applyProfile(parametricEQ)
    }

    
    @OptIn(UnstableApi::class)
    fun disable() {
        if (audioProcessors.isEmpty()) {
            Timber.tag(TAG).w("No audio processors set yet. Storing disable as pending")
            shouldDisable = true
            pendingProfile = null
            return
        }

        shouldDisable = true 
        pendingProfile = null

        audioProcessors.forEach { processor ->
            try {
                processor.disable()
            } catch (e: Exception) {
                Timber.tag(TAG).e("Failed to disable equalizer: ${e.message}")
            }
        }
        Timber.tag(TAG).d("Equalizer disabled on all processors")
    }

    
    fun isInitialized(): Boolean {
        return audioProcessors.isNotEmpty()
    }

    
    @OptIn(UnstableApi::class)
    fun isEnabled(): Boolean {
        return audioProcessors.any { it.isEnabled() }
    }

    
    fun getEqualizerInfo(): EqualizerInfo {
        return EqualizerInfo(
            supportsUnlimitedBands = true,
            maxBands = Int.MAX_VALUE,
            description = "Custom ExoPlayer AudioProcessor with biquad filters"
        )
    }

    
    fun release() {
        
        audioProcessors.clear()
        Timber.tag(TAG).d("Audio processor references cleared")
    }
}


data class EqualizerInfo(
    val supportsUnlimitedBands: Boolean,
    val maxBands: Int,
    val description: String
)
