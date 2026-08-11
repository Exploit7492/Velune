package com.nikhil.yt.recognition

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

data class DecodedAudio(
    val data: ByteArray,
    val channelCount: Int,
    val sampleRate: Int,
    val pcmEncoding: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedAudio) return false
        return data.contentEquals(other.data) &&
                channelCount == other.channelCount &&
                sampleRate == other.sampleRate &&
                pcmEncoding == other.pcmEncoding
    }
    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + channelCount
        result = 31 * result + sampleRate
        result = 31 * result + pcmEncoding
        return result
    }
}

object AudioResampler {
    fun resample(decodedAudio: DecodedAudio, targetSampleRate: Int): Result<DecodedAudio> {
        return try {
            val sourceRate = decodedAudio.sampleRate
            val sourceData = decodedAudio.data
            val bytesPerSample = when (decodedAudio.pcmEncoding) {
                AudioFormat.ENCODING_PCM_16BIT -> 2
                AudioFormat.ENCODING_PCM_8BIT -> 1
                else -> return Result.failure(IllegalArgumentException("Unsupported PCM encoding"))
            }
            val sourceSamples = sourceData.size / bytesPerSample
            val ratio = targetSampleRate.toDouble() / sourceRate
            val targetSamples = (sourceSamples * ratio).toInt()
            val targetData = ByteArray(targetSamples * bytesPerSample)

            if (decodedAudio.pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
                val sourceBuffer = ByteBuffer.wrap(sourceData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val targetBuffer = ByteBuffer.wrap(targetData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

                for (i in 0 until targetSamples) {
                    val sourceIndex = i / ratio
                    val index0 = sourceIndex.toInt()
                    val index1 = min(index0 + 1, sourceSamples - 1)
                    val fraction = sourceIndex - index0
                    val sample0 = sourceBuffer.get(index0).toInt()
                    val sample1 = sourceBuffer.get(index1).toInt()
                    val interpolated = (sample0 + fraction * (sample1 - sample0)).toInt().coerceIn(-32768, 32767).toShort()
                    targetBuffer.put(i, interpolated)
                }
            } else {
                return Result.failure(IllegalArgumentException("Only 16-bit PCM is supported"))
            }

            Result.success(DecodedAudio(
                data = targetData,
                channelCount = decodedAudio.channelCount,
                sampleRate = targetSampleRate,
                pcmEncoding = decodedAudio.pcmEncoding
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
