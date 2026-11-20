package dev.co508.drums.audio

import dev.co508.drums.playback.DrumKit
import dev.co508.drums.playback.Pattern
import dev.co508.drums.playback.PatternEvent
import dev.co508.drums.playback.PlaySettings
import dev.co508.drums.playback.Transport
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * End to end (#83): through the transport and the mixer, every dynamic and
 * variation of the sampled snare is *heard* on its beat, not a few ms late.
 */
class AttackAlignmentTest {
    private val rate = 48_000

    private fun load(layer: String) = SampledSounds.snare(layer, rate) { File("src/main/assets", it).inputStream() }

    @Test
    fun everySampledStrokeIsHeardOnItsBeat() {
        val ghost = load("ghost")
        val kit =
            DrumKit(
                snare = load("normal"),
                buzz = SampledSounds.buzz(ghost, rate),
                ghost = ghost,
                accent = load("accent"),
            )
        // One stroke a second (60 BPM), each layer twice so both variations play.
        val sounds = listOf(kit.ghost, kit.ghost, kit.snare, kit.snare, kit.accent, kit.accent)
        val pattern =
            Pattern(
                lengthQuarters = sounds.size.toDouble(),
                beatQuarters = 1.0,
                countInBeats = 1,
                events = sounds.mapIndexed { i, sound -> PatternEvent(i.toDouble(), sound) },
                clicks = emptyList(),
            )
        val transport = Transport(rate, Sound("click", FloatArray(1)), Sound("accent", FloatArray(1)))
        transport.start(PlaySettings(pattern, beatsPerMinute = 60.0, countIn = true, loop = false))
        val mixer = Mixer()
        val block = 240
        val stereo = FloatArray(block * 2)
        val left = FloatArray(rate * (sounds.size + 2))
        for (b in 0 until left.size / block) {
            mixer.render(stereo, block, transport)
            for (i in 0 until block) left[b * block + i] = stereo[2 * i]
        }
        // The count-in is one beat: stroke k is due at frame (k + 1) · 48 000.
        for (k in sounds.indices) {
            val beat = (k + 1) * rate
            val from = beat - rate / 50 // 20 ms before
            val heard = from + SampledSounds.perceivedAttack(left.copyOfRange(from, from + rate / 5), rate)
            val offMillis = (heard - beat) * 1000.0 / rate
            assertTrue(
                "stroke $k (${sounds[k].name}) heard ${"%.2f".format(offMillis)} ms off its beat",
                abs(offMillis) < 1.0,
            )
        }
    }
}
