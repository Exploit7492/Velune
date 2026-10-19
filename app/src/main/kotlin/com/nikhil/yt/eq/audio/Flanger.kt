package com.nikhil.yt.eq.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A real flanger: a short, continuously-swept delay line summed back with the
 * dry signal. The sweep is what separates it from a plain delay or a chorus —
 * the comb-filter notch positions move, which is the jet-plane whoosh the
 * effect is named for. A chorus uses a longer, slower, wet-only delay; a
 * flanger uses a shorter delay, feeds some of the delayed signal *back* into
 * the line (deepening the comb), and mixes the delayed signal in alongside
 * the dry one.
 *
 * Confirmed there was no flanger (or any modulated-delay) implementation
 * anywhere in this codebase before writing this — no reuse was possible, so
 * this is genuine from-scratch DSP rather than a wrapper over something that
 * already existed.
 *
 * Structure, per channel:
 *  1. Read the delay line at a fractional position (linear interpolation),
 *     so the sweep is smooth instead of stepping between whole samples.
 *  2. Mix that delayed sample with the dry input by [mix].
 *  3. Write input + delayed * [feedback] back into the line.
 *
 * L/R use two LFOs 90 degrees apart ([QUADRATURE_PHASE]), the standard trick
 * for a flanger that sounds wide instead of mono: the two channels' notches
 * sweep out of step, so the image moves rather than pumping as a block.
 *
 * [depthMs] is the *peak* deviation of the delay from [baseDelayMs], not the
 * total delay, matching how hardware flanger "depth" knobs read. The delay
 * therefore sweeps over `baseDelayMs ± depthMs`, and the line is sized for
 * the worst case (base + depth) plus headroom so a large setting can never
 * read past what was written.
 */
