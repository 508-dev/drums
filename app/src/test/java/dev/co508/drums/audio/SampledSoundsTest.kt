package dev.co508.drums.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class SampledSoundsTest {
    private fun load(
        layer: String,
        rate: Int = 48_000,
    ) = SampledSounds.snare(layer, rate) { File("src/main/assets", it).inputStream() }

    private fun raw(name: String) =
        SampledSounds.wav(File("src/main/assets/samples/$name.wav").inputStream(), 48_000, name)

    @Test
    fun bundledFilesStartCleanlyAndAreHeardTwoToThreeMillisecondsIn() {
        for (name in listOf("ghost-1", "ghost-2", "normal-1", "normal-2", "accent-1", "accent-2")) {
            val sound = raw(name)
            assertEquals(24_000, sound.frames)
            val peak = sound.samples.maxOf { abs(it) }
            assertTrue(peak in 0.79f..0.81f)
            assertTrue(sound.samples.indexOfFirst { abs(it) >= peak * 0.05f } < 96)
            assertEquals(0f, sound.samples.last(), 0.0001f)
            // The stick's transient is heard at 1.8–2.7 ms, though the drum's body peaks at 9–13 ms.
            val attackMillis = SampledSounds.perceivedAttack(sound.samples, 48_000) / 48.0
            assertTrue("$name attack $attackMillis ms", attackMillis in 1.5..3.0)
        }
    }

    @Test
    fun bundledLayersAreAlignedOnOneAttackAndResample() {
        for (layer in listOf("ghost", "normal", "accent")) {
            val sound = load(layer)
            assertEquals(192, sound.attackFrames)
            assertEquals(176, load(layer, 44_100).attackFrames)
            for (variation in listOf(sound) + sound.alternatives) {
                assertEquals(192, variation.attackFrames)
                assertEquals(192.0, SampledSounds.perceivedAttack(variation.samples, 48_000), 0.5)
                // Only silence was added in front.
                assertTrue(variation.samples.take(48).all { it == 0f })
                assertEquals(0f, variation.samples.last(), 0.0001f)
            }
            assertTrue(!sound.samples.contentEquals(sound.alternatives.single().samples))
        }
    }

    @Test
    fun perceivedAttackFindsTheStartOfASharpRise() {
        // Silence, then a full-scale square wave from frame 1 000.
        val samples =
            FloatArray(10_000) {
                if (it < 1_000) {
                    0f
                } else if (it / 10 % 2 == 0) {
                    1f
                } else {
                    -1f
                }
            }
        // Loudness integrated over 10 ms reaches −15 dB (3.2%) about 0.3 ms after the start.
        assertEquals(1_015.0, SampledSounds.perceivedAttack(samples, 48_000), 2.0)
        assertEquals(0.0, SampledSounds.perceivedAttack(FloatArray(100), 48_000), 0.0)
    }

    @Test
    fun buzzIsReproducibleAndHasNoLongSilentGaps() {
        val first = SampledSounds.buzz(load("ghost"), 48_000)
        val second = SampledSounds.buzz(load("ghost"), 48_000)
        assertArrayEquals(first.samples, second.samples, 0f)
        assertEquals(384_000, first.frames)
        // The roll's first bounce is an aligned ghost hit, unshifted: its attack is the hits' attack.
        val ghost = load("ghost")
        assertEquals(ghost.attackFrames, first.attackFrames)
        val opening = 192 + 480
        val best =
            (listOf(ghost) + ghost.alternatives).maxOf { variation ->
                var dot = 0.0
                var a = 0.0
                var b = 0.0
                for (i in 0 until opening) {
                    dot += first.samples[i] * variation.samples[i]
                    a += first.samples[i] * first.samples[i]
                    b += variation.samples[i] * variation.samples[i]
                }
                dot / kotlin.math.sqrt(a * b)
            }
        assertTrue("correlation $best", best > 0.99)
        for (start in 192 until first.frames - 960 step 960) {
            assertTrue((start until start + 960).maxOf { abs(first.samples[it]) } > 0.001f)
        }
    }

    @Test
    fun mixerAdvancesEachSoundsRoundRobinIndependently() {
        val a = Sound("a", floatArrayOf(0.1f), listOf(Sound("a2", floatArrayOf(0.3f))))
        val b = Sound("b", floatArrayOf(0.2f), listOf(Sound("b2", floatArrayOf(0.4f))))
        val mixer = Mixer()
        val output = FloatArray(8)
        mixer.render(
            output,
            4,
            EventSource { from, _, sink ->
                sink.play(from, a, 1f, -1f)
                sink.play(from + 1, b, 1f, -1f)
                sink.play(from + 2, a, 1f, -1f)
                sink.play(from + 3, b, 1f, -1f)
            },
        )
        assertEquals(0.1f, output[0], 0.0001f)
        assertEquals(0.2f, output[2], 0.0001f)
        assertEquals(0.3f, output[4], 0.0001f)
        assertEquals(0.4f, output[6], 0.0001f)
        assertNotEquals(output[0], output[4])
    }
}
