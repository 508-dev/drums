package dev.co508.drums.hits

import dev.co508.drums.audio.OutputTimestampSource
import dev.co508.drums.playback.AudioClock
import dev.co508.drums.playback.TempoMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HitTimelineTest {
    private val rate = 48_000
    private val ms = 1_000_000L

    // Output frame 0 is heard at 2 s; nothing has drifted.
    private val clock =
        AudioClock(
            OutputTimestampSource { out ->
                out.framePosition = 0
                out.nanoTime = 2_000 * ms
                true
            },
            rate,
            systemNanoTime = { 2_000 * ms },
        )

    // 120 BPM in quarters from output frame 4 800: 1 quarter = 24 000 frames.
    private val tempoMap = TempoMap().apply { begin(4_800.0, 0.0, 1.0 / 24_000) }

    @Test
    fun `a hit lines up with the output frame heard at that moment`() {
        val timeline = HitTimeline(clock, tempoMap)
        // The 2nd beat (position 1.0) is output frame 28 800, heard at 2.6 s.
        assertEquals(28_800.0, timeline.outputFrame(2_600 * ms), 1e-6)
        assertEquals(1.0, timeline.position(2_600 * ms), 1e-9)
        // 5 ms late is 5 ms of quarter notes at 120 BPM.
        assertEquals(1.01, timeline.position(2_605 * ms), 1e-9)
    }

    @Test
    fun `calibrated latency is taken off`() {
        val timeline = HitTimeline(clock, tempoMap)
        timeline.latencyNanos = 25 * ms
        assertEquals(1.0, timeline.position(2_625 * ms), 1e-9)
    }

    @Test
    fun `hits from before the input clock anchored have no position`() {
        assertTrue(HitTimeline(clock, tempoMap).position(Long.MIN_VALUE).isNaN())
    }
}
