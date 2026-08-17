package com.nikhil.yt.eq.audio

/**
 * A simple append/discard sample queue backed by a single growable
 * [DoubleArray] (compacted in place rather than reallocated on every
 * `discard`, so steady-state streaming doesn't churn the heap). Used by
 * [WsolaTimeStretcher] and [LinearResampler] to buffer input across
 * `queueInput` calls, since both need to look back/ahead of whatever
 * chunk boundary Media3 happens to hand them.
 *
 * Not thread-safe on its own -- callers (the audio-processing classes
 * above) are only ever touched from the single audio thread that drives
 * the Media3 processor chain, same assumption every other class in this
 * package already makes.
 */
internal class SampleQueue {
    private var buf = DoubleArray(4096)
    private var start = 0
    private var count = 0

    val size: Int get() = count

    operator fun get(i: Int): Double = buf[start + i]

    fun append(samples: DoubleArray, offset: Int, length: Int) {
        ensureCapacity(count + length)
        System.arraycopy(samples, offset, buf, start + count, length)
        count += length
    }

    fun discard(n: Int) {
        require(n <= count) { "discard($n) > available($count)" }
        start += n
        count -= n
        if (start > buf.size / 2) {
            System.arraycopy(buf, start, buf, 0, count)
            start = 0
        }
    }

    private fun ensureCapacity(needed: Int) {
        if (start + needed <= buf.size) return
        if (count + needed <= buf.size) {
            System.arraycopy(buf, start, buf, 0, count)
            start = 0
            return
        }
        var newSize = buf.size * 2
        while (newSize < count + needed) newSize *= 2
        val newBuf = DoubleArray(newSize)
        System.arraycopy(buf, start, newBuf, 0, count)
        buf = newBuf
        start = 0
    }

    fun clear() {
        start = 0
        count = 0
    }
}
