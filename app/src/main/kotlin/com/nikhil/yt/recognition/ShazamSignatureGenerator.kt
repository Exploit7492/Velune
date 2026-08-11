package com.nikhil.yt.recognition

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

internal object ShazamSignatureGenerator {

    private const val SAMPLE_RATE = 16_000
    private const val FFT_SIZE = 2048
    private const val FFT_OUTPUT_SIZE = FFT_SIZE / 2 + 1
    private const val MAX_PEAKS = 255
    private const val MAX_TIME_SECONDS = 12.0
    private const val RING_BUF_SIZE = 256

    private const val BAND_250_520 = 0
    private const val BAND_520_1450 = 1
    private const val BAND_1450_3500 = 2
    private const val BAND_3500_5500 = 3

    private val HANNING = DoubleArray(FFT_SIZE) { i ->
        0.5 * (1.0 - cos(2.0 * PI * (i + 1).toDouble() / 2049.0))
    }

    fun fromI16(samples: ByteArray): String {
        require(samples.size >= 2 && samples.size % 2 == 0) {
            "samples must be a non-empty byte array with even length (16-bit PCM)"
        }
        val pcm = ShortArray(samples.size / 2)
        ByteBuffer.wrap(samples).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        return SignatureGeneratorState().process(pcm)
    }

    private class SignatureGeneratorState {

        private val samplesRing = IntArray(FFT_SIZE)
        private var samplesPos = 0

        private val fftOutputs = Array(RING_BUF_SIZE) { DoubleArray(FFT_OUTPUT_SIZE) }
        private var fftPos = 0
        private var fftNumWritten = 0

        private val spreadFfts = Array(RING_BUF_SIZE) { DoubleArray(FFT_OUTPUT_SIZE) }
        private var spreadPos = 0
        private var spreadNumWritten = 0

        private var numSamples = 0

        private val bandPeaks = Array(4) { mutableListOf<FrequencyPeak>() }
        private var totalPeaks = 0

        fun process(pcm: ShortArray): String {
            var offset = 0
            while (offset + 128 <= pcm.size) {
                val elapsedSec = numSamples.toDouble() / SAMPLE_RATE
                if (elapsedSec >= MAX_TIME_SECONDS && totalPeaks >= MAX_PEAKS) break

                numSamples += 128
                feedSamples(pcm, offset, 128)
                doFFT()
                doPeakSpreadingAndRecognition()
                offset += 128
            }
            return encodeSignature()
        }

        private fun feedSamples(pcm: ShortArray, start: Int, count: Int) {
            for (k in start until start + count) {
                samplesRing[samplesPos] = pcm[k].toInt()
                samplesPos = (samplesPos + 1) % FFT_SIZE
            }
        }

        private fun doFFT() {
            val windowed = DoubleArray(FFT_SIZE) { i ->
                samplesRing[(samplesPos + i) % FFT_SIZE].toDouble() * HANNING[i]
            }
            val result = computeRfft(windowed)
            result.copyInto(fftOutputs[fftPos])
            fftPos = (fftPos + 1) % RING_BUF_SIZE
            fftNumWritten++
        }

        private fun doPeakSpreadingAndRecognition() {
            doPeakSpreading()
            if (spreadNumWritten >= 47) {
                doPeakRecognition()
            }
        }

        private fun doPeakSpreading() {
            val lastFftIdx = (fftPos - 1 + RING_BUF_SIZE) % RING_BUF_SIZE
            val spread = fftOutputs[lastFftIdx].copyOf()

            for (pos in 0 until FFT_OUTPUT_SIZE - 2) {
                spread[pos] = maxOf(spread[pos], spread[pos + 1], spread[pos + 2])
            }

            for (pos in 0 until FFT_OUTPUT_SIZE) {
                var maxVal = spread[pos]
                for (offset in intArrayOf(-1, -3, -6)) {
                    val idx = ((spreadPos + offset) % RING_BUF_SIZE + RING_BUF_SIZE) % RING_BUF_SIZE
                    val oldVal = spreadFfts[idx][pos]
                    if (oldVal > maxVal) maxVal = oldVal
                    spreadFfts[idx][pos] = maxVal
                }
            }

            spread.copyInto(spreadFfts[spreadPos])
            spreadPos = (spreadPos + 1) % RING_BUF_SIZE
            spreadNumWritten++
        }

