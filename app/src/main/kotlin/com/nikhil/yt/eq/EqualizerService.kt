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

    // Stereo width and limiter — same "pending" pattern as balance/bass
    // boost above, so they survive a processor being torn down/recreated.
    private var pendingStereoWidth: Double = 1.0
    private var pendingLimiterEnabled: Boolean = false
    private var pendingLimiterCeilingDb: Double = 0.0

    // Convolution-based tone shaping (see ConvolutionAudioProcessor) — same
    // "pending" pattern again: remember the last loaded IR file and whether
    // it should be active, so a processor torn down/recreated for a new
    // track picks the same state back up without the user having to
    // reload it.
    private var pendingImpulseResponseFile: java.io.File? = null
    private var pendingConvolutionEnabled: Boolean = false

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
        processor.setStereoWidth(pendingStereoWidth)
        processor.setLimiter(pendingLimiterEnabled, pendingLimiterCeilingDb)
        pendingImpulseResponseFile?.let { processor.loadImpulseResponse(it) }
        processor.setConvolutionEnabled(pendingConvolutionEnabled)
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

    /**
     * Stereo image width — 0.0 (mono) .. 1.0 (unchanged) .. 2.0 (extra
     * wide), via mid/side processing. Independent of [setBalance]: balance
     * shifts L/R *level*, this reshapes the stereo *image* itself.
     */
    @OptIn(UnstableApi::class)
    fun setStereoWidth(value: Double) {
        pendingStereoWidth = value.coerceIn(0.0, 2.0)
        audioProcessors.forEach { it.setStereoWidth(pendingStereoWidth) }
    }

    /**
     * Master limiter — the last stage of the whole chain (per-band EQ, bass
     * boost, balance, and width have all already been applied by the time
     * this runs), so raising preamp/band gain to taste can't clip the
     * output. [ceilingDb] is the output ceiling in dBFS, e.g. -1.0 to leave
     * a small amount of headroom.
     */
    @OptIn(UnstableApi::class)
    fun setLimiter(enabled: Boolean, ceilingDb: Double) {
        pendingLimiterEnabled = enabled
        pendingLimiterCeilingDb = ceilingDb.coerceIn(-12.0, 0.0)
        audioProcessors.forEach { it.setLimiter(pendingLimiterEnabled, pendingLimiterCeilingDb) }
    }

    /**
     * Loads a WAV impulse response (a measured headphone/DAC correction
     * curve, etc.) for convolution-based tone shaping. This runs ahead of
     * the parametric EQ bands in the chain — see ConvolutionAudioProcessor.
     * Loading doesn't itself enable it; call [setConvolutionEnabled] too
     * (or call it first — order doesn't matter, both are remembered).
     */
    @OptIn(UnstableApi::class)
    fun loadImpulseResponse(file: java.io.File) {
        pendingImpulseResponseFile = file
        audioProcessors.forEach { it.loadImpulseResponse(file) }
    }

    @OptIn(UnstableApi::class)
    fun setConvolutionEnabled(enabled: Boolean) {
        pendingConvolutionEnabled = enabled
        audioProcessors.forEach { it.setConvolutionEnabled(enabled) }
    }

    @OptIn(UnstableApi::class)
    fun clearImpulseResponse() {
        pendingImpulseResponseFile = null
        pendingConvolutionEnabled = false
        audioProcessors.forEach { it.clearImpulseResponse() }
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
