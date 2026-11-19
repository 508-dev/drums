package dev.co508.drums.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PlaceholderSoundsTest {
    private val rates = listOf(44_100, 48_000)

    private fun peak(sound: Sound) = sound.samples.maxOf { abs(it) }

    /** Index of the first sample reaching half the sound's peak. */
    private fun onset(sound: Sound): Int {
        val half = peak(sound) / 2
        return sound.samples.indexOfFirst { abs(it) >= half }
    }

    private fun zeroCrossings(sound: Sound): Int {
        val s = sound.samples
        return (1 until s.size).count { (s[it - 1] < 0) != (s[it] < 0) }
    }

    @Test
    fun soundsStartWithinAMillisecondOfFrameZero() {
        for (rate in rates) {
            for (sound in listOf(
                PlaceholderSounds.click(rate, accented = false),
                PlaceholderSounds.click(rate, accented = true),
                PlaceholderSounds.snare(rate),
            )) {
                assertTrue("$sound at $rate Hz starts late: ${onset(sound)}", onset(sound) in 0..rate / 1000)
            }
        }
    }

    @Test
    fun soundsStayWithinFullScaleAndEndSilently() {
        for (rate in rates) {
            for (sound in listOf(
                PlaceholderSounds.click(rate, accented = false),
                PlaceholderSounds.click(rate, accented = true),
                PlaceholderSounds.snare(rate),
            )) {
                assertTrue("$sound peak", peak(sound) <= 1f && peak(sound) > 0.5f)
                assertTrue("$sound tail", abs(sound.samples.last()) < 0.01f)
            }
        }
    }

    @Test
    fun accentedClickIsLouderAndHigher() {
        val normal = PlaceholderSounds.click(48_000, accented = false)
        val accent = PlaceholderSounds.click(48_000, accented = true)
        assertTrue(peak(accent) > peak(normal))
        assertTrue(zeroCrossings(accent) > zeroCrossings(normal))
    }

    @Test
    fun lengthsFollowTheSampleRate() {
        assertEquals(1_440, PlaceholderSounds.click(48_000, accented = false).frames)
        assertEquals(12_000, PlaceholderSounds.snare(48_000).frames)
        assertEquals(11_025, PlaceholderSounds.snare(44_100).frames)
    }

    @Test
    fun snareIsReproducibleForASeed() {
        val seven = PlaceholderSounds.snare(48_000, seed = 7).samples
        assertArrayEquals(seven, PlaceholderSounds.snare(48_000, seed = 7).samples, 0f)
        assertFalse(seven.contentEquals(PlaceholderSounds.snare(48_000, seed = 8).samples))
    }

    /**
     * The strongest normalized autocorrelation of the sound's loudness envelope
     * (2 ms windows) at lags of 20 to 60 ms: high when the sound ticks at a
     * steady rate, as the first placeholder buzz did (a "sprinkler").
     */
    private fun pulseRegularity(samples: FloatArray): Double {
        val window = 96 // 2 ms at 48 kHz
        val envelope =
            DoubleArray(
                samples.size / window,
            ) { w -> (0 until window).sumOf { abs(samples[w * window + it]).toDouble() } }
        val mean = envelope.average()
        val e = envelope.map { it - mean }
        val energy = e.sumOf { it * it }
        return (10..30).maxOf { lag -> (0 until e.size - lag).sumOf { e[it] * e[it + lag] } / energy }
    }

    /** The first placeholder buzz: steady noise pulsing at 28 Hz. */
    private fun sprinkler(frames: Int): FloatArray {
        val random = kotlin.random.Random(1)
        return FloatArray(frames) { i ->
            val bounce = (i / 48_000.0 * 28) % 1.0
            (random.nextDouble(-1.0, 1.0) * (0.45 + 0.55 * kotlin.math.exp(-bounce * 6))).toFloat()
        }
    }

    @Test
    fun theBuzzIsDenseAndIrregularNotATickingPulse() {
        val buzz = PlaceholderSounds.buzz(48_000)
        assertEquals(8 * 48_000, buzz.frames)
        assertTrue(onset(buzz) in 0..48)
        assertTrue(peak(buzz) <= 0.5f + 1e-6f)
        // Dense: no 20 ms stretch falls silent.
        val window = 960
        val quietest =
            (0 until buzz.frames - window step window).minOf { w ->
                (w until w + window).maxOf { abs(buzz.samples[it]) }
            }
        assertTrue("a gap in the roll: $quietest", quietest > 0.02f)
        // Irregular: far less periodic than the sprinkler.
        val regularity = pulseRegularity(buzz.samples)
        val ticking = pulseRegularity(sprinkler(buzz.frames))
        println("buzz regularity $regularity, sprinkler $ticking, quietest 20 ms peak $quietest")
        assertTrue("buzz regularity $regularity vs the sprinkler's $ticking", regularity < 0.5 * ticking)
    }

    @Test
    fun theBuzzIsReproducibleForASeed() {
        assertArrayEquals(
            PlaceholderSounds.buzz(48_000, seed = 3).samples,
            PlaceholderSounds.buzz(48_000, seed = 3).samples,
            0f,
        )
    }
}
