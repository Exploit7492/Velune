package com.nikhil.yt.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * Independent tempo/pitch control, built on [WsolaTimeStretcher] +
 * [LinearResampler] instead of Media3's built-in `SonicAudioProcessor` --
 * this is the "pro level, like Neutron and Poweramp" engine the user
 * asked for, replacing the Sonic-backed tempo/pitch dialog that used to
 * drive this feature via `PlaybackParameters`.
 *
 * Sits early in the chain (see `MusicService.createRenderersFactory`),
 * ahead of [CustomEqualizerAudioProcessor], so everything downstream
 * (convolution, biquad bands, limiter, spectrum analyzer) sees audio
 * that's already at its final tempo/pitch, exactly like a real track's
 * tempo/pitch is a property of the source signal, not a mix-bus effect.
 *
 * **Why this isn't just wired into `PlaybackParameters` like before**:
 * `PlaybackParameters(speed, pitch)` is Media3's own API for driving its
 * *own* `SonicAudioProcessor`; there's no supported way to make it drive
 * a different processor instead. So this class is driven directly (see
 * [setTempo]/[setPitchSemitones], called from `AxionEqViewModel`/
 * `EqualizerService` the same "pending state" way every other control in
 * this package is), and `TempoPitchAudioProcessorChain` -- the custom
 * `AudioSink.AudioProcessorChain` this processor is installed through --
 * makes `player.playbackParameters` a no-op passthrough instead, since
 * nothing else in the app currently sets it.
 */
