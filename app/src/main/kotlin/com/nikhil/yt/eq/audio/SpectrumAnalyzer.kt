package com.nikhil.yt.eq.audio

import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A single published analysis result: the current (ballistics-smoothed) bar
 * levels and their peak-hold caps, each normalized 0f..1f over the same
 * dB range. Bundled together so a UI frame that reads both always sees a
 * matching pair from the same block — reading [SpectrumAnalyzer.snapshot]
 * once and destructuring beats two separate reads that could straddle a
 * publish.
 */
data class SpectrumSnapshot(
    val levels: FloatArray,
    val peaks: FloatArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is SpectrumSnapshot &&
            levels.contentEquals(other.levels) && peaks.contentEquals(other.peaks))

    override fun hashCode(): Int = 31 * levels.contentHashCode() + peaks.contentHashCode()
}

/**
 * FFT-driven spectrum analyzer for the EQ UI, built to the same ballpark as
 * a real-time analyzer (RTA) in Neutron/Poweramp rather than a raw block-
 * by-block magnitude dump:
 *
 * - **75% overlapping analysis windows** (hop = FFT_SIZE/4), not one FFT
 *   per full block. A block-aligned analyzer only ever looks at
 *   non-overlapping 23ms chunks, so a transient landing near a block
 *   boundary gets smeared or missed; overlapping windows re-analyze near-
 *   identical (mostly-shared) data 4x more often, which is what makes a
 *   professional RTA look continuous instead of stepping every ~23ms.
 * - **Ballistic bar smoothing** — instant attack (a level rising is shown
 *   immediately, same as a peak meter), bounded-rate release (a level
 *   dropping decays at a fixed dB/sec instead of snapping to the new,
 *   possibly near-silent block). Without this a spectrum display looks
 *   like static; every real analyzer applies some form of it.
 * - **Peak-hold caps** — per-bar peak that latches on a new high, holds
 *   flat for a short window, then decays at its own (slower) dB/sec rate.
 *   Standard RTA feature, gives the eye a stable reference for where a
 *   transient peaked even after the bar itself has moved on.
 *
 * Both ballistics are driven by **elapsed audio-domain time**
 * (hopSize/sampleRate seconds per block), not wall-clock time or UI frame
 * timing — the same block interval elapses whether or not anything is
 * polling [snapshot], so the decay rate is exact regardless of UI frame
 * rate or how often the UI happens to read it.
 *
 * Fed post-chain (after convolution/bands/bass-boost/width/limiter, so
 * what it shows matches what actually reaches the output) one mono sample
 * at a time from the audio thread via [accept]; the UI thread reads the
 * latest analysis with [snapshot] on its own schedule (a Compose polling
 * loop, see AxionEqViewModel). Reuses [Fft] the same way
 * ConvolutionAudioProcessor does — the convolution engine's transform, not
 * a new one.
 *
 * Threading: [accept] runs on the audio thread. All ballistics/peak-hold
 * state (`smoothedLevels`, `peakLevels`, `peakHoldRemainingMs`, the
 * circular `history` buffer) is touched only from that thread — there's
 * exactly one writer. [snapshot] (UI thread) only ever reads the published
 * [AtomicReference], never the working arrays directly, so a single
 * `AtomicReference.set` per finished block is the only cross-thread
 * handoff: the UI always sees either a complete previous result or a
 * complete new one, never a partially-updated one.
 */
class SpectrumAnalyzer {

