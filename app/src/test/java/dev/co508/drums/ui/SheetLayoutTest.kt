package dev.co508.drums.ui

import dev.co508.drums.lesson.DirectoryPackSource
import dev.co508.drums.lesson.Expansion
import dev.co508.drums.lesson.Notation
import dev.co508.drums.lesson.PackLoader
import dev.co508.drums.lesson.PackResult
import dev.co508.drums.musicxml.ImportResult
import dev.co508.drums.musicxml.fixture
import dev.co508.drums.musicxml.testImporter
import dev.co508.drums.ui.sheet.SheetLayout
import dev.co508.drums.ui.sheet.SheetPosition
import dev.co508.drums.ui.sheet.sheetPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SheetLayoutTest {
    private val pack =
        (
            PackLoader(
                testImporter(),
            ).load(
                DirectoryPackSource(File("src/main/assets/${PackLoader.BUILT_IN_FOLDER}")),
                strict = true,
            ) as PackResult.Success
        ).pack

    private fun lesson(id: String) = pack.lessons.single { it.id == id }.notation

    private fun museScore(name: String) =
        (testImporter().read(fixture(name), strict = true) as ImportResult.Success).notation

    private fun measure(
        notation: Notation,
        width: Float = 60f,
    ) = SheetLayout
        .layout(notation, width)
        .systems
        .single()
        .measures
        .single()

    @Test
    fun beamsGroupByBeat() {
        assertEquals(listOf(4, 4, 4, 4), measure(lesson("single-stroke-roll")).beams.map { it.notes.size })
        assertEquals(listOf(2, 2, 2, 2), measure(lesson("double-stroke-open-roll")).beams.map { it.notes.size })
        assertEquals(listOf(3, 3, 3, 3), measure(lesson("flam-accent")).beams.map { it.notes.size })
        // Quarter notes have no beams or flags.
        assertTrue(measure(lesson("flam")).beams.isEmpty())
        assertTrue(measure(lesson("drag")).notes.none { it.beamed || it.beams > 0 })
    }

    @Test
    fun beamedTupletsShowANumberWithoutABracket() {
        val tuplets = measure(lesson("flam-accent")).tuplets
        assertEquals(4, tuplets.size)
        assertTrue(tuplets.all { it.number == 3 && !it.bracket && it.notes.size == 3 })
    }

    @Test
    fun stickingTextIncludesGraceNotes() {
        assertEquals(listOf("lR", "rL", "lR", "rL"), measure(lesson("flam")).notes.map { it.sticking })
        assertEquals(listOf("llR", "rrL", "llR", "rrL"), measure(lesson("drag")).notes.map { it.sticking })
    }

    @Test
    fun spacingIsProportionalToTime() {
        val m = measure(lesson("single-paradiddle"))
        val gaps = m.notes.zipWithNext { a, b -> b.x - a.x }
        assertTrue(gaps.all { kotlin.math.abs(it - m.spacesPerQuarter / 4) < 1e-3f })
        assertEquals(m.contentX, m.notes.first().x, 1e-4f)
        assertEquals(m.notes[4].x, m.xAt(1.0), 1e-4f)
    }

    @Test
    fun aLineIsStretchedToTheWidth() {
        val layout = SheetLayout.layout(lesson("flam"), 50f)
        val m =
            layout.systems
                .single()
                .measures
                .single()
        assertEquals(50f, m.x + m.width, 1e-3f)
    }

    @Test
    fun barsPerLineFollowTheWidth() {
        val twoBars = museScore("sticking-and-ornaments.musicxml")
        assertEquals(listOf(2), SheetLayout.layout(twoBars, 200f).systems.map { it.measures.size })
        assertEquals(listOf(1, 1), SheetLayout.layout(twoBars, 50f).systems.map { it.measures.size })
        // The time signature shows only at the start and when it changes.
        assertEquals(
            listOf(true, false),
            SheetLayout.layout(twoBars, 200f).systems.single().measures.map {
                it.showTimeSignature
            },
        )
    }

    @Test
    fun denseMusicNeedsMoreRoom() {
        assertTrue(
            SheetLayout.spacesPerQuarterNeeded(lesson("single-stroke-roll")) >
                SheetLayout.spacesPerQuarterNeeded(lesson("flam")),
        )
        assertEquals(
            SheetLayout.MIN_NOTE_GAP / 0.25f,
            SheetLayout.spacesPerQuarterNeeded(lesson("single-stroke-roll")),
            1e-4f,
        )
    }

    @Test
    fun tiesTupletsRestsAndDotsFromTheFixtures() {
        val ornaments = SheetLayout.layout(museScore("sticking-and-ornaments.musicxml"), 200f)
        assertEquals(1, ornaments.ties.size)
        val bar2 = ornaments.systems.single().measures[1]
        assertTrue(bar2.tuplets.single().let { it.number == 3 && !it.bracket })
        val compound =
            SheetLayout
                .layout(museScore("compound-and-repeats.musicxml"), 200f)
                .systems
                .single()
                .measures
        assertEquals(1, compound[1].rests.size)
        assertEquals(
            1,
            compound[0]
                .notes
                .last()
                .note.dots,
        )
        // 6/8: beamed in dotted-quarter beats.
        assertEquals(listOf(3), compound[0].beams.map { it.notes.size })
    }

    @Test
    fun thePlayheadMapsPlaybackPositionToTheWrittenMeasure() {
        // A one-bar lesson repeated: the timeline is two passes (8 quarters) and loops.
        val timeline = Expansion.expand(lesson("single-stroke-roll"))
        assertNull(sheetPosition(timeline, -1.0)) // Count-in.
        assertEquals(SheetPosition(0, 1.5), sheetPosition(timeline, 1.5))
        assertEquals(SheetPosition(0, 0.5), sheetPosition(timeline, 4.5)) // Second pass.
        assertEquals(SheetPosition(0, 1.0), sheetPosition(timeline, 9.0)) // Next loop.
        // Two written bars with a repeat on the second: bar 2 plays twice.
        val two = Expansion.expand(museScore("sticking-and-ornaments.musicxml"))
        assertEquals(SheetPosition(1, 1.0), sheetPosition(two, 9.0))
    }
}
