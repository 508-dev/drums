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
    fun everyLessonIsOneRepeatedBarOfFourFour() {
        for (lesson in pack.lessons) {
            val measure = lesson.notation.measures.single()
            assertEquals(lesson.id, TimeSignature.FOUR_FOUR, measure.timeSignature)
            assertTrue(lesson.id, measure.repeatStart)
            assertEquals(lesson.id, 2, measure.repeatTimes)
            assertEquals(lesson.id, f(4), measure.length)
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
    fun singleParadiddle() {
        val strokes = firstPass("single-paradiddle")
        assertEquals("RLRRLRLL".repeat(2), hands(strokes))
        assertEquals((0 until 16).map { if (it % 4 == 0) ACCENT else NORMAL }, strokes.map { it.dynamic })
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
