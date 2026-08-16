package com.nikhil.yt.eq.audio

import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * FFT-driven spectrum analyzer for the EQ UI. Fed post-chain (after
 * convolution/bands/bass-boost/width/limiter, so what it shows matches what
 * actually reaches the output) one mono sample at a time from the audio
 * thread via [accept]; the UI thread reads the latest analysis with
 * [snapshot] on its own schedule (a Compose polling loop, see
 * AxionEqViewModel). Reuses [Fft] the same way ConvolutionAudioProcessor
 * does — the convolution engine's transform, not a new one.
 *
 * Threading: [accept] runs on the audio thread and must never block or
 * allocate on the hot path beyond what's pre-sized in [configure]. The
 * finished bar array for each completed block is published via a single
 * [AtomicReference.set] so [snapshot] (called from the UI thread) always
 * sees either a complete previous block or a complete new one, never a
 * half-written array — no locks needed on either side.
 */
class SpectrumAnalyzer {

    companion object {
        /** Power-of-two analysis block size. 1024 @ 44.1kHz ≈ 23ms/block —
         * fine frequency resolution (~43Hz/bin) at a refresh rate faster
         * than the UI will ever poll. */
        const val FFT_SIZE = 1024

        /** Bars the UI draws, log-spaced across the audible range — same
         * bar count regardless of sample rate, so the UI layer never needs
         * to know it. */
        const val BAR_COUNT = 28

        private const val MIN_FREQ_HZ = 20.0
        private const val MIN_DB = -70.0
        private const val MAX_DB = 0.0
    }

    @Volatile
    var enabled: Boolean = false

    private val fft = Fft(FFT_SIZE)
    private val window = DoubleArray(FFT_SIZE) { i ->
        // Hann window — standard choice for a magnitude-only spectrum
        // display, tames spectral leakage from analyzing an arbitrary,
        // non-periodic block of program audio.
        0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))
    }

    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)
    private var fillPos = 0

    // Precomputed once per [configure]: for each output bar, the inclusive
    // FFT-bin range it pools (log-spaced edges converted to bin indices).
    // Rebuilt whenever the sample rate changes since bin-to-Hz mapping
    // depends on it.
    private var binRangeStart = IntArray(BAR_COUNT)
    private var binRangeEnd = IntArray(BAR_COUNT)
    private var configuredSampleRate = 0

    private val latest = AtomicReference(FloatArray(BAR_COUNT))

    fun configure(sampleRate: Int) {
        if (sampleRate <= 0 || sampleRate == configuredSampleRate) return
        configuredSampleRate = sampleRate
        fillPos = 0

        val nyquist = sampleRate / 2.0
        val maxFreq = min(nyquist, 20000.0)
        val binHz = sampleRate.toDouble() / FFT_SIZE
        val maxBinIndex = FFT_SIZE / 2 - 1

        // Log-spaced frequency edges from MIN_FREQ_HZ to maxFreq, one more
        // edge than there are bars, then map each [edge_i, edge_i+1) pair to
        // an FFT bin-index range. Bars below the first usable bin or above
        // Nyquist just end up with a zero-width/empty range, which
        // [analyzeBlock] treats as silence rather than a crash.
        val logMin = ln(MIN_FREQ_HZ)
        val logMax = ln(maxFreq)
        val edgesHz = DoubleArray(BAR_COUNT + 1) { i ->
            kotlin.math.exp(logMin + (logMax - logMin) * i / BAR_COUNT)
        }
        for (bar in 0 until BAR_COUNT) {
            val startBin = (edgesHz[bar] / binHz).toInt().coerceIn(1, maxBinIndex)
            val endBin = (edgesHz[bar + 1] / binHz).toInt().coerceIn(startBin, maxBinIndex)
            binRangeStart[bar] = startBin
            binRangeEnd[bar] = endBin
        }
    }

    /** Feeds one post-chain mono sample (already downmixed by the caller). */
    fun accept(sample: Double) {
        if (!enabled || configuredSampleRate == 0) return
        re[fillPos] = sample * window[fillPos]
        im[fillPos] = 0.0
        fillPos++
        if (fillPos >= FFT_SIZE) {
            analyzeBlock()
            fillPos = 0
        }
    }

    private fun analyzeBlock() {
        fft.forward(re, im)

        // Hann-window compensation: the window's own DC gain is 0.5, so
        // magnitudes need scaling back up to read as if unwindowed.
        val normFactor = 2.0 / FFT_SIZE
        val bars = FloatArray(BAR_COUNT)
        for (bar in 0 until BAR_COUNT) {
            var peakMag = 0.0
            for (bin in binRangeStart[bar]..binRangeEnd[bar]) {
                val magnitude = sqrt(re[bin] * re[bin] + im[bin] * im[bin]) * normFactor
                if (magnitude > peakMag) peakMag = magnitude
            }
            val db = if (peakMag > 1e-10) 20.0 * log10(peakMag) else MIN_DB
            val normalized = ((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0.0, 1.0)
            bars[bar] = normalized.toFloat()
        }
        latest.set(bars)
    }

    /** Latest completed block's per-bar levels, each normalized 0f..1f. */
    fun snapshot(): FloatArray = latest.get()

    fun reset() {
        fillPos = 0
        latest.set(FloatArray(BAR_COUNT))
    }
}