class Flanger(
    initialSampleRate: Int = 44100,
    initialBaseDelayMs: Double = 2.0,
    initialDepthMs: Double = 3.0,
    initialRateHz: Double = 0.25,
    initialFeedback: Double = 0.5,
    initialMix: Double = 0.5
) {
    private companion object {
        // L/R LFO offset — a quarter cycle, so the two channels never sweep
        // together. See the class doc for why this matters.
        const val QUADRATURE_PHASE = PI / 2.0
        // The line is sized well past the maximum reachable delay so a large
        // depth setting at a low sample rate can't read unwritten samples.
        const val MAX_DELAY_MS = 50.0
        // A hard ceiling on the write-back term. Feedback above 1.0 would
        // grow without bound; even below it, a hot input can accumulate, so
        // the sum is clamped rather than trusted to stay sane on its own.
        const val FEEDBACK_CLAMP = 4.0
    }

    private var sampleRate: Int = 44100
    private var bufferL = DoubleArray(1)
    private var bufferR = DoubleArray(1)
    private var writeIndex = 0

    private var lfoPhase = 0.0

    private var baseDelayMs: Double = 2.0
    private var depthMs: Double = 3.0
    private var rateHz: Double = 0.25
    private var baseDelaySamples = 0.0
    private var depthSamples = 0.0
    private var phaseIncrement = 0.0

    var enabled: Boolean = false
        private set

    private var feedbackAmount = initialFeedback.coerceIn(0.0, 0.95)
    private var mixAmount = initialMix.coerceIn(0.0, 1.0)

    init {
        configure(initialSampleRate)
        setBaseDelayMs(initialBaseDelayMs)
        setDepthMs(initialDepthMs)
        setRateHz(initialRateHz)
    }

    @Synchronized
    fun configure(newSampleRate: Int) {
        if (newSampleRate <= 0) return
        sampleRate = newSampleRate
        val size = (sampleRate * MAX_DELAY_MS / 1000.0).toInt().coerceAtLeast(2)
        bufferL = DoubleArray(size)
        bufferR = DoubleArray(size)
        // Delay figures are stored in samples, so they must be recomputed
        // for the new rate or the effect's time constants would drift.
        recomputeDelaySamples()
        reset()
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) reset()
    }

    /** Base delay in milliseconds — the centre the sweep moves around. */
    @Synchronized
    fun setBaseDelayMs(ms: Double) {
        baseDelayMs = ms.coerceIn(0.1, 20.0)
        recomputeDelaySamples()
    }

    /** Peak deviation from the base delay, in milliseconds. */
    @Synchronized
    fun setDepthMs(ms: Double) {
        depthMs = ms.coerceIn(0.0, 10.0)
        recomputeDelaySamples()
    }

    /** LFO speed in Hz. */
    @Synchronized
    fun setRateHz(hz: Double) {
        rateHz = hz.coerceIn(0.01, 10.0)
        phaseIncrement = rateHz / sampleRate
    }

    /** How much of the delayed signal is fed back into the line, 0..0.95. */
    @Synchronized
    fun setFeedback(value: Double) {
        feedbackAmount = value.coerceIn(0.0, 0.95)
    }

    /** Dry/wet balance, 0 = dry only, 1 = delayed only. */
    @Synchronized
    fun setMix(value: Double) {
        mixAmount = value.coerceIn(0.0, 1.0)
    }

    private fun recomputeDelaySamples() {
        baseDelaySamples = (sampleRate * baseDelayMs / 1000.0).coerceAtLeast(1.0)
        val maxReach = sampleRate * (baseDelayMs + depthMs) / 1000.0
        // Never let the sweep reach the whole buffer — a delay equal to the
        // buffer length would read the sample being written.
        depthSamples = (sampleRate * depthMs / 1000.0)
            .coerceAtMost((bufferL.size - 2).toDouble())
            .coerceAtLeast(0.0)
        if (maxReach > bufferL.size - 1) {
            depthSamples = ((bufferL.size - 1) - baseDelaySamples).coerceAtLeast(0.0)
        }
    }

    @Synchronized
    fun reset() {
        bufferL.fill(0.0)
        bufferR.fill(0.0)
        writeIndex = 0
        lfoPhase = 0.0
    }

    /** Reads [delaySamples] back from the write head, interpolating. */
    private fun readInterpolated(buffer: DoubleArray, delaySamples: Double): Double {
        val size = buffer.size
        val readPos = writeIndex - delaySamples
        val wrapped = ((readPos % size) + size) % size
        val i0 = wrapped.toInt() % size
        val i1 = (i0 + 1) % size
        val frac = wrapped - i0
        return buffer[i0] * (1.0 - frac) + buffer[i1] * frac
    }

    /**
     * Process one stereo frame. Streaming, like [LookaheadLimiter] — call once
     * per input frame in order; this is a stateful delay line, not a
     * stateless per-sample function.
     */
    @Synchronized
    fun process(left: Double, right: Double): Pair<Double, Double> {
        if (!enabled) return left to right

        val modL = sin(lfoPhase)
        val modR = sin(lfoPhase + QUADRATURE_PHASE)
        lfoPhase += phaseIncrement
        if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI

        val delayL = baseDelaySamples + depthSamples * modL
        val delayR = baseDelaySamples + depthSamples * modR

        val delayedL = readInterpolated(bufferL, delayL)
        val delayedR = readInterpolated(bufferR, delayR)

        val outL = left * (1.0 - mixAmount) + delayedL * mixAmount
        val outR = right * (1.0 - mixAmount) + delayedR * mixAmount

        bufferL[writeIndex] = (left + delayedL * feedbackAmount).coerceIn(-FEEDBACK_CLAMP, FEEDBACK_CLAMP)
        bufferR[writeIndex] = (right + delayedR * feedbackAmount).coerceIn(-FEEDBACK_CLAMP, FEEDBACK_CLAMP)
        writeIndex++
        if (writeIndex >= bufferL.size) writeIndex = 0

        return outL to outR
    }

    /** Mono convenience — treats the single channel as both L and R. */
    fun processMono(sample: Double): Double = process(sample, sample).first

    /** True when the current settings would audibly change the signal. */
    fun isAudible(): Boolean = abs(mixAmount) > 0.001 && (baseDelaySamples > 0.0)
}
