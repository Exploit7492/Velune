package com.nikhil.yt.eq.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Streaming WSOLA (Waveform-Similarity Overlap-Add) time-stretcher --
 * changes the *duration* of a signal without changing its pitch. This is
 * the actual "pro level" piece [TempoPitchAudioProcessor] is built
 * around: it's the same family of algorithm SoundTouch/Neutron/Poweramp
 * use, as opposed to Media3's built-in `SonicAudioProcessor`, which uses
 * a much cheaper (and audibly rougher at extreme ratios) approach.
 *
 * Multi-channel aware: the correlation search that picks each analysis
 * window's position is always run against a *mixdown* of all channels
 * (so stereo content only ever gets one search per hop, not one per
 * channel), and the resulting offset is applied identically to every
 * channel. That keeps L/R phase-coherent -- searching each channel
 * independently would let them drift apart and collapse the stereo
 * image. See `TestWsola.kt`'s "stereo coherence" check (harness-only,
 * not shipped) for how this was verified.
 *
 * [speed] is purely a time-axis ratio: `1.0` = unchanged, `> 1.0` =
 * shorter/faster output, `< 1.0` = longer/slower output. Pitch is a
 * separate concern entirely -- see [LinearResampler] for how the two are
 * composed into independent tempo/pitch control.
 *
 * Algorithm shape, iteration by iteration:
 * 1. Nominal read position advances by `hop * speed` every iteration
 *    (hop = windowSize / 2), independent of any search jitter below --
 *    that's what keeps the actual output/input length ratio locked to
 *    `speed` over a whole track instead of drifting.
 * 2. Search a small window around that nominal position for the offset
 *    whose *first* `hop` samples correlate best (via a normalized dot
 *    product) with the *last* `hop` samples of the previously chosen
 *    segment -- the classic WSOLA "pick the segment that continues
 *    naturally" step, which is what avoids the audible glitching of
 *    plain fixed-hop overlap-add.
 * 3. The chosen `windowSize` segment is Hann-windowed and overlap-added
 *    into a small rolling accumulator. Hann at exactly 50% overlap sums
 *    to a constant (the standard COLA property), so once two windows'
 *    worth of iterations have run, the *oldest* `hop` samples of the
 *    accumulator are final and get flushed as output.
 */
class WsolaTimeStretcher(private val channelCount: Int) {

    /** `1.0` = unchanged duration. `>1.0` = shorter/faster. `<1.0` = longer/slower. */
    var speed: Double = 1.0

    private var windowSize = 0
    private var hop = 0
    private var tolerance = 0
    private var window = DoubleArray(0)

    private val inputQ = Array(channelCount) { SampleQueue() }
    private var queueBaseOffset = 0L

    private var readPosNominal = 0.0
    private var started = false
    private var prevTail: DoubleArray? = null

    private var pending: Array<DoubleArray> = emptyArray()
    private var pendingHasWindow = false

