package com.nikhil.yt.eq.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A real lookahead limiter: it delays the audio by [lookaheadMs] and uses that
 * window to see a transient *before* it reaches the output, so the gain
 * reduction can already be in place by the time the peak arrives instead of
 * reacting a sample after it (which is what the old [CustomEqualizerAudioProcessor]
 * soft-knee curve did — it only ever saw the current sample, so on a fast
 * transient it could only bend the curve *after* the peak was already there).
 *
 * L/R gain reduction is linked (computed from whichever channel is louder and
 * applied equally to both) so a hot moment in one channel doesn't shift the
 * stereo image the way independent per-channel limiting would.
 *
 * Algorithm: classic sliding-window-minimum lookahead limiter.
 *  1. For every incoming sample, compute the gain that sample alone would need
 *     to stay under the ceiling.
 *  2. Push that gain into a monotonic deque covering the last [lookaheadSamples]
 *     samples, so the *front* of the deque is always the minimum required gain
 *     over that window — O(1) amortized instead of rescanning the window per
 *     sample.
 *  3. Output the delayed sample (from [lookaheadSamples] ago) multiplied by
 *     that minimum gain, smoothed with a fast attack / slow release envelope
 *     so recovery after a peak doesn't pump.
 */
class LookaheadLimiter(
    private var sampleRate: Int,
    private val lookaheadMs: Double = 5.0,
    private val releaseMs: Double = 60.0,
    private val attackMs: Double = 1.0
) {
    private var ceiling: Double = 1.0
    private var lookaheadSamples: Int = 1
    private var delayL: DoubleArray = DoubleArray(1)
    private var delayR: DoubleArray = DoubleArray(1)
    private var writeIndex: Int = 0
    private var filled: Int = 0

    // Monotonic deque of (sampleIndex, requiredGain), ascending gain order.
    private var dequeIndex = LongArray(1)
    private var dequeGain = DoubleArray(1)
    private var dequeHead = 0
    private var dequeTail = 0 // one-past-last valid entry

    private var sampleCounter: Long = 0
    private var currentGain: Double = 1.0
    private var attackCoeff: Double = 0.0
    private var releaseCoeff: Double = 0.0

    var enabled: Boolean = false
        private set

    init {
        configure(sampleRate)
    }

    @Synchronized
    fun configure(newSampleRate: Int) {
        if (newSampleRate <= 0) return
        sampleRate = newSampleRate
        lookaheadSamples = max(1, (sampleRate * lookaheadMs / 1000.0).toInt())
        delayL = DoubleArray(lookaheadSamples)
        delayR = DoubleArray(lookaheadSamples)
        // Deque can never hold more than one entry per sample in the window,
        // so lookaheadSamples + 1 is always enough capacity.
        dequeIndex = LongArray(lookaheadSamples + 1)
        dequeGain = DoubleArray(lookaheadSamples + 1)
        attackCoeff = timeConstantToCoeff(attackMs)
        releaseCoeff = timeConstantToCoeff(releaseMs)
        reset()
    }

    private fun timeConstantToCoeff(ms: Double): Double =
        1.0 - exp(-1.0 / (sampleRate * (ms / 1000.0)))

    @Synchronized
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) reset()
    }

    @Synchronized
    fun setCeilingDb(ceilingDb: Double) {
        ceiling = 10.0.pow(ceilingDb.coerceIn(-12.0, 0.0) / 20.0)
    }

    @Synchronized
    fun reset() {
        delayL.fill(0.0)
        delayR.fill(0.0)
        writeIndex = 0
        filled = 0
        dequeHead = 0
        dequeTail = 0
        sampleCounter = 0
        currentGain = 1.0
    }

    /** How many silent/zero samples the limiter is currently holding back. */
    fun latencySamples(): Int = lookaheadSamples

    /**
     * Process one stereo frame. Returns the delayed, gain-reduced frame.
     * Must be called once per input frame in order — this is a streaming
     * delay line, not a stateless per-sample function.
     */
    @Synchronized
    fun process(left: Double, right: Double): Pair<Double, Double> {
        if (!enabled) return left to right

        val peak = max(abs(left), abs(right))
        val requiredGain = if (peak > ceiling) (ceiling / peak).coerceIn(0.0, 1.0) else 1.0

        // Evict entries from the back that are >= the new gain — they can
        // never be the window minimum again while this one is in range.
        while (dequeTail > dequeHead && dequeGain[dequeTail - 1] >= requiredGain) {
            dequeTail--
        }
        dequeIndex[dequeTail] = sampleCounter
        dequeGain[dequeTail] = requiredGain
        dequeTail++

        // Evict entries that have fallen out of the lookahead window.
        val windowStart = sampleCounter - lookaheadSamples + 1
        while (dequeHead < dequeTail && dequeIndex[dequeHead] < windowStart) {
            dequeHead++
        }

        val targetGain = if (dequeHead < dequeTail) dequeGain[dequeHead] else 1.0

        // Fast attack when reducing gain, slower release when recovering,
        // so recovery after a transient doesn't audibly pump.
        currentGain = if (targetGain < currentGain) {
            currentGain + (targetGain - currentGain) * attackCoeff
        } else {
            currentGain + (targetGain - currentGain) * releaseCoeff
        }

        val outL = delayL[writeIndex]
        val outR = delayR[writeIndex]
        delayL[writeIndex] = left
        delayR[writeIndex] = right
        writeIndex++
        if (writeIndex >= lookaheadSamples) writeIndex = 0
        if (filled < lookaheadSamples) filled++
        sampleCounter++

        val gain = min(currentGain, 1.0)
        return (outL * gain) to (outR * gain)
    }

    /** Mono convenience — treats the single channel as both L and R. */
    fun processMono(sample: Double): Double = process(sample, sample).first
}
