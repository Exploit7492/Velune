package com.nikhil.yt.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.nikhil.yt.eq.data.ImpulseResponse
import com.nikhil.yt.eq.data.ImpulseResponseLoader
import com.nikhil.yt.eq.data.ParametricEQ
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
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

    // Convolution-based tone shaping — the first stage of the chain, ahead
    // of the biquad EQ bands below, so a loaded correction IR (measured
    // headphone/DAC response, etc.) sets the baseline tonality and the
    // user's parametric bands sit on top of it as manual trim. This is the
    // "real impulse-response based tone shaping" piece the biquad chain
    // alone can't do — see ConvolutionAudioProcessor for how it works.
    private var convolutionEngine: ConvolutionAudioProcessor? = null
    private var pendingImpulseResponseFile: File? = null
    private var convolutionRequestedEnabled: Boolean = false

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

    // Spectrum analyzer — taps the fully-processed signal (after
    // convolution/bands/bass-boost/width/limiter, so what it visualizes is
    // exactly what reaches the output) and feeds it a mono downmix, one
    // sample at a time, from the same audio thread this whole class already
    // runs on. Off by default (see SpectrumAnalyzer.enabled) so there's no
    // extra per-sample cost while no spectrum UI is on screen; the EQ
    // screen turns it on/off via setSpectrumAnalyzerEnabled as it appears/
    // disappears from composition. See EqualizerService for the UI-facing
    // wrapper and SpectrumAnalyzer for the FFT/binning itself.
    val spectrumAnalyzer = SpectrumAnalyzer()

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

    /**
     * Loads a WAV impulse response for convolution-based tone shaping. If
     * the sample rate isn't known yet (processor not configured for a
     * stream), the file is remembered and actually parsed/resampled once
     * [configure] runs — same "pending" approach [applyProfile] uses for a
     * profile applied before the first track loads.
     */
    @Synchronized
    fun loadImpulseResponse(file: File) {
        if (sampleRate == 0) {
            pendingImpulseResponseFile = file
            Timber.tag(TAG).d("Sample rate not known yet, storing IR file as pending: ${file.name}")
            return
        }
        try {
            val ir = FileInputStream(file).use { ImpulseResponseLoader.load(it, sampleRate) }
            installImpulseResponse(ir)
            Timber.tag(TAG).d("Loaded IR ${file.name}: ${ir.left.size} taps @ ${ir.sampleRate}Hz")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load impulse response: ${file.name}")
        }
    }

    private fun installImpulseResponse(ir: ImpulseResponse) {
        convolutionEngine = ConvolutionAudioProcessor(ir).also { it.enabled = convolutionRequestedEnabled }
    }

    @Synchronized
    fun setConvolutionEnabled(enabled: Boolean) {
        convolutionRequestedEnabled = enabled
        convolutionEngine?.enabled = enabled
    }

    @Synchronized
    fun clearImpulseResponse() {
        convolutionEngine = null
        pendingImpulseResponseFile = null
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

    fun setSpectrumAnalyzerEnabled(enabled: Boolean) {
        spectrumAnalyzer.enabled = enabled
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
        spectrumAnalyzer.configure(sampleRate)

        pendingImpulseResponseFile?.let { file ->
            try {
                val ir = FileInputStream(file).use { ImpulseResponseLoader.load(it, sampleRate) }
                installImpulseResponse(ir)
                Timber.tag(TAG).d("Applied pending IR ${file.name}: ${ir.left.size} taps @ ${ir.sampleRate}Hz")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load pending impulse response: ${file.name}")
            }
            pendingImpulseResponseFile = null
        }

        return inputAudioFormat
    }

    // Deliberately unconditional — NOT "true only if some control currently
    // deviates from its default," which is what this used to be (an
    // OR-chain checking equalizerEnabled/bassBoostFilter/balance/
    // stereoWidth/limiter/convolution/spectrumAnalyzer one at a time).
    //
    // That pattern is a structural bug generator, not a series of
    // unrelated bugs: Media3's AudioProcessingPipeline only re-evaluates
    // isActive() for every processor at flush() (a format change, i.e. a
    // new track — confirmed against the real androidx/media source, see
    // TempoPitchAudioProcessor.isActive()'s doc comment for the full
    // mechanism, and the fix history below for how this exact class of
    // bug was found and fixed there first). Any control not itself
    // checked in that OR-chain — preamp gain, for instance, which was
    // missing from it entirely — silently did nothing when changed
    // mid-track: the processor was never in the active chain to begin
    // with, so queueInput() (where every one of these stages actually
    // runs) never fired for it. It only started working once *some other*
    // control also went non-default and flipped the OR true, or the next
    // track's own flush picked up the by-then-nondefault value — which is
    // exactly why it read as "only takes effect after leaving and
    // reopening the app": backgrounding/returning doesn't fix anything by
    // itself, but switching tracks does, and users conflate the two.
    //
    // The actual fix is the same one already applied to
    // TempoPitchAudioProcessor: stop trying to keep an exhaustive,
    // hand-maintained list of "is anything non-default" in sync with
    // every control this class has (or will ever gain), and instead keep
    // the processor in the active chain unconditionally whenever the
    // format is supported. Every stage below — biquad filters, bass
    // boost, balance, stereo width, the limiter, the convolver, the
    // spectrum tap, preamp gain — is already its own confirmed no-op at
    // its default/disabled state (empty filter list, null bass filter,
    // balance=0 producing unity gain, stereoWidth=1.0 short-circuiting to
    // identity, the limiter/convolver/analyzer each gated by their own
    // `enabled` flag, preampGain=1.0 at 0dB being an exact identity
    // multiply), so always running them costs a small constant amount of
    // CPU and changes no audio when nothing is engaged — the same
    // trade-off already justified and shipped for tempo/pitch.
    override fun isActive(): Boolean = isActive

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
        val convolver = convolutionEngine
        for (i in 0 until sampleCount / channelCount) {
            if (channelCount == 2) {
                var left = input.getShort().toDouble() / 32768.0
                var right = input.getShort().toDouble() / 32768.0

                if (convolver != null) {
                    val convolved = convolver.process(left, right)
                    left = convolved.first
                    right = convolved.second
                }

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

                if (spectrumAnalyzer.enabled) {
                    spectrumAnalyzer.accept((left + right) * 0.5)
                }

                output.putShort((left * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                output.putShort((right * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            } else {
                var sample = input.getShort().toDouble() / 32768.0
                convolver?.let { sample = it.processMono(sample) }
                filters.forEach { sample = it.processSample(sample) }
                bassFilter?.let { sample = it.processSample(sample) }
                sample *= preampGain
                sample = lookaheadLimiter.processMono(sample)
                if (spectrumAnalyzer.enabled) {
                    spectrumAnalyzer.accept(sample)
                }
                output.putShort((sample * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
        }
    }

    private fun processFloatBuffer(input: ByteBuffer, output: ByteBuffer, sampleCount: Int) {
        val leftGain = preampGain * (1.0 - balance.coerceAtLeast(0.0))
        val rightGain = preampGain * (1.0 + balance.coerceAtMost(0.0))
        val bassFilter = bassBoostFilter
        val convolver = convolutionEngine
        for (i in 0 until sampleCount / channelCount) {
            if (channelCount == 2) {
                var left = input.getFloat().toDouble()
                var right = input.getFloat().toDouble()

                if (convolver != null) {
                    val convolved = convolver.process(left, right)
                    left = convolved.first
                    right = convolved.second
                }

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

                if (spectrumAnalyzer.enabled) {
                    spectrumAnalyzer.accept((left + right) * 0.5)
                }

                output.putFloat(left.coerceIn(-1.0, 1.0).toFloat())
                output.putFloat(right.coerceIn(-1.0, 1.0).toFloat())
            } else {
                var sample = input.getFloat().toDouble()
                convolver?.let { sample = it.processMono(sample) }
                filters.forEach { sample = it.processSample(sample) }
                bassFilter?.let { sample = it.processSample(sample) }
                sample *= preampGain
                sample = lookaheadLimiter.processMono(sample)
                if (spectrumAnalyzer.enabled) {
                    spectrumAnalyzer.accept(sample)
                }
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
        convolutionEngine?.reset()
        spectrumAnalyzer.reset()
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
