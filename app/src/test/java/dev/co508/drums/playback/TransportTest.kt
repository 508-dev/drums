package dev.co508.drums.playback

import dev.co508.drums.ALLOCATION_NOISE_BYTES
import dev.co508.drums.audio.EventSink
import dev.co508.drums.audio.Sound
import dev.co508.drums.threadAllocatedBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

class TransportTest {
    private val rate = 48_000
    private val click = Sound("click", floatArrayOf(1f))
    private val accent = Sound("accent", floatArrayOf(1f))
    private val snare = Sound("snare", floatArrayOf(1f))

    private data class Played(
        val frame: Long,
        val sound: String,
        val gain: Float,
        val pan: Float,
        val frames: Int? = null,
    )

    /** Drives a transport like the mixer does, checking every event lands in the block it was asked for. */
    private inner class Driver(
        val transport: Transport,
        val blockFrames: Int,
    ) {
        val played = mutableListOf<Played>()
        var frame = 0L
        private var blockEnd = 0L
        private val sink =
            object : EventSink {
                override fun play(
                    frame: Long,
                    sound: Sound,
                    gain: Float,
                    pan: Float,
                ) = record(frame, sound, gain, pan, null)

                override fun playFor(
                    frame: Long,
                    sound: Sound,
                    gain: Float,
                    pan: Float,
                    frames: Int,
                ) = record(frame, sound, gain, pan, frames)
            }

        private fun record(
            f: Long,
            sound: Sound,
            gain: Float,
            pan: Float,
            frames: Int?,
        ) {
            assertTrue("event at $f outside block ending $blockEnd", f in frame until blockEnd)
            played += Played(f, sound.name, gain, pan, frames)
        }

        fun block() {
            blockEnd = frame + blockFrames
            transport.events(frame, blockEnd, sink)
            frame = blockEnd
        }

        fun runUntil(endFrame: Long) {
            while (frame < endFrame) block()
        }

        fun ofSound(name: String) = played.filter { it.sound == name }
    }

    private fun transport() = Transport(rate, click, accent)

    /** Snare strokes every [step] quarters across [lengthQuarters], with a 4/4 click. */
    private fun strokes(
        lengthQuarters: Double,
        step: Double,
    ): Pattern {
        val count = (lengthQuarters / step).toInt()
        return Pattern(
            lengthQuarters,
            beatQuarters = 1.0,
            countInBeats = 4,
            events = List(count) { PatternEvent(it * step, snare, gain = 0.5f, pan = 0.25f) },
            clicks = Pattern.steadyClicks(lengthQuarters, beatsPerBar = 4, beatQuarters = 1.0),
        )
    }

    @Test
    fun countInBarThenPatternOnExactFrames() {
        // 120 BPM: 24 000 frames per beat.
        val t = transport()
        t.start(PlaySettings(strokes(4.0, 1.0), beatsPerMinute = 120.0))
        val d = Driver(t, 240)
        d.runUntil(200_000)
        assertEquals(
            listOf(
                Played(0, "accent", 1f, 0f),
                Played(24_000, "click", 1f, 0f),
                Played(48_000, "click", 1f, 0f),
                Played(72_000, "click", 1f, 0f),
                Played(96_000, "accent", 1f, 0f),
                Played(96_000, "snare", 0.5f, 0.25f),
                Played(120_000, "click", 1f, 0f),
                Played(120_000, "snare", 0.5f, 0.25f),
                Played(144_000, "click", 1f, 0f),
                Played(144_000, "snare", 0.5f, 0.25f),
                Played(168_000, "click", 1f, 0f),
                Played(168_000, "snare", 0.5f, 0.25f),
                Played(192_000, "accent", 1f, 0f),
                Played(192_000, "snare", 0.5f, 0.25f),
            ),
            d.played,
        )
        assertEquals(Transport.State.PLAYING, t.state)
    }

