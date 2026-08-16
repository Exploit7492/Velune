package com.nikhil.yt.eq.audio

/**
 * Single-channel uniform-partitioned overlap-add (UPOLA) convolution.
 *
 * Real-time convolution against an impulse response can't just FFT the
 * whole IR once and multiply — the IR is static but the input is an
 * unbounded stream, and IRs used for tone shaping are almost always longer
 * than one audio callback's worth of samples. The standard fix is uniform
 * partitioning: chop the IR into equal-length blocks, FFT each block once
 * up front, and for every new block of *input* audio, multiply its
 * spectrum against each IR partition's spectrum (with the appropriate
 * delay) and sum the results — that sum is exactly equivalent to convolving
 * the full IR against the input, just computed incrementally per block
 * instead of needing the entire signal in memory at once.
 *
 * [blockSize] is both the processing block size and (via zero-padding to
 * `2 * blockSize`) sets the FFT size, following the classic overlap-add
 * convolution constraint: FFT size must be at least
 * `blockSize + partitionLength - 1` to avoid circular-convolution wraparound,
 * and `2 * blockSize` satisfies that since each IR partition is itself
 * `blockSize` samples.
 */
class PartitionedConvolver(impulseResponse: DoubleArray, private val blockSize: Int) {

    private val fftSize = blockSize * 2
    private val fft = Fft.forSize(fftSize)
    private val partitionCount: Int

    // Precomputed spectra of each zero-padded IR partition. Shared/reusable
    // across channels that use the same IR (see ConvolutionAudioProcessor),
    // since this is read-only once built.
    private val partitionRe: Array<DoubleArray>
    private val partitionIm: Array<DoubleArray>

    // Per-instance streaming state (NOT shareable across channels).
    private val historyRe: Array<DoubleArray>
    private val historyIm: Array<DoubleArray>
    private var historyHead = 0 // index of the most recently written history slot
    private val overlapTail = DoubleArray(blockSize)

    init {
        partitionCount = if (impulseResponse.isEmpty()) 1 else (impulseResponse.size + blockSize - 1) / blockSize
        partitionRe = Array(partitionCount) { DoubleArray(fftSize) }
        partitionIm = Array(partitionCount) { DoubleArray(fftSize) }
        for (p in 0 until partitionCount) {
            val re = partitionRe[p]
            val start = p * blockSize
            for (i in 0 until blockSize) {
                val srcIndex = start + i
                re[i] = if (srcIndex < impulseResponse.size) impulseResponse[srcIndex] else 0.0
            }
            // re[blockSize..fftSize) stays zero — the zero-padding overlap-add needs.
            fft.forward(re, partitionIm[p])
        }
        historyRe = Array(partitionCount) { DoubleArray(fftSize) }
        historyIm = Array(partitionCount) { DoubleArray(fftSize) }
    }

    /**
     * Convolve one block of exactly [blockSize] input samples. Returns a new
     * array of [blockSize] output samples (the previous block's overlap tail
     * plus this block's freshly-computed contribution) — always the same
     * length in, same length out; the algorithmic delay this introduces is
     * fixed and handled by the caller (see ConvolutionAudioProcessor).
     */
    fun processBlock(input: DoubleArray): DoubleArray {
        require(input.size == blockSize) { "Expected block of $blockSize samples, got ${input.size}" }

        val xRe = DoubleArray(fftSize)
        val xIm = DoubleArray(fftSize)
        System.arraycopy(input, 0, xRe, 0, blockSize)
        fft.forward(xRe, xIm)

        historyHead = (historyHead + 1) % partitionCount
        System.arraycopy(xRe, 0, historyRe[historyHead], 0, fftSize)
        System.arraycopy(xIm, 0, historyIm[historyHead], 0, fftSize)

        val sumRe = DoubleArray(fftSize)
        val sumIm = DoubleArray(fftSize)
        for (p in 0 until partitionCount) {
            val histIndex = (historyHead - p + partitionCount) % partitionCount
            val hRe = historyRe[histIndex]
            val hIm = historyIm[histIndex]
            val pRe = partitionRe[p]
            val pIm = partitionIm[p]
            for (i in 0 until fftSize) {
                // Complex multiply-accumulate: (hRe+i*hIm) * (pRe+i*pIm).
                sumRe[i] += hRe[i] * pRe[i] - hIm[i] * pIm[i]
                sumIm[i] += hRe[i] * pIm[i] + hIm[i] * pRe[i]
            }
        }

        fft.inverse(sumRe, sumIm)

        val output = DoubleArray(blockSize)
        for (i in 0 until blockSize) {
            output[i] = sumRe[i] + overlapTail[i]
        }
        for (i in 0 until blockSize) {
            overlapTail[i] = sumRe[blockSize + i]
        }
        return output
    }

    fun reset() {
        for (p in 0 until partitionCount) {
            historyRe[p].fill(0.0)
            historyIm[p].fill(0.0)
        }
        overlapTail.fill(0.0)
        historyHead = 0
    }
}
