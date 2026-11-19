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
}
