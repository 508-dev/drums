package dev.co508.drums.playback

import dev.co508.drums.audio.Sound
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternTest {
    private val snare = Sound("snare", floatArrayOf(1f))

    @Test
    fun steadyClicksFallOnEveryBeatWithBarAccents() {
        val clicks = Pattern.steadyClicks(8.0, beatsPerBar = 4, beatQuarters = 1.0)
        assertEquals((0 until 8).map { it.toDouble() }, clicks.map { it.position })
        assertEquals(listOf(true, false, false, false, true, false, false, false), clicks.map { it.accent })
    }

    @Test
    fun compoundClicksOnDottedQuarters() {
        val clicks = Pattern.steadyClicks(6.0, beatsPerBar = 2, beatQuarters = 1.5)
        assertEquals(listOf(0.0, 1.5, 3.0, 4.5), clicks.map { it.position })
        assertEquals(listOf(true, false, true, false), clicks.map { it.accent })
        assertEquals(3, Pattern.steadyClicks(2.5, 4, 1.0).size) // A partial last beat still clicks.
    }

    @Test
    fun eventsAreSortedAndTheirLeadLevelsTracked() {
        val pattern =
            Pattern(
                1.0,
                1.0,
                4,
                listOf(PatternEvent(0.5, snare), PatternEvent(0.0, snare, gain = 0.2f, leadLevel = 2)),
                emptyList(),
            )
        assertArrayEquals(doubleArrayOf(0.0, 0.5), pattern.eventPositions, 0.0)
        assertEquals(0.2f, pattern.eventGains[0])
        assertEquals(2, pattern.maxLeadLevel)
    }

    @Test(expected = IllegalArgumentException::class)
    fun eventsMustLieInsideThePattern() {
        Pattern(1.0, 1.0, 4, listOf(PatternEvent(1.0, snare)), emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun leadLevelsAreBounded() {
        Pattern(1.0, 1.0, 4, listOf(PatternEvent(0.0, snare, leadLevel = Pattern.MAX_LEAD_LEVEL + 1)), emptyList())
    }

    @Test
    fun firstAboveIsABinarySearch() {
        val values = doubleArrayOf(0.0, 1.0, 1.0, 2.0)
        assertEquals(0, firstAbove(values, -1.0))
        assertEquals(3, firstAbove(values, 1.0))
        assertEquals(4, firstAbove(values, 2.0))
        assertEquals(0, firstAbove(DoubleArray(0), 0.0))
    }

    @Test
    fun speedTrainerStepsEveryNLoopsUpToTheTarget() {
        val trainer = SpeedTrainer(stepBpm = 5.0, everyLoops = 3, targetBpm = 112.0)
        var bpm = 100.0
        val tempos = (0L..12L).map { loop -> trainer.tempoEntering(loop, bpm).also { bpm = it } }
        assertEquals(
            listOf(100.0, 100.0, 100.0, 105.0, 105.0, 105.0, 110.0, 110.0, 110.0, 112.0, 112.0, 112.0, 112.0),
            tempos,
        )
    }

    @Test
    fun speedTrainerNeverLowersAFasterTempo() {
        assertEquals(150.0, SpeedTrainer(5.0, 1, 120.0).tempoEntering(4, 150.0), 0.0)
    }

    @Test
    fun focusLossesStopPlayback() {
        // Values of AudioManager.AUDIOFOCUS_*: LOSS -1, LOSS_TRANSIENT -2, CAN_DUCK -3, GAIN 1.
        assertTrue(PlaybackSession.stopsPlayback(-1))
        assertTrue(PlaybackSession.stopsPlayback(-2))
        assertTrue(PlaybackSession.stopsPlayback(-3))
        assertFalse(PlaybackSession.stopsPlayback(1))
    }
}