@UnstableApi
class TempoPitchAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActiveFormat = false

    private var wsola: WsolaTimeStretcher? = null
    private var resampler: LinearResampler? = null

    @Volatile private var tempoRatio: Double = 1.0
    @Volatile private var pitchRatio: Double = 1.0

    private var inputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    // Cumulative frame counts since the last flush. [mediaDurationForPlayoutDuration]
    // reports the *observed* ratio between these to the audio sink's position
    // tracking rather than trusting the nominal tempoRatio -- windowing/rounding
    // inside WSOLA means the real ratio drifts very slightly from the
    // theoretical one over a long track, same reason Sonic's own
    // getMediaDuration() is byte-count-based rather than parameter-based.
    private var totalInputFrames = 0L
    private var totalOutputFrames = 0L

    @Synchronized
    fun setTempo(ratio: Double) {
        tempoRatio = ratio.coerceIn(0.25, 3.0)
        applyRatios()
    }

    @Synchronized
    fun setPitchSemitones(semitones: Double) {
        pitchRatio = 2.0.pow(semitones.coerceIn(-12.0, 12.0) / 12.0)
        applyRatios()
    }

    fun currentTempo(): Double = tempoRatio
    fun currentPitchSemitones(): Double = 12.0 * (kotlin.math.ln(pitchRatio) / kotlin.math.ln(2.0))

    private fun applyRatios() {
        resampler?.rate = pitchRatio
        wsola?.speed = tempoRatio / pitchRatio
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        isActiveFormat = (encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT) &&
            channelCount in 1..2

        wsola = if (isActiveFormat) WsolaTimeStretcher(channelCount).also { it.configure(sampleRate) } else null
        resampler = if (isActiveFormat) LinearResampler(channelCount) else null
        applyRatios()

        totalInputFrames = 0L
        totalOutputFrames = 0L

        Timber.tag(TAG).d(
            "Configured: sampleRate=$sampleRate channels=$channelCount encoding=$encoding active=$isActiveFormat"
        )
        return inputAudioFormat
    }

    /**
     * Was `isActiveFormat && (ratio deviates from 1.0)` — looked like a
     * reasonable "skip processing when there's nothing to do" optimization,
     * but it broke the feature entirely. Media3's `AudioProcessingPipeline`
     * only re-checks `isActive()` on a `flush()` following a `configure()`
     * (i.e. a format change / new track) — confirmed against the real
     * source (androidx/media, `AudioProcessingPipeline#flush`), not assumed.
     * A track starts at tempo=1.0/pitch=0, so this processor was excluded
     * from the active chain at that flush; calling setTempo/setPitchSemitones
     * afterward updated the ratios, but the pipeline was never routing audio
     * through this processor for the *current* track anymore, so nothing
     * audible happened until the next track's own flush picked up the
     * already-changed values. That's the "tempo/pitch not working" bug —
     * this now stays active whenever the format is supported, so it's in
     * the chain from the very first flush and later changes take effect
     * immediately on whatever's currently playing. The old Sonic-backed
     * dialog didn't have this problem because it went through
     * `player.playbackParameters =`, which Media3 specifically forces a
     * sink reconfigure for — bypassing that (required, since Sonic is
     * gone) lost that automatic trigger, so this compensates for it
     * instead. The cost was assumed "small" based on patch 0015's
     * near-identity *output* verification, but that only checked the
     * waveform result, not the CPU cost of getting there — running full
     * WSOLA + resampling on every buffer of every track, even at the
     * identity ratio, is real per-sample work and real allocation
     * pressure on the audio thread, not free. See the identity fast path
     * in queueInput() below, added once this was reported as an actual
     * on-device stutter, for the fix that keeps this correctness
     * guarantee without paying that cost when tempo/pitch aren't touched.
     */
    override fun isActive(): Boolean = isActiveFormat

    /**
     * True when both ratios are close enough to 1.0 that WSOLA/resampling
     * would produce output indistinguishable from a straight copy (per
     * patch 0015's own near-identity verification) — the case every
     * track is in unless the user has actually opened the tempo/pitch
     * dialog and moved something. isActive() above must stay
     * unconditionally true for correctness (see its doc comment), but
     * that only obligates this processor to keep receiving queueInput()
     * calls — it says nothing about what queueInput() has to *do* on
     * each call. Skipping the real DSP path here when there's nothing to
     * change is safe and free to toggle: the very next queueInput() call
     * after setTempo/setPitchSemitones moves a ratio off 1.0 takes the
     * real path immediately, same buffer, no reconfigure/flush needed.
     */
    private fun isIdentity(): Boolean =
        kotlin.math.abs(tempoRatio - 1.0) < IDENTITY_EPSILON &&
            kotlin.math.abs(pitchRatio - 1.0) < IDENTITY_EPSILON

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        if (isIdentity()) {
            // Fast path: a straight byte copy, no per-sample decode to
            // DoubleArray, no resampler, no WSOLA analysis/overlap-add, and
            // critically no array allocation at all (the decode/resample/
            // stretch path below allocates three fresh Array<DoubleArray>
            // per call) — this is what actually made the "untouched" case
            // cheap; the full pipeline below never was.
            val bytes = inputBuffer.remaining()
            val out = if (outputBuffer.capacity() < bytes) {
                ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
            } else {
                outputBuffer.clear() as ByteBuffer
            }
            out.put(inputBuffer)
            out.flip()
            outputBuffer = out
            val bytesPerSample = bytesPerSample()
            val frameCount = bytes / (bytesPerSample * channelCount)
            totalInputFrames += frameCount
            totalOutputFrames += frameCount
            return
        }
        val ws = wsola ?: return
        val rs = resampler ?: return

        val limit = inputBuffer.limit()
        val remaining = limit - inputBuffer.position()
        val bytesPerSample = bytesPerSample()
        val frameCount = remaining / (bytesPerSample * channelCount)
        if (frameCount == 0) return

        val decoded = Array(channelCount) { DoubleArray(frameCount) }
        when (encoding) {
            C.ENCODING_PCM_FLOAT ->
                for (i in 0 until frameCount) for (c in 0 until channelCount) decoded[c][i] = inputBuffer.getFloat().toDouble()
            C.ENCODING_PCM_16BIT ->
                for (i in 0 until frameCount) for (c in 0 until channelCount) decoded[c][i] = inputBuffer.getShort().toDouble() / 32768.0
        }
        inputBuffer.position(limit)
        totalInputFrames += frameCount

        val resampled = rs.process(decoded, frameCount)
        val stretched = if (resampled[0].isNotEmpty()) {
            ws.process(resampled, resampled[0].size)
        } else {
            Array(channelCount) { DoubleArray(0) }
        }
        val outFrames = stretched[0].size
        totalOutputFrames += outFrames

        if (outFrames == 0) {
            outputBuffer = EMPTY_BUFFER
            return
        }
        writeOutput(stretched, outFrames, bytesPerSample)
    }

    private fun writeOutput(frames: Array<DoubleArray>, outFrames: Int, bytesPerSample: Int) {
        val outBytes = outFrames * bytesPerSample * channelCount
        // FIX: `outputBuffer.clear()` resolves to `java.nio.Buffer.clear()` (not the
        // ByteBuffer-covariant override) against Android's bootclasspath, so without
        // the explicit cast this `if` infers its common branch type as `Buffer`
        // instead of `ByteBuffer` -- losing putFloat/putShort and failing the
        // `outputBuffer = out` assignment below. Same class of issue as flip()/
        // limit()/position() everywhere else in this file already casts around it.
        val out = if (outputBuffer.capacity() < outBytes) {
            ByteBuffer.allocateDirect(outBytes).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear() as ByteBuffer
        }
        when (encoding) {
            C.ENCODING_PCM_FLOAT ->
                for (i in 0 until outFrames) for (c in 0 until channelCount) out.putFloat(frames[c][i].coerceIn(-1.0, 1.0).toFloat())
            C.ENCODING_PCM_16BIT ->
                for (i in 0 until outFrames) for (c in 0 until channelCount) out.putShort((frames[c][i] * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
        }
        out.flip()
        outputBuffer = out
    }

    private fun bytesPerSample(): Int = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        wsola?.reset()
        resampler?.reset()
        totalInputFrames = 0L
        totalOutputFrames = 0L
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        inputBuffer = EMPTY_BUFFER
        wsola = null
        resampler = null
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActiveFormat = false
    }

    override fun queueEndOfStream() {
        // WSOLA always holds back a partially-filled window waiting for one
        // more hop of "future" audio to crossfade against, so the very last
        // ~20ms it was given is otherwise never flushed. Push a little
        // silence through to force it out instead of silently dropping it.
        // This does mean the last moment of a track is a few ms of natural
        // decay rather than a hard cut when tempo/pitch is active -- not
        // verified against a real device, see HANDOVER.md.
        val ws = wsola
        val rs = resampler
        if (ws != null && rs != null && !inputEnded) {
            val silence = Array(channelCount) { DoubleArray(FLUSH_SILENCE_FRAMES) }
            val resampled = rs.process(silence, FLUSH_SILENCE_FRAMES)
            if (resampled[0].isNotEmpty()) {
                val stretched = ws.process(resampled, resampled[0].size)
                val outFrames = stretched[0].size
                totalOutputFrames += outFrames
                if (outFrames > 0) writeOutput(stretched, outFrames, bytesPerSample())
            }
        }
        inputEnded = true
    }

    /**
     * Scales a playout duration (time actually spent producing output) to
     * the corresponding media duration (time consumed from the source), so
     * ExoPlayer's position tracking stays accurate while this processor is
     * changing the signal's length. See [TempoPitchAudioProcessorChain].
     * Falls back to an unscaled passthrough before any real output exists
     * yet (immediately after configure/flush, or while inactive).
     */
    fun mediaDurationForPlayoutDuration(playoutDurationUs: Long): Long {
        if (!isActive() || totalOutputFrames <= 0L) return playoutDurationUs
        return (playoutDurationUs.toDouble() * totalInputFrames / totalOutputFrames).toLong()
    }

    companion object {
        private const val TAG = "TempoPitchAudioProcessor"
        private const val FLUSH_SILENCE_FRAMES = 4096
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        // How close tempoRatio/pitchRatio need to be to 1.0 to take the
        // zero-allocation bypass path in queueInput() instead of running
        // real WSOLA/resampling. setTempo/setPitchSemitones already
        // coerce to a 0.25..3.0 / -12..12 semitone range, both far
        // outside this window, so this only ever catches the genuine
        // "user hasn't touched it" default state, never a real subtle
        // adjustment silently getting skipped.
        private const val IDENTITY_EPSILON = 0.0005
    }
}
