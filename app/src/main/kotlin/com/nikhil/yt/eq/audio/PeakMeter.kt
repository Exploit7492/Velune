package com.nikhil.yt.eq.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * One published meter reading. `leftDb`/`rightDb` are the smoothed bar
 * levels (instant attack, timed release); `leftPeakDb`/`rightPeakDb` are
 * the peak-hold caps (latch on a new high, hold flat, then decay on their
 * own slower schedule); `leftClipping`/`rightClipping` latch true for
 * [PeakMeter.CLIP_HOLD_MS] after any sample reaches
 * [PeakMeter.CLIP_THRESHOLD_DB], so a single brief overshoot is still
 * visible instead of blinking for one publish interval. Published as one
 * atomic value (see [PeakMeter.snapshot]) so a reader never sees a bar
 * level from one interval paired with a peak/clip state from another.
 */
data class PeakMeterSnapshot(
    val leftDb: Float,
    val rightDb: Float,
    val leftPeakDb: Float,
    val rightPeakDb: Float,
    val leftClipping: Boolean,
    val rightClipping: Boolean
)

/**
 * Compact stereo peak level meter feeding the "studio" output meter next
 * to the Preamp knob in the Master tab — shows exactly what leaves the DSP
 * chain, fed from the same post-limiter tap point [SpectrumAnalyzer] uses.
 * Sample-accurate true peak per channel (running `max(abs(sample))`, not
 * RMS/VU), not an FFT — no windowing, no [Fft] involvement, this is a much
 * simpler class than the analyzer it sits next to.
 *
 * Ballistics (bar decay, peak-hold latch+decay) are driven by elapsed
 * *audio-domain* time (`samplesSincePublish / sampleRate`), the same
 * approach [SpectrumAnalyzer]'s peak-hold uses and for the same reason:
 * exact regardless of how often, or how unevenly, the UI happens to poll
 * [snapshot].
 */
class PeakMeter {
    /** Cheap early-out — [accept] no-ops entirely when this is false, so
     *  there's no cost while no meter UI is on screen. */
    @Volatile var enabled: Boolean = false

    private var sampleRate: Int = 44100
    private var secondsPerSample: Double = 1.0 / 44100.0
    private val publishIntervalSamples: Int
        get() = max(1, sampleRate / PUBLISH_RATE_HZ)

    // Running true peak within the current publish interval — reset to 0
    // each time a snapshot is published, so each published bar level means
    // "loudest sample since the last publish," standard peak-meter
    // behavior.
    private var runningPeakLeft = 0.0
    private var runningPeakRight = 0.0
    private var samplesSincePublish = 0

    // Smoothed bar levels shown to the user — NOT the raw running peak
    // above, which would flicker at PUBLISH_RATE_HZ. Instant attack (a
    // louder peak shows immediately), timed release.
    private var barLeftDb = SILENCE_DB.toDouble()
    private var barRightDb = SILENCE_DB.toDouble()

    // Peak-hold caps: latch on a new high, hold flat for PEAK_HOLD_MS,
    // then decay at their own slower rate — same shape as
    // SpectrumAnalyzer's per-bar peak-hold.
    private var peakLeftDb = SILENCE_DB.toDouble()
    private var peakRightDb = SILENCE_DB.toDouble()
    private var peakLeftHeldSeconds = 0.0
    private var peakRightHeldSeconds = 0.0

    // Clip latch: -1.0 means "not clipping", >=0.0 tracks how long it's
    // been since the last sample at/above CLIP_THRESHOLD_DB.
    private var clipLeftHeldSeconds = -1.0
    private var clipRightHeldSeconds = -1.0

    @Volatile private var published = SILENT_SNAPSHOT

    fun configure(sampleRate: Int) {
        this.sampleRate = sampleRate
        secondsPerSample = 1.0 / sampleRate
        // Same reasoning as SpectrumAnalyzer.configure(): publish a
        // cleared snapshot immediately on reconfigure (e.g. a track with a
        // different sample rate) rather than leaving a stale reading
        // visible until the next publish interval happens to complete.
        reset()
    }

    fun reset() {
        runningPeakLeft = 0.0
        runningPeakRight = 0.0
        samplesSincePublish = 0
        barLeftDb = SILENCE_DB.toDouble()
        barRightDb = SILENCE_DB.toDouble()
        peakLeftDb = SILENCE_DB.toDouble()
        peakRightDb = SILENCE_DB.toDouble()
        peakLeftHeldSeconds = 0.0
        peakRightHeldSeconds = 0.0
        clipLeftHeldSeconds = -1.0
        clipRightHeldSeconds = -1.0
        published = SILENT_SNAPSHOT
    }

    /** Feeds one already fully-processed stereo frame. No-op if [enabled] is false. */
    fun accept(left: Double, right: Double) {
        if (!enabled) return
        runningPeakLeft = max(runningPeakLeft, abs(left))
        runningPeakRight = max(runningPeakRight, abs(right))
        samplesSincePublish++
        if (samplesSincePublish >= publishIntervalSamples) {
            publish(samplesSincePublish * secondsPerSample)
            samplesSincePublish = 0
        }
    }