        private fun doPeakRecognition() {
            val fftMinus46 = fftOutputs[(fftPos - 46 + RING_BUF_SIZE * 2) % RING_BUF_SIZE]
            val spreadMinus49 = spreadFfts[(spreadPos - 49 + RING_BUF_SIZE * 2) % RING_BUF_SIZE]

            val otherOffsets = intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)

            for (binPos in 10 until FFT_OUTPUT_SIZE - 8) {
                val fftVal = fftMinus46[binPos]
                if (fftVal < 1.0 / 64.0 || fftVal < spreadMinus49[binPos]) continue

                var maxNeighborSpread49 = 0.0
                for (neighborOffset in intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)) {
                    val v = spreadMinus49[binPos + neighborOffset]
                    if (v > maxNeighborSpread49) maxNeighborSpread49 = v
                }
                if (fftVal <= maxNeighborSpread49) continue

                var maxNeighborOther = maxNeighborSpread49
                for (otherOffset in otherOffsets) {
                    val spreadIdx = ((spreadPos + otherOffset) % RING_BUF_SIZE + RING_BUF_SIZE) % RING_BUF_SIZE
                    val v = spreadFfts[spreadIdx][binPos - 1]
                    if (v > maxNeighborOther) maxNeighborOther = v
                }
                if (fftVal <= maxNeighborOther) continue

                val fftNumber = spreadNumWritten - 46

                val peakMag = ln(max(1.0 / 64.0, fftVal)) * 1477.3 + 6144
                val peakMagBefore = ln(max(1.0 / 64.0, fftMinus46[binPos - 1])) * 1477.3 + 6144
                val peakMagAfter = ln(max(1.0 / 64.0, fftMinus46[binPos + 1])) * 1477.3 + 6144

                val peakVariation1 = peakMag * 2 - peakMagBefore - peakMagAfter
                val peakVariation2 = (peakMagAfter - peakMagBefore) * 32 / peakVariation1

                val correctedBin = binPos * 64.0 + peakVariation2
                val frequencyHz = correctedBin * (16000.0 / 2.0 / 1024.0 / 64.0)

                val band = when {
                    frequencyHz < 250.0 -> continue
                    frequencyHz < 520.0 -> BAND_250_520
                    frequencyHz < 1450.0 -> BAND_520_1450
                    frequencyHz < 3500.0 -> BAND_1450_3500
                    frequencyHz <= 5500.0 -> BAND_3500_5500
                    else -> continue
                }

