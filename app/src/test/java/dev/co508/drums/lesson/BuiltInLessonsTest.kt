package dev.co508.drums.lesson

import dev.co508.drums.lesson.Hand.LEFT
import dev.co508.drums.lesson.Hand.RIGHT
import dev.co508.drums.lesson.StrokeDynamic.ACCENT
import dev.co508.drums.lesson.StrokeDynamic.GRACE
import dev.co508.drums.lesson.StrokeDynamic.NORMAL
import dev.co508.drums.musicxml.testImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What each built-in rudiment plays, after expansion: one pass of its
 * repeated bar. Guards the lessons' content against a bad edit or export.
 */
class BuiltInLessonsTest {
    private val pack =
        (
            PackLoader(
                testImporter(),
            ).load(
                DirectoryPackSource(File("src/main/assets/${PackLoader.BUILT_IN_FOLDER}")),
                strict = true,
            ) as PackResult.Success
        ).pack

    private fun lesson(id: String) = pack.lessons.single { it.id == id }

    /** The first pass: strokes in the first bar. */
    private fun firstPass(id: String): List<Stroke> {
        val timeline = Expansion.expand(lesson(id).notation)
        return timeline.strokes.filter { it.position < timeline.bars.first().length }
    }

    private fun hands(strokes: List<Stroke>) = strokes.joinToString("") { if (it.hand == RIGHT) "R" else "L" }

    private fun f(
        n: Long,
        d: Long = 1,
    ) = Fraction.of(n, d)

    @Test
    fun everyLessonIsOneRepeatedBar() {
        for (lesson in pack.lessons) {
            val measure = lesson.notation.measures.single()
            val expectedTime = if (lesson.id == "double-paradiddle") TimeSignature(3, 4) else TimeSignature.FOUR_FOUR
            assertEquals(lesson.id, expectedTime, measure.timeSignature)
            assertTrue(lesson.id, measure.repeatStart)
            assertEquals(lesson.id, 2, measure.repeatTimes)
            assertEquals(lesson.id, expectedTime.barQuarters, measure.length)
            // The written tempo mark matches the start tempo in pack.json.
            assertEquals(
                lesson.id,
                lesson.manifest.tempo.start
                    .toDouble(),
                lesson.notation.tempo!!.quarterNotesPerMinute,
                0.0,
            )
        }
    }

    @Test
    fun singleStrokeRoll() {
        val strokes = firstPass("single-stroke-roll")
        assertEquals("RL".repeat(8), hands(strokes))
        assertEquals((0L until 16).map { f(it, 4) }, strokes.map { it.position })
    }

    @Test
    fun doubleStrokeOpenRoll() {
        val strokes = firstPass("double-stroke-open-roll")
        assertEquals("RRLL".repeat(4), hands(strokes))
        assertEquals((0L until 16).map { f(it, 4) }, strokes.map { it.position })
    }

    @Test
    fun multipleBounceRoll() {
        val strokes = firstPass("multiple-bounce-roll")
        assertEquals("RL".repeat(4), hands(strokes))
        assertTrue(strokes.all { it.buzzLength == f(1, 2) })
    }

    @Test
    fun allLessonsExpand() {
        for (lesson in pack.lessons) {
            val timeline = Expansion.expand(lesson.notation)
            assertTrue(lesson.id, timeline.strokes.isNotEmpty())
            assertTrue(lesson.id, timeline.strokes.all { it.hand != null })
        }
    }

    @Test
    fun newRollsHaveExpectedFirstPhrase() {
        val phrases =
            mapOf(
                "single-stroke-four" to "RLRL",
                "single-stroke-seven" to "RLRLRLR",
                "triple-stroke-roll" to "RRRLLLRRRLLL",
                "five-stroke-roll" to "RRLLR",
                "six-stroke-roll" to "RRLLRL",
                "seven-stroke-roll" to "RRLLRRL",
                "nine-stroke-roll" to "RRLLRRLLR",
                "ten-stroke-roll" to "RRLLRRLLRL",
                "eleven-stroke-roll" to "RRLLRRLLRRL",
                "thirteen-stroke-roll" to "RRLLRRLLRRLLR",
                "fifteen-stroke-roll" to "RRLLRRLLRRLLRRL",
                "seventeen-stroke-roll" to "RRLLRRLLRRLLRRLLR",
            )
        for ((id, expected) in phrases) {
            val strokes = firstPass(id)
            assertTrue(id, hands(strokes).startsWith(expected))
            val firstPhrase = strokes.take(expected.length)
            val denominator = if (id.startsWith("single-stroke-") || id == "triple-stroke-roll") 4L else 8L
            if (id != "triple-stroke-roll") {
                assertEquals(id, expected.indices.map { f(it.toLong(), denominator) }, firstPhrase.map { it.position })
                assertEquals(id, List(expected.length - 1) { NORMAL } + ACCENT, firstPhrase.map { it.dynamic })
            }
        }
    }

