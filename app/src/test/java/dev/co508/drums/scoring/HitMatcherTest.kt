package dev.co508.drums.scoring

import dev.co508.drums.lesson.Hand
import dev.co508.drums.scoring.NoteMark.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HitMatcherTest {
    private val rate = 48_000

    /** A steady tempo from frame 0, position 0 there: [bpm] quarter notes a minute. */
    private class Steady(
        bpm: Double,
    ) : TimeMap {
        val perQuarter = 60.0 / bpm * 48_000

        override fun frameAt(position: Double) = position * perQuarter

        override fun positionAt(frame: Double) = frame / perQuarter
    }

    private val ms = 48.0
    private val graceGap = 12 * ms

    private fun lesson(
        length: Double,
        vararg strokes: ScoredStroke,
    ) = ScoredLesson(length, strokes.mapIndexed { i, s -> s.copy(index = i) })

    private fun stroke(
        position: Double,
        hand: Hand? = Hand.RIGHT,
        graceLead: Int = 0,
        buzz: Double = 0.0,
    ) = ScoredStroke(0, position, graceLead, hand, buzz)

    /** Quarter notes R L R L in one 4/4 bar. */
    private val quarters = lesson(4.0, stroke(0.0), stroke(1.0, Hand.LEFT), stroke(2.0), stroke(3.0, Hand.LEFT))

    private fun run(
        matcher: HitMatcher,
        map: TimeMap,
        hits: List<Double>,
        until: Double,
    ): ScoringSnapshot {
        hits.forEach(matcher::addHit)
        matcher.advance(until, map, graceGap)
        return matcher.snapshot()
    }

    @Test
    fun `hits are graded by how far off they were`() {
        val map = Steady(120.0) // 24 000 frames a quarter
        val matcher = HitMatcher(quarters, rate, Strictness.STANDARD)
        val snapshot =
            run(
                matcher,
                map,
                listOf(0 + 5 * ms, 24_000 - 20 * ms, 48_000 + 50 * ms, 72_000.0 + 2),
                until =
                    96_000.0 - 100 * ms,
            )
        val marks = (0..3).map { snapshot.markFor(it, 0)!! }
        assertEquals(listOf(Grade.GREEN, Grade.YELLOW, Grade.RED, Grade.GREEN), marks.map { it.grade })
        assertEquals(5.0, marks[0].offsetMillis, 1e-9)
        assertEquals(-20.0, marks[1].offsetMillis, 1e-9)
        // 50 ms late at 120 BPM is a tenth of a quarter note.
        assertEquals(0.1, marks[2].offsetQuarters, 1e-9)
        val stats = snapshot.stats
        assertEquals(4, stats.hits)
        assertEquals(35.0 + 2.0 / ms, stats.sumOffsetMillis, 1e-6)
        assertEquals(2, stats.perHand.getValue(Hand.LEFT).hits)
    }

    @Test
    fun `missed notes and extra hits`() {
        val map = Steady(120.0)
        val matcher = HitMatcher(quarters, rate, Strictness.STANDARD)
        // Beat 2 not played; one hit half-way between beats 3 and 4.
        val snapshot = run(matcher, map, listOf(0.0, 48_000.0, 60_000.0, 72_000.0), until = 95_000.0)
        assertEquals(Kind.MISS, snapshot.markFor(1, 0)!!.kind)
        assertEquals(1, snapshot.stats.misses)
        assertEquals(1, snapshot.stats.extras)
        assertEquals(2.5, snapshot.extras.single().position, 1e-9)
        assertEquals(
            1,
            snapshot.stats.perHand
                .getValue(Hand.LEFT)
                .misses,
        )
    }

    @Test
    fun `notes wait until their window has passed the settled frame`() {
        val map = Steady(120.0)
        val matcher = HitMatcher(quarters, rate, Strictness.STANDARD)
        // Beat 1's late window ends 80 ms after it: not decided at 70 ms.
        assertNull(run(matcher, map, emptyList(), until = 70 * ms).markFor(0, 0))
        matcher.addHit(60 * ms) // A late hit arriving late still counts.
        val mark = run(matcher, map, emptyList(), until = 90 * ms).markFor(0, 0)!!
        assertEquals(Grade.RED, mark.grade)
    }

    @Test
    fun `windows narrow at fast tempos so neighbours never share a hit`() {
        // 32nd notes at 300 BPM: 25 ms apart, so each side of a window is 10 ms.
        val dense = lesson(1.0, *Array(8) { stroke(it / 8.0) })
        val map = Steady(300.0)
        val matcher = HitMatcher(dense, rate, Strictness.STANDARD)
        val step = 25 * ms
        // Note 2 hit 12 ms late: outside its 10 ms window, and outside note 3's too.
        val hits = (0 until 8).map { it * step + if (it == 2) 12 * ms else 0.0 }
        val snapshot = run(matcher, map, hits, until = 8 * step)
        assertEquals(Kind.MISS, snapshot.markFor(2, 0)!!.kind)
        assertEquals(Kind.HIT, snapshot.markFor(3, 0)!!.kind)
        assertEquals(0.0, snapshot.markFor(3, 0)!!.offsetMillis, 1e-9)
        assertEquals(1, snapshot.stats.extras)
        assertEquals(NoteWindows(Strictness.STANDARD, 10.0, 10.0), NoteWindows.of(Strictness.STANDARD, 25.0, 25.0))
        // Colour bands are capped too: 9 ms off is green at most-10 ms windows only if within green (15).
        assertEquals(Grade.GREEN, NoteWindows.of(Strictness.STANDARD, 25.0, 25.0).grade(9.0))
    }

    @Test
    fun `strictness changes the bands`() {
        val wide = NoteWindows.of(Strictness.RELAXED, 1_000.0, 1_000.0)
        val tight = NoteWindows.of(Strictness.STRICT, 1_000.0, 1_000.0)
        assertEquals(Grade.GREEN, wide.grade(20.0))
        assertEquals(Grade.YELLOW, tight.grade(20.0))
        assertEquals(Grade.RED, tight.grade(-40.0))
        assertEquals(Strictness.STANDARD, Strictness.of("nonsense"))
    }

    private val flams =
        lesson(
            2.0,
            stroke(0.0, Hand.RIGHT),
            stroke(0.0, Hand.LEFT, graceLead = 1),
            stroke(1.0, Hand.LEFT),
            stroke(1.0, Hand.RIGHT, graceLead = 1),
        )

    @Test
    fun `a flam heard as two onsets scores both strokes`() {
        val map = Steady(120.0)
        val matcher = HitMatcher(flams, rate, Strictness.STANDARD)
        val snapshot =
            run(
                matcher,
                map,
                listOf(-15 * ms, 2 * ms, 24_000 - 10 * ms, 24_000 + 3 * ms),
                until =
                    48_000.0 - 100 * ms,
            )
        assertEquals(2.0, snapshot.markFor(0, 0)!!.offsetMillis, 1e-9)
        // The grace sounds 12 ms early; it was played 15 ms early: 3 ms early of its own time.
        assertEquals(-3.0, snapshot.markFor(1, 0)!!.offsetMillis, 1e-9)
        assertEquals(2, snapshot.stats.gracesHeard)
        assertEquals(0, snapshot.stats.extras)
        assertEquals(2, snapshot.stats.hits)
    }

    @Test
    fun `a flam merged into one onset scores the main stroke, its grace optional`() {
        val map = Steady(120.0)
        val matcher = HitMatcher(flams, rate, Strictness.STANDARD)
        val snapshot = run(matcher, map, listOf(-4 * ms, 24_000.0), until = 48_000.0 - 100 * ms)
        assertEquals(Kind.HIT, snapshot.markFor(0, 0)!!.kind)
        assertEquals(-4.0, snapshot.markFor(0, 0)!!.offsetMillis, 1e-9)
        assertEquals(Kind.GRACE_UNHEARD, snapshot.markFor(1, 0)!!.kind)
        assertEquals(0, snapshot.stats.misses)
        assertEquals(2, snapshot.stats.gracesUnheard)
    }

    @Test
    fun `a drag's two graces take the two onsets before its main stroke`() {
        val drag =
            lesson(1.0, stroke(0.0), stroke(0.0, Hand.LEFT, graceLead = 2), stroke(0.0, Hand.LEFT, graceLead = 1))
        val map = Steady(60.0)
        val matcher = HitMatcher(drag, rate, Strictness.STANDARD)
        val snapshot = run(matcher, map, listOf(-26 * ms, -13 * ms, 1 * ms), until = 200 * ms)
        assertEquals(1.0, snapshot.markFor(0, 0)!!.offsetMillis, 1e-9)
        assertEquals(-2.0, snapshot.markFor(1, 0)!!.offsetMillis, 1e-9) // Lead 2: expected −24 ms.
        assertEquals(-1.0, snapshot.markFor(2, 0)!!.offsetMillis, 1e-9) // Lead 1: expected −12 ms.
    }

    @Test
    fun `a buzz roll absorbs its own bounces`() {
        val roll = lesson(2.0, stroke(0.0, buzz = 1.0), stroke(1.0, Hand.LEFT))
        val map = Steady(120.0)
        val matcher = HitMatcher(roll, rate, Strictness.STANDARD)
        val bounces = (0 until 20).map { it * 1_100.0 } // Every 23 ms through the roll.
        val snapshot = run(matcher, map, bounces + 24_000.0, until = 48_000.0 - 100 * ms)
        assertEquals(Kind.HIT, snapshot.markFor(0, 0)!!.kind)
        assertEquals(Kind.HIT, snapshot.markFor(1, 0)!!.kind)
        assertEquals(0, snapshot.stats.extras)
    }

    @Test
    fun `count-in hits are ignored and loops repeat`() {
        // Position 0 at frame 96 000, after a one-bar count-in.
        val map =
            object : TimeMap {
                override fun frameAt(position: Double) = 96_000 + position * 24_000

                override fun positionAt(frame: Double) = (frame - 96_000) / 24_000
            }
        val matcher = HitMatcher(quarters, rate, Strictness.STANDARD)
        val hits = listOf(10_000.0, 40_000.0) + (0 until 8).map { 96_000 + it * 24_000.0 }
        val snapshot = run(matcher, map, hits, until = 96_000 + 8 * 24_000.0 - 100 * ms)
        assertEquals(0, snapshot.stats.extras)
        assertEquals(8, snapshot.stats.hits)
        assertEquals(1L, snapshot.markFor(0, 1)!!.loop)
        // Loop 2 shows loop 1's mark, one loop old; loop 4 shows nothing.
        assertEquals(1L, snapshot.markFor(0, 2)!!.loop)
        assertNull(snapshot.markFor(0, 4))
        assertEquals(0.35f, ScoringSnapshot.fade(1), 0f)
    }

    @Test
    fun `a lesson played once ends after its last note`() {
        val map = Steady(120.0)
        val matcher = HitMatcher(quarters, rate, Strictness.STANDARD, loops = false)
        matcher.finish(10 * 24_000.0, 10 * 24_000.0, map, graceGap)
        val snapshot = matcher.snapshot()
        assertEquals(4, snapshot.stats.misses)
        assertTrue(snapshot.marks.all { it.size <= 1 })
    }

    @Test
    fun `stopping decides what was played and drops the rest`() {
        val map = Steady(120.0)
        val matcher = HitMatcher(quarters, rate, Strictness.STANDARD)
        // Two notes, an extra, then the stop at 36 000; a ringing tail heard after it doesn't count.
        listOf(0.0, 24_000.0, 30_000.0, 40_000.0).forEach(matcher::addHit)
        matcher.finish(36_000.0, 60_000.0, map, graceGap)
        val snapshot = matcher.snapshot()
        assertEquals(2, snapshot.stats.hits)
        assertEquals(1, snapshot.stats.extras)
        assertNull(snapshot.markFor(2, 0))
    }
}