    companion object {
        /** Power-of-two analysis window size. 1024 @ 44.1kHz ≈ 23ms window
         * — fine frequency resolution (~43Hz/bin). */
        const val FFT_SIZE = 1024

        /** 75% overlap: a new analysis every FFT_SIZE/4 samples (~5.8ms
         * @44.1kHz, ~172 analyses/sec) — see class doc for why overlap
         * instead of block-aligned analysis. */
        private const val OVERLAP_FACTOR = 4
        private const val HOP_SIZE = FFT_SIZE / OVERLAP_FACTOR

        /** Bars the UI draws, log-spaced across the audible range — same
         * bar count regardless of sample rate, so the UI layer never needs
         * to know it. */
        const val BAR_COUNT = 28

        private const val MIN_FREQ_HZ = 20.0
        private const val MIN_DB = -70.0
        private const val MAX_DB = 0.0
        private const val DB_RANGE = MAX_DB - MIN_DB

        /** Bar release ballistics — how fast a bar falls back down once the
         * signal at that frequency drops, in normalized-height dB/sec.
         * Attack is instant (see [analyzeBlock]), only release is rate-
         * limited — the same asymmetric shape a peak meter uses. */
        private const val BAR_RELEASE_DB_PER_SEC = 24.0

        /** How long a peak-hold cap sits flat before it starts decaying. */
        private const val PEAK_HOLD_MS = 1200.0

        /** Peak-hold decay rate once the hold window has elapsed — slower
         * than the bar's own release so the cap visibly trails behind it. */
        private const val PEAK_DECAY_DB_PER_SEC = 10.0

        /** "Nice" frequency labels a UI can request via [barIndexForLabel]
         * — the same round numbers Poweramp/Neutron-style graphic displays
         * tick, filtered by the caller to whatever's under the configured
         * Nyquist/max frequency. */
        val LABEL_FREQUENCIES_HZ = doubleArrayOf(
            20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0
        )
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

    // Circular history of the last FFT_SIZE raw (unwindowed) samples.
    // accept() only ever writes one slot and bumps a counter — the actual
    // windowing + FFT only happens once per HOP_SIZE samples, in
    // analyzeBlock(). historyPos is the index of the *oldest* sample
    // currently held (== next write position), so analyzeBlock reads the
    // buffer starting there to get chronological order.
    private val history = DoubleArray(FFT_SIZE)
    private var historyPos = 0
    private var samplesSinceAnalysis = 0

    private val re = DoubleArray(FFT_SIZE)
    private val im = DoubleArray(FFT_SIZE)

    // Precomputed once per [configure]: for each output bar, the inclusive
    // FFT-bin range it pools (log-spaced edges converted to bin indices),
    // and the bar's center frequency for UI labeling. Rebuilt whenever the
    // sample rate changes since bin-to-Hz mapping depends on it.
    private var binRangeStart = IntArray(BAR_COUNT)
    private var binRangeEnd = IntArray(BAR_COUNT)
    private var barCenterHz = DoubleArray(BAR_COUNT)
    private var configuredSampleRate = 0
    private var elapsedMsPerBlock = 0.0

    // Ballistics state — single-writer (audio thread), read only via the
    // published snapshot below.
    private val smoothedLevels = FloatArray(BAR_COUNT)
    private val peakLevels = FloatArray(BAR_COUNT)
    private val peakHoldRemainingMs = DoubleArray(BAR_COUNT)

    private val latest = AtomicReference(SpectrumSnapshot(FloatArray(BAR_COUNT), FloatArray(BAR_COUNT)))

    fun configure(sampleRate: Int) {
        if (sampleRate <= 0 || sampleRate == configuredSampleRate) return
        configuredSampleRate = sampleRate
        historyPos = 0
        samplesSinceAnalysis = 0
        elapsedMsPerBlock = HOP_SIZE * 1000.0 / sampleRate
        history.fill(0.0)
        smoothedLevels.fill(0f)
        peakLevels.fill(0f)
        peakHoldRemainingMs.fill(0.0)
        latest.set(SpectrumSnapshot(FloatArray(BAR_COUNT), FloatArray(BAR_COUNT)))

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
            exp(logMin + (logMax - logMin) * i / BAR_COUNT)
        }
        for (bar in 0 until BAR_COUNT) {
            val startBin = (edgesHz[bar] / binHz).toInt().coerceIn(1, maxBinIndex)
            val endBin = (edgesHz[bar + 1] / binHz).toInt().coerceIn(startBin, maxBinIndex)
            binRangeStart[bar] = startBin
            binRangeEnd[bar] = endBin
            barCenterHz[bar] = sqrt(edgesHz[bar] * edgesHz[bar + 1])
        }
    }

