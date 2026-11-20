package dev.co508.drums.hits

import dev.co508.drums.ALLOCATION_NOISE_BYTES
import dev.co508.drums.threadAllocatedBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class OnsetDetectorTest {
    private val rate = 48_000

    private fun millis(frames: Double) = frames * 1000.0 / rate

    /**
     * Asserts that [onsets] are exactly [expected], one each, in order, and
     * returns the timing errors in milliseconds.
     */
    private fun matchAll(
        expected: List<Double>,
        onsets: List<Pair<Double, Float>>,
    ): List<Double> {
        assertEquals(
            "onsets ${onsets.map { "%.1f".format(millis(it.first)) }} vs ${expected.map { "%.1f".format(millis(it)) }}",
            expected.size,
            onsets.size,
        )
        return expected.zip(onsets).map { (e, o) -> millis(o.first - e) }
    }

    private fun assertTight(
        errors: List<Double>,
        maxBiasMillis: Double = 0.6,
        maxSpreadMillis: Double = 0.4,
    ) {
        val mean = errors.average()
        val spread = errors.maxOf { abs(it - mean) }
        val summary = "mean %.3f ms, max deviation %.3f ms".format(mean, spread)
        println(summary)
        assertTrue("bias: $summary", abs(mean) < maxBiasMillis)
        assertTrue("spread: $summary", spread < maxSpreadMillis)
    }

    @Test
    fun `finds isolated hits at sub-sample positions with sub-millisecond precision`() {
        val s = SyntheticSignals(rate, seconds = 6.0)
        s.whiteNoise(-70.0)
        val random = Random(7)
        val expected = (0 until 20).map { s.padHit(0.3 + it * 0.25 + random.nextDouble() * 0.01, 0.3) }
        assertTight(matchAll(expected, detect(s.toFloats())))
    }

    @Test
    fun `timing doesn't depend on how hard the pad was hit`() {
        val s = SyntheticSignals(rate, seconds = 8.0, seed = 3)
        s.whiteNoise(-65.0)
        // A 40 dB range, from a ghost note well above the noise to near full scale.
        val levels = listOf(-45.0, -40.0, -35.0, -30.0, -25.0, -20.0, -15.0, -10.0, -6.0, -3.0)
        val expected =
            levels.flatMapIndexed { i, db ->
                listOf(
                    s.padHit(0.3 + i * 0.6, decibelsToAmplitude(db)),
                    s.padHit(
                        0.6 + i * 0.6 + 0.0001,
                        decibelsToAmplitude(db),
                    ),
                )
            }
        val onsets = detect(s.toFloats())
        assertTight(matchAll(expected, onsets))
        // Louder hits report higher levels.
        val levelsSeen = onsets.map { it.second }
        assertTrue(levelsSeen.first() < levelsSeen.last() - 30)
    }

    @Test
    fun `separates 32nd notes at 300 BPM, accents and ghost notes mixed`() {
        val s = SyntheticSignals(rate, seconds = 3.0, seed = 5)
        s.whiteNoise(-65.0)
        val gap = 60.0 / 300 / 8 // 25 ms
        val gains = listOf(1.0, 0.18, 0.55, 0.18, 0.55, 0.18, 0.55, 0.18)
        val expected = (0 until 80).map { s.padHit(0.4 + it * gap, 0.4 * gains[it % gains.size]) }
        assertTight(matchAll(expected, detect(s.toFloats())))
    }

    @Test
    fun `a flam's grace note 12 ms before its main note is a separate onset`() {
        val s = SyntheticSignals(rate, seconds = 4.0, seed = 9)
        s.whiteNoise(-65.0)
        val expected = mutableListOf<Double>()
        for (i in 0 until 8) {
            val beat = 0.4 + i * 0.4
            expected += s.padHit(beat - 0.012, 0.3 * 0.3)
            expected += s.padHit(beat, 0.3)
        }
        assertTight(matchAll(expected, detect(s.toFloats())))
    }

    @Test
    fun `hits closer than the minimum gap merge into one onset at the first`() {
        val s = SyntheticSignals(rate, seconds = 2.0, seed = 11)
        s.whiteNoise(-65.0)
        val grace = s.padHit(0.5, 0.1)
        s.padHit(0.503, 0.3) // A "flat" flam, 3 ms apart: one sound to the mic.
        val onsets = detect(s.toFloats())
        assertEquals("${onsets.map { millis(it.first - grace) }}", 1, onsets.size)
        assertTrue("${millis(onsets[0].first - grace)} ms", abs(millis(onsets[0].first - grace)) < 1.0)
    }

    @Test
    fun `hears nothing in noise and rumble without hits`() {
        val s = SyntheticSignals(rate, seconds = 20.0, seed = 13)
        s.whiteNoise(-50.0)
        s.rumble(decibelsToAmplitude(-12.0))
        assertEquals(emptyList<Pair<Double, Float>>(), detect(s.toFloats()))
    }

    @Test
    fun `noise that swells slowly, like a fan starting, is not a hit`() {
        val s = SyntheticSignals(rate, seconds = 6.0, seed = 15)
        s.whiteNoise(-70.0)
        s.whiteNoise(-40.0, fromSeconds = 2.0, rampSeconds = 0.3)
        assertEquals(emptyList<Pair<Double, Float>>(), detect(s.toFloats()))
    }

    @Test
    fun `finds hits in loud room noise and rumble`() {
        val s = SyntheticSignals(rate, seconds = 6.0, seed = 17)
        s.whiteNoise(-45.0)
        s.rumble(decibelsToAmplitude(-10.0))
        val expected = (0 until 16).map { s.padHit(0.5 + it * 0.3, decibelsToAmplitude(-20.0)) }
        assertTight(matchAll(expected, detect(s.toFloats())), maxSpreadMillis = 0.4)
    }

    @Test
    fun `a wall reflection and a ringing drum don't double-trigger`() {
        val s = SyntheticSignals(rate, seconds = 5.0, seed = 19)
        s.whiteNoise(-70.0)
        val expected = (0 until 12).map { s.padHit(0.3 + it * 0.35, 0.3, decayMillis = if (it % 2 == 0) 6.0 else 80.0) }
        s.reflection(delayMillis = 6.0, gain = 0.45)
        assertTight(matchAll(expected, detect(s.toFloats())))
    }

    @Test
    fun `an acoustic snare's long ring doesn't hide 16ths`() {
        val s = SyntheticSignals(rate, seconds = 4.0, seed = 29)
        s.whiteNoise(-60.0)
        val gap = 60.0 / 140 / 4
        val gains = listOf(1.0, 0.4, 0.55, 0.4)
        val expected =
            (0 until 28).map {
                s.padHit(
                    0.3 + it * gap,
                    0.3 * gains[it % 4],
                    decayMillis = 60.0,
                    damping = 0.5,
                )
            }
        assertTight(matchAll(expected, detect(s.toFloats())))
    }

    @Test
    fun `clipped hits still time correctly`() {
        val s = SyntheticSignals(rate, seconds = 3.0, seed = 31)
        s.whiteNoise(-60.0)
        val expected = (0 until 12).map { s.padHit(0.3 + it * 0.2, if (it % 2 == 0) 4.0 else 0.5) }
        val samples = s.toFloats()
        for (i in samples.indices) samples[i] = samples[i].coerceIn(-1f, 1f)
        assertTight(matchAll(expected, detect(samples)))
    }

    @Test
    fun `a crescendo is followed by the floor without losing hits`() {
        val s = SyntheticSignals(rate, seconds = 12.0, seed = 21)
        s.whiteNoise(-60.0)
        // Continuous 16ths at 160 BPM for 11 s, from very soft to loud.
        val gap = 60.0 / 160 / 4
        val count = (11.0 / gap).toInt()
        val expected = (0 until count).map { s.padHit(0.4 + it * gap, decibelsToAmplitude(-40.0 + 35.0 * it / count)) }
        assertTight(matchAll(expected, detect(s.toFloats())))
    }

    @Test
    fun `ignores ticks and whine bursts far below the playing`() {
        val s = SyntheticSignals(rate, seconds = 6.0, seed = 33)
        s.whiteNoise(-80.0)
        val random = Random(34)
        // Bursts like the ones a quiet house gave a test phone: about 5 a second.
        repeat(25) { s.toneBurst(0.2 + random.nextDouble() * 5.6, decibelsToAmplitude(-50.0)) }
        val expected =
            (0 until 20).map {
                // Accents and ghost notes, 15 dB apart: both well above the bursts.
                s.padHit(0.5 + it * 0.25, decibelsToAmplitude(if (it % 2 == 0) -10.0 else -25.0))
            }
        val detector = OnsetDetector(rate)
        val onsets = detect(s.toFloats(), detector)
        // Only bursts before the first hit, when there's nothing to compare with, come through.
        val beforeFirst = onsets.count { it.first < expected.first() - rate * 0.001 }
        assertTight(matchAll(expected, onsets.drop(beforeFirst)))
        assertTrue(detector.quietOnsetCount > 10)
    }

    @Test
    fun `quiet playing after a pause is heard again`() {
        val s = SyntheticSignals(rate, seconds = 16.0, seed = 35)
        s.whiteNoise(-80.0)
        val loud = (0 until 4).map { s.padHit(0.5 + it * 0.25, decibelsToAmplitude(-10.0)) }
        // 12 s later the loud hits are forgotten: 36 dB of decay.
        val soft = (0 until 4).map { s.padHit(14.0 + it * 0.25, decibelsToAmplitude(-45.0)) }
        assertTight(matchAll(loud + soft, detect(s.toFloats())))
    }

    @Test
    fun `block size doesn't change the onsets`() {
        val s = SyntheticSignals(rate, seconds = 3.0, seed = 23)
        s.whiteNoise(-60.0)
        repeat(20) { s.padHit(0.3 + it * 0.12, 0.2 + 0.02 * it) }
        val samples = s.toFloats()
        val reference = detect(samples, block = 240)
        for (block in listOf(1, 37, 144, 4096)) assertEquals(reference, detect(samples, block = block))
    }

    @Test
    fun `tracks the noise floor`() {
        val s = SyntheticSignals(rate, seconds = 2.0, seed = 25)
        s.whiteNoise(-50.0)
        val detector = OnsetDetector(rate)
        detect(s.toFloats(), detector)
        // Above 500 Hz, white noise keeps most of its power; the floor reads a little under its mean.
        assertEquals(-52.0, detector.noiseFloorDb.toDouble(), 3.0)
    }

    @Test
    fun `processing doesn't allocate`() {
        val allocated = threadAllocatedBytes() ?: return
        val s = SyntheticSignals(rate, seconds = 2.0, seed = 27)
        s.whiteNoise(-60.0)
        repeat(60) { s.padHit(0.1 + it * 0.025, 0.3) }
        val samples = s.toFloats()
        val detector = OnsetDetector(rate)
        var seen = 0
        val sink = OnsetSink { _, _ -> seen++ }
        var frame = 0L

        fun run() {
            var offset = 0
            while (offset + 240 <= samples.size) {
                detector.process(samples, offset, 240, frame, sink)
                offset += 240
                frame += 240
            }
        }
        repeat(5) { run() } // Warm up the JIT.
        val before = allocated()
        repeat(10) { run() }
        val bytes = allocated() - before
        assertTrue(seen > 0)
        assertTrue("$bytes bytes allocated while detecting", bytes < ALLOCATION_NOISE_BYTES)
    }
}
