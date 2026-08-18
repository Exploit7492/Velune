package com.nikhil.yt.eq


import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import com.nikhil.yt.eq.audio.CustomEqualizerAudioProcessor
import com.nikhil.yt.eq.data.EQProfileRepository
import com.nikhil.yt.eq.data.ImpulseResponseLoader
import com.nikhil.yt.eq.data.ParametricEQ
import com.nikhil.yt.eq.data.SavedEQProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton


/**
 * The single canonical DSP engine — per-band EQ, master-bus controls
 * (balance/bass boost/width/limiter), convolution, tempo/pitch, spectrum
 * analysis. Simple and Master (see AxionEqScreen/AxionEqViewModel/
 * EQViewModel) are two UIs over this one engine, not two separate DSP
 * paths — everything they do ultimately funnels through the methods below.
 *
 * This class owns its own persistence and self-restores on construction
 * (see the `init` block at the bottom of the primary-constructor fields),
 * rather than depending on something external to load state into it after
 * the fact. Why that distinction matters:
 *
 * Previously, every setting here was restored by a *separate* function
 * (`EqStartupInitializer.restorePersistedEqState`) that had to be invoked
 * by `MusicService.onCreate()` at exactly the right moment — before
 * `createRenderersFactory()` forces the lazy audio-processor properties
 * into existence, which is when `addAudioProcessor()` first applies these
 * pending* fields to a real processor. Because that restore read from
 * Jetpack DataStore (suspend-only, backed by a Flow), there was no way to
 * call it in a truly synchronous, ordering-guaranteed way — the best
 * available was a *bounded blocking wait* (`runBlocking` +
 * `withTimeoutOrNull`) around the one call site, which is still
 * fundamentally a race with a deadline attached, not a structural
 * guarantee: if that bound is ever actually hit, the fallback path is
 * "finish restoring in the background and hope nothing plays before that
 * completes."
 *
 * This class removes that race entirely by changing *where* the
 * dependency lives. Instead of an external call that has to win a timing
 * race against another external call (`createRenderersFactory()`), this
 * class restores its own state as part of being constructed — a Kotlin
 * `init` block runs synchronously and unconditionally as part of the
 * constructor, full stop, by language guarantee, not by scheduling. And
 * because this is a Hilt `@Singleton`, Hilt constructs it lazily on first
 * injection — which for `MusicService` happens during field injection
 * inside `super.onCreate()` (Hilt's generated `Hilt_MusicService` injects
 * before delegating to the app's own `onCreate()` body), i.e. strictly
 * before `createRenderersFactory()` runs later in that same method. So by
 * the time anything holds a reference to an `EqualizerService` instance at
 * all, it is already fully configured — there is no "constructed but not
 * yet restored" state to race against, because construction *is*
 * restoration now. This is the same pattern this codebase's own
 * `EQProfileRepository` already used correctly (plain `SharedPreferences`,
 * loaded synchronously in its own `init {}`) — this class now follows it
 * too, for the fields that previously didn't.
 *
 * Concretely, that meant moving the 8 scalar knobs this class didn't
 * already own a synchronous copy of (balance, bass boost, stereo width,
 * limiter enabled/ceiling, tempo, pitch, convolution enabled/IR path) off
 * of DataStore-only storage and onto a small private `SharedPreferences`
 * file this class reads and writes directly — see [enginePrefs] below.
 * The band/profile data itself didn't need this treatment: it already
 * lived in [EQProfileRepository], which was already synchronous. The
 * master on/off toggle also didn't need a new copy: AxionEqViewModel
 * already kept a synchronous mirror of it in its own SharedPreferences
 * (`echo_eq_prefs`/"enabled") alongside its DataStore write — this class
 * just reads that same key directly rather than depending on DataStore's
 * copy of it.
 *
 * The DataStore keys for all of these fields are left in place and still
 * written by the ViewModels — they remain the reactive source the EQ
 * screen's Compose state observes across recompositions/tab switches.
 * They are simply no longer load-bearing for *this* class's own startup
 * correctness, which is the property that was actually missing.
 */