    @Test
    fun playbackStartsAtTheBlockWhereStartIsSeen() {
        val t = transport()
        val d = Driver(t, 100)
        d.runUntil(1_000)
        t.start(PlaySettings(strokes(4.0, 1.0), beatsPerMinute = 120.0, countIn = false))
        d.runUntil(30_000)
        assertEquals(listOf(1_000L, 25_000L), d.ofSound("snare").map { it.frame })
        assertEquals(0.0, t.positionAt(1_000.0), 0.0)
        assertEquals(0.0, t.positionAt(500.0), 0.0) // Before the start: held at the start.
    }

    @Test
    fun eventsLandOnTheFirstFrameAtOrAfterTheirExactTime() {
        // 7 BPM: 411 428.57… frames per quarter, so no event falls on a whole frame.
        val t = transport()
        t.start(PlaySettings(strokes(1.0, 1.0 / 3), beatsPerMinute = 7.0, countIn = false))
        val d = Driver(t, 256)
        d.runUntil(1_300_000)
        val framesPerQuarter = rate * 60.0 / 7.0
        val expected = (0 until 9).map { ceil(it / 3.0 * framesPerQuarter).toLong() }
        assertEquals(expected, d.ofSound("snare").map { it.frame }.take(9))
    }

    @Test
    fun eventFramesDoNotDependOnTheBlockSize() {
        fun run(block: Int): List<Played> {
            val t = transport()
            t.start(PlaySettings(strokes(3.0, 0.125 / 3), beatsPerMinute = 97.0, trainer = SpeedTrainer(3.0, 1, 130.0)))
            // Within a block, events come in no particular order; compare by frame.
            return Driver(t, block)
                .apply { runUntil(1_000_000) }
                .played
                .filter { it.frame < 990_000 }
                .sortedWith(compareBy({ it.frame }, { it.sound }))
        }
        val reference = run(240)
        assertTrue(reference.size > 500)
        for (block in listOf(1, 7, 96, 192, 1_024, 4_096)) {
            assertEquals("block $block", reference, run(block))
        }
    }

    @Test
    fun loopsRepeatWithoutGapsOrDuplicates() {
        val t = transport()
        t.start(PlaySettings(strokes(2.0, 0.5), beatsPerMinute = 150.0, countIn = false))
        val d = Driver(t, 333)
        d.runUntil(10 * 38_400 + 1)
        // 150 BPM: 19 200 frames per beat, 9 600 per eighth; a loop is 38 400 frames.
        assertEquals((0..40).map { it * 9_600L }, d.ofSound("snare").map { it.frame })
        assertEquals((0..20).map { it * 19_200L }, (d.ofSound("click") + d.ofSound("accent")).map { it.frame }.sorted())
        // Clicks count from the pattern's start, so each loop starts on an accent.
        assertEquals((0..10).map { it * 38_400L }, d.ofSound("accent").map { it.frame })
    }

    @Test
    fun withoutLoopItPlaysOnceThenFinishes() {
        val t = transport()
        t.start(PlaySettings(strokes(4.0, 1.0), beatsPerMinute = 120.0, loop = false))
        val d = Driver(t, 240)
        d.runUntil(1_000_000)
        assertEquals(listOf(96_000L, 120_000L, 144_000L, 168_000L), d.ofSound("snare").map { it.frame })
        assertEquals(8, (d.ofSound("click") + d.ofSound("accent")).size)
        assertEquals(Transport.State.FINISHED, t.state)
        assertEquals(4.0, t.positionAt(900_000.0), 1e-9)
        assertEquals(0.0, t.beatsPerMinuteAt(900_000.0), 0.0)
    }

