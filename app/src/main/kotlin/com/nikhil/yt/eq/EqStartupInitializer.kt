package com.nikhil.yt.eq

import android.content.Context
import com.nikhil.yt.constants.EqBalanceKey
import com.nikhil.yt.constants.EqBassBoostKey
import com.nikhil.yt.constants.EqConvolutionEnabledKey
import com.nikhil.yt.constants.EqConvolutionIrPathKey
import com.nikhil.yt.constants.EqLimiterCeilingKey
import com.nikhil.yt.constants.EqLimiterEnabledKey
import com.nikhil.yt.constants.EqPitchSemitonesKey
import com.nikhil.yt.constants.EqStereoWidthKey
import com.nikhil.yt.constants.EqTempoKey
import com.nikhil.yt.constants.ParametricEQEnabledKey
import com.nikhil.yt.eq.data.EQProfileRepository
import com.nikhil.yt.eq.data.ImpulseResponseLoader
import com.nikhil.yt.utils.dataStore
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

private const val TAG = "EqStartupInitializer"

/**
 * Restores every persisted EQ/DSP setting into [EqualizerService] once, at
 * playback-service startup (see MusicService.onCreate(), which calls this
 * before its ExoPlayer/audio-processor chain is built) — not lazily
 * whenever the EQ screen happens to be opened.
 *
 * Previously, every one of these settings was only ever loaded and pushed
 * into EqualizerService from inside AxionEqViewModel/EQViewModel's own
 * init{} blocks. Those ViewModels only get constructed when the user
 * navigates to the EQ screen — so a track played right after an app
 * restart, before the EQ screen was ever opened that session, played
 * completely unequalized (flat, no limiter, no bass boost, tempo/pitch at
 * defaults, no convolution) despite the user having real saved settings
 * sitting in DataStore. EqualizerService itself has no self-initialization
 * — every `pending*` field starts at a hardcoded default and stays there
 * until something calls a setter.
 *
 * Calling this here means the very first CustomEqualizerAudioProcessor
 * EqualizerService.addAudioProcessor() ever sees already has the pending*
 * fields populated correctly — that function's existing "apply pending
 * state to a newly attached processor" logic (untouched by this change)
 * takes care of the rest.
 *
 * Restoration logic (which keys, which defaults, the re-validate-by-
 * reparsing approach for convolution) is kept deliberately in sync with
 * AxionEqViewModel.init{}'s own restore block — if one changes, check the
 * other.
 *
 * One honest caveat: DataStore reads are suspend, so this can't be a truly
 * synchronous read. `MusicService.onCreate()` calls this inside a bounded
 * `runBlocking`/`withTimeoutOrNull` (see the call site) rather than firing
 * it off with `launch{}` and moving on — so in the normal case, by the
 * time `createRenderersFactory()` runs and processors are attached, this
 * has already fully applied. The bound exists only so a pathological
 * slow/stuck read (corrupted file, extreme disk contention) can't turn
 * into a startup hang; if it's ever actually hit, the call site falls
 * back to finishing this in the background instead, which is still safe
 * — every setter here (`EqualizerService.setBalance` et al.) is safe to
 * call whether a processor already exists or not (see
 * `addAudioProcessor()`'s pending-state application), so the worst case
 * in that rare fallback path is a very brief unequalized start, never a
 * stuck bad state. This has not been verified on an actual device; only
 * the restore logic's shape has been checked against `AxionEqViewModel`'s
 * existing (working) version, and the blocking-with-timeout behavior at
 * the call site has only been reasoned through, not measured against a
 * real cold start's actual DataStore-read latency.
 */
suspend fun restorePersistedEqState(
    context: Context,
    equalizerService: EqualizerService,
    eqProfileRepository: EQProfileRepository,
) {
    val prefs = context.dataStore.data.first()

    equalizerService.setBalance((prefs[EqBalanceKey] ?: 0f).toDouble())
    equalizerService.setBassBoost((prefs[EqBassBoostKey] ?: 0f).toDouble())
    equalizerService.setStereoWidth((prefs[EqStereoWidthKey] ?: 1f).toDouble())
    equalizerService.setLimiter(
        prefs[EqLimiterEnabledKey] ?: false,
        (prefs[EqLimiterCeilingKey] ?: -0.3f).toDouble(),
    )
    equalizerService.setTempo((prefs[EqTempoKey] ?: 1f).toDouble())
    equalizerService.setPitchSemitones((prefs[EqPitchSemitonesKey] ?: 0f).toDouble())

    // Only push the active band profile if the master toggle is actually
    // on — matching AxionEqViewModel's own `if (_enabled.value)` guard.
    // A disabled-but-persisted profile shouldn't play as if it were on.
    if (prefs[ParametricEQEnabledKey] == true) {
        eqProfileRepository.getActiveProfile()?.let { equalizerService.applyProfile(it) }
    }

    // Convolution — re-validated by actually reparsing the file, same as
    // AxionEqViewModel.init{}, so a copy that's gone or corrupted outside
    // the app just leaves convolution unloaded instead of claiming a
    // broken file works.
    val irPath = prefs[EqConvolutionIrPathKey]
    if (irPath != null) {
        val file = File(irPath)
        if (file.exists()) {
            val parsed = runCatching {
                FileInputStream(file).use { ImpulseResponseLoader.load(it, targetSampleRate = 48000) }
            }.getOrNull()
            if (parsed != null) {
                equalizerService.loadImpulseResponse(file)
                equalizerService.setConvolutionEnabled(prefs[EqConvolutionEnabledKey] ?: false)
            } else {
                Timber.tag(TAG).w("Persisted impulse response at $irPath failed to re-validate; leaving convolution unloaded")
            }
        }
    }
}
