package dev.co508.drums.settings

import dev.co508.drums.audio.EventSink
import dev.co508.drums.audio.Sound
import dev.co508.drums.playback.Pattern
import dev.co508.drums.playback.PatternEvent
import dev.co508.drums.playback.Transport
import dev.co508.drums.ui.practice.practiceProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticePlaybackTest {
    private val sound = Sound("test", floatArrayOf(1f))
    private val pattern = Pattern(4.0, 1.0, 4, listOf(PatternEvent(0.0, sound)), emptyList())

    @Test
    fun plainLoopRunsPastOneHundredLoopsWithEventsAndNoTempoChange() {
        val settings = LessonPreferences(tempo = 120, countIn = false, loop = true, trainer = false)
        val transport = Transport(48_000, sound, sound)
        var events = 0
        val sink =
            object : EventSink {
                override fun play(
                    frame: Long,
                    sound: Sound,
                    gain: Float,
                    pan: Float,
                ) {
                    events++
                }
            }
        transport.start(settings.playSettings(pattern))
        for (frame in 0L until 96_000L * 101 step 240) transport.events(frame, frame + 240, sink)
        assertEquals(101, events)
        assertEquals(Transport.State.PLAYING, transport.state)
        assertEquals(120.0, transport.beatsPerMinuteAt(96_000.0 * 100), 1e-9)
        assertEquals("Loop 101", practiceProgress(settings.playSettings(pattern), 400.0, 120.0))
    }

    @Test
    fun trainerFollowsConfiguredLoopScheduleThenStaysAtTarget() {
        val settings =
            LessonPreferences(
                tempo = 180,
                trainer = true,
                countIn = false,
                loop = true,
                trainerStart = 100,
                trainerStep = 10,
                trainerLoops = 2,
                trainerTarget = 125,
            )
        val play = settings.playSettings(pattern)
        val transport = Transport(48_000, sound, sound)
        val sink =
            object : EventSink {
                override fun play(
                    frame: Long,
                    sound: Sound,
                    gain: Float,
                    pan: Float,
                ) = Unit
            }
        transport.start(play)
        var frame = 0L
        var boundary = 0.0
        for ((loop, bpm) in listOf(100, 100, 110, 110, 120, 120, 125, 125, 125, 125).withIndex()) {
            while (frame <= boundary + 2) {
                transport.events(frame, frame + 240, sink)
                frame += 240
            }
            assertEquals("loop $loop", bpm.toDouble(), transport.beatsPerMinuteAt(boundary + 1), 1e-9)
            val status = practiceProgress(play, loop * 4.0, bpm.toDouble())
            if (bpm == 125) {
                assertTrue(status.contains("at target"))
            } else {
                assertEquals(
                    "Loop ${loop % 2 + 1}/2, next +${minOf(10, 125 - bpm)} → ${minOf(bpm + 10, 125)} BPM",
                    status,
                )
            }
            boundary += 4.0 * 60 * 48_000 / bpm
        }
        assertEquals(Transport.State.PLAYING, transport.state)
        assertEquals("Count-in", practiceProgress(play, -0.5, 100.0))
    }
}