    @Test
    fun aTempoChangeTakesEffectAtTheNextBlockWithPositionContinuous() {
        val t = transport()
        t.start(PlaySettings(strokes(4.0, 0.25), beatsPerMinute = 120.0, countIn = false))
        val d = Driver(t, 240)
        d.runUntil(30_000) // Position 1.25 at frame 30 000.
        t.setTempo(60.0)
        d.runUntil(200_000)
        assertEquals(1.25, t.positionAt(30_000.0), 1e-12)
        assertEquals(120.0, t.beatsPerMinuteAt(29_999.0), 1e-9)
        assertEquals(60.0, t.beatsPerMinuteAt(30_000.0), 1e-9)
        val frames = d.ofSound("snare").map { it.frame }
        // Sixteenths: 6 000 frames apart at 120 BPM, then 12 000 apart at 60 BPM.
        assertEquals((0..5).map { it * 6_000L }, frames.take(6))
        assertEquals(listOf(30_000L, 42_000L, 54_000L), frames.subList(5, 8))
    }

    @Test
    fun randomTempoChangesNeverSkipOrRepeatAStroke() {
        val random = Random(42)
        // Room for every tempo change, so old frames can still be checked.
        val t = Transport(rate, click, accent, TempoMap(capacity = 4_096))
        t.clickVolume = 0f
        val step = 0.125 / 3 // 32nd-note triplets: never on a whole frame.
        t.start(PlaySettings(strokes(4.0, step), beatsPerMinute = 200.0, countIn = false))
        val d = Driver(t, 192)
        while (d.frame < 3_000_000) {
            if (random.nextInt(20) == 0) t.setTempo(random.nextDouble(30.0, 300.0))
            d.block()
        }
        val frames = d.ofSound("snare").map { it.frame }
        val reached = t.positionAt((d.frame - 1).toDouble())
        // Stroke n is at position n * step: every one up to the position reached, once.
        assertEquals(floor(reached / step + 1e-9).toLong() + 1, frames.size.toLong())
        for ((n, frame) in frames.withIndex()) {
            val position = n * step
            assertTrue("stroke $n at $frame is early", t.positionAt(frame.toDouble()) >= position - 1e-9)
            assertTrue("stroke $n at $frame is late", t.positionAt(frame - 1.0) < position + 1e-9)
        }
    }

    @Test
    fun speedTrainerChangesTempoExactlyOnLoopBoundaries() {
        val t = transport()
        val trainer = SpeedTrainer(stepBpm = 10.0, everyLoops = 2, targetBpm = 125.0)
        t.start(PlaySettings(strokes(4.0, 1.0), beatsPerMinute = 100.0, countIn = false, trainer = trainer))
        val d = Driver(t, 240)
        d.runUntil(2_000_000)
        val downbeats = d.ofSound("snare").map { it.frame }.filterIndexed { i, _ -> i % 4 == 0 }
        // A loop is 4 beats: 115 200 frames at 100 BPM, 104 727.27 at 110,
        // 96 000 at 120, 92 160 at 125.
        val loopFrames = listOf(115_200.0, 115_200.0, 1_152_000.0 / 11, 1_152_000.0 / 11, 96_000.0, 96_000.0, 92_160.0)
        val starts = loopFrames.runningFold(0.0) { start, length -> start + length }
        assertEquals(starts.map { ceil(it - 1e-6).toLong() }, downbeats.take(starts.size))
        for (loop in loopFrames.indices) {
            val bpm = listOf(100.0, 100.0, 110.0, 110.0, 120.0, 120.0, 125.0)[loop]
            assertEquals("loop $loop", bpm, t.beatsPerMinuteAt(starts[loop] + 10), 1e-9)
        }
        assertEquals(125.0, t.beatsPerMinuteAt(1_999_999.0), 1e-9)
    }

    @Test
    fun stopSilencesAndHoldsThePosition() {
        val t = transport()
        t.start(PlaySettings(strokes(4.0, 1.0), beatsPerMinute = 120.0, countIn = false))
        val d = Driver(t, 240)
        d.runUntil(36_000)
        t.stop()
        val before = d.played.size
        d.runUntil(500_000)
        assertEquals(before, d.played.size)
        assertEquals(Transport.State.STOPPED, t.state)
        assertEquals(1.5, t.positionAt(400_000.0), 1e-12)
    }

