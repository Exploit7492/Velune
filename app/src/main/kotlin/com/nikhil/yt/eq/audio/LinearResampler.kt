package com.nikhil.yt.eq.audio

/**
 * Streaming linear-interpolation resampler -- reading input at [rate] !=
 * `1.0` changes pitch *and* duration together (the classic "varispeed"
 * tape-speed effect: `rate > 1.0` = higher pitch, shorter; `rate < 1.0` =
 * lower pitch, longer).
 *
 * On its own that's not independent pitch control, which is why
 * [TempoPitchAudioProcessor] always pairs this with a [WsolaTimeStretcher]
 * downstream: resample by the desired pitch ratio here (shifts pitch,
 * incidentally changes duration by `1/rate`), then WSOLA-stretch by
 * `tempoRatio / pitchRatio` to land on the actually-desired duration.
 * Composing the two this way (rather than, say, two independent WSOLA
 * passes) is the standard approach every consumer time-stretch library
 * uses, and is what keeps tempo and pitch genuinely orthogonal to each
 * other -- see `TestPitch.kt`'s independence checks (harness-only, not
 * shipped).
 */
class LinearResampler(private val channelCount: Int) {

    /** `1.0` = unchanged. `>1.0` = higher pitch (and shorter, before WSOLA compensates). */
    var rate: Double = 1.0

    private val inputQ = Array(channelCount) { SampleQueue() }
    private var queueBaseOffset = 0L
    private var readPos = 0.0

    fun reset() {
        for (c in 0 until channelCount) inputQ[c].clear()
        queueBaseOffset = 0L
        readPos = 0.0
    }

    fun process(input: Array<DoubleArray>, length: Int): Array<DoubleArray> {
        for (c in 0 until channelCount) inputQ[c].append(input[c], 0, length)

        val maxAbs = queueBaseOffset + inputQ[0].size
        val outLists = Array(channelCount) { ArrayList<Double>() }

        while (true) {
            val i0 = readPos.toLong()
            if (i0 + 1 >= maxAbs) break
            val frac = readPos - i0
            val idx0 = (i0 - queueBaseOffset).toInt()
            val idx1 = idx0 + 1
            for (c in 0 until channelCount) {
                val q = inputQ[c]
                val a = q[idx0]
                val b = q[idx1]
                outLists[c].add(a + (b - a) * frac)
            }
            readPos += rate
        }

        val safeAbs = readPos.toLong() - 2
        val discardCount = (safeAbs - queueBaseOffset).toInt()
        if (discardCount > 0) {
            for (c in 0 until channelCount) inputQ[c].discard(discardCount)
            queueBaseOffset += discardCount
        }

        return Array(channelCount) { c -> outLists[c].toDoubleArray() }
    }
}
