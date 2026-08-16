package com.nikhil.yt.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.nikhil.yt.eq.data.ParametricEQ
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

@UnstableApi
class CustomEqualizerAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false
    private var equalizerEnabled = false

    private var inputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var filters: List<BiquadFilter> = emptyList()
    private var preampGain: Double = 1.0
    private var pendingProfile: ParametricEQ? = null

    // Master-bus controls — independent of the per-band profile, always applied
    // on top of it (or on their own if no profile/filters are set), same as a
    // real mixer's master bus sits above individual channel EQ.
    private var balance: Double = 0.0 // -1.0 = full left, 0 = center, 1.0 = full right
    private var bassBoostGainDb: Double = 0.0 // 0..12 dB shelf boost below ~120Hz
    private var bassBoostFilter: BiquadFilter? = null

    // Stereo width — mid/side processing, distinct from [balance]: balance
    // shifts *volume* between the two channels, width narrows/widens the
    // *stereo image* itself (how far apart L/R content sounds) without
    // touching overall level. 1.0 = unchanged, 0.0 = mono (mid only),
    // >1.0 = wider than the source, same knob Neutron's Stereo module and
    // most mastering-style DSP chains expose.
    private var stereoWidth: Double = 1.0

    // Master limiter — the last stage in the chain, after preamp/band gain/
    // bass boost/balance/width have already been applied, so it catches the
    // combined result of all of them rather than any one stage in isolation
    // (the same "output ceiling" position Poweramp's limiter and Neutron's
    // final brickwall/lookahead limiter both use).
    //
    // This is a true lookahead limiter (see LookaheadLimiter): it holds audio
    // back by a few milliseconds so the gain reduction is already ramped in
    // by the time a transient reaches the output, instead of only reacting to
    // the sample it's currently given. That's the difference between this and
    // the previous instant soft-knee curve — the soft knee could still only
    // bend *after* a peak arrived; it never saw it coming.
    private val lookaheadLimiter = LookaheadLimiter(sampleRate = 44100)

    companion object {
        private const val TAG = "CustomEqualizerAudioProcessor"
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }

    @Synchronized
    fun applyProfile(parametricEQ: ParametricEQ) {
        if (sampleRate == 0) {
            Timber.tag(TAG).d("Audio processor not configured yet. Storing profile as pending with ${parametricEQ.bands.size} bands")
            pendingProfile = parametricEQ
            return
        }
        preampGain = 10.0.pow(parametricEQ.preamp / 20.0)
        createFilters(parametricEQ.bands)
        equalizerEnabled = true
        filters.forEach { it.reset() }
        Timber.tag(TAG).d("Applied EQ profile with ${filters.size} bands and ${parametricEQ.preamp} dB preamp")
    }

    @Synchronized
    fun disable() {
        equalizerEnabled = false
        filters = emptyList()
        preampGain = 1.0
        pendingProfile = null
        Timber.tag(TAG).d("Equalizer disabled")
    }

    fun isEnabled(): Boolean = equalizerEnabled

    @Synchronized
    fun setBalance(value: Double) {
        balance = value.coerceIn(-1.0, 1.0)
    }

    @Synchronized
    fun setBassBoost(gainDb: Double) {
        bassBoostGainDb = gainDb.coerceIn(0.0, 12.0)
        rebuildBassBoostFilter()
    }

    @Synchronized
    fun setStereoWidth(value: Double) {
        stereoWidth = value.coerceIn(0.0, 2.0)
    }

    @Synchronized
    fun setLimiter(enabled: Boolean, ceilingDb: Double) {
        lookaheadLimiter.setCeilingDb(ceilingDb)
        lookaheadLimiter.setEnabled(enabled)
    }

    private fun applyStereoWidth(left: Double, right: Double): Pair<Double, Double> {
        if (kotlin.math.abs(stereoWidth - 1.0) < 0.001) return left to right
        val mid = (left + right) * 0.5
        val side = (left - right) * 0.5 * stereoWidth
        return (mid + side) to (mid - side)
    }

    private fun rebuildBassBoostFilter() {
        bassBoostFilter = if (sampleRate > 0 && bassBoostGainDb > 0.01) {
            BiquadFilter(
                sampleRate = sampleRate,
                frequency = 120.0,
                gain = bassBoostGainDb,
                q = 0.9,
                filterType = com.nikhil.yt.eq.data.FilterType.LSC
            )
        } else {
            null
        }
    }

    private fun createFilters(bands: List<com.nikhil.yt.eq.data.ParametricEQBand>) {
        if (sampleRate == 0) {
            Timber.tag(TAG).w("Cannot create filters: sample rate not set")
            return
        }
        filters = bands
            .filter { it.enabled && it.frequency < sampleRate / 2.0 }
            .map { band ->
                BiquadFilter(
                    sampleRate = sampleRate,
                    frequency = band.frequency,
                    gain = band.gain,
                    q = band.q,
                    filterType = band.filterType
                )
            }
        Timber.tag(TAG).d("Created ${filters.size} biquad filters from ${bands.size} bands")
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        Timber.tag(TAG).d("Configured: sampleRate=$sampleRate, channels=$channelCount, encoding=$encoding")

        pendingProfile?.let { profile ->
            preampGain = 10.0.pow(profile.preamp / 20.0)
            createFilters(profile.bands)
            equalizerEnabled = true
            pendingProfile = null
            Timber.tag(TAG).d("Applied pending profile with ${filters.size} bands")
        }

        isActive = (encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT)
        rebuildBassBoostFilter()
        lookaheadLimiter.configure(sampleRate)
        return inputAudioFormat
    }

    override fun isActive(): Boolean =
        isActive && (
            (equalizerEnabled && filters.isNotEmpty()) ||
                bassBoostFilter != null ||
                balance != 0.0 ||
                kotlin.math.abs(stereoWidth - 1.0) >= 0.001 ||
                lookaheadLimiter.enabled
            )

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remaining = limit - position

        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        when (encoding) {
            C.ENCODING_PCM_FLOAT -> processFloatBuffer(inputBuffer, outputBuffer, remaining / 4)
            C.ENCODING_PCM_16BIT -> processShortBuffer(inputBuffer, outputBuffer, remaining / 2)
            else -> outputBuffer.put(inputBuffer)
        }

        inputBuffer.position(limit)
        outputBuffer.flip()
        this.inputBuffer = inputBuffer
        this.outputBuffer = outputBuffer
    }

    private fun processShortBuffer(input: ByteBuffer, output: ByteBuffer, sampleCount: Int) {
        val leftGain = preampGain * (1.0 - balance.coerceAtLeast(0.0))
        val rightGain = preampGain * (1.0 + balance.coerceAtMost(0.0))
        val bassFilter = bassBoostFilter
        for (i in 0 until sampleCount / channelCount) {
            if (channelCount == 2) {
                var left = input.getShort().toDouble() / 32768.0
                var right = input.getShort().toDouble() / 32768.0

                filters.forEach { filter ->
                    val (l, r) = filter.processStereo(left, right)
                    left = l
                    right = r
                }
                bassFilter?.let {
                    val (l, r) = it.processStereo(left, right)
                    left = l
                    right = r
                }

                val widened = applyStereoWidth(left, right)
                left = widened.first
                right = widened.second

                left *= leftGain
                right *= rightGain

                val limited = lookaheadLimiter.process(left, right)
                left = limited.first
                right = limited.second

                output.putShort((left * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                output.putShort((right * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            } else {
                var sample = input.getShort().toDouble() / 32768.0
                filters.forEach { sample = it.processSample(sample) }
                bassFilter?.let { sample = it.processSample(sample) }
                sample *= preampGain
                sample = lookaheadLimiter.processMono(sample)
                output.putShort((sample * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
        }
    }

    private fun processFloatBuffer(input: ByteBuffer, output: ByteBuffer, sampleCount: Int) {
        val leftGain = preampGain * (1.0 - balance.coerceAtLeast(0.0))
        val rightGain = preampGain * (1.0 + balance.coerceAtMost(0.0))
        val bassFilter = bassBoostFilter
        for (i in 0 until sampleCount / channelCount) {
            if (channelCount == 2) {
                var left = input.getFloat().toDouble()
                var right = input.getFloat().toDouble()

                filters.forEach { filter ->
                    val (l, r) = filter.processStereo(left, right)
                    left = l
                    right = r
                }
                bassFilter?.let {
                    val (l, r) = it.processStereo(left, right)
                    left = l
                    right = r
                }

                val widened = applyStereoWidth(left, right)
                left = widened.first
                right = widened.second

                left *= leftGain
                right *= rightGain

                val limited = lookaheadLimiter.process(left, right)
                left = limited.first
                right = limited.second

                output.putFloat(left.coerceIn(-1.0, 1.0).toFloat())
                output.putFloat(right.coerceIn(-1.0, 1.0).toFloat())
            } else {
                var sample = input.getFloat().toDouble()
                filters.forEach { sample = it.processSample(sample) }
                bassFilter?.let { sample = it.processSample(sample) }
                sample *= preampGain
                sample = lookaheadLimiter.processMono(sample)
                output.putFloat(sample.coerceIn(-1.0, 1.0).toFloat())
            }
        }
    }

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
        filters.forEach { it.reset() }
        lookaheadLimiter.reset()
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        inputBuffer = EMPTY_BUFFER
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
        filters.forEach { it.reset() }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }
}
