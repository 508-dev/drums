package dev.co508.drums.musicxml

import dev.co508.drums.lesson.Accent
import dev.co508.drums.lesson.Fraction
import dev.co508.drums.lesson.GraceNote
import dev.co508.drums.lesson.Hand.LEFT
import dev.co508.drums.lesson.Hand.RIGHT
import dev.co508.drums.lesson.Notation
import dev.co508.drums.lesson.NoteValue
import dev.co508.drums.lesson.Ornament
import dev.co508.drums.lesson.TimeSignature
import dev.co508.drums.lesson.Tuplet
import dev.co508.drums.musicxml.LessonXml.grace
import dev.co508.drums.musicxml.LessonXml.note
import dev.co508.drums.musicxml.LessonXml.rest
import dev.co508.drums.musicxml.LessonXml.rlrl
import dev.co508.drums.musicxml.LessonXml.score
import dev.co508.drums.musicxml.LessonXml.words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicXmlImporterTest {
    private fun f(
        n: Long,
        d: Long = 1,
    ) = Fraction.of(n, d)

    private fun import(
        xml: String,
        strict: Boolean = true,
    ) = testImporter().read(xml.byteInputStream(), strict)

    private fun notation(
        xml: String,
        strict: Boolean = true,
    ): Notation {
        val result = import(xml, strict)
        assertTrue(result.toString(), result is ImportResult.Success)
        return (result as ImportResult.Success).notation
    }

    private fun errors(
        xml: String,
        strict: Boolean = true,
    ): List<String> {
        val result = import(xml, strict)
        assertTrue(result.toString(), result is ImportResult.Failure)
        return (result as ImportResult.Failure).errors.map { it.toString() }
    }

    private fun warnings(xml: String) =
        (import(xml, strict = false) as ImportResult.Success).warnings.map { it.toString() }

    // ---- MuseScore 4.7.5 fixtures ----

    @Test
    fun museScoreFixtureImportsStrictly() {
        val result = testImporter().read(fixture("sticking-and-ornaments.musicxml"), strict = true)
        assertTrue(result.toString(), result is ImportResult.Success)
        val n = (result as ImportResult.Success).notation
        assertEquals(emptyList<ImportProblem>(), result.warnings)
        assertEquals("Features", n.title)
        assertEquals(100.0, n.tempo!!.quarterNotesPerMinute, 0.0)
        val (m1, m2) = n.measures
        assertEquals(TimeSignature(4, 4), m1.timeSignature)
        assertEquals(f(4), m1.length)
        assertEquals(f(4), m2.start)

        val (accented, ghost, flam, drag) = m1.notes
        assertEquals(RIGHT, accented.hand)
        assertEquals(Accent.ACCENT, accented.accent)
        assertEquals(LEFT, ghost.hand)
        assertTrue(ghost.ghost)
        assertEquals(f(2), flam.offset)
        assertEquals(RIGHT, flam.hand)
        assertEquals(listOf(GraceNote(LEFT, slashed = true, value = NoteValue.EIGHTH)), flam.graceNotes)
        assertEquals(RIGHT, drag.hand)
        assertEquals(listOf(LEFT, LEFT), drag.graceNotes.map { it.hand })
        assertEquals(listOf(false, false), drag.graceNotes.map { it.slashed })

        assertTrue(m2.repeatStart)
        assertEquals(2, m2.repeatTimes)
        val notes = m2.notes
        assertEquals(listOf(RIGHT, LEFT, RIGHT, LEFT, RIGHT, RIGHT, RIGHT), notes.map { it.hand })
        assertEquals(listOf(Ornament.Tremolo(1), Ornament.Tremolo(1)), notes.take(2).map { it.ornament })
        assertEquals(listOf(f(0), f(1, 2), f(1), f(4, 3), f(5, 3), f(2), f(3)), notes.map { it.offset })
        assertEquals(Tuplet(3, 2, starts = true), notes[2].tuplet)
        assertEquals(Tuplet(3, 2, stops = true), notes[4].tuplet)
        assertEquals(f(1, 3), notes[3].duration)
        // The tied buzz roll: both notes carry it; the continuation keeps the hand.
        assertEquals(listOf(Ornament.Buzz, Ornament.Buzz), notes.takeLast(2).map { it.ornament })
        assertTrue(notes[5].tieStart && notes[6].tieStop)
    }

    @Test
    fun museScoreStackedGraceStickingIsAmbiguous() {
        val result = testImporter().read(fixture("grace-sticking-stacked.musicxml"), strict = false)
        val errors = (result as ImportResult.Failure).errors.map { it.toString() }
        assertEquals(1, errors.size)
        assertTrue(
            errors.single(),
            errors.single().startsWith("Measure 1, beat 3: sticking \"L\" + \"R\" is ambiguous"),
        )
    }

    @Test
    fun museScoreCompoundFixture() {
        val result = testImporter().read(fixture("compound-and-repeats.musicxml"), strict = true)
        val n = (result as ImportResult.Success).notation
        val m1 = n.measures[0]
        assertEquals(TimeSignature(6, 8), m1.timeSignature)
        assertTrue(m1.timeSignature.isCompound)
        assertEquals(f(3, 2), m1.timeSignature.beatQuarters)
        assertEquals(90.0, n.tempo!!.quarterNotesPerMinute, 0.0)
        assertEquals(listOf("p", "f"), m1.dynamics.map { it.level })
        assertEquals(Accent.MARCATO, m1.notes[0].accent)
        assertEquals(Ornament.Tremolo(2), m1.notes.last().ornament)
        assertEquals(1, m1.notes.last().dots)
        assertEquals(f(3, 2), m1.notes.last().duration)
        assertEquals(3, n.measures[1].repeatTimes)
        assertEquals(1, n.measures[1].rests.size)
    }

    // ---- Sticking by position ----

    @Test
    fun stickingIsMatchedByPositionNotDocumentOrder() {
        // Directions written after their notes, using <offset> to point back, and out of order.
        val xml =
            score(
                note() + note() + note() + note() +
                    """<direction><direction-type><words>L</words></direction-type><offset>-4</offset></direction>""" +
                    """<direction><direction-type><words>R</words></direction-type><offset>-16</offset></direction>""" +
                    """<direction><direction-type><words>L</words></direction-type><offset>-12</offset></direction>""" +
                    """<direction><direction-type><words>R</words></direction-type><offset>-8</offset></direction>""",
            )
        assertEquals(listOf(RIGHT, LEFT, RIGHT, LEFT), notation(xml).notes.map { it.hand })
    }

    @Test
    fun otherWordsAreNotSticking() {
        val xml = score(words("rit.") + rlrl)
        assertEquals(listOf(RIGHT, LEFT, RIGHT, LEFT), notation(xml).notes.map { it.hand })
    }

    @Test
    fun stickingFromLyricsAndFingering() {
        val lyric = """<lyric><text>R</text></lyric>"""
        val xml =
            score(
                note(extra = "").replace("</type>", "</type>$lyric") +
                    note(notations = "<technical><fingering>L</fingering></technical>") +
                    words("R") + note() + words("L") + note(),
            )
        assertEquals(listOf(RIGHT, LEFT, RIGHT, LEFT), notation(xml).notes.map { it.hand })
    }

    @Test
    fun missingStickingIsAnErrorForBuiltInLessonsNamingMeasureAndBeat() {
        val xml =
            score(
                rlrl,
                words(
                    "R",
                ) + note() + note(duration = 2, type = "eighth") + words("R") + note(duration = 2, type = "eighth") +
                    note(duration = 4),
            )
        assertEquals(
            listOf("Measure 2, beat 2: no sticking for this note.", "Measure 2, beat 3: no sticking for this note."),
            errors(xml),
        )
        // Imports keep going: the notes just have no hand.
        assertEquals(listOf(RIGHT, null, RIGHT, null), notation(xml, strict = false).measures[1].notes.map { it.hand })
        assertEquals(2, warnings(xml).size)
    }

    @Test
    fun beatsBetweenBeatsAndInCompoundTimeAreNamed() {
        val sixEight =
            score(
                words("R") + note(duration = 2, type = "eighth") + note(duration = 2, type = "eighth") +
                    words(
                        "R",
                    ) + note(duration = 2, type = "eighth") + words("L") + note(duration = 6, type = "quarter"),
                time = "<beats>6</beats><beat-type>8</beat-type>",
            )
        assertEquals(listOf("Measure 1, beat 1 + 1/3: no sticking for this note."), errors(sixEight))
    }

    @Test
    fun graceNoteStickingInTheStyleGuidesForm() {
        val xml = score(words("lR") + grace() + note() + words("rrL") + grace(false) + grace(false) + note() + rest(8))
        val (flam, drag) = notation(xml).notes
        assertEquals(listOf(LEFT), flam.graceNotes.map { it.hand })
        assertEquals(listOf(RIGHT, RIGHT), drag.graceNotes.map { it.hand })
        assertEquals(LEFT, drag.hand)
    }

    @Test
    fun aGraceNoteWithoutItsLetterIsAnError() {
        val xml = score(words("R") + grace() + note() + words("L") + note() + rest(8))
        assertEquals(
            listOf("Measure 1, beat 1: sticking \"R\" has 1 letters; expected 2 letters (1 grace notes and the note)."),
            errors(xml),
        )
    }

    @Test
    fun stackedButUnambiguousStickingIsOnlyAStyleProblem() {
        val xml = score(words("l") + words("R") + grace() + note() + words("L") + note() + rest(8))
        assertEquals(
            listOf(
                "Measure 1, beat 1: sticking \"l\" + \"R\" should be one word with lowercase grace notes, like \"lR\".",
            ),
            errors(xml),
        )
        assertEquals(
            LEFT,
            notation(xml, strict = false)
                .notes[0]
                .graceNotes
                .single()
                .hand,
        )
    }

    @Test
    fun stickingOnARestIsAStyleError() {
        val xml = score(words("R") + note() + words("L") + rest() + words("R") + note() + words("L") + note())
        assertEquals(listOf("Measure 1, beat 2: sticking \"L\" is on a rest."), errors(xml))
    }

    // ---- Structure ----

    @Test
    fun measuresAccumulateTheirStart() {
        val n = notation(score(rlrl, rlrl, rlrl))
        assertEquals(listOf(f(0), f(4), f(8)), n.measures.map { it.start })
        assertEquals(listOf("1", "2", "3"), n.measures.map { it.number })
        assertEquals(12, n.notes.size)
    }

    @Test
    fun divisionsAreConvertedExactly() {
        val n = notation(score(rlrl, divisions = 3))
        assertEquals(listOf(f(0), f(4, 3), f(8, 3), f(4)), n.notes.map { it.offset })
        assertNull(n.tempo)
    }

    @Test
    fun multiPartScoresAreNotSupportedYet() {
        assertEquals(
            listOf("The score has 2 parts; scores with more than one part aren't supported yet."),
            errors(score(rlrl, parts = 2)),
        )
        // But a part can be picked by id.
        val result = testImporter().read(score(rlrl, parts = 2).byteInputStream(), strict = true, partId = "P2")
        assertTrue(result is ImportResult.Success)
    }

    @Test
    fun unsupportedNotationIsReported() {
        val backup = score(rlrl + "<backup><duration>16</duration></backup>")
        assertEquals(
            listOf("Measure 1: Only one voice is supported; the measure goes back in time (<backup>)."),
            errors(backup),
        )
        val volta = score(rlrl + """<barline location="right"><ending number="1" type="stop"/></barline>""")
        assertEquals(listOf("Measure 1: Voltas (first and second endings) aren't supported yet."), errors(volta))
        val chord =
            score(
                words("R") + note() + note().replace("<unpitched>", "<chord/><unpitched>") + note() + note() + note(),
            )
        assertTrue(errors(chord).single().contains("Chords"))
        val twoNoteTremolo =
            score(
                words(
                    "R",
                ) + note(notations = """<ornaments><tremolo type="start">2</tremolo></ornaments>""") + rest(12),
            )
        assertEquals(listOf("Measure 1, beat 1: Two-note tremolos aren't supported."), errors(twoNoteTremolo))
    }

    @Test
    fun buzzRollsAcceptAnEmptyValue() {
        val xml =
            score(
                words("R") + note(notations = """<ornaments><tremolo type="unmeasured"/></ornaments>""") + rest(12),
            )
        assertEquals(Ornament.Buzz, notation(xml).notes.single().ornament)
    }

    @Test
    fun aNonSnarePartIsAStyleProblem() {
        val xml =
            score(
                rlrl,
            ).replace(
                "<midi-unpitched>39</midi-unpitched>",
                "<midi-unpitched>43</midi-unpitched>",
            ).replace("Acoustic Snare", "Tom")
        assertEquals(
            listOf("The part isn't marked as a snare (MIDI unpitched 39, or an instrument named \"snare\")."),
            errors(xml),
        )
        assertEquals(1, warnings(xml).size)
    }

    @Test
    fun notMusicXmlAtAll() {
        assertEquals(
            listOf("This isn't a MusicXML score (the root element is <html>)."),
            errors("<html><body/></html>"),
        )
        assertEquals(listOf("The part has no notes."), errors(score(rest(16))))
    }
}