                bandPeaks[band].add(
                    FrequencyPeak(
                        fftPassNumber = fftNumber,
                        peakMagnitude = peakMag.toInt(),
                        correctedPeakFrequencyBin = correctedBin.toInt()
                    )
                )
                totalPeaks++
            }
        }

        private fun encodeSignature(): String {
            val contentsStream = ByteArrayOutputStream()

            for (bandId in 0..3) {
                val peaks = bandPeaks[bandId]
                if (peaks.isEmpty()) continue

                val peakBuf = ByteArrayOutputStream()
                var prevFftPassNumber = 0

                for (peak in peaks) {
                    val diff = peak.fftPassNumber - prevFftPassNumber
                    if (diff >= 255) {
                        peakBuf.write(0xFF)
                        writeLittleEndian32(peakBuf, peak.fftPassNumber)
                        prevFftPassNumber = peak.fftPassNumber
                    }
                    peakBuf.write(peak.fftPassNumber - prevFftPassNumber)
                    writeLittleEndian16(peakBuf, peak.peakMagnitude)
                    writeLittleEndian16(peakBuf, peak.correctedPeakFrequencyBin)
                    prevFftPassNumber = peak.fftPassNumber
                }

                val peakBytes = peakBuf.toByteArray()

                writeLittleEndian32(contentsStream, 0x60030040 + bandId)
                writeLittleEndian32(contentsStream, peakBytes.size)
                contentsStream.write(peakBytes)

                val padBytes = (4 - peakBytes.size % 4) % 4
                repeat(padBytes) { contentsStream.write(0) }
            }

            val contents = contentsStream.toByteArray()
            val sizeMinusHeader = contents.size + 8
            val samplesAndOffset = (numSamples + SAMPLE_RATE * 0.24).toInt()

            val headerBytes = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0xcafe2580.toInt())
                putInt(0)
                putInt(sizeMinusHeader)
                putInt(0x94119c00.toInt())
                putInt(0); putInt(0); putInt(0)
                putInt(3 shl 27)
                putInt(0); putInt(0)
                putInt(samplesAndOffset)
                putInt((15 shl 19) + 0x40000)
            }.array()

            val fullBuf = ByteArrayOutputStream(56 + contents.size)
            fullBuf.write(headerBytes)
            writeLittleEndian32(fullBuf, 0x40000000)
            writeLittleEndian32(fullBuf, contents.size + 8)
            fullBuf.write(contents)

            val fullBytes = fullBuf.toByteArray()

            val crc = CRC32()
            crc.update(fullBytes, 8, fullBytes.size - 8)
            val crc32Value = crc.value.toInt()

            fullBytes[4] = (crc32Value and 0xFF).toByte()
            fullBytes[5] = ((crc32Value shr 8) and 0xFF).toByte()
            fullBytes[6] = ((crc32Value shr 16) and 0xFF).toByte()
            fullBytes[7] = ((crc32Value shr 24) and 0xFF).toByte()

            return Base64.encodeToString(fullBytes, Base64.NO_WRAP)
        }

        private fun writeLittleEndian16(stream: ByteArrayOutputStream, value: Int) {
            stream.write(value and 0xFF)
            stream.write((value shr 8) and 0xFF)
        }

        private fun writeLittleEndian32(stream: ByteArrayOutputStream, value: Int) {
            stream.write(value and 0xFF)
            stream.write((value shr 8) and 0xFF)
            stream.write((value shr 16) and 0xFF)
            stream.write((value shr 24) and 0xFF)
        }

        private fun computeRfft(input: DoubleArray): DoubleArray {
            val n = input.size
            val output = DoubleArray(n / 2 + 1)
            val complex = Array(n) { DoubleArray(2) }
            for (i in 0 until n) {
                complex[i][0] = input[i]
                complex[i][1] = 0.0
            }
            fftIterative(complex)
            for (i in 0 until n / 2 + 1) {
                val real = complex[i][0]
                val imag = complex[i][1]
                output[i] = sqrt(real * real + imag * imag)
            }
            return output
        }

        private fun fftIterative(a: Array<DoubleArray>) {
            val n = a.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j >= bit) {
                    j -= bit
                    bit = bit shr 1
                }
                j += bit
                if (i < j) {
                    val temp = a[i]
                    a[i] = a[j]
                    a[j] = temp
                }
            }
            var len = 2
            while (len <= n) {
                val ang = 2 * PI / len
                val wlenReal = cos(ang)
                val wlenImag = kotlin.math.sin(ang)
                for (i in 0 until n step len) {
                    var wReal = 1.0
                    var wImag = 0.0
                    for (k in 0 until len / 2) {
                        val uReal = a[i + k][0]
                        val uImag = a[i + k][1]
                        val vReal = a[i + k + len / 2][0] * wReal - a[i + k + len / 2][1] * wImag
                        val vImag = a[i + k + len / 2][0] * wImag + a[i + k + len / 2][1] * wReal
                        a[i + k][0] = uReal + vReal
                        a[i + k][1] = uImag + vImag
                        a[i + k + len / 2][0] = uReal - vReal
                        a[i + k + len / 2][1] = uImag - vImag
                        val nextWReal = wReal * wlenReal - wImag * wlenImag
                        val nextWImag = wReal * wlenImag + wImag * wlenReal
                        wReal = nextWReal
                        wImag = nextWImag
                    }
                }
                len = len shl 1
            }
        }
    }

    private data class FrequencyPeak(
        val fftPassNumber: Int,
        val peakMagnitude: Int,
        val correctedPeakFrequencyBin: Int
    )
}
