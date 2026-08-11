/*
 * Velune - Custom Parametric EQ AudioProcessor for ExoPlayer.
 * Runs a cascade of biquad filters on the audio stream.
 * Ported from Echo Music (GPL-3.0).
 */

package com.nikhil.yt.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.nikhil.yt.eq.data.ParametricEQ
import com.nikhil.yt.eq.data.ParametricEQProfile
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class CustomEqualizerAudioProcessor : AudioProcessor {

    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var pendingOutputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private val biquads = mutableListOf<BiquadFilter>()
    private var enabled = false
    private var preamp = 1f
    private var sampleRate = 48000

    @Volatile
    private var pendingProfile: ParametricEQProfile? = null

    fun setProfile(profile: ParametricEQProfile?) {
        pendingProfile = profile
    }

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            this.inputAudioFormat = AudioFormat.NOT_SET
            outputAudioFormat = AudioFormat.NOT_SET
            pendingOutputBuffer = EMPTY_BUFFER
            return AudioFormat.NOT_SET
        }

        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat
        this.sampleRate = inputAudioFormat.sampleRate

        // Apply any pending profile now that we know the sample rate
        pendingProfile?.let { applyProfile(it) }
        pendingProfile = null

        pendingOutputBuffer = EMPTY_BUFFER
        return outputAudioFormat
    }

    override fun isActive(): Boolean {
        return enabled && inputAudioFormat != AudioFormat.NOT_SET && biquads.isNotEmpty()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remaining = limit - position

        if (pendingOutputBuffer.capacity() < remaining) {
            pendingOutputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            pendingOutputBuffer.clear()
        }

        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            processFloatBuffer(inputBuffer, pendingOutputBuffer, remaining / 4)
        } else {
            processShortBuffer(inputBuffer, pendingOutputBuffer, remaining / 2)
        }

        inputBuffer.position(limit)
        pendingOutputBuffer.flip()
        outputBuffer = pendingOutputBuffer
    }

    private fun processFloatBuffer(input: ByteBuffer, output: ByteBuffer, sampleCount: Int) {
        val temp = FloatArray(sampleCount)
        input.asFloatBuffer().get(temp)

        // Apply preamp
        if (preamp != 1f) {
            for (i in temp.indices) temp[i] *= preamp
        }

        // Run biquad cascade
        for (biquad in biquads) {
            biquad.process(temp, temp, 0, temp.size)
        }

        output.asFloatBuffer().put(temp)
    }

    private fun processShortBuffer(input: ByteBuffer, output: ByteBuffer, sampleCount: Int) {
        val temp = FloatArray(sampleCount)
        val shortView = input.asShortBuffer()
        for (i in 0 until sampleCount) {
            temp[i] = shortView.get() / 32768f
        }

        // Apply preamp
        if (preamp != 1f) {
            for (i in temp.indices) temp[i] *= preamp
        }

        // Run biquad cascade
        for (biquad in biquads) {
            biquad.process(temp, temp, 0, temp.size)
        }

        val outShort = output.asShortBuffer()
        for (i in 0 until sampleCount) {
            val clamped = (temp[i] * 32768f).toInt().coerceIn(-32768, 32767)
            outShort.put(clamped.toShort())
        }
    }

    private fun applyProfile(profile: ParametricEQProfile) {
        biquads.clear()
        preamp = 10f.pow(profile.preamp / 20f)
        for (band in profile.bands) {
            val filter = BiquadFilter()
            filter.configure(sampleRate, band)
            biquads.add(filter)
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        for (biquad in biquads) biquad.reset()
    }

    override fun reset() {
        flush()
        biquads.clear()
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        private fun Float.pow(exp: Float): Float = kotlin.math.pow(this, exp)
    }
}
