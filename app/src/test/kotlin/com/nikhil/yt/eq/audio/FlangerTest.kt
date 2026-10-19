package com.nikhil.yt.eq.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * JVM-level checks for [Flanger]. This is the first flanger DSP in the
 * codebase, so the tests pin down the properties that make it a flanger
 * rather than a plain delay: it passes signal through untouched when
 * disabled, it changes the signal when enabled, the LFO actually moves the
 * delay over time, feedback deepens the comb (rather than being ignored),
 * and no setting can make the output run away.
 */
class FlangerTest {

    private val sampleRate = 44100

    @Test
    fun disabled_passesSignalThroughUnchanged() {
        val flanger = Flanger(initialSampleRate = sampleRate)
        flanger.setEnabled(false)
        for (i in 0 until 1000) {
            val input = (i % 100) / 100.0
            val (l, r) = flanger.process(input, input)
            assertEquals(input, l, 0.0)
            assertEquals(input, r, 0.0)
        }
    }

    @Test
    fun enabled_changesTheSignal() {
        val flanger = Flanger(initialSampleRate = sampleRate)
        flanger.setEnabled(true)
        flanger.setBaseDelayMs(2.0)
        flanger.setDepthMs(3.0)
        flanger.setRateHz(1.0)
        flanger.setMix(0.5)

        var differing = 0
        for (i in 0 until 2000) {
            val input = kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sampleRate)
            val (l, _) = flanger.process(input, input)
            if (abs(l - input) > 1e-6) differing++
        }
        assertTrue("flanger should alter a steady tone", differing > 0)
    }

    @Test
    fun lfoSweepsTheDelayOverTime() {
        val flanger = Flanger(initialSampleRate = sampleRate)
        flanger.setEnabled(true)
        flanger.setBaseDelayMs(2.0)
        flanger.setDepthMs(4.0)
        flanger.setRateHz(2.0)
        flanger.setMix(1.0) // fully wet: output IS the delayed signal
        flanger.setFeedback(0.0)

        // An impulse train makes the changing delay visible as changing
        // output values; a frozen delay would repeat the same value.
        val outputs = ArrayList<Double>()
        for (i in 0 until 4000) {
            val input = if (i % 200 == 0) 1.0 else 0.0
            outputs.add(flanger.process(input, input).first)
        }
        val distinct = outputs.map { (it * 1e6).toLong() }.distinct().size
        assertTrue("swept delay should produce many distinct outputs", distinct > 10)
    }

    @Test
    fun feedbackDeepensTheComb() {
        val noFeedback = Flanger(initialSampleRate = sampleRate)
        noFeedback.setEnabled(true)
        noFeedback.setBaseDelayMs(5.0)
        noFeedback.setDepthMs(0.0)
        noFeedback.setMix(1.0)
        noFeedback.setFeedback(0.0)

        val withFeedback = Flanger(initialSampleRate = sampleRate)
        withFeedback.setEnabled(true)
        withFeedback.setBaseDelayMs(5.0)
        withFeedback.setDepthMs(0.0)
        withFeedback.setMix(1.0)
        withFeedback.setFeedback(0.8)

        var fbEnergy = 0.0
        for (i in 0 until 3000) {
            val input = if (i < 10) 1.0 else 0.0
            val a = noFeedback.process(input, input).first
            val b = withFeedback.process(input, input).first
            fbEnergy += abs(b) - abs(a)
        }
        assertTrue("feedback should add energy to the tail", fbEnergy > 0.0)
    }

    @Test
    fun outputStaysBoundedWithMaximumSettings() {
        val flanger = Flanger(initialSampleRate = sampleRate)
        flanger.setEnabled(true)
        flanger.setBaseDelayMs(20.0)
        flanger.setDepthMs(10.0)
        flanger.setRateHz(10.0)
        flanger.setMix(1.0)
        flanger.setFeedback(0.95)

        var maxAbs = 0.0
        for (i in 0 until 200_000) {
            // Full-scale square wave: the worst case for feedback build-up.
            val input = if ((i / 50) % 2 == 0) 1.0 else -1.0
            val (l, r) = flanger.process(input, input)
            maxAbs = maxOf(maxAbs, abs(l), abs(r))
        }
        assertTrue("output must not run away, got $maxAbs", maxAbs < 100.0)
        assertFalse(maxAbs.isNaN())
        assertFalse(maxAbs.isInfinite())
    }

    @Test
    fun zeroMixIsDryOnly() {
        val flanger = Flanger(initialSampleRate = sampleRate)
        flanger.setEnabled(true)
        flanger.setMix(0.0)
        flanger.setFeedback(0.5)

        for (i in 0 until 500) {
            val input = kotlin.math.sin(2.0 * Math.PI * 220.0 * i / sampleRate)
            val (l, r) = flanger.process(input, input)
            assertEquals(input, l, 1e-12)
            assertEquals(input, r, 1e-12)
        }
    }

    @Test
    fun channelsAreNotIdenticalWhenSweeping() {
        val flanger = Flanger(initialSampleRate = sampleRate)
        flanger.setEnabled(true)
        flanger.setBaseDelayMs(2.0)
        flanger.setDepthMs(3.0)
        flanger.setRateHz(1.0)
        flanger.setMix(1.0)

        var sawDifference = false
        for (i in 0 until 2000) {
            val input = if (i % 100 == 0) 1.0 else 0.0
            val (l, r) = flanger.process(input, input)
            if (abs(l - r) > 1e-9) sawDifference = true
        }
        assertTrue("quadrature LFOs should make L/R differ", sawDifference)
    }

    @Test
    fun reconfigureAtDifferentSampleRateStaysStable() {
        val flanger = Flanger(initialSampleRate = 48000)
        flanger.setEnabled(true)
        flanger.setBaseDelayMs(3.0)
        flanger.setDepthMs(5.0)
        flanger.setRateHz(0.5)
        flanger.setMix(0.5)
        flanger.setFeedback(0.7)

        for (i in 0 until 1000) {
            flanger.process(kotlin.math.sin(i / 10.0), kotlin.math.sin(i / 10.0))
        }

        flanger.configure(44100)
        var maxAbs = 0.0
        for (i in 0 until 100_000) {
            val input = kotlin.math.sin(2.0 * Math.PI * 440.0 * i / 44100)
            val (l, r) = flanger.process(input, input)
            maxAbs = maxOf(maxAbs, abs(l), abs(r))
        }
        assertTrue("reconfigured flanger must stay bounded, got $maxAbs", maxAbs < 100.0)
    }
}
