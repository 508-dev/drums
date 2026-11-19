package dev.co508.drums.diagnostics

import dev.co508.drums.audio.Sound
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsMathTest {
    private val snare = Sound("snare", floatArrayOf(1f))

    @Test
    fun thirtySecondNotesFillTheBar() {
        val pattern = subdivisionPattern(8, snare)
        assertEquals(32, pattern.events.size)
        assertEquals(0.125, pattern.events[1].position, 0.0)
        assertEquals(3.875, pattern.events.last().position, 0.0)
        assertEquals(listOf(0.3f, -0.3f), pattern.events.take(2).map { it.pan })
        assertEquals(listOf(true, false, false, false), pattern.clicks.map { it.accent })
    }

    @Test
    fun tripletsAreEvenlySpaced() {
        val pattern = subdivisionPattern(3, snare)
        assertEquals(12, pattern.events.size)
        assertEquals(1.0 / 3, pattern.events[1].position, 1e-12)
    }

    @Test
    fun beatPhaseFindsTheBeatAndTimeSinceIt() {
        val phase = beatPhase(position = 5.25, beatsPerBar = 4, beatQuarters = 1.0, beatsPerMinute = 120.0)
        assertEquals(1, phase.beatInBar)
        assertEquals(0.25, phase.fraction, 1e-12)
        assertEquals(125.0, phase.millisSinceBeat, 1e-9)
    }

    @Test
    fun countInBeatsAreNegativePositions() {
        assertEquals(0, beatPhase(-4.0, 4, 1.0, 60.0).beatInBar)
        assertEquals(3, beatPhase(-0.5, 4, 1.0, 60.0).beatInBar)
    }

    @Test
    fun frameDropsAreCountedFromGaps() {
        val counter = FrameDropCounter()
        val interval = 16_666_667L
        var t = 1_000_000_000L
        counter.onFrame(t, interval)
        t += interval
        counter.onFrame(t, interval) // On time.
        t += 3 * interval
        counter.onFrame(t, interval) // Two frames missed.
        t += interval + interval / 3
        counter.onFrame(t, interval) // Late, but not a whole frame.
        assertEquals(4L, counter.frames)
        assertEquals(2L, counter.dropped)
        assertEquals(50.0, counter.worstGapMillis, 0.01)
        counter.reset()
        assertEquals(0L, counter.dropped)
    }
}