    @Test
    fun singleParadiddle() {
        val strokes = firstPass("single-paradiddle")
        assertEquals("RLRRLRLL".repeat(2), hands(strokes))
        assertEquals((0 until 16).map { if (it % 4 == 0) ACCENT else NORMAL }, strokes.map { it.dynamic })
    }

    @Test
    fun diddleAndFlamMainStickingAndGracePlacement() {
        val expected =
            mapOf(
                "double-paradiddle" to "RLRLRRLRLRLL",
                "triple-paradiddle" to "RLRLRLRRLRLRLRLL",
                "single-paradiddle-diddle" to "RLRRLL".repeat(4),
                "flam-tap" to "RRLL".repeat(3),
                "flamacue" to "RLRLRLRLRL",
                "flam-paradiddle" to "RLRRLRLL".repeat(2),
                "single-flammed-mill" to "RRLRLLRL".repeat(2),
                "flam-paradiddle-diddle" to "RLRRLL".repeat(4),
                "pataflafla" to "RLRL".repeat(4),
                "swiss-army-triplet" to "RRL".repeat(4),
                "inverted-flam-tap" to "RLLR".repeat(3),
                "flam-drag" to "RLRLRL".repeat(2),
            )
        val graceGroups =
            mapOf(
                "flam-tap" to listOf(0, 2, 4, 6, 8, 10),
                "flamacue" to listOf(0, 4, 5, 9),
                "flam-paradiddle" to listOf(0, 4, 8, 12),
                "single-flammed-mill" to listOf(0, 4, 8, 12),
                "flam-paradiddle-diddle" to listOf(0, 6, 12, 18),
                "pataflafla" to listOf(0, 3, 4, 7, 8, 11, 12, 15),
                "swiss-army-triplet" to listOf(0, 3, 6, 9),
                "inverted-flam-tap" to listOf(0, 2, 4, 6, 8, 10),
                "flam-drag" to listOf(0, 1, 3, 4, 6, 7, 9, 10),
            )
        for ((id, expectedHands) in expected) {
            val notation = lesson(id).notation
            val main = firstPass(id).filter { it.graceLead == 0 }
            assertEquals(id, expectedHands, hands(main))
            val actualGraceGroups = notation.notes.mapIndexedNotNull { i, n -> i.takeIf { n.graceNotes.isNotEmpty() } }
            assertEquals(id, graceGroups[id].orEmpty(), actualGraceGroups)
            assertTrue(id, notation.notes.all { n -> n.graceNotes.all { it.hand != n.hand } })
            val expectedPositions =
                when (id) {
                    "flamacue" -> listOf(f(0), f(1, 4), f(1, 2), f(3, 4), f(1), f(2), f(9, 4), f(5, 2), f(11, 4), f(3))
                    "single-paradiddle-diddle", "flam-paradiddle-diddle" -> main.indices.map { f(it.toLong(), 6) }
                    "flam-tap", "inverted-flam-tap", "swiss-army-triplet", "flam-drag" ->
                        main.indices.map { f(it.toLong(), 3) }
                    else -> main.indices.map { f(it.toLong(), 4) }
                }
            assertEquals(id, expectedPositions, main.map { it.position })
        }
        val flamacueAccents =
            lesson("flamacue").notation.notes.mapIndexedNotNull { i, n -> i.takeIf { n.accent != Accent.NONE } }
        assertEquals(listOf(1, 6), flamacueAccents)
        val flamDragGraces = lesson("flam-drag").notation.notes.map { it.graceNotes.size }
        assertEquals(List(4) { listOf(1, 2, 0) }.flatten(), flamDragGraces)
    }

    @Test
    fun flam() {
        val strokes = firstPass("flam")
        assertEquals("LRRLLRRL", hands(strokes))
        assertEquals(listOf(GRACE, ACCENT).let { p -> p + p + p + p }, strokes.map { it.dynamic })
        assertEquals(listOf(1, 0, 1, 0, 1, 0, 1, 0), strokes.map { it.graceLead })
    }

    @Test
    fun flamAccent() {
        val strokes = firstPass("flam-accent")
        // Per beat: grace, accented flam, two taps; the lead hand alternates.
        assertEquals("LRLR" + "RLRL" + "LRLR" + "RLRL", hands(strokes))
        val mains = strokes.filter { it.graceLead == 0 }
        assertEquals((0L until 12).map { f(it, 3) }, mains.map { it.position })
        assertEquals((0 until 12).map { if (it % 3 == 0) ACCENT else NORMAL }, mains.map { it.dynamic })
    }

    @Test
    fun drag() {
        val strokes = firstPass("drag")
        assertEquals("LLR" + "RRL" + "LLR" + "RRL", hands(strokes))
        assertEquals(listOf(2, 1, 0).let { p -> p + p + p + p }, strokes.map { it.graceLead })
        assertEquals(listOf(LEFT, LEFT), strokes.take(2).map { it.hand })
    }
}
