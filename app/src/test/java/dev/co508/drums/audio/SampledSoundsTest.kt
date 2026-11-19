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

    @Test
    fun bundledLayersHaveCleanAttacksAndResample() {
        for (layer in listOf("ghost", "normal", "accent")) {
            val sound = load(layer)
            assertEquals(24_000, sound.frames)
            assertEquals(22_050, load(layer, 44_100).frames)
            for (variation in listOf(sound) + sound.alternatives) {
                val peak = variation.samples.maxOf { abs(it) }
                assertTrue(peak in 0.79f..0.81f)
                assertTrue(variation.samples.indexOfFirst { abs(it) >= peak * 0.05f } < 96)
                assertEquals(0f, variation.samples.last(), 0.0001f)
            }
            assertTrue(!sound.samples.contentEquals(sound.alternatives.single().samples))
        }
    }

    @Test
    fun buzzIsReproducibleAndHasNoLongSilentGaps() {
        val first = SampledSounds.buzz(load("ghost"), 48_000)
        val second = SampledSounds.buzz(load("ghost"), 48_000)
        assertArrayEquals(first.samples, second.samples, 0f)
        assertEquals(384_000, first.frames)
        for (start in 0 until first.frames - 960 step 960) {
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
