package com.nikhil.yt.eq.audio

import com.nikhil.yt.eq.data.FilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class BiquadFilter(
    private val sampleRate: Int,
    private val frequency: Double,
    private val gain: Double,
    private val q: Double = 1.41,
    private val filterType: FilterType = FilterType.PK
) {
    private var a0 = 1.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0

    private var x1L = 0.0
    private var x2L = 0.0
    private var y1L = 0.0
    private var y2L = 0.0

    private var x1R = 0.0
    private var x2R = 0.0
    private var y1R = 0.0
    private var y2R = 0.0

    init {
        calculateCoefficients()
    }

    private fun calculateCoefficients() {
        val A = 10.0.pow(gain / 40.0)
        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q)

        when (filterType) {
            FilterType.PK -> calculatePeakingCoefficients(A, cosOmega, alpha)
            FilterType.LSC -> calculateLowShelfCoefficients(A, cosOmega, sinOmega)
            FilterType.HSC -> calculateHighShelfCoefficients(A, cosOmega, sinOmega)
            FilterType.LPQ -> calculateLowPassCoefficients(cosOmega, alpha)
            FilterType.HPQ -> calculateHighPassCoefficients(cosOmega, alpha)
            FilterType.BPF -> calculateBandPassCoefficients(alpha, cosOmega)
            FilterType.NOTCH -> calculateNotchCoefficients(cosOmega, alpha)
            FilterType.APF -> calculateAllPassCoefficients(cosOmega, alpha)
        }
    }

    private fun calculatePeakingCoefficients(A: Double, cosOmega: Double, alpha: Double) {
        b0 = 1.0 + alpha * A
        b1 = -2.0 * cosOmega
        b2 = 1.0 - alpha * A
        a0 = 1.0 + alpha / A
        a1 = -2.0 * cosOmega
        a2 = 1.0 - alpha / A
        normalize()
    }

    private fun calculateLowShelfCoefficients(A: Double, cosOmega: Double, sinOmega: Double) {
        val sqrtA = sqrt(A)
        val S = 1.0
        val alpha = sinOmega / 2.0 * sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0)
        b0 = A * ((A + 1.0) - (A - 1.0) * cosOmega + 2.0 * sqrtA * alpha)
        b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosOmega)
        b2 = A * ((A + 1.0) - (A - 1.0) * cosOmega - 2.0 * sqrtA * alpha)
        a0 = (A + 1.0) + (A - 1.0) * cosOmega + 2.0 * sqrtA * alpha
        a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosOmega)
        a2 = (A + 1.0) + (A - 1.0) * cosOmega - 2.0 * sqrtA * alpha
        normalize()
    }

    private fun calculateHighShelfCoefficients(A: Double, cosOmega: Double, sinOmega: Double) {
        val sqrtA = sqrt(A)
        val S = 1.0
        val alpha = sinOmega / 2.0 * sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0)
        b0 = A * ((A + 1.0) + (A - 1.0) * cosOmega + 2.0 * sqrtA * alpha)
        b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosOmega)
        b2 = A * ((A + 1.0) + (A - 1.0) * cosOmega - 2.0 * sqrtA * alpha)
        a0 = (A + 1.0) - (A - 1.0) * cosOmega + 2.0 * sqrtA * alpha
        a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosOmega)
        a2 = (A + 1.0) - (A - 1.0) * cosOmega - 2.0 * sqrtA * alpha
        normalize()
    }

    private fun calculateLowPassCoefficients(cosOmega: Double, alpha: Double) {
        b0 = (1.0 - cosOmega) / 2.0
        b1 = 1.0 - cosOmega
        b2 = (1.0 - cosOmega) / 2.0
        a0 = 1.0 + alpha
        a1 = -2.0 * cosOmega
        a2 = 1.0 - alpha
        normalize()
    }

    private fun calculateHighPassCoefficients(cosOmega: Double, alpha: Double) {
        b0 = (1.0 + cosOmega) / 2.0
        b1 = -(1.0 + cosOmega)
        b2 = (1.0 + cosOmega) / 2.0
        a0 = 1.0 + alpha
        a1 = -2.0 * cosOmega
        a2 = 1.0 - alpha
        normalize()
    }

    private fun calculateBandPassCoefficients(alpha: Double, cosOmega: Double) {
        b0 = alpha
        b1 = 0.0
        b2 = -alpha
        a0 = 1.0 + alpha
        a1 = -2.0 * cosOmega
        a2 = 1.0 - alpha
        normalize()
    }

    private fun calculateNotchCoefficients(cosOmega: Double, alpha: Double) {
        b0 = 1.0
        b1 = -2.0 * cosOmega
        b2 = 1.0
        a0 = 1.0 + alpha
        a1 = -2.0 * cosOmega
        a2 = 1.0 - alpha
        normalize()
    }

    private fun calculateAllPassCoefficients(cosOmega: Double, alpha: Double) {
        b0 = 1.0 - alpha
        b1 = -2.0 * cosOmega
        b2 = 1.0 + alpha
        a0 = 1.0 + alpha
        a1 = -2.0 * cosOmega
        a2 = 1.0 - alpha
        normalize()
    }

    private fun normalize() {
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 /= a0
        a2 /= a0
        a0 = 1.0
    }

    fun processSample(input: Double): Double {
        val output = b0 * input + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L
        x1L = input
        y2L = y1L
        y1L = output
        return output
    }

    fun processStereo(inputLeft: Double, inputRight: Double): Pair<Double, Double> {
        val outputLeft = b0 * inputLeft + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L
        x1L = inputLeft
        y2L = y1L
        y1L = outputLeft

        val outputRight = b0 * inputRight + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R
        x1R = inputRight
        y2R = y1R
        y1R = outputRight

        return Pair(outputLeft, outputRight)
    }

    fun reset() {
        x1L = 0.0; x2L = 0.0; y1L = 0.0; y2L = 0.0
        x1R = 0.0; x2R = 0.0; y1R = 0.0; y2R = 0.0
    }
}
