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
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive && equalizerEnabled && filters.isNotEmpty()

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
        for (i in 0 until sampleCount / channelCount) {
            if (channelCount == 2) {
                var left = input.getShort().toDouble() / 32768.0
                var right = input.getShort().toDouble() / 32768.0

                filters.forEach { filter ->
                    val (l, r) = filter.processStereo(left, right)
                    left = l
                    right = r
                }

                left *= preampGain
                right *= preampGain

                output.putShort((left * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                output.putShort((right * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            } else {
                var sample = input.getShort().toDouble() / 32768.0
                filters.forEach { sample = it.processSample(sample) }
                sample *= preampGain
                output.putShort((sample * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
        }
    }

    private fun processFloatBuffer(input: ByteBuffer, output: ByteBuffer, sampleCount: Int) {
        for (i in 0 until sampleCount / channelCount) {
            if (channelCount == 2) {
                var left = input.getFloat().toDouble()
                var right = input.getFloat().toDouble()

                filters.forEach { filter ->
                    val (l, r) = filter.processStereo(left, right)
                    left = l
                    right = r
                }

                left *= preampGain
                right *= preampGain

                output.putFloat(left.coerceIn(-1.0, 1.0).toFloat())
                output.putFloat(right.coerceIn(-1.0, 1.0).toFloat())
            } else {
                var sample = input.getFloat().toDouble()
                filters.forEach { sample = it.processSample(sample) }
                sample *= preampGain
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
