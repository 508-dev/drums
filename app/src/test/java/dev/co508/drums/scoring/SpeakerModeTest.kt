package dev.co508.drums.scoring

import dev.co508.drums.audio.EventSink
import dev.co508.drums.audio.OutputRoute
import dev.co508.drums.audio.PlaceholderSounds
import dev.co508.drums.audio.Sound
import dev.co508.drums.hits.OnsetDetector
import dev.co508.drums.hits.SyntheticSignals
import dev.co508.drums.hits.decibelsToAmplitude
import dev.co508.drums.hits.detect
import dev.co508.drums.playback.Pattern
import dev.co508.drums.playback.PlaySettings
import dev.co508.drums.playback.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.tanh

class SpeakerModeTest {
    private val rate = 48_000

    /**
     * The speaker click as the mic hears it: played loud through a phone
     * speaker that distorts (tanh, driven hard enough to add strong harmonics).
     */
    private fun throughSpeaker(
        s: SyntheticSignals,
        click: FloatArray,
        at: Int,
        gain: Double,
        drive: Double = 3.0,
    ) {
        for (i in click.indices) s.samples[at + i] += gain * tanh(drive * click[i]) / tanh(drive)
    }

    @Test
    fun `the speaker-mode detector hears none of the speaker clicks, loud and mildly distorted`() {
        val normal = PlaceholderSounds.speakerClick(rate, accented = false)
        val accent = PlaceholderSounds.speakerClick(rate, accented = true)
        // 100 BPM for 7 s at full scale: the room's loudest sound by far, 70 dB over its noise.
        for (drive in listOf(0.01, 0.5)) {
            val s = SyntheticSignals(rate, seconds = 8.0, seed = 51)
            s.whiteNoise(-70.0)
            for (beat in 0 until 11) {
                val click = sampleOf(if (beat % 4 == 0) accent else normal)
                throughSpeaker(s, click, at = 24_000 + beat * 28_800, gain = 1.0, drive = drive)
            }
            val onsets = detect(s.toFloats(), OnsetDetector(rate, SpeakerMode.DETECTOR))
            assertEquals("drive $drive", emptyList<Pair<Double, Float>>(), onsets)
        }
    }

    @Test
    fun `a speaker driven into hard distortion does leak clicks (best effort)`() {
        val s = SyntheticSignals(rate, seconds = 3.0, seed = 54)
        s.whiteNoise(-70.0)
        val click = sampleOf(PlaceholderSounds.speakerClick(rate, accented = true))
        for (beat in 0 until 4) throughSpeaker(s, click, at = 24_000 + beat * 28_800, gain = 1.0, drive = 3.0)
        assertTrue(detect(s.toFloats(), OnsetDetector(rate, SpeakerMode.DETECTOR)).isNotEmpty())
    }

    @Test
    fun `pad hits between and on the speaker clicks are still found`() {
        val s = SyntheticSignals(rate, seconds = 8.0, seed = 52)
        s.whiteNoise(-70.0)
        val click = sampleOf(PlaceholderSounds.speakerClick(rate, accented = true))
        val expected = mutableListOf<Double>()
        for (beat in 0 until 11) {
            val at = 24_000 + beat * 28_800
            throughSpeaker(s, click, at, gain = 1.0, drive = 1.0)
            // The player hits on the click and on the "&", much quieter than the click.
            expected += s.padHit(at / 48_000.0, decibelsToAmplitude(-30.0))
            expected += s.padHit((at + 14_400) / 48_000.0, decibelsToAmplitude(-35.0))
        }
        val onsets = detect(s.toFloats(), OnsetDetector(rate, SpeakerMode.DETECTOR))
        assertEquals(expected.size, onsets.size)
        // Still timed within a millisecond, hits right on a click included.
        expected.zip(onsets).forEach { (e, o) -> assertTrue("${(o.first - e) / 48} ms", abs(o.first - e) < 48) }
    }

    @Test
    fun `the usual click is heard, which is why speaker mode swaps it`() {
        val s = SyntheticSignals(rate, seconds = 3.0, seed = 53)
        s.whiteNoise(-70.0)
        val click = sampleOf(PlaceholderSounds.click(rate, accented = true))
        for (beat in 0 until 4) throughSpeaker(s, click, at = 24_000 + beat * 28_800, gain = decibelsToAmplitude(-6.0))
        assertTrue(detect(s.toFloats()).size >= 3)
    }

    @Test
    fun `speaker mode mutes the snare and swaps the click, only on the speaker`() {
        val normalClick = Sound("click", FloatArray(4) { 1f })
        val transport = Transport(rate, normalClick, Sound("accent", FloatArray(4) { 1f }))
        val mode = SpeakerMode(transport)
        transport.instrumentVolume = 0.8f
        assertFalse(mode.applyFor(OutputRoute(OutputRoute.Kind.WIRED, "x")))
        assertEquals(0.8f, transport.instrumentVolume)
        assertTrue(mode.applyFor(OutputRoute(OutputRoute.Kind.SPEAKER, "phone")))
        assertEquals(0f, transport.instrumentVolume)
        assertEquals(0f, mode.instrumentVolume(0.8f))
        assertEquals("speaker-click", firstClick(transport))
        // Its soft attack is heard a few ms in; the transport leads it by that much.
        val attack = PlaceholderSounds.speakerClick(rate, accented = false).attackFrames
        assertTrue("attack $attack", attack in 4 * 48..12 * 48)
        mode.exit()
        assertFalse(mode.isActive)
        assertEquals(0.8f, mode.instrumentVolume(0.8f))
        assertEquals("click", firstClick(transport))
        assertFalse(SpeakerMode.appliesTo(null))
    }

    /** The name of the first non-accented click the transport plays. */
    private fun firstClick(transport: Transport): String {
        val pattern = Pattern(4.0, 1.0, 4, emptyList(), Pattern.steadyClicks(4.0, 4, 1.0))
        transport.start(PlaySettings(pattern, 120.0, countIn = false))
        val names = mutableListOf<String>()
        val sink =
            object : EventSink {
                override fun play(
                    frame: Long,
                    sound: Sound,
                    gain: Float,
                    pan: Float,
                ) {
                    names += sound.name
                }
            }
        var frame = 0L
        while (names.size < 2) {
            transport.events(frame, frame + 240, sink)
            frame += 240
        }
        transport.stop()
        return names[1]
    }

    private fun sampleOf(sound: Sound): FloatArray = sound.samples
}