    @Test
    fun startingAgainRestartsFromTheCountIn() {
        val t = transport()
        val settings = PlaySettings(strokes(4.0, 1.0), beatsPerMinute = 120.0)
        t.start(settings)
        val d = Driver(t, 240)
        d.runUntil(150_000)
        t.start(settings)
        d.runUntil(150_000 + 96_001)
        assertEquals(-4.0, t.positionAt(150_000.0), 0.0)
        assertEquals(Played(150_000, "accent", 1f, 0f), d.played.first { it.frame >= 150_000 })
        assertEquals(246_000L, d.ofSound("snare").last().frame)
    }

    @Test
    fun aRestartedStreamCarriesOnWhereItWas() {
        val t = transport()
        t.clickVolume = 0f
        t.start(PlaySettings(strokes(4.0, 0.25), beatsPerMinute = 120.0, countIn = false))
        val d = Driver(t, 240)
        d.runUntil(30_000) // Strokes 0 to 1.0 played; 1.25 is due at frame 30 000.
        val before = d.played.size
        d.frame = 0 // The engine restarted its stream.
        d.runUntil(30_000)
        val after = d.ofSound("snare").drop(before).map { it.frame }
        assertEquals(5, before)
        // The new stream's frame 0 carries on at 1.25.
        assertEquals(listOf(0L, 6_000L, 12_000L), after.take(3))
        assertEquals(2.5, t.positionAt(29_999.0), 1e-3)
    }

    @Test
    fun volumesScaleAndSilence() {
        val t = transport()
        t.instrumentVolume = 0.5f
        t.clickVolume = 0f
        t.start(PlaySettings(strokes(4.0, 1.0), beatsPerMinute = 120.0))
        val d = Driver(t, 240)
        d.runUntil(200_000)
        assertTrue(d.played.all { it.sound == "snare" && it.gain == 0.25f })
        t.instrumentVolume = 0f
        t.clickVolume = 0.3f
        val before = d.played.size
        d.runUntil(400_000)
        assertTrue(d.played.drop(before).all { it.sound != "snare" && it.gain == 0.3f })
    }

    @Test
    fun compoundMeterClicksOnDottedQuarters() {
        // 6/8 at 60 dotted quarters per minute: a click every second.
        val t = transport()
        val sixEight =
            Pattern(
                3.0,
                beatQuarters = 1.5,
                countInBeats = 2,
                events = emptyList(),
                clicks = Pattern.steadyClicks(3.0, 2, 1.5),
            )
        t.start(PlaySettings(sixEight, beatsPerMinute = 60.0))
        val d = Driver(t, 240)
        d.runUntil(4 * 48_000 + 1)
        assertEquals(
            listOf("accent" to 0L, "click" to 48_000L, "accent" to 96_000L, "click" to 144_000L, "accent" to 192_000L),
            d.played.map { it.sound to it.frame },
        )
        assertEquals(60.0, t.beatsPerMinuteAt(10.0), 1e-9)
    }

    private fun graces(vararg leads: Int): Pattern {
        val events = listOf(PatternEvent(1.0, snare)) + leads.map { PatternEvent(1.0, accent, leadLevel = it) }
        return Pattern(4.0, 1.0, 4, events, emptyList())
    }

    @Test
    fun graceStrokesSoundAFixedTimeBeforeTheirBeat() {
        // 120 BPM: the beat at position 1 is frame 24 000. A 30 ms gap is 1 440 frames.
        for (block in listOf(1, 96, 240, 1_024)) {
            val t = transport()
            t.graceGapMillis = 30.0
            t.start(PlaySettings(graces(1, 2), beatsPerMinute = 120.0, countIn = false))
            val d = Driver(t, block)
            d.runUntil(200_000)
            val frames = d.played.map { it.sound to it.frame }.sortedBy { it.second }
            assertEquals(
                "block $block",
                listOf("accent" to 21_120L, "accent" to 22_560L, "snare" to 24_000L),
                frames.take(3),
            )
            // The next loop's graces, exactly once each.
            assertEquals(
                listOf(117_120L, 118_560L),
                d
                    .ofSound("accent")
                    .map { it.frame }
                    .drop(2)
                    .take(2),
            )
        }
    }

