package dev.co508.drums.playback

import dev.co508.drums.audio.OutputTimestamp
import dev.co508.drums.audio.OutputTimestampSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioClockTest {
    private class FakeStream : OutputTimestampSource {
        var available = true
        var frame = 0L
        var nanos = 0L
        var queries = 0

        override fun outputTimestamp(out: OutputTimestamp): Boolean {
            queries++
            if (!available) return false
            out.framePosition = frame
            out.nanoTime = nanos
            return true
        }
    }

    private val stream = FakeStream()
    private var now = 0L
    private val clock = AudioClock(stream, 48_000, refreshIntervalNanos = 100_000_000L, systemNanoTime = { now })

    private val ms = 1_000_000L

    @Test
    fun isNaNUntilTheStreamReportsATimestamp() {
        stream.available = false
        assertTrue(clock.frameAt(0).isNaN())
        assertFalse(clock.stats().anchored)
    }

    @Test
    fun extrapolatesFromTheLatestTimestamp() {
        stream.frame = 4_800
        stream.nanos = 1_000 * ms
        now = 1_000 * ms
        assertEquals(4_800.0, clock.frameAt(1_000 * ms), 1e-9)
        assertEquals(4_800.0 + 480, clock.frameAt(1_010 * ms), 1e-9)
        // Also backwards, for mic timestamps from just before.
        assertEquals(4_800.0 - 48, clock.frameAt(999 * ms), 1e-9)
    }

    @Test
    fun refreshesOnlyEveryInterval() {
        stream.nanos = 0
        clock.frameAt(0)
        now = 50 * ms
        clock.frameAt(now)
        assertEquals(1, stream.queries)
        now = 100 * ms
        clock.frameAt(now)
        assertEquals(2, stream.queries)
    }

    @Test
    fun measuresJitterAgainstTheExtrapolation() {
        clock.frameAt(0) // Anchor: frame 0 at 0 ns.
        // 100 ms later the stream reports 4 848 frames: 1 ms ahead of the 4 800 expected.
        now = 100 * ms
        stream.frame = 4_848
        stream.nanos = 100 * ms
        assertEquals(4_848.0, clock.frameAt(100 * ms), 1e-9)
        // Then 200 ms in, 9 552 frames: 2 ms behind (expected 4 848 + 4 800 = 9 648).
        now = 200 * ms
        stream.frame = 9_552
        stream.nanos = 200 * ms
        clock.frameAt(now)
        val stats = clock.stats()
        assertEquals(2L, stats.updates)
        assertEquals(-2.0, stats.lastErrorMillis, 1e-9)
        assertEquals(2.0, stats.maxAbsErrorMillis, 1e-9)
        assertEquals(1.5, stats.meanAbsErrorMillis, 1e-9)
    }

    @Test
    fun keepsTheAnchorWhenATimestampIsBrieflyUnavailable() {
        clock.frameAt(0)
        stream.available = false
        now = 500 * ms
        assertEquals(24_000.0, clock.frameAt(now), 1e-9)
    }

    @Test
    fun resetForgetsTheOldStream() {
        stream.frame = 1_000_000
        clock.frameAt(0)
        clock.reset()
        stream.available = false
        assertTrue(clock.frameAt(0).isNaN())
        assertEquals(0L, clock.stats().updates)
    }

    @Test
    fun visualOffsetDelaysTheVisualsOnly() {
        clock.frameAt(0)
        clock.visualOffsetMillis = 20.0
        assertEquals(48_000.0 - 960, clock.visualFrameAt(1_000 * ms), 1e-9)
        assertEquals(48_000.0, clock.frameAt(1_000 * ms), 1e-9)
    }
}
