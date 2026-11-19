package dev.co508.drums.audio

import dev.co508.drums.ALLOCATION_NOISE_BYTES
import dev.co508.drums.threadAllocatedBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class MixerTest {
    private data class Hit(
        val frame: Long,
        val sound: Sound,
        val gain: Float = 1f,
        val pan: Float = 0f,
    )

    /** An event source over a fixed list, honouring the [EventSource] contract. */
    private class ListSource(
        private val hits: List<Hit>,
    ) : EventSource {
        override fun events(
            fromFrame: Long,
            toFrame: Long,
            sink: EventSink,
        ) {
            for (h in hits) {
                if (h.frame in fromFrame until toFrame) sink.play(h.frame, h.sound, h.gain, h.pan)
            }
        }
    }

    /** Renders [totalFrames] frames in blocks of [blockFrames] and returns the whole interleaved stream. */
    private fun renderStream(
        mixer: Mixer,
        source: EventSource?,
        totalFrames: Int,
        blockFrames: Int,
    ): FloatArray {
        val stream = FloatArray(totalFrames * 2)
        val block = FloatArray(blockFrames * 2)
        var done = 0
        while (done < totalFrames) {
            val n = minOf(blockFrames, totalFrames - done)
            mixer.render(block, n, source)
            block.copyInto(stream, done * 2, 0, n * 2)
            done += n
        }
        return stream
    }

    private fun left(
        stream: FloatArray,
        frame: Int,
    ) = stream[frame * 2]

    private fun right(
        stream: FloatArray,
        frame: Int,
    ) = stream[frame * 2 + 1]

    private val centre = (1 / sqrt(2.0)).toFloat()

    private fun impulse(name: String = "impulse") = Sound(name, floatArrayOf(1f))

    @Test
    fun soundStartsOnItsExactFrameForEveryBlockSize() {
        val sound = Sound("ramp", floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f))
        for (frame in listOf(0L, 1L, 63L, 64L, 65L, 255L, 256L, 1000L)) {
            for (blockSize in listOf(1, 7, 64, 240, 256, 1024)) {
                val stream = renderStream(Mixer(), ListSource(listOf(Hit(frame, sound, pan = -1f))), 1200, blockSize)
                val f = frame.toInt()
                for (i in 0 until 1200) {
                    val expected = if (i in f until f + 5) sound.samples[i - f] else 0f
                    assertEquals("frame $frame, block $blockSize, output $i", expected, left(stream, i), 1e-6f)
                }
            }
        }
    }

    @Test
    fun outputIsIdenticalWhateverTheBlockSize() {
        val snare = PlaceholderSounds.snare(48_000)
        val click = PlaceholderSounds.click(48_000, accented = true)
        val hits =
            (0 until 40).map { n ->
                Hit(
                    frame = n * 1_237L + n % 3,
                    sound = if (n % 4 == 0) click else snare,
                    gain = 0.3f,
                    pan = (n % 5 - 2) / 2f,
                )
            }
        val reference = renderStream(Mixer(), ListSource(hits), 60_000, 1)
        for (blockSize in listOf(13, 96, 192, 240, 4096)) {
            // Voices may land in different slots, so sums can differ in the last bit.
            assertArrayEquals(
                "block $blockSize",
                reference,
                renderStream(Mixer(), ListSource(hits), 60_000, blockSize),
                1e-6f,
            )
        }
    }

    @Test
    fun overlappingVoicesAreSummed() {
        val a = Sound("a", floatArrayOf(0.1f, 0.1f, 0.1f, 0.1f))
        val b = Sound("b", floatArrayOf(0.2f, 0.2f))
        val stream = renderStream(Mixer(), ListSource(listOf(Hit(10, a, pan = -1f), Hit(12, b, pan = -1f))), 20, 16)
        assertEquals(0.1f, left(stream, 10), 1e-6f)
        assertEquals(0.1f, left(stream, 11), 1e-6f)
        assertEquals(0.3f, left(stream, 12), 1e-6f)
        assertEquals(0.3f, left(stream, 13), 1e-6f)
        assertEquals(0f, left(stream, 14), 1e-6f)
    }

    @Test
    fun theSameSoundCanOverlapItself() {
        val sound = Sound("tail", FloatArray(100) { 0.01f })
        val stream =
            renderStream(Mixer(), ListSource(listOf(Hit(0, sound, pan = -1f), Hit(50, sound, pan = -1f))), 200, 32)
        assertEquals(0.01f, left(stream, 49), 1e-6f)
        assertEquals(0.02f, left(stream, 50), 1e-6f)
        assertEquals(0.02f, left(stream, 99), 1e-6f)
        assertEquals(0.01f, left(stream, 100), 1e-6f)
        assertEquals(0f, left(stream, 150), 1e-6f)
    }

    @Test
    fun gainScalesTheVoice() {
        val stream = renderStream(Mixer(), ListSource(listOf(Hit(3, impulse(), gain = 0.25f, pan = 1f))), 8, 8)
        assertEquals(0.25f, right(stream, 3), 1e-6f)
        assertEquals(0f, left(stream, 3), 1e-6f)
    }

    @Test
    fun panUsesConstantPower() {
        fun gains(pan: Float): Pair<Float, Float> {
            // Half scale, below the soft-clip knee, then scaled back up.
            val stream = renderStream(Mixer(), ListSource(listOf(Hit(0, impulse(), gain = 0.5f, pan = pan))), 1, 1)
            return 2 * left(stream, 0) to 2 * right(stream, 0)
        }
        val (ll, lr) = gains(-1f)
        assertEquals(1f, ll, 1e-6f)
        assertEquals(0f, lr, 1e-6f)
        val (cl, cr) = gains(0f)
        assertEquals(centre, cl, 1e-6f)
        assertEquals(centre, cr, 1e-6f)
        val (rl, rr) = gains(1f)
        assertEquals(0f, rl, 1e-6f)
        assertEquals(1f, rr, 1e-6f)
        for (pan in listOf(-0.7f, -0.2f, 0.3f, 0.9f)) {
            val (l, r) = gains(pan)
            assertEquals("power at pan $pan", 1f, l * l + r * r, 1e-5f)
            assertTrue("pan $pan leans the right way", (pan < 0) == (l > r))
        }
    }

    @Test
    fun panOutsideTheRangeIsClamped() {
        val stream = renderStream(Mixer(), ListSource(listOf(Hit(0, impulse(), gain = 0.5f, pan = 5f))), 1, 1)
        assertEquals(0f, left(stream, 0), 1e-6f)
        assertEquals(0.5f, right(stream, 0), 1e-6f)
    }

    @Test
    fun loudMixesAreSoftClippedWithinFullScale() {
        val loud = Sound("loud", floatArrayOf(0.8f, -0.8f))
        val hits = listOf(Hit(0, loud, pan = -1f), Hit(0, loud, pan = -1f))
        val stream = renderStream(Mixer(), ListSource(hits), 2, 2)
        // 1.6 comes out just under full scale, and symmetric.
        assertTrue(left(stream, 0) in 0.99f..1f)
        assertEquals(-left(stream, 0), left(stream, 1), 0f)
    }

    @Test
    fun softClipIsTransparentBelowTheKneeAndBoundedAbove() {
        for (x in listOf(0f, 0.1f, -0.5f, 0.9f, -0.9f)) assertEquals(x, Mixer.softClip(x), 0f)
        var previous = Mixer.softClip(Mixer.SOFT_CLIP_KNEE)
        var x = Mixer.SOFT_CLIP_KNEE
        while (x < 20f) {
            x += 0.01f
            val y = Mixer.softClip(x)
            assertTrue("rising at $x", y >= previous)
            assertTrue("bounded at $x", y <= 1f)
            assertEquals("odd at $x", -y, Mixer.softClip(-x), 0f)
            previous = y
        }
        // Slope matches at the knee: no kink.
        val above = Mixer.softClip(Mixer.SOFT_CLIP_KNEE + 1e-3f) - Mixer.SOFT_CLIP_KNEE
        assertEquals(1e-3f, above, 1e-5f)
    }

    @Test
    fun masterGainAppliesBeforeClipping() {
        val stream = renderStream(Mixer(masterGain = 0.5f), ListSource(listOf(Hit(0, impulse(), pan = -1f))), 1, 1)
        assertEquals(0.5f, left(stream, 0), 1e-6f)
    }

    @Test
    fun theSourceIsAskedAboutEachBlockExactlyOnce() {
        val windows = mutableListOf<Pair<Long, Long>>()
        val source = EventSource { from, to, _ -> windows += from to to }
        renderStream(Mixer(), source, 1000, 240)
        assertEquals(listOf(0L to 240L, 240L to 480L, 480L to 720L, 720L to 960L, 960L to 1000L), windows)
    }

    @Test
    fun framesRenderedCountsTheStream() {
        val mixer = Mixer()
        renderStream(mixer, null, 1234, 100)
        assertEquals(1234L, mixer.framesRendered)
    }

    @Test
    fun aLateEventPlaysAtTheBlockStartWithItsAttack() {
        val sound = Sound("ramp", floatArrayOf(0.5f, 0.25f))
        // Reports frame 90 while the block is [100, 200).
        val source =
            EventSource { from, _, sink -> if (from == 100L) sink.play(90, sound, 1f, -1f) }
        val mixer = Mixer()
        val stream = renderStream(mixer, source, 200, 100)
        assertEquals(0.5f, left(stream, 100), 1e-6f)
        assertEquals(0.25f, left(stream, 101), 1e-6f)
        assertEquals(1L, mixer.stats().lateEvents)
    }

    @Test
    fun anEventBeyondTheBlockIsDropped() {
        val source = EventSource { from, to, sink -> if (from == 0L) sink.play(to, impulse(), 1f, 0f) }
        val mixer = Mixer()
        val stream = renderStream(mixer, source, 200, 100)
        assertTrue(stream.all { it == 0f })
        assertEquals(1L, mixer.stats().droppedEvents)
    }

    @Test
    fun aVoiceIsFreedWhenItsSoundEnds() {
        val mixer = Mixer(maxVoices = 1)
        val sound = Sound("short", floatArrayOf(0.1f, 0.1f))
        val hits = listOf(Hit(0, sound, pan = -1f), Hit(2, sound, pan = -1f), Hit(4, sound, pan = -1f))
        renderStream(mixer, ListSource(hits), 10, 1)
        assertEquals(0L, mixer.stats().stolenVoices)
    }

    @Test
    fun whenAllVoicesAreBusyTheOldestIsStolen() {
        val mixer = Mixer(maxVoices = 2)
        val long = Sound("long", FloatArray(100) { 0.01f })
        val hits =
            listOf(
                Hit(0, long, pan = -1f),
                Hit(10, long, pan = -1f),
                Hit(20, long, pan = -1f),
            )
        val stream = renderStream(mixer, ListSource(hits), 200, 8)
        assertEquals(1L, mixer.stats().stolenVoices)
        // The voice started at 0 is cut when the block holding frame 20 starts.
        assertEquals(0.02f, left(stream, 25), 1e-6f)
        // The voice from 10 ends at 110; the voice from 20 ends at 120.
        assertEquals(0.01f, left(stream, 115), 1e-6f)
        assertEquals(0f, left(stream, 120), 1e-6f)
        assertEquals(2, mixer.stats().peakVoices)
    }

    @Test
    fun renderingDoesNotAllocate() {
        val allocated = threadAllocatedBytes() ?: return // Not measurable on this JVM.
        val snare = PlaceholderSounds.snare(48_000)
        val click = PlaceholderSounds.click(48_000, accented = false)
        val mixer = Mixer()
        // 32nd notes at 300 BPM is one event every 1200 frames at 48 kHz.
        val source =
            EventSource { from, to, sink ->
                var frame = (from + 1199) / 1200 * 1200
                while (frame < to) {
                    sink.play(frame, snare, 0.8f, -0.3f)
                    if (frame % 9600 == 0L) sink.play(frame, click, 0.5f, 0f)
                    frame += 1200
                }
            }
        val block = FloatArray(240 * 2)
        repeat(5_000) { mixer.render(block, 240, source) } // Warm up the JIT.
        val before = allocated()
        repeat(20_000) { mixer.render(block, 240, source) }
        val bytes = allocated() - before
        // Reading the counter reflectively costs a few bytes; one allocation per block would be 20 000+.
        assertTrue("$bytes bytes allocated while rendering", bytes < ALLOCATION_NOISE_BYTES)
    }
}