    fun configure(sampleRate: Int) {
        windowSize = ((sampleRate * WINDOW_MS) / 1000.0).roundToLong().toInt().coerceAtLeast(64)
        if (windowSize % 2 != 0) windowSize++
        hop = windowSize / 2
        tolerance = ((sampleRate * TOLERANCE_MS) / 1000.0).roundToLong().toInt().coerceAtLeast(4)
        window = DoubleArray(windowSize) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (windowSize - 1)) }
        pending = Array(channelCount) { DoubleArray(windowSize) }
        reset()
    }

    fun reset() {
        for (c in 0 until channelCount) inputQ[c].clear()
        queueBaseOffset = 0L
        readPosNominal = 0.0
        started = false
        prevTail = null
        pendingHasWindow = false
        for (c in 0 until channelCount) pending.getOrNull(c)?.fill(0.0)
    }

    /**
     * Appends [length] new frames (channel-major: `input[c][0 until length]`)
     * and returns as much output as could be produced this call -- may be
     * zero frames (not enough buffered yet to finish an analysis hop) or
     * several hops' worth at once. Output arrays are freshly allocated
     * per call and are the same length across every channel.
     */
    fun process(input: Array<DoubleArray>, length: Int): Array<DoubleArray> {
        if (windowSize == 0) return Array(channelCount) { DoubleArray(0) }
        for (c in 0 until channelCount) inputQ[c].append(input[c], 0, length)

        val chunks = ArrayList<DoubleArray>()
        var totalFrames = 0
        while (true) {
            val produced = tryProduceOneHop() ?: break
            chunks.add(produced)
            totalFrames += produced.size / channelCount
        }
        if (chunks.isEmpty()) return Array(channelCount) { DoubleArray(0) }

        val result = Array(channelCount) { DoubleArray(totalFrames) }
        var writeIdx = 0
        for (chunk in chunks) {
            val frames = chunk.size / channelCount
            for (f in 0 until frames) {
                for (c in 0 until channelCount) {
                    result[c][writeIdx + f] = chunk[f * channelCount + c]
                }
            }
            writeIdx += frames
        }
        return result
    }

    private fun refAt(absPos: Long): Double {
        val idx = (absPos - queueBaseOffset).toInt()
        return if (channelCount == 2) {
            (inputQ[0][idx] + inputQ[1][idx]) * 0.5
        } else {
            inputQ[0][idx]
        }
    }

    /** Returns one interleaved output hop, or null if there isn't enough buffered input yet. */
    private fun tryProduceOneHop(): DoubleArray? {
        val target = readPosNominal.roundToLong()
        val searchLo = max(0L, target - tolerance)
        val searchHi = target + tolerance
        val maxAbsNeeded = searchHi + windowSize
        val availableAbs = queueBaseOffset + inputQ[0].size
        if (maxAbsNeeded > availableAbs) return null

        val bestPos: Long = if (!started) {
            target.coerceAtLeast(queueBaseOffset)
        } else {
            val tail = prevTail!!
            var best = target
            var bestScore = Double.NEGATIVE_INFINITY
            var p = searchLo
            while (p <= searchHi) {
                var dot = 0.0
                var na = 0.0
                var nb = 0.0
                for (j in 0 until hop) {
                    val a = tail[j]
                    val b = refAt(p + j)
                    dot += a * b
                    na += a * a
                    nb += b * b
                }
                val denom = sqrt(na * nb)
                val score = if (denom > 1e-12) dot / denom else -1.0
                if (score > bestScore) {
                    bestScore = score
                    best = p
                }
                p++
            }
            best
        }

        val segIdx = (bestPos - queueBaseOffset).toInt()
        val segWindowed = Array(channelCount) { DoubleArray(windowSize) }
        for (c in 0 until channelCount) {
            val q = inputQ[c]
            for (j in 0 until windowSize) segWindowed[c][j] = q[segIdx + j] * window[j]
        }
        val refRaw = DoubleArray(windowSize) { j -> refAt(bestPos + j) }

        var output: DoubleArray? = null
        if (pendingHasWindow) {
            output = DoubleArray(hop * channelCount)
            for (f in 0 until hop) {
                for (c in 0 until channelCount) output[f * channelCount + c] = pending[c][f]
            }
            for (c in 0 until channelCount) {
                System.arraycopy(pending[c], hop, pending[c], 0, windowSize - hop)
                for (j in windowSize - hop until windowSize) pending[c][j] = 0.0
            }
        }
        for (c in 0 until channelCount) {
            for (j in 0 until windowSize) pending[c][j] += segWindowed[c][j]
        }
        pendingHasWindow = true

        prevTail = DoubleArray(hop) { i -> refRaw[hop + i] }
        started = true

        readPosNominal += hop * speed

        val safeDiscardAbs = minOf(bestPos, target - tolerance)
        val discardCount = (safeDiscardAbs - queueBaseOffset).toInt()
        if (discardCount > 0) {
            for (c in 0 until channelCount) inputQ[c].discard(discardCount)
            queueBaseOffset += discardCount
        }

        return output
    }

    /**
     * Drains everything currently buffered inside this stretcher (the raw
     * input queue plus the half-finished overlap-add accumulator) as final
     * output frames, then resets to a clean idle state ready for reuse.
     *
     * Call this before abandoning the stretcher mid-stream (e.g.
     * [TempoPitchAudioProcessor] switching from active WSOLA processing to
     * its identity bypass path) instead of just stopping — without it,
     * whatever's already been ingested but not yet emitted (up to
     * roughly one window's worth, ~40ms) would simply be discarded, an
     * audible gap right at the moment tempo/pitch gets reset to default.
     *
     * How: feeds exactly enough silence ([windowSize] + [tolerance]
     * frames — the maximum lookahead any single hop could ever need, a
     * small fixed amount, not proportional to how much real audio is
     * buffered) so every hop the already-queued *real* samples support
     * gets produced normally through [tryProduceOneHop]. That still
     * leaves one irregular remainder: whatever's sitting in [pending]
     * never got a second overlapping window to complete its Hann/COLA
     * sum, so the oldest [hop] samples of it are emitted as-is rather
     * than discarded — verified (see `test_drain.py` in this session's
     * scratch verification, not shipped) to be small, bounded, and free
     * of the numerical blow-up a half-summed window might suggest, since
     * Hann tapers to ~0 at the window edges either way.
     */
    fun drain(): Array<DoubleArray> {
        if (windowSize == 0) return Array(channelCount) { DoubleArray(0) }

        val padFrames = windowSize + tolerance
        val silence = Array(channelCount) { DoubleArray(padFrames) }
        for (c in 0 until channelCount) inputQ[c].append(silence[c], 0, padFrames)

        val chunks = ArrayList<DoubleArray>()
        var totalFrames = 0
        while (true) {
            val produced = tryProduceOneHop() ?: break
            chunks.add(produced)
            totalFrames += produced.size / channelCount
        }

        if (pendingHasWindow) {
            val tail = DoubleArray(hop * channelCount)
            for (f in 0 until hop) {
                for (c in 0 until channelCount) tail[f * channelCount + c] = pending[c][f]
            }
            chunks.add(tail)
            totalFrames += hop
        }

        reset()
        if (chunks.isEmpty()) return Array(channelCount) { DoubleArray(0) }

        val result = Array(channelCount) { DoubleArray(totalFrames) }
        var writeIdx = 0
        for (chunk in chunks) {
            val frames = chunk.size / channelCount
            for (f in 0 until frames) {
                for (c in 0 until channelCount) {
                    result[c][writeIdx + f] = chunk[f * channelCount + c]
                }
            }
            writeIdx += frames
        }
        return result
    }

    companion object {
        private const val WINDOW_MS = 40.0
        private const val TOLERANCE_MS = 10.0
    }
}
