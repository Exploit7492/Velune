package com.nikhil.yt.eq.audio

import com.nikhil.yt.eq.data.ImpulseResponse

/**
 * Convolution-based tone shaping: convolves the signal against a loaded
 * impulse response (a measured headphone/DAC correction curve, a room/
 * cabinet capture, whatever the IR file represents) instead of a bank of
 * parametric biquads. This is the "real impulse-response based tone
 * shaping, not just parametric bands" piece — it sits *before* the existing
 * biquad EQ chain in [CustomEqualizerAudioProcessor], so a loaded correction
 * IR handles the baseline device/headphone response and the user's manual
 * bands sit on top of that, the same layering Neutron uses (convolver first,
 * parametric EQ as user-adjustable trim on top).
 *
 * Exposes the same one-sample-in/one-sample-out [process] shape as
 * [LookaheadLimiter] on purpose: [PartitionedConvolver] only produces output
 * a whole block at a time, so this class hides that behind an output ring
 * buffer pre-seeded with [blockSize] samples of silence. That means calling
 * code never has to special-case "not enough output yet" — every call
 * returns a sample immediately — but it does mean enabling convolution adds
 * `blockSize / sampleRate` seconds of fixed algorithmic latency (~23ms at
 * 44.1kHz for the default 1024-sample block), same trade-off any block-based
 * convolution engine makes.
 */
class ConvolutionAudioProcessor(
    impulseResponse: ImpulseResponse,
    private val blockSize: Int = 1024
) {
    private val convolverL = PartitionedConvolver(impulseResponse.left, blockSize)
    private val convolverR = PartitionedConvolver(impulseResponse.right ?: impulseResponse.left, blockSize)

    private val accumL = DoubleArray(blockSize)
    private val accumR = DoubleArray(blockSize)
    private var accumCount = 0

    // Ring buffer sized with headroom above the worst-case occupancy
    // (blockSize + 1, right after a block flush) so it never has to grow.
    private val ringCapacity = blockSize * 4
    private val ringL = DoubleArray(ringCapacity)
    private val ringR = DoubleArray(ringCapacity)
    private var ringHead = 0 // next slot to pop
    private var ringCount = 0

    var enabled: Boolean = true

    init {
        repeat(blockSize) { pushOutput(0.0, 0.0) }
    }

    private fun pushOutput(left: Double, right: Double) {
        val writeIndex = (ringHead + ringCount) % ringCapacity
        ringL[writeIndex] = left
        ringR[writeIndex] = right
        ringCount++
    }

    private fun popOutput(): Pair<Double, Double> {
        val l = ringL[ringHead]
        val r = ringR[ringHead]
        ringHead = (ringHead + 1) % ringCapacity
        ringCount--
        return l to r
    }

    @Synchronized
    fun process(left: Double, right: Double): Pair<Double, Double> {
        if (!enabled) return left to right

        accumL[accumCount] = left
        accumR[accumCount] = right
        accumCount++

        if (accumCount == blockSize) {
            val outL = convolverL.processBlock(accumL)
            val outR = convolverR.processBlock(accumR)
            for (i in 0 until blockSize) pushOutput(outL[i], outR[i])
            accumCount = 0
        }

        return popOutput()
    }

    fun processMono(sample: Double): Double = process(sample, sample).first

    fun latencySamples(): Int = blockSize

    @Synchronized
    fun reset() {
        accumL.fill(0.0)
        accumR.fill(0.0)
        accumCount = 0
        ringHead = 0
        ringCount = 0
        convolverL.reset()
        convolverR.reset()
        repeat(blockSize) { pushOutput(0.0, 0.0) }
    }
}
