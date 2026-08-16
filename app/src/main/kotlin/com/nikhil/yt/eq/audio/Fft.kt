package com.nikhil.yt.eq.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 Cooley-Tukey FFT over [n] (must be a power of
 * two). Twiddle factors and the bit-reversal permutation are precomputed
 * once per instance and reused across every transform, since the
 * convolution engine calls this thousands of times per second on the same
 * fixed block size — recomputing `cos`/`sin` per call would be wasted work.
 *
 * This is a plain, portable Kotlin implementation (no native/SIMD), which is
 * the right trade-off for the IR lengths this is meant for: short-to-medium
 * device/headphone correction impulse responses (tens to a few hundred ms).
 * A multi-second convolution-reverb-length IR would want a native/SIMD FFT
 * for comfortable real-time headroom on lower-end devices — out of scope
 * here, flagged for whoever picks that up.
 */
class Fft(val n: Int) {

    init {
        require(n > 0 && (n and (n - 1)) == 0) { "Fft size must be a power of two, got $n" }
    }

    private val bitReverse: IntArray = IntArray(n)
    private val cosTable: DoubleArray = DoubleArray(n / 2)
    private val sinTable: DoubleArray = DoubleArray(n / 2)

    init {
        var shift = 1
        while ((1 shl shift) < n) shift++
        val bits = shift
        for (i in 0 until n) {
            var reversed = 0
            var value = i
            for (b in 0 until bits) {
                reversed = (reversed shl 1) or (value and 1)
                value = value shr 1
            }
            bitReverse[i] = reversed
        }
        for (k in 0 until n / 2) {
            val angle = -2.0 * PI * k / n
            cosTable[k] = cos(angle)
            sinTable[k] = sin(angle)
        }
    }

    /** Forward transform, in place. [re]/[im] must both have length [n]. */
    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = false)

    /** Inverse transform, in place, including the 1/n scaling. */
    fun inverse(re: DoubleArray, im: DoubleArray) {
        transform(re, im, inverse = true)
        val scale = 1.0 / n
        for (i in 0 until n) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        require(re.size == n && im.size == n) { "Array size must match Fft size $n" }

        // Bit-reversal permutation.
        for (i in 0 until n) {
            val j = bitReverse[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var size = 2
        while (size <= n) {
            val half = size / 2
            val tableStep = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val tIndex = k * tableStep
                    val cosV = cosTable[tIndex]
                    val sinV = if (inverse) -sinTable[tIndex] else sinTable[tIndex]

                    val evenRe = re[j]
                    val evenIm = im[j]
                    val oddRe = re[j + half]
                    val oddIm = im[j + half]

                    val twRe = oddRe * cosV - oddIm * sinV
                    val twIm = oddRe * sinV + oddIm * cosV

                    re[j] = evenRe + twRe
                    im[j] = evenIm + twIm
                    re[j + half] = evenRe - twRe
                    im[j + half] = evenIm - twIm
                    k++
                }
                start += size
            }
            size = size shl 1
        }
    }

    companion object {
        private val cache = HashMap<Int, Fft>()

        /** Shared, cached instance per size — avoids rebuilding twiddle tables. */
        @Synchronized
        fun forSize(n: Int): Fft = cache.getOrPut(n) { Fft(n) }

        fun nextPowerOfTwo(value: Int): Int {
            var p = 1
            while (p < value) p = p shl 1
            return p
        }
    }
}
