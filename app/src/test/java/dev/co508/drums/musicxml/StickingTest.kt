package dev.co508.drums.musicxml

import dev.co508.drums.lesson.Hand.LEFT
import dev.co508.drums.lesson.Hand.RIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickingTest {
    private fun ok(
        words: List<String>,
        graces: Int,
    ) = Sticking.parse(words, graces) as StickingResult.Ok

    private fun invalid(
        words: List<String>,
        graces: Int,
    ) = (Sticking.parse(words, graces) as StickingResult.Invalid).reason

    @Test
    fun recognizesStickingWords() {
        for (w in listOf("R", "L", "lR", "llR", " rL ", "RL")) assertTrue(w, Sticking.isSticking(w))
        for (w in listOf("", "rit.", "Fine", "R L", "1", "x")) assertFalse(w, Sticking.isSticking(w))
    }

    @Test
    fun styleGuideWords() {
        assertEquals(StickingAssignment(emptyList(), RIGHT), ok(listOf("R"), 0).assignment)
        assertEquals(StickingAssignment(listOf(LEFT), RIGHT), ok(listOf("lR"), 1).assignment)
        assertEquals(StickingAssignment(listOf(RIGHT, RIGHT), LEFT), ok(listOf("rrL"), 2).assignment)
        assertFalse(ok(listOf("llR"), 2).offStyle)
    }

    @Test
    fun aWordMustCoverEveryGraceNoteAndTheNote() {
        assertEquals("sticking \"lR\" has 2 letters; expected one letter", invalid(listOf("lR"), 0))
        assertEquals(
            "sticking \"R\" has 1 letters; expected 3 letters (2 grace notes and the note)",
            invalid(listOf("R"), 2),
        )
    }

    @Test
    fun uppercaseWordsAreReadInOrderButOffStyle() {
        val result = ok(listOf("LR"), 1)
        assertEquals(StickingAssignment(listOf(LEFT), RIGHT), result.assignment)
        assertTrue(result.offStyle)
    }

    @Test
    fun mixedCaseMustPutTheGraceNotesInLowercase() {
        assertTrue(invalid(listOf("Rl"), 1).contains("uppercase letter on a grace note"))
    }

    @Test
    fun stackedWordsAreAcceptedWhenEveryOrderAgrees() {
        // MuseScore's two stacked Stickings, but distinguishable by case.
        val byCase = ok(listOf("R", "l"), 1)
        assertEquals(StickingAssignment(listOf(LEFT), RIGHT), byCase.assignment)
        assertTrue(byCase.offStyle)
        // The same hand twice: order doesn't matter.
        assertEquals(StickingAssignment(listOf(RIGHT), RIGHT), ok(listOf("R", "R"), 1).assignment)
    }

    @Test
    fun stackedWordsAreRejectedWhenTheOrderMatters() {
        assertTrue(invalid(listOf("L", "R"), 1).contains("ambiguous"))
        assertTrue(invalid(listOf("l", "r", "R"), 2).contains("ambiguous"))
    }
}