    private fun toDb(linear: Double): Double =
        if (linear > 1e-9) 20.0 * log10(linear) else SILENCE_DB.toDouble()

    private fun publish(elapsedSeconds: Double) {
        val intervalLeftDb = toDb(runningPeakLeft).coerceIn(SILENCE_DB.toDouble(), DISPLAY_CEIL_DB)
        val intervalRightDb = toDb(runningPeakRight).coerceIn(SILENCE_DB.toDouble(), DISPLAY_CEIL_DB)
        runningPeakLeft = 0.0
        runningPeakRight = 0.0

        barLeftDb = if (intervalLeftDb > barLeftDb) {
            intervalLeftDb
        } else {
            max(intervalLeftDb, barLeftDb - BAR_DECAY_DB_PER_SEC * elapsedSeconds)
        }
        barRightDb = if (intervalRightDb > barRightDb) {
            intervalRightDb
        } else {
            max(intervalRightDb, barRightDb - BAR_DECAY_DB_PER_SEC * elapsedSeconds)
        }

        val (newPeakLeftDb, newPeakLeftHeld) = advancePeakHold(intervalLeftDb, peakLeftDb, peakLeftHeldSeconds, elapsedSeconds)
        peakLeftDb = newPeakLeftDb
        peakLeftHeldSeconds = newPeakLeftHeld
        val (newPeakRightDb, newPeakRightHeld) = advancePeakHold(intervalRightDb, peakRightDb, peakRightHeldSeconds, elapsedSeconds)
        peakRightDb = newPeakRightDb
        peakRightHeldSeconds = newPeakRightHeld

        clipLeftHeldSeconds = advanceClipLatch(intervalLeftDb, clipLeftHeldSeconds, elapsedSeconds)
        clipRightHeldSeconds = advanceClipLatch(intervalRightDb, clipRightHeldSeconds, elapsedSeconds)

        published = PeakMeterSnapshot(
            leftDb = barLeftDb.toFloat(),
            rightDb = barRightDb.toFloat(),
            leftPeakDb = peakLeftDb.toFloat(),
            rightPeakDb = peakRightDb.toFloat(),
            leftClipping = clipLeftHeldSeconds >= 0.0,
            rightClipping = clipRightHeldSeconds >= 0.0
        )
    }

    /** Returns the updated (peakDb, heldSeconds) pair for one channel. */
    private fun advancePeakHold(
        currentDb: Double,
        peakDb: Double,
        heldSeconds: Double,
        elapsedSeconds: Double
    ): Pair<Double, Double> {
        if (currentDb >= peakDb) {
            return currentDb to 0.0
        }
        val newHeld = heldSeconds + elapsedSeconds
        if (newHeld * 1000.0 < PEAK_HOLD_MS) {
            return peakDb to newHeld
        }
        val decaySeconds = newHeld - PEAK_HOLD_MS / 1000.0
        val decayed = (peakDb - PEAK_DECAY_DB_PER_SEC * decaySeconds).coerceAtLeast(currentDb)
        return decayed to newHeld
    }

    /** Returns the updated heldSeconds for one channel's clip latch. */
    private fun advanceClipLatch(currentDb: Double, heldSeconds: Double, elapsedSeconds: Double): Double {
        val clipping = currentDb >= CLIP_THRESHOLD_DB
        if (clipping) return 0.0
        if (heldSeconds < 0.0) return -1.0
        val newHeld = heldSeconds + elapsedSeconds
        return if (newHeld * 1000.0 > CLIP_HOLD_MS) -1.0 else newHeld
    }

    /**
     * Latest reading. Safe to call from any thread — reads a single
     * volatile reference to a value this class never mutates after
     * publishing it.
     */
    fun snapshot(): PeakMeterSnapshot = published

    companion object {
        const val SILENCE_DB = -80f
        const val CLIP_THRESHOLD_DB = -0.3
        const val DISPLAY_CEIL_DB = 3.0 // a little headroom above 0dBFS to show a true over, not just clamp it away
        private const val PUBLISH_RATE_HZ = 60 // fast enough that "loudest sample since last publish" tracks true peak closely
        private const val BAR_DECAY_DB_PER_SEC = 20.0
        private const val PEAK_HOLD_MS = 1500.0
        private const val PEAK_DECAY_DB_PER_SEC = 8.0
        private const val CLIP_HOLD_MS = 1500.0

        private val SILENT_SNAPSHOT = PeakMeterSnapshot(
            leftDb = SILENCE_DB,
            rightDb = SILENCE_DB,
            leftPeakDb = SILENCE_DB,
            rightPeakDb = SILENCE_DB,
            leftClipping = false,
            rightClipping = false
        )
    }
}
