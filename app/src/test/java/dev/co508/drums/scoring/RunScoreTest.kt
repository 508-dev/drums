package dev.co508.drums.scoring

import dev.co508.drums.lesson.Hand
import dev.co508.drums.ui.history.scoreLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class RunScoreTest {
    private val stats =
        ScoringStats(
            green = 6,
            yellow = 2,
            red = 1,
            misses = 1,
            extras = 2,
            // Offsets 0, 2, 4, …, 16 ms: mean 8, population SD √(26.67) ≈ 5.16.
            sumOffsetMillis = (0..8).sumOf { it * 2.0 },
            sumSquaredOffsetMillis = (0..8).sumOf { (it * 2.0) * (it * 2.0) },
            perHand =
                mapOf(
                    Hand.LEFT to HandStats(hits = 4, misses = 1, sumOffsetMillis = 48.0),
                    Hand.RIGHT to HandStats(hits = 5, misses = 0, sumOffsetMillis = 24.0),
                ),
            gracesHeard = 3,
            gracesUnheard = 1,
        )

    @Test
    fun `a run's totals become a practice-log row`() {
        val score = stats.toSessionScore("run", Strictness.STRICT, "wired", calibrated = true)
        assertEquals("STRICT", score.strictness)
        assertEquals(10, score.notes)
        assertEquals(0.8, score.inWindowShare, 1e-12)
        assertEquals(8.0, score.meanOffsetMillis!!, 1e-12)
        assertEquals(5.164, score.spreadMillis!!, 1e-3)
        assertEquals(12.0, score.leftMeanOffsetMillis!!, 1e-12)
        assertEquals(1, score.leftMisses)
        assertEquals(4.8, score.rightMeanOffsetMillis!!, 1e-12)
    }

    @Test
    fun `no hits means no averages`() {
        val score = ScoringStats(misses = 4).toSessionScore("run", Strictness.STANDARD, null, calibrated = false)
        assertNull(score.meanOffsetMillis)
        assertNull(score.spreadMillis)
        assertEquals(0.0, score.inWindowShare, 0.0)
        assertEquals(
            listOf(
                "0% in window (0% green) · 4 missed · 0 extras",
                "Not calibrated for this output: offsets include its delay.",
            ),
            scoreLines(score, Locale.ROOT),
        )
    }

    @Test
    fun `the summary reads as a drummer would say it`() {
        val score = stats.toSessionScore("run", Strictness.STANDARD, "wired", calibrated = true)
        assertEquals(
            listOf(
                "80% in window (60% green) · 1 missed · 2 extras",
                "Average +8.0 ms (dragging) · spread ±5.2 ms",
                "Left +12.0 ms, 1 missed · Right +4.8 ms",
                "3 of 4 grace notes heard as their own stroke",
            ),
            scoreLines(score, Locale.ROOT),
        )
        val rushing =
            stats.copy(
                sumOffsetMillis = -45.0,
                perHand = emptyMap(),
                gracesHeard = 0,
                gracesUnheard = 0,
                extras = 1,
            )
        assertEquals(
            "Average -5.0 ms (rushing)",
            scoreLines(
                rushing.toSessionScore("r", Strictness.STANDARD, null, true),
                Locale.ROOT,
            )[1].substringBefore(" ·"),
        )
    }
}
