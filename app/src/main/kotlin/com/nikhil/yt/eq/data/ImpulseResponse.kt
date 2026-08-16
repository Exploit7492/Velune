package com.nikhil.yt.eq.data

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A loaded impulse response, resampled to the device's actual playback
 * sample rate so [com.nikhil.yt.eq.audio.PartitionedConvolver] never has to
 * think about a rate mismatch. [right] is null for a mono IR (applied
 * identically to both channels); non-null for a true stereo/dual-channel IR.
 */
data class ImpulseResponse(
    val sampleRate: Int,
    val left: DoubleArray,
    val right: DoubleArray?
)

/**
 * Minimal WAV (RIFF/PCM) parser for loading impulse response files. Supports
 * the formats an IR is realistically shipped in: 16-bit integer PCM and
 * 32-bit float PCM, mono or stereo. Not a general-purpose audio decoder —
 * for anything more exotic (compressed formats, >2 channels) the existing
 * ExoPlayer extractors are the right tool, this is intentionally small and
 * dependency-free since IR files are tiny compared to a track.
 */
object ImpulseResponseLoader {

    class UnsupportedWavException(message: String) : IOException(message)

    /**
     * Reads a WAV file from [input] and resamples it (simple linear
     * interpolation — adequate for a one-time IR load, not a hot path) to
     * [targetSampleRate] if it doesn't already match.
     */
    fun load(input: InputStream, targetSampleRate: Int): ImpulseResponse {
        val bytes = input.readBytes()
        if (bytes.size < 44) throw UnsupportedWavException("File too small to be a WAV")

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4).also { buffer.get(it) }
        if (String(riff, Charsets.US_ASCII) != "RIFF") throw UnsupportedWavException("Not a RIFF file")
        buffer.int // overall chunk size, unused
        val wave = ByteArray(4).also { buffer.get(it) }
        if (String(wave, Charsets.US_ASCII) != "WAVE") throw UnsupportedWavException("Not a WAVE file")

        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var audioFormat = 0
        var dataOffset = -1
        var dataSize = 0

        while (buffer.remaining() >= 8) {
            val chunkId = ByteArray(4).also { buffer.get(it) }
            val chunkIdStr = String(chunkId, Charsets.US_ASCII)
            val chunkSize = buffer.int
            if (chunkSize < 0 || chunkSize > buffer.remaining()) {
                // Malformed/truncated chunk size — stop parsing rather than
                // throw, in case what we've already found (a preceding fmt
                // chunk) is usable.
                break
            }
            when (chunkIdStr) {
                "fmt " -> {
                    val chunkStart = buffer.position()
                    audioFormat = buffer.short.toInt() and 0xFFFF
                    channels = buffer.short.toInt() and 0xFFFF
                    sampleRate = buffer.int
                    buffer.int // byte rate, unused
                    buffer.short // block align, unused
                    bitsPerSample = buffer.short.toInt() and 0xFFFF
                    buffer.position(chunkStart + chunkSize)
                }
                "data" -> {
                    dataOffset = buffer.position()
                    dataSize = chunkSize
                    buffer.position(buffer.position() + chunkSize)
                }
                else -> buffer.position(buffer.position() + chunkSize)
            }
            // WAV chunks are word-aligned; skip the pad byte if chunkSize is odd.
            if (chunkSize % 2 == 1 && buffer.remaining() > 0) buffer.get()
        }

        if (channels == 0 || sampleRate == 0 || dataOffset < 0) {
            throw UnsupportedWavException("Missing fmt or data chunk")
        }
        // 1 = PCM integer, 3 = IEEE float. Anything else (ADPCM, etc.) isn't
        // handled by this minimal parser.
        if (audioFormat != 1 && audioFormat != 3) {
            throw UnsupportedWavException("Unsupported WAV codec (format code $audioFormat)")
        }
        if (bitsPerSample != 16 && bitsPerSample != 32) {
            throw UnsupportedWavException("Unsupported bit depth: $bitsPerSample")
        }
        if (channels > 2) {
            throw UnsupportedWavException("Only mono/stereo IR files are supported, got $channels channels")
        }

        val bytesPerSample = bitsPerSample / 8
        val frameCount = dataSize / (bytesPerSample * channels)
        val left = DoubleArray(frameCount)
        val right = if (channels == 2) DoubleArray(frameCount) else null

        val data = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            for (ch in 0 until channels) {
                if (data.remaining() < bytesPerSample) throw EOFException("Truncated WAV data")
                val sample = when {
                    bitsPerSample == 16 -> data.short.toDouble() / 32768.0
                    audioFormat == 3 -> data.float.toDouble()
                    else -> data.int.toDouble() / 2147483648.0 // 32-bit int PCM
                }
                if (ch == 0) left[i] = sample else right?.set(i, sample)
            }
        }

        return if (sampleRate == targetSampleRate) {
            ImpulseResponse(targetSampleRate, left, right)
        } else {
            ImpulseResponse(
                sampleRate = targetSampleRate,
                left = resample(left, sampleRate, targetSampleRate),
                right = right?.let { resample(it, sampleRate, targetSampleRate) }
            )
        }
    }

    /** Linear-interpolation resample — fine for a one-time IR load. */
    private fun resample(input: DoubleArray, fromRate: Int, toRate: Int): DoubleArray {
        if (input.isEmpty() || fromRate == toRate) return input
        val ratio = toRate.toDouble() / fromRate.toDouble()
        val outLength = (input.size * ratio).toInt().coerceAtLeast(1)
        val output = DoubleArray(outLength)
        for (i in 0 until outLength) {
            val srcPos = i / ratio
            val i0 = srcPos.toInt().coerceIn(0, input.size - 1)
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = srcPos - i0
            output[i] = input[i0] * (1.0 - frac) + input[i1] * frac
        }
        return output
    }
}
