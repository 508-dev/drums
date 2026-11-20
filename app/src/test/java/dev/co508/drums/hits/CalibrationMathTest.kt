package dev.co508.drums.hits

import dev.co508.drums.hits.CalibrationOutcome.Failed
import dev.co508.drums.hits.CalibrationOutcome.Measured
import dev.co508.drums.hits.CalibrationOutcome.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CalibrationMathTest {
    private val rate = 48_000
    private val perMilli = 48.0

    /** 16 clicks a beat apart at [bpm], starting at frame 100 000. */
    private fun clicks(bpm: Double) = DoubleArray(16) { 100_000 + it * 60.0 / bpm * rate }

    private fun ms(value: Double) = value * perMilli

    @Test
    fun `loopback measures a consistent delay and ignores stray sounds`() {
        val clicks = clicks(100.0)
        val random = Random(1)
        val hits = mutableListOf<Double>()
        for (c in clicks) {
            hits += c + ms(35.0 + random.nextDouble(-0.2, 0.2))
            hits += c + ms(46.0) // A later echo: only the earliest per click counts.
            hits += c + ms(random.nextDouble(120.0, 400.0)) // Room noise.
        }
        hits += clicks[0] - ms(300.0)
        val outcome = CalibrationMath.loopback(clicks, hits.shuffled(random).toDoubleArray(), rate) as Measured
        assertEquals(35.0, outcome.latencyMillis, 0.1)
        assertTrue(outcome.spreadMillis < 0.2)
        assertEquals(16, outcome.used)
    }

    @Test
    fun `loopback tolerates a few missed or noisy clicks`() {
        val clicks = clicks(100.0)
        val hits =
            clicks.toList().mapIndexedNotNull { i, c ->
                when (i) {
                    3 -> null // Missed.
                    7 -> c + ms(12.0) // Heard something else instead.
                    else -> c + ms(80.0)
                }
            }
        val outcome = CalibrationMath.loopback(clicks, hits.toDoubleArray(), rate) as Measured
        assertEquals(80.0, outcome.latencyMillis, 1e-9)
        assertEquals(14, outcome.used)
    }

    @Test
    fun `loopback isn't fooled by room ticks arriving before the click`() {
        // As in a quiet house on the ASUS: about 5 ticks a second, some landing
        // between a click and its arrival 35 ms later.
        val clicks = clicks(100.0)
        val random = Random(9)
        val hits = mutableListOf<Double>()
        for ((i, c) in clicks.withIndex()) {
            hits += c + ms(35.0 + random.nextDouble(-0.2, 0.2))
            if (i % 3 == 0) hits += c + ms(random.nextDouble(-4.0, 30.0))
        }
        repeat(40) { hits += clicks.first() + random.nextDouble() * (clicks.last() - clicks.first()) }
        val outcome = CalibrationMath.loopback(clicks, hits.toDoubleArray(), rate) as Measured
        assertEquals(35.0, outcome.latencyMillis, 0.15)
        assertEquals(16, outcome.used)
    }

    @Test
    fun `loopback can measure a slow Bluetooth speaker`() {
        val clicks = clicks(100.0)
        val outcome = CalibrationMath.loopback(clicks, clicks.map { it + ms(260.0) }.toDoubleArray(), rate) as Measured
        assertEquals(260.0, outcome.latencyMillis, 1e-9)
    }

    @Test
    fun `loopback failures say why`() {
        val clicks = clicks(100.0)
        assertEquals(Reason.NOT_HEARD, (CalibrationMath.loopback(clicks, DoubleArray(0), rate) as Failed).reason)
        val few = clicks.take(8).map { it + ms(30.0) }.toDoubleArray()
        val tooFew = CalibrationMath.loopback(clicks, few, rate) as Failed
        assertEquals(Reason.TOO_FEW, tooFew.reason)
        assertEquals(8, tooFew.heard)
        val random = Random(2)
        val scattered = clicks.map { it + ms(random.nextDouble(20.0, 120.0)) }.toDoubleArray()
        assertEquals(Reason.INCONSISTENT, (CalibrationMath.loopback(clicks, scattered, rate) as Failed).reason)
    }

    @Test
    fun `tap-along takes the median of human taps`() {
        val clicks = clicks(80.0)
        val random = Random(3)
        // Bluetooth headphones: heard 180 ms late, tapped with ±8 ms of human wobble.
        val taps = clicks.map { it + ms(180.0 + random.nextDouble(-8.0, 8.0)) }.toMutableList()
        taps += clicks[5] + ms(260.0) // A double tap: the first per click counts.
        taps += clicks[0] - ms(750.0 * 2) // Tapping during the count-in.
        taps[9] = clicks[9] + ms(250.0) // One sloppy tap, left out.
        val outcome = CalibrationMath.tapAlong(clicks, taps.toDoubleArray(), rate) as Measured
        assertEquals(180.0, outcome.latencyMillis, 4.0)
        assertTrue(outcome.used in 13..15)
    }

    @Test
    fun `early taps count as negative delays`() {
        val clicks = clicks(80.0)
        val taps = clicks.map { it - ms(12.0) }.toDoubleArray()
        assertEquals(-12.0, (CalibrationMath.tapAlong(clicks, taps, rate) as Measured).latencyMillis, 1e-9)
    }

    @Test
    fun `tap-along failures say why`() {
        val clicks = clicks(80.0)
        assertEquals(Reason.NOT_HEARD, (CalibrationMath.tapAlong(clicks, DoubleArray(0), rate) as Failed).reason)
        val few = clicks.take(6).map { it + ms(20.0) }.toDoubleArray()
        assertEquals(Reason.TOO_FEW, (CalibrationMath.tapAlong(clicks, few, rate) as Failed).reason)
        val random = Random(4)
        val wild = clicks.map { it + ms(random.nextDouble(-150.0, 150.0)) }.toDoubleArray()
        val inconsistent = CalibrationMath.tapAlong(clicks, wild, rate) as Failed
        assertEquals(Reason.INCONSISTENT, inconsistent.reason)
        assertTrue(inconsistent.spreadMillis > CalibrationMath.TAP_MAX_SPREAD_MILLIS)
    }

    @Test
    fun `the detector times the calibration click to a fraction of a millisecond`() {
        // The click through a speaker, 35.3 ms later, over room noise, at three volumes.
        val click = Calibrator.calibrationClick(rate).samples
        val s = SyntheticSignals(rate, seconds = 12.0, seed = 8)
        s.whiteNoise(-60.0)
        val expected = mutableListOf<Double>()
        for (k in 0 until 16) {
            val start = 48_000 + k * 28_800 + 1_694 // Each click 35.3 ms after its beat.
            val gain = listOf(0.05, 0.2, 0.6)[k % 3]
            for (i in click.indices) s.samples[start + i] += gain * click[i]
            expected += start.toDouble()
        }
        val onsets = detect(s.toFloats())
        assertEquals(16, onsets.size)
        val errors = expected.zip(onsets).map { (e, o) -> (o.first - e) / perMilli }
        val mean = errors.average()
        assertTrue("errors $errors", errors.all { kotlin.math.abs(it - mean) < 0.1 })
        assertTrue("mean $mean", kotlin.math.abs(mean) < 0.3)
    }
}