    /** Feeds one post-chain mono sample (already downmixed by the caller). */
    fun accept(sample: Double) {
        if (!enabled || configuredSampleRate == 0) return
        history[historyPos] = sample
        historyPos = (historyPos + 1) % FFT_SIZE
        samplesSinceAnalysis++
        if (samplesSinceAnalysis >= HOP_SIZE) {
            analyzeBlock()
            samplesSinceAnalysis = 0
        }
    }

    private fun analyzeBlock() {
        for (i in 0 until FFT_SIZE) {
            val sample = history[(historyPos + i) % FFT_SIZE]
            re[i] = sample * window[i]
            im[i] = 0.0
        }
        fft.forward(re, im)

        // Hann-window compensation: the window's own DC gain is 0.5, so
        // magnitudes need scaling back up to read as if unwindowed.
        val normFactor = 2.0 / FFT_SIZE
        val elapsedSec = elapsedMsPerBlock / 1000.0
        val releaseStep = (BAR_RELEASE_DB_PER_SEC / DB_RANGE * elapsedSec).toFloat()
        val peakDecayStep = (PEAK_DECAY_DB_PER_SEC / DB_RANGE * elapsedSec).toFloat()

        for (bar in 0 until BAR_COUNT) {
            var peakMag = 0.0
            for (bin in binRangeStart[bar]..binRangeEnd[bar]) {
                val magnitude = sqrt(re[bin] * re[bin] + im[bin] * im[bin]) * normFactor
                if (magnitude > peakMag) peakMag = magnitude
            }
            val db = if (peakMag > 1e-10) 20.0 * log10(peakMag) else MIN_DB
            val raw = ((db - MIN_DB) / DB_RANGE).coerceIn(0.0, 1.0).toFloat()

            // Bar ballistics: instant attack, rate-limited release.
            smoothedLevels[bar] = if (raw >= smoothedLevels[bar]) {
                raw
            } else {
                max(raw, smoothedLevels[bar] - releaseStep)
            }

            // Peak-hold: latch on a new high (and reset the hold timer),
            // otherwise count down the hold window, then decay — never
            // below the current bar level, since a cap can't sit under
            // the bar it's capping.
            if (raw >= peakLevels[bar]) {
                peakLevels[bar] = raw
                peakHoldRemainingMs[bar] = PEAK_HOLD_MS
            } else if (peakHoldRemainingMs[bar] > 0.0) {
                peakHoldRemainingMs[bar] -= elapsedMsPerBlock
            } else {
                peakLevels[bar] = max(smoothedLevels[bar], peakLevels[bar] - peakDecayStep)
            }
        }
        latest.set(SpectrumSnapshot(smoothedLevels.copyOf(), peakLevels.copyOf()))
    }

    /** Latest ballistics-smoothed bars + peak-hold caps, each 0f..1f. */
    fun snapshot(): SpectrumSnapshot = latest.get()

    /**
     * Bar index whose center frequency is closest to [frequencyHz], for a
     * UI drawing axis labels (see [LABEL_FREQUENCIES_HZ]). Returns -1 if
     * not configured yet or [frequencyHz] is outside every bar's range.
     */
    fun barIndexForLabel(frequencyHz: Double): Int {
        if (configuredSampleRate == 0) return -1
        var closest = -1
        var closestDelta = Double.MAX_VALUE
        for (bar in barCenterHz.indices) {
            val delta = kotlin.math.abs(ln(barCenterHz[bar]) - ln(frequencyHz))
            if (delta < closestDelta) {
                closestDelta = delta
                closest = bar
            }
        }
        return closest
    }

    /** True once [configure] has run and [barIndexForLabel] is meaningful. */
    fun isConfigured(): Boolean = configuredSampleRate != 0

    fun reset() {
        historyPos = 0
        samplesSinceAnalysis = 0
        history.fill(0.0)
        smoothedLevels.fill(0f)
        peakLevels.fill(0f)
        peakHoldRemainingMs.fill(0.0)
        latest.set(SpectrumSnapshot(FloatArray(BAR_COUNT), FloatArray(BAR_COUNT)))
    }
}