    @Test
    fun theGraceGapIsATimeNotANoteValue() {
        for (bpm in listOf(60.0, 240.0)) {
            val t = transport()
            t.start(PlaySettings(graces(1), beatsPerMinute = bpm, countIn = false))
            val d = Driver(t, 240)
            d.runUntil(300_000)
            val gap = d.ofSound("snare").first().frame - d.ofSound("accent").first().frame
            assertEquals("at $bpm BPM", (Transport.DEFAULT_GRACE_GAP_MILLIS * 48).toLong(), gap)
        }
    }

    @Test
    fun changingTheGraceGapNeverRepeatsOrDropsAGrace() {
        val t = transport()
        t.start(PlaySettings(graces(1), beatsPerMinute = 240.0, countIn = false))
        val d = Driver(t, 240)
        var gap = 0.0
        while (d.frame < 2_000_000) {
            t.graceGapMillis = gap
            gap = (gap + 7) % 80 // Wobbles up and down every block.
            d.block()
        }
        // 240 BPM: a 4-beat loop every 48 000 frames; one grace per loop.
        val loops = d.ofSound("snare").size
        assertTrue(d.ofSound("accent").size in loops..loops + 1)
    }

    @Test
    fun durationsBecomeFrameCountsAtTheCurrentTempo() {
        val t = transport()
        val pattern = Pattern(4.0, 1.0, 4, listOf(PatternEvent(0.0, snare, durationQuarters = 1.5)), emptyList())
        t.start(PlaySettings(pattern, beatsPerMinute = 120.0, countIn = false))
        val d = Driver(t, 240)
        d.runUntil(100_000)
        // 1.5 quarters at 120 BPM, on every loop (one every 96 000 frames).
        assertEquals(listOf(36_000, 36_000), d.played.map { it.frames })
    }

    @Test
    fun renderingEventsDoesNotAllocate() {
        val allocated = threadAllocatedBytes() ?: return // Not measurable on this JVM.
        val t = transport()
        // 32nd notes at 300 BPM, with the trainer and slider moving the tempo.
        // With flams (a grace lead) and a buzz (a duration) in the mix too.
        val pattern =
            Pattern(
                4.0,
                beatQuarters = 1.0,
                countInBeats = 4,
                events =
                    List(32) { PatternEvent(it * 0.125, snare, pan = -0.2f) } +
                        List(4) { PatternEvent(it * 1.0, snare, leadLevel = 1) } +
                        PatternEvent(3.5, snare, durationQuarters = 0.5),
                clicks = Pattern.steadyClicks(4.0, 4, 1.0),
            )
        t.start(PlaySettings(pattern, beatsPerMinute = 300.0, trainer = SpeedTrainer(1.0, 1, 1_000.0)))
        var count = 0L
        val sink =
            object : EventSink {
                override fun play(
                    frame: Long,
                    sound: Sound,
                    gain: Float,
                    pan: Float,
                ) {
                    count++
                }

                override fun playFor(
                    frame: Long,
                    sound: Sound,
                    gain: Float,
                    pan: Float,
                    frames: Int,
                ) {
                    count++
                }
            }
        var frame = 0L

        fun blocks(n: Int) {
            repeat(n) {
                if (it % 50 == 0) t.setTempo(250.0 + it % 100)
                t.events(frame, frame + 240, sink)
                frame += 240
            }
        }
        blocks(5_000) // Warm up the JIT.
        val before = allocated()
        blocks(20_000)
        val bytes = allocated() - before
        assertTrue("only $count events", count > 5_000)
        assertTrue("$bytes bytes allocated while scheduling", bytes < ALLOCATION_NOISE_BYTES)
    }
}
