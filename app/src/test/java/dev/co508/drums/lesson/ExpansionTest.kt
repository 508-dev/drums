package dev.co508.drums.lesson

import dev.co508.drums.lesson.Hand.LEFT
import dev.co508.drums.lesson.Hand.RIGHT
import dev.co508.drums.lesson.StrokeDynamic.ACCENT
import dev.co508.drums.lesson.StrokeDynamic.GHOST
import dev.co508.drums.lesson.StrokeDynamic.GRACE
import dev.co508.drums.lesson.StrokeDynamic.NORMAL
import dev.co508.drums.musicxml.ImportResult
import dev.co508.drums.musicxml.fixture
import dev.co508.drums.musicxml.testImporter
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpansionTest {
    private fun f(
        n: Long,
        d: Long = 1,
    ) = Fraction.of(n, d)

    private fun measure(
        vararg notes: Note,
        repeatStart: Boolean = false,
        repeatTimes: Int? = null,
        number: String = "1",
    ) = Measure(
        number,
        Fraction.ZERO,
        f(4),
        TimeSignature.FOUR_FOUR,
        notes.toList(),
        repeatStart = repeatStart,
        repeatTimes = repeatTimes,
    )

    private fun quarter(
        beat: Int,
        hand: Hand = RIGHT,
    ) = Note(offset = f(beat.toLong()), duration = f(1), hand = hand, value = NoteValue.QUARTER)

    private fun notation(vararg measures: Measure) = Notation("T", "Snare", measures.toList())

    private fun museScore(name: String) =
        (testImporter().read(fixture(name), strict = true) as ImportResult.Success).notation

    @Test
    fun repeatsAreUnrolled() {
        fun order(vararg m: Measure) = Expansion.unrolledOrder(m.toList())
        val plain = measure()
        // |: A B :|  C  → A B A B C
        assertEquals(listOf(0, 1, 0, 1, 2), order(measure(repeatStart = true), measure(repeatTimes = 2), plain))
        // A :| without a forward repeat goes back to the start.
        assertEquals(listOf(0, 1, 0, 1), order(plain, measure(repeatTimes = 2)))
        // Two sections, the second played three times: A :| B C :|x3
        assertEquals(listOf(0, 0, 1, 2, 1, 2, 1, 2), order(measure(repeatTimes = 2), plain, measure(repeatTimes = 3)))
        // A broken count is capped.
        assertEquals(Expansion.MAX_REPEAT_TIMES, order(measure(repeatTimes = 1_000)).size)
    }

    @Test
    fun unrolledBarsFollowEachOther() {
        val timeline = Expansion.expand(notation(measure(quarter(0), repeatStart = true, repeatTimes = 3)))
        assertEquals(listOf(f(0), f(4), f(8)), timeline.bars.map { it.start })
        assertEquals(listOf(f(0), f(4), f(8)), timeline.strokes.map { it.position })
        assertEquals(f(12), timeline.length)
        assertEquals(StrokeSource(0, "1", 0), timeline.strokes.last().source)
    }

    @Test
    fun accentsGhostsAndGraceNotesBecomeDynamics() {
        val flam = quarter(0).copy(accent = Accent.ACCENT, graceNotes = listOf(GraceNote(LEFT, true, NoteValue.EIGHTH)))
        val ghost = quarter(1, LEFT).copy(ghost = true)
        val marcato = quarter(2).copy(accent = Accent.MARCATO)
        val strokes = Expansion.expand(notation(measure(flam, ghost, marcato))).strokes
        assertEquals(listOf(GRACE, ACCENT, GHOST, ACCENT), strokes.map { it.dynamic })
        assertEquals(listOf(1, 0, 0, 0), strokes.map { it.graceLead })
        assertEquals(listOf(f(0), f(0), f(1), f(2)), strokes.map { it.position })
    }

    @Test
    fun dragGraceStrokesLeadInPlayingOrder() {
        val drag =
            quarter(
                0,
            ).copy(
                graceNotes =
                    listOf(
                        GraceNote(LEFT, false, NoteValue.SIXTEENTH),
                        GraceNote(RIGHT, false, NoteValue.SIXTEENTH),
                    ),
            )
        val strokes = Expansion.expand(notation(measure(drag))).strokes
        assertEquals(listOf(2, 1, 0), strokes.map { it.graceLead })
        assertEquals(listOf(LEFT, RIGHT, RIGHT), strokes.map { it.hand })
    }

    @Test
    fun tremoloSlashesBecomeDiddlesOfTheNotesHand() {
        val eighth = Note(f(0), f(1, 2), LEFT, NoteValue.EIGHTH, ornament = Ornament.Tremolo(1), accent = Accent.ACCENT)
        val strokes = Expansion.expand(notation(measure(eighth))).strokes
        assertEquals(listOf(f(0), f(1, 4)), strokes.map { it.position })
        assertEquals(listOf(LEFT, LEFT), strokes.map { it.hand })
        assertEquals(listOf(ACCENT, NORMAL), strokes.map { it.dynamic })
    }

    @Test
    fun diddleCountsFollowBeamsDotsAndTuplets() {
        fun count(
            value: NoteValue,
            slashes: Int,
            dots: Int = 0,
        ) = Expansion.diddleCount(Note(f(0), f(1), RIGHT, value, dots = dots), slashes)
        assertEquals(2, count(NoteValue.EIGHTH, 1)) // Two 16ths.
        assertEquals(4, count(NoteValue.QUARTER, 2)) // Four 16ths.
        assertEquals(6, count(NoteValue.QUARTER, 2, dots = 1)) // A dotted quarter: six 16ths.
        assertEquals(4, count(NoteValue.HALF, 1)) // Four 8ths.
        // A triplet eighth with a slash: two strokes, each half the triplet.
        val triplet =
            Note(f(0), f(1, 3), RIGHT, NoteValue.EIGHTH, ornament = Ornament.Tremolo(1), tuplet = Tuplet(3, 2))
        assertEquals(listOf(f(0), f(1, 6)), Expansion.expand(notation(measure(triplet))).strokes.map { it.position })
    }

    @Test
    fun aTiedNoteIsntStruckAgainButATiedBuzzGoesOn() {
        val held = listOf(quarter(0).copy(tieStart = true), quarter(1).copy(tieStop = true), quarter(2, LEFT))
        assertEquals(
            listOf(f(0), f(2)),
            Expansion.expand(notation(measure(*held.toTypedArray()))).strokes.map { it.position },
        )

        val buzz = quarter(0).copy(ornament = Ornament.Buzz, tieStart = true)
        val more = quarter(0).copy(ornament = Ornament.Buzz, tieStop = true, hand = null)
        val strokes =
            Expansion
                .expand(
                    notation(
                        measure(quarter(0), quarter(1), quarter(2), buzz.copy(offset = f(3))),
                        measure(more, number = "2"),
                    ),
                ).strokes
        assertEquals(f(2), strokes.last().buzzLength)
        assertEquals(4, strokes.size)
    }

    @Test
    fun leadWithLeftSwapsEveryHand() {
        val flam = quarter(0).copy(graceNotes = listOf(GraceNote(LEFT, true, NoteValue.EIGHTH)))
        val strokes = Expansion.expand(notation(measure(flam, quarter(1, LEFT))), leadWithLeft = true).strokes
        assertEquals(listOf(RIGHT, LEFT, RIGHT), strokes.map { it.hand })
    }

    @Test
    fun theMuseScoreFixtureExpands() {
        val timeline = Expansion.expand(museScore("sticking-and-ornaments.musicxml"))
        // Bar 1, then bar 2 twice (its repeat).
        assertEquals(listOf(f(0), f(4), f(8)), timeline.bars.map { it.start })
        val bar2 = timeline.strokes.filter { it.source.measureIndex == 1 && it.position < f(8) }
        // Two diddled eighths, a triplet, then the buzz tied over two quarters.
        assertEquals(
            listOf(f(4), f(17, 4), f(9, 2), f(19, 4), f(5), f(16, 3), f(17, 3), f(6)),
            bar2.map { it.position },
        )
        assertEquals(f(2), bar2.last().buzzLength)
        val bar1 = timeline.strokes.filter { it.source.measureIndex == 0 }
        assertEquals(listOf(0, 0, 1, 0, 2, 1, 0), bar1.map { it.graceLead })
    }

    @Test
    fun compoundTimeKeepsItsBars() {
        val timeline = Expansion.expand(museScore("compound-and-repeats.musicxml"))
        assertEquals(TimeSignature(6, 8), timeline.timeSignature)
        // Bar 1, then bar 2 three times.
        assertEquals(listOf(0, 1, 1, 1), timeline.bars.map { it.source })
        // The dotted-quarter diddle: six 16ths.
        assertEquals(6, timeline.strokes.count { it.source.measureIndex == 0 && it.source.noteIndex == 3 })
    }
}