@Singleton
class EqualizerService @Inject constructor(
    @ApplicationContext context: Context,
    private val eqProfileRepository: EQProfileRepository,
) {

    // This class's own synchronous, non-suspending persistence for the
    // scalar DSP knobs that previously only lived in DataStore. Separate
    // file from AxionEqViewModel's "echo_eq_prefs" (which stays exactly as
    // it was) so this class's schema is self-contained and doesn't need to
    // agree on key ownership with a ViewModel that also writes there.
    private val enginePrefs: SharedPreferences =
        context.getSharedPreferences("eq_engine_state", Context.MODE_PRIVATE)

    // The one field this class deliberately does NOT keep its own copy
    // of: the master on/off toggle already has a synchronous home
    // (AxionEqViewModel's "echo_eq_prefs"/"enabled", written via plain
    // SharedPreferences.edit()...apply(), same as this class's own
    // enginePrefs). Reading it directly here avoids a third copy of the
    // same boolean that could drift out of sync with the other two.
    private val axionPrefs: SharedPreferences =
        context.getSharedPreferences("echo_eq_prefs", Context.MODE_PRIVATE)

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

    // Spectrum analyzer — same "pending" pattern as everything else above,
    // so a toggle flipped before a track is loaded (or across a processor
    // being torn down/recreated on track change) still takes effect once a
    // processor exists.
    private var pendingSpectrumEnabled: Boolean = false

    // Tempo/pitch — a separate registry from [audioProcessors] above since
    // it's a standalone AudioProcessor (see TempoPitchAudioProcessor's class
    // doc for why), not another feature of CustomEqualizerAudioProcessor.
    // Same "pending" pattern as everything else: remembered here so a
    // processor torn down/recreated on track change picks the same tempo/
    // pitch back up without the user having to reset it.
    @SuppressLint("UnsafeOptInUsageError")
    private val tempoPitchProcessors = mutableListOf<com.nikhil.yt.eq.audio.TempoPitchAudioProcessor>()
    private var pendingTempoRatio: Double = 1.0
    private var pendingPitchSemitones: Double = 0.0

    companion object {
        private const val TAG = "EqualizerService"

        private const val KEY_BALANCE = "balance"
        private const val KEY_BASS_BOOST_DB = "bass_boost_db"
        private const val KEY_STEREO_WIDTH = "stereo_width"
        private const val KEY_LIMITER_ENABLED = "limiter_enabled"
        private const val KEY_LIMITER_CEILING_DB = "limiter_ceiling_db"
        private const val KEY_TEMPO = "tempo_ratio"
        private const val KEY_PITCH_SEMITONES = "pitch_semitones"
        private const val KEY_CONVOLUTION_ENABLED = "convolution_enabled"
        private const val KEY_CONVOLUTION_IR_PATH = "convolution_ir_path"
    }

    // Self-restore, synchronously, as part of construction — see this
    // class's own doc comment above for why this replaces the old
    // external-call-racing-the-renderer-chain approach. Every field read
    // here comes from enginePrefs/axionPrefs (plain SharedPreferences,
    // genuinely synchronous, no suspend point anywhere in this block) —
    // never DataStore, which is exactly what made the old path racy.
    //
    // Calls straight through the public setters below rather than
    // duplicating their pending-field-assignment logic: at this point in
    // construction `audioProcessors`/`tempoPitchProcessors` are always
    // empty, so each setter's `audioProcessors.forEach { ... }` is a
    // guaranteed no-op — the only real effect is populating the pending*
    // fields and (harmlessly) re-writing the same value back to
    // enginePrefs it was just read from. A few redundant small
    // SharedPreferences writes on cold start is a trivial cost, and
    // reusing the exact same code path the UI uses for these values (
    // rather than a second hand-written copy of the assignment logic)
    // means there is only one place that can drift from correct.
    init {
        setBalance(enginePrefs.getFloat(KEY_BALANCE, 0f).toDouble())
        setBassBoost(enginePrefs.getFloat(KEY_BASS_BOOST_DB, 0f).toDouble())
        setStereoWidth(enginePrefs.getFloat(KEY_STEREO_WIDTH, 1f).toDouble())
        setLimiter(
            enginePrefs.getBoolean(KEY_LIMITER_ENABLED, false),
            enginePrefs.getFloat(KEY_LIMITER_CEILING_DB, -0.3f).toDouble(),
        )
        setTempo(enginePrefs.getFloat(KEY_TEMPO, 1f).toDouble())
        setPitchSemitones(enginePrefs.getFloat(KEY_PITCH_SEMITONES, 0f).toDouble())

        // Only push the active band profile if the master toggle is
        // actually on — matching AxionEqViewModel's own `if
        // (_enabled.value)` guard. A disabled-but-persisted profile
        // shouldn't play as if it were on. "enabled" here is the exact
        // same key AxionEqViewModel.setEnabled() already writes
        // synchronously to this same SharedPreferences file/key.
        if (axionPrefs.getBoolean("enabled", false)) {
            eqProfileRepository.getActiveProfile()?.let { applyProfile(it) }
        }

        // Convolution — re-validated by actually reparsing the file
        // (blocking java.io, not suspend — this was never the part that
        // needed DataStore), same as the old restore path: a copy that's
        // gone or corrupted outside the app just leaves convolution
        // unloaded instead of claiming a broken file works.
        val irPath = enginePrefs.getString(KEY_CONVOLUTION_IR_PATH, null)
        if (irPath != null) {
            val file = File(irPath)
            if (file.exists()) {
                val parsed = runCatching {
                    FileInputStream(file).use { ImpulseResponseLoader.load(it, targetSampleRate = 48000) }
                }.getOrNull()
                if (parsed != null) {
                    loadImpulseResponse(file)
                    setConvolutionEnabled(enginePrefs.getBoolean(KEY_CONVOLUTION_ENABLED, false))
                } else {
                    Timber.tag(TAG).w("Persisted impulse response at $irPath failed to re-validate; leaving convolution unloaded")
                }
            }
        }
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
        processor.setSpectrumAnalyzerEnabled(pendingSpectrumEnabled)
    }

    
    fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor) {
        audioProcessors.remove(processor)
    }

    @OptIn(UnstableApi::class)
    fun registerTempoPitchProcessor(processor: com.nikhil.yt.eq.audio.TempoPitchAudioProcessor) {
        tempoPitchProcessors.add(processor)
        processor.setTempo(pendingTempoRatio)
        processor.setPitchSemitones(pendingPitchSemitones)
        Timber.tag(TAG).d("Tempo/pitch processor added. Total: ${tempoPitchProcessors.size}")
    }

    fun unregisterTempoPitchProcessor(processor: com.nikhil.yt.eq.audio.TempoPitchAudioProcessor) {
        tempoPitchProcessors.remove(processor)
    }

    /** `1.0` = unchanged, independent of [setPitchSemitones]. Coerced to 0.25x..3.0x. */
    @OptIn(UnstableApi::class)
    fun setTempo(ratio: Double) {
        pendingTempoRatio = ratio
        enginePrefs.edit { putFloat(KEY_TEMPO, ratio.toFloat()) }
        tempoPitchProcessors.forEach { it.setTempo(ratio) }
    }

    /** Semitones, independent of [setTempo]. Coerced to -12..+12. */
    @OptIn(UnstableApi::class)
    fun setPitchSemitones(semitones: Double) {
        pendingPitchSemitones = semitones
        enginePrefs.edit { putFloat(KEY_PITCH_SEMITONES, semitones.toFloat()) }
        tempoPitchProcessors.forEach { it.setPitchSemitones(semitones) }
    }

    fun currentTempo(): Double = pendingTempoRatio
    fun currentPitchSemitones(): Double = pendingPitchSemitones

    /** -1.0 (full left) .. 0.0 (center) .. 1.0 (full right). */
    @OptIn(UnstableApi::class)
    fun setBalance(value: Double) {
        pendingBalance = value.coerceIn(-1.0, 1.0)
        enginePrefs.edit { putFloat(KEY_BALANCE, pendingBalance.toFloat()) }
        audioProcessors.forEach { it.setBalance(pendingBalance) }
    }

    /** 0..12 dB shelf boost below ~120Hz, applied on top of whatever profile is active. */
    @OptIn(UnstableApi::class)
    fun setBassBoost(gainDb: Double) {
        pendingBassBoostDb = gainDb.coerceIn(0.0, 12.0)
        enginePrefs.edit { putFloat(KEY_BASS_BOOST_DB, pendingBassBoostDb.toFloat()) }
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
        enginePrefs.edit { putFloat(KEY_STEREO_WIDTH, pendingStereoWidth.toFloat()) }
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
        enginePrefs.edit {
            putBoolean(KEY_LIMITER_ENABLED, pendingLimiterEnabled)
            putFloat(KEY_LIMITER_CEILING_DB, pendingLimiterCeilingDb.toFloat())
        }
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
        enginePrefs.edit { putString(KEY_CONVOLUTION_IR_PATH, file.absolutePath) }
        audioProcessors.forEach { it.loadImpulseResponse(file) }
    }

    @OptIn(UnstableApi::class)
    fun setConvolutionEnabled(enabled: Boolean) {
        pendingConvolutionEnabled = enabled
        enginePrefs.edit { putBoolean(KEY_CONVOLUTION_ENABLED, enabled) }
        audioProcessors.forEach { it.setConvolutionEnabled(enabled) }
    }

    @OptIn(UnstableApi::class)
    fun clearImpulseResponse() {
        pendingImpulseResponseFile = null
        pendingConvolutionEnabled = false
        enginePrefs.edit {
            remove(KEY_CONVOLUTION_IR_PATH)
            putBoolean(KEY_CONVOLUTION_ENABLED, false)
        }
        audioProcessors.forEach { it.clearImpulseResponse() }
    }

    /**
     * Turns the spectrum analyzer tap on/off across all active processors.
     * Left off unless the spectrum UI is actually on screen — see
     * SpectrumAnalyzer's class doc for why (avoids a per-sample cost with
     * nothing displaying it).
     */
    @OptIn(UnstableApi::class)
    fun setSpectrumAnalyzerEnabled(enabled: Boolean) {
        pendingSpectrumEnabled = enabled
        audioProcessors.forEach { it.setSpectrumAnalyzerEnabled(enabled) }
    }

    /**
     * Latest spectrum analysis — ballistics-smoothed bar levels plus their
     * peak-hold caps, [SpectrumAnalyzer.BAR_COUNT] of each, normalized
     * 0f..1f. See [SpectrumAnalyzer]'s class doc for what "ballistics-
     * smoothed" means here (instant attack, rate-limited release) and why
     * it isn't just a raw per-block magnitude dump. Reads from whichever
     * processor is currently active; in the normal single-player case
     * there's exactly one.
     */
    @OptIn(UnstableApi::class)
    fun spectrumSnapshot(): com.nikhil.yt.eq.audio.SpectrumSnapshot =
        audioProcessors.firstOrNull()?.spectrumAnalyzer?.snapshot()
            ?: com.nikhil.yt.eq.audio.SpectrumSnapshot(
                FloatArray(com.nikhil.yt.eq.audio.SpectrumAnalyzer.BAR_COUNT),
                FloatArray(com.nikhil.yt.eq.audio.SpectrumAnalyzer.BAR_COUNT)
            )

    /**
     * Bar index closest to [frequencyHz], for the spectrum UI's axis
     * labels (see [SpectrumAnalyzer.LABEL_FREQUENCIES_HZ] for the "nice"
     * frequencies it's meant to be called with). -1 if no processor is
     * configured yet.
     */
    @OptIn(UnstableApi::class)
    fun spectrumBarIndexForLabel(frequencyHz: Double): Int =
        audioProcessors.firstOrNull()?.spectrumAnalyzer?.barIndexForLabel(frequencyHz) ?: -1

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
