/*
 * Velune - Biquad IIR filter for parametric EQ.
 * Direct Form 1 implementation. Double-precision coefficients,
 * float-precision processing for speed.
 * Ported from Echo Music (GPL-3.0).
 */

package com.nikhil.yt.eq.audio

import com.nikhil.yt.eq.data.FilterType
import com.nikhil.yt.eq.data.ParametricEQ
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class BiquadFilter {

    private var b0: Double = 1.0
    private var b1: Double = 0.0
    private var b2: Double = 0.0
    private var a1: Double = 0.0
    private var a2: Double = 0.0

    private var x1: Float = 0f
    private var x2: Float = 0f
    private var y1: Float = 0f
    private var y2: Float = 0f

    fun configure(sampleRate: Int, eq: ParametricEQ) {
        val w0 = 2.0 * PI * eq.frequency / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * eq.q)
        val a = 10.0.pow(eq.gain / 40.0) // for shelf filters
        val sqrtA = sqrt(a)

        when (eq.filterType) {
            FilterType.PEAK -> {
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosW0
                b2 = 1.0 - alpha * a
                val a0 = 1.0 + alpha / a
                a1 = (-2.0 * cosW0) / a0
                a2 = (1.0 - alpha / a) / a0
                b0 /= a0
                b1 /= a0
                b2 /= a0
            }
            FilterType.LOW_SHELF -> {
                b0 = a * ((a + 1) - (a - 1) * cosW0 + 2 * sqrtA * alpha)
                b1 = 2 * a * ((a - 1) - (a + 1) * cosW0)
                b2 = a * ((a + 1) - (a - 1) * cosW0 - 2 * sqrtA * alpha)
                val a0 = (a + 1) + (a - 1) * cosW0 + 2 * sqrtA * alpha
                a1 = (-2 * ((a - 1) + (a + 1) * cosW0)) / a0
                a2 = ((a + 1) + (a - 1) * cosW0 - 2 * sqrtA * alpha) / a0
                b0 /= a0
                b1 /= a0
                b2 /= a0
            }
            FilterType.HIGH_SHELF -> {
                b0 = a * ((a + 1) + (a - 1) * cosW0 + 2 * sqrtA * alpha)
                b1 = -2 * a * ((a - 1) + (a + 1) * cosW0)
                b2 = a * ((a + 1) + (a - 1) * cosW0 - 2 * sqrtA * alpha)
                val a0 = (a + 1) - (a - 1) * cosW0 + 2 * sqrtA * alpha
                a1 = (2 * ((a - 1) - (a + 1) * cosW0)) / a0
                a2 = ((a + 1) - (a - 1) * cosW0 - 2 * sqrtA * alpha) / a0
                b0 /= a0
                b1 /= a0
                b2 /= a0
            }
            FilterType.LOW_PASS -> {
                b0 = (1.0 - cosW0) / 2.0
                b1 = 1.0 - cosW0
                b2 = (1.0 - cosW0) / 2.0
                val a0 = 1.0 + alpha
                a1 = (-2.0 * cosW0) / a0
                a2 = (1.0 - alpha) / a0
                b0 /= a0
                b1 /= a0
                b2 /= a0
            }
            FilterType.HIGH_PASS -> {
                b0 = (1.0 + cosW0) / 2.0
                b1 = -(1.0 + cosW0)
                b2 = (1.0 + cosW0) / 2.0
                val a0 = 1.0 + alpha
                a1 = (-2.0 * cosW0) / a0
                a2 = (1.0 - alpha) / a0
                b0 /= a0
                b1 /= a0
                b2 /= a0
            }
            FilterType.BAND_PASS -> {
                b0 = alpha
                b1 = 0.0
                b2 = -alpha
                val a0 = 1.0 + alpha
                a1 = (-2.0 * cosW0) / a0
                a2 = (1.0 - alpha) / a0
                b0 /= a0
                b1 /= a0
                b2 /= a0
            }
            FilterType.NOTCH -> {
                b0 = 1.0
                b1 = -2.0 * cosW0
                b2 = 1.0
                val a0 = 1.0 + alpha
                a1 = (-2.0 * cosW0) / a0
                a2 = (1.0 - alpha) / a0
                b0 /= a0
                b1 /= a0
                b2 /= a0
            }
            FilterType.ALL_PASS -> {
                b0 = 1.0 - alpha
                b1 = -2.0 * cosW0
                b2 = 1.0 + alpha
                val a0 = 1.0 + alpha
                a1 = (-2.0 * cosW0) / a0
                a2 = (1.0 - alpha) / a0
                b0 /= a0
                b1 /= a0
                b2 /= a0
            }
        }

        // Reset state on reconfiguration
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }

    fun process(input: FloatArray, output: FloatArray, offset: Int, length: Int) {
        for (i in offset until offset + length) {
            val x0 = input[i]
            val y0 = (b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2).toFloat()
            output[i] = y0
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
    }

    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }

    companion object {
        private fun Double.pow(exp: Double): Double = kotlin.math.pow(this, exp)
    }
}
