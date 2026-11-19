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
    fun clicksFallOnEveryBeatWithBarAccents() {
        val pattern = Pattern(8.0, Meter.FOUR_FOUR, emptyList())
        assertArrayEquals(DoubleArray(8) { it.toDouble() }, pattern.clickPositions, 0.0)
        assertEquals(listOf(true, false, false, false, true, false, false, false), pattern.clickAccents.toList())
    }

    @Test
    fun compoundMeterClicksOnDottedQuarters() {
        val pattern = Pattern(6.0, Meter(2, 1.5), emptyList())
        assertArrayEquals(doubleArrayOf(0.0, 1.5, 3.0, 4.5), pattern.clickPositions, 0.0)
        assertEquals(listOf(true, false, true, false), pattern.clickAccents.toList())
    }

    @Test
    fun aPartialLastBeatStillClicks() {
        assertEquals(3, Pattern(2.5, Meter.FOUR_FOUR, emptyList()).clickPositions.size)
    }

    @Test
    fun eventsAreSortedByPosition() {
        val pattern =
            Pattern(1.0, Meter.FOUR_FOUR, listOf(PatternEvent(0.5, snare), PatternEvent(0.0, snare, gain = 0.2f)))
        assertArrayEquals(doubleArrayOf(0.0, 0.5), pattern.eventPositions, 0.0)
        assertEquals(0.2f, pattern.eventGains[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun eventsMustLieInsideThePattern() {
        Pattern(1.0, Meter.FOUR_FOUR, listOf(PatternEvent(1.0, snare)))
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
