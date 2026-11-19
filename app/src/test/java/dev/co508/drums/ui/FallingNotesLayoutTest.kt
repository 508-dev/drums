package dev.co508.drums.ui

import dev.co508.drums.lesson.DirectoryPackSource
import dev.co508.drums.lesson.Expansion
import dev.co508.drums.lesson.PackLoader
import dev.co508.drums.lesson.PackResult
import dev.co508.drums.lesson.StrokeDynamic
import dev.co508.drums.musicxml.testImporter
import dev.co508.drums.ui.falling.FallingNotesGeometry
import dev.co508.drums.ui.falling.FallingNotesLayout
import dev.co508.drums.ui.falling.LIT_QUARTERS
import dev.co508.drums.ui.falling.Lane
import dev.co508.drums.ui.falling.graceOffset
import dev.co508.drums.ui.falling.litAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FallingNotesLayoutTest {
    private val pack =
        (
            PackLoader(
                testImporter(),
            ).load(
                DirectoryPackSource(File("src/main/assets/${PackLoader.BUILT_IN_FOLDER}")),
                strict = true,
            ) as PackResult.Success
        ).pack

    private fun layout(id: String) =
        FallingNotesLayout.from(Expansion.expand(pack.lessons.single { it.id == id }.notation))

    @Test
    fun handsBecomeLanes() {
        val paradiddle = layout("single-paradiddle")
        // Both passes of the repeated bar, as the transport plays them.
        val hands = paradiddle.notes.joinToString("") { if (it.lane == Lane.RIGHT) "R" else "L" }
        assertEquals("RLRRLRLL".repeat(4), hands)
        assertFalse(paradiddle.hasMiddleLane)
        assertEquals(StrokeDynamic.ACCENT, paradiddle.notes.first().dynamic)
    }

    @Test
    fun graceNotesFallInTheOtherHandsLane() {
        val flam = layout("flam").notes.take(2)
        assertEquals(listOf(Lane.LEFT, Lane.RIGHT), flam.map { it.lane })
        assertEquals(listOf(1, 0), flam.map { it.graceLead })
        assertEquals(flam[0].position, flam[1].position, 0.0)
    }

    @Test
    fun buzzRollsHaveATail() {
        assertTrue(layout("multiple-bounce-roll").notes.all { it.buzzQuarters == 0.5 })
    }

    @Test
    fun theGridMarksBeatsAndBars() {
        val l = layout("single-stroke-roll")
        // One loop is the repeated bar's two passes.
        assertEquals(8.0, l.lengthQuarters, 0.0)
        assertEquals((0 until 8).map { it.toDouble() }, l.grid.map { it.position })
        assertEquals((0 until 8).map { it % 4 == 0 }, l.grid.map { it.isBar })
        assertEquals(listOf("1", "e", "&", "a", "2"), l.counts.take(5).map { it.text })
        assertEquals(0.25, l.counts[1].position, 0.0)
    }

    @Test
    fun loopsCoverTheVisibleRange() {
        val l = layout("flam")
        assertEquals(-1L..0L, l.loopsBetween(-4.0, 7.9))
        assertEquals(0L..1L, l.loopsBetween(4.0, 8.5))
    }

    @Test
    fun timeRunsUpTheScreenAtAFixedDistancePerBeat() {
        val g =
            FallingNotesGeometry(
                1000f,
                2000f,
                pixelsPerBeat = 200f,
                beatQuarters = 1.0,
                lanes = listOf(Lane.LEFT, Lane.RIGHT),
                laneWidth = 200f,
            )
        assertEquals(1640f, g.hitLineY, 0.01f)
        assertEquals(g.hitLineY, g.y(3.0, now = 3.0), 0f)
        assertEquals(g.hitLineY - 200f, g.y(4.0, now = 3.0), 0.01f)
        assertEquals(g.hitLineY + 100f, g.y(2.5, now = 3.0), 0.01f)
        // Lanes centred: L then R.
        assertEquals(400f, g.laneCenterX(Lane.LEFT), 0f)
        assertEquals(600f, g.laneCenterX(Lane.RIGHT), 0f)
        val visible = g.visiblePositions(now = 3.0, margin = 0f)
        assertEquals(3.0 - 360.0 / 200, visible.start, 1e-4)
        assertEquals(3.0 + 1640.0 / 200, visible.endInclusive, 1e-4)
    }

    @Test
    fun aDottedQuarterBeatSpacesByTheBeat() {
        val g =
            FallingNotesGeometry(
                1000f,
                2000f,
                pixelsPerBeat = 150f,
                beatQuarters = 1.5,
                lanes = listOf(Lane.LEFT, Lane.RIGHT),
                laneWidth = 200f,
            )
        assertEquals(100f, g.pixelsPerQuarter, 0.01f)
    }

    @Test
    fun notesLightUpAsTheyCrossTheHitLine() {
        assertEquals(0f, litAmount(2.0, now = 1.99), 0f)
        assertEquals(1f, litAmount(2.0, now = 2.0), 0f)
        assertEquals(0.5f, litAmount(2.0, now = 2.0 + LIT_QUARTERS / 2), 1e-6f)
        assertEquals(0f, litAmount(2.0, now = 2.0 + LIT_QUARTERS + 0.01), 0f)
    }

    @Test
    fun notesWithoutStickingFallInAMiddleLane() {
        // Imported files may have no sticking (M6); the proper treatment is #36.
        val note =
            dev.co508.drums.lesson.Note(
                dev.co508.drums.lesson.Fraction.ZERO,
                dev.co508.drums.lesson.Fraction.ONE,
                hand = null,
            )
        val measure =
            dev.co508.drums.lesson.Measure(
                "1",
                dev.co508.drums.lesson.Fraction.ZERO,
                dev.co508.drums.lesson.Fraction
                    .of(4),
                dev.co508.drums.lesson.TimeSignature.FOUR_FOUR,
                listOf(note),
            )
        val l =
            FallingNotesLayout.from(
                Expansion.expand(
                    dev.co508.drums.lesson
                        .Notation(null, null, listOf(measure)),
                ),
            )
        assertEquals(Lane.MIDDLE, l.notes.single().lane)
        assertTrue(l.hasMiddleLane)
    }

    @Test
    fun graceNotesKnowTheGapToTheirLaneNeighbour() {
        // Flam accent: beat 2's grace (r, R lane, at 1.0) follows beat 1's last tap (R, at 2/3).
        val grace = layout("flam-accent").notes.first { it.graceLead > 0 && it.position == 1.0 }
        assertEquals(Lane.RIGHT, grace.lane)
        assertEquals(1.0 / 3, grace.laneGapQuarters, 1e-9)
        assertEquals(1, grace.graceCount)
        // The first grace wraps round the loop to the last note in its lane.
        val first = layout("flam-accent").notes.first { it.graceLead > 0 }
        assertEquals(1.0 / 3, first.laneGapQuarters, 1e-9)
        // A drag's two graces share one main note.
        assertTrue(layout("drag").notes.filter { it.graceLead > 0 }.all { it.graceCount == 2 })
    }

    @Test
    fun graceNotesSitCloserToTheirMainNoteThanToTheirLaneNeighbour() {
        // The Sony: xxhdpi (3 px per dp), lanes 120 dp wide, staff space a fifth of a lane.
        val staffSpace = 360f * 0.2f
        val maxStep = staffSpace * 0.7f
        for (zoomDp in listOf(48f, 110f, 240f)) {
            val pixelsPerQuarter = zoomDp * 3
            for (id in listOf("flam", "flam-accent", "drag")) {
                for (g in layout(id).notes.filter { it.graceLead > 0 }) {
                    val offset = graceOffset(g.graceLead, g.graceCount, g.laneGapQuarters, pixelsPerQuarter, maxStep)
                    val gap = (g.laneGapQuarters * pixelsPerQuarter).toFloat()
                    assertTrue("$id at $zoomDp dp: grace offset $offset vs gap $gap", offset < gap / 2)
                    // Clear of the neighbour whenever the neighbour is clear of the main note.
                    val halfHeads = staffSpace * (0.5f + 0.3f)
                    if (gap >
                        staffSpace
                    ) {
                        assertTrue(
                            "$id at $zoomDp dp: grace touches its neighbour",
                            gap - offset > halfHeads,
                        )
                    }
                    assertTrue(offset <= g.graceLead * maxStep)
                }
            }
        }
    }

    @Test
    fun graceOffsetsUseTheFullStepWhenThereIsRoom() {
        assertEquals(50.4f, graceOffset(1, 1, laneGapQuarters = 2.0, pixelsPerQuarter = 330f, maxStep = 50.4f), 1e-4f)
        // Triplet spacing at the default zoom: a third of a quarter is 110 px; 30% of that.
        assertEquals(33f, graceOffset(1, 1, laneGapQuarters = 1.0 / 3, pixelsPerQuarter = 330f, maxStep = 50.4f), 1e-3f)
        // Two graces share the room.
        assertEquals(33f, graceOffset(2, 2, laneGapQuarters = 1.0 / 3, pixelsPerQuarter = 330f, maxStep = 50.4f), 1e-3f)
        assertEquals(0f, graceOffset(0, 1, 1.0, 330f, 50.4f), 0f)
    }
}
