package dev.co508.drums.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class TempoMapTest {
    // 120 BPM in quarters at 48 kHz: 24 000 frames per quarter note.
    private val r120 = 1.0 / 24_000
    private val r60 = 1.0 / 48_000

    @Test
    fun isNaNBeforeBegin() {
        val map = TempoMap()
        assertTrue(map.isEmpty)
        assertTrue(map.positionAt(0.0).isNaN())
        assertTrue(map.frameAt(0.0).isNaN())
        assertTrue(map.rateAt(0.0).isNaN())
    }

    @Test
    fun oneSegmentIsLinear() {
        val map = TempoMap()
        map.begin(1_000.0, -4.0, r120)
        assertEquals(-4.0, map.positionAt(1_000.0), 1e-12)
        assertEquals(0.0, map.positionAt(97_000.0), 1e-12)
        assertEquals(97_000.0, map.frameAt(0.0), 1e-9)
        assertEquals(109_000.0, map.frameAt(0.5), 1e-9)
    }

    @Test
    fun framesBeforeTheEpochMapToItsStart() {
        val map = TempoMap()
        map.begin(1_000.0, -4.0, r120)
        assertEquals(-4.0, map.positionAt(0.0), 0.0)
        assertEquals(1_000.0, map.frameAt(-10.0), 0.0)
    }

    @Test
    fun positionIsContinuousAcrossARateChangeAtAFrame() {
        val map = TempoMap()
        map.begin(0.0, 0.0, r120)
        val at = map.changeRateAtFrame(36_000.0, r60)
        assertEquals(1.5, at, 1e-12)
        assertEquals(1.5, map.positionAt(36_000.0), 1e-12)
        // Just before: old rate. After: new rate.
        assertEquals(1.5 - r120, map.positionAt(35_999.0), 1e-12)
        assertEquals(1.5 + r60, map.positionAt(36_001.0), 1e-12)
        assertEquals(2.5, map.positionAt(84_000.0), 1e-12)
        assertEquals(84_000.0, map.frameAt(2.5), 1e-9)
        assertEquals(12_000.0, map.frameAt(0.5), 1e-9)
    }

    @Test
    fun aRateChangeAtAPositionLandsOnItsExactFractionalFrame() {
        val map = TempoMap()
        map.begin(0.0, 0.0, 1.0 / 26_181.818181818182) // 110 BPM
        val frame = map.changeRateAtPosition(8.0, r120)
        assertEquals(209_454.545454, frame, 1e-5)
        assertEquals(8.0, map.positionAt(frame), 1e-12)
        assertEquals(frame + 24_000, map.frameAt(9.0), 1e-6)
    }

    @Test
    fun aRateChangeAtAPassedPositionStartsWithTheLatestSegment() {
        val map = TempoMap()
        map.begin(0.0, 0.0, r120)
        map.changeRateAtFrame(24_000.0, r120)
        val frame = map.changeRateAtPosition(0.99999, r60)
        assertEquals(24_000.0, frame, 0.0)
        assertEquals(1.0 + r60, map.positionAt(24_001.0), 1e-12)
    }

    @Test
    fun rateAtFollowsTheSegments() {
        val map = TempoMap()
        map.begin(0.0, 0.0, r120)
        map.changeRateAtFrame(100.0, r60)
        assertEquals(r120, map.rateAt(99.0), 0.0)
        assertEquals(r60, map.rateAt(100.0), 0.0)
    }

    @Test
    fun aZeroRateHoldsThePosition() {
        val map = TempoMap()
        map.begin(0.0, 0.0, r120)
        map.changeRateAtFrame(48_000.0, 0.0)
        assertEquals(2.0, map.positionAt(1e9), 0.0)
        assertEquals(Double.POSITIVE_INFINITY, map.frameAt(2.5), 0.0)
        assertEquals(24_000.0, map.frameAt(1.0), 1e-9)
    }

    @Test
    fun aNewEpochHidesTheOldOne() {
        val map = TempoMap()
        map.begin(0.0, 0.0, r120)
        map.changeRateAtFrame(480_000.0, 0.0) // Stopped at position 20.
        map.begin(500_000.0, -4.0, r60)
        assertEquals(-4.0, map.positionAt(100_000.0), 0.0)
        assertEquals(-3.0, map.positionAt(548_000.0), 1e-12)
        assertEquals(500_000.0, map.frameAt(10.0 - 14.0), 0.0)
    }

    @Test
    fun anEpochCanRestartFramesAtZero() {
        val map = TempoMap()
        map.begin(0.0, 0.0, r120)
        map.begin(0.0, 10.0, r120) // A new stream, carrying on from position 10.
        assertEquals(10.5, map.positionAt(12_000.0), 1e-12)
        assertEquals(0.0, map.frameAt(10.0), 1e-9)
    }

    @Test
    fun theRingKeepsRecentSegmentsAfterWrapping() {
        val map = TempoMap(capacity = 8)
        map.begin(0.0, 0.0, r120)
        // 1000 changes, alternating 120 and 60 BPM, one every 1000 frames.
        var position = 0.0
        for (i in 1..1_000) {
            position = map.changeRateAtFrame(i * 1_000.0, if (i % 2 == 0) r120 else r60)
        }
        // Segment 1000 (even: 120 BPM) starts at frame 1 000 000, at `position`.
        assertEquals(position + 500 * r120, map.positionAt(1_000_500.0), 1e-9)
        // Segment 999 (odd: 60 BPM) covers frames 999 000 to 1 000 000.
        assertEquals(position - 500 * r60, map.positionAt(999_500.0), 1e-9)
    }

    @Test
    fun readersOnOtherThreadsSeeOnlyConsistentSegments() {
        // Every segment lies on one line, position = frame * rate, whatever its
        // start, so any torn read shows up as a point off the line. The rate is
        // a power of two, so the arithmetic is exact.
        val rate = 1.0 / 32_768
        val map = TempoMap(capacity = 16)
        map.begin(0.0, 0.0, rate)
        val failure = AtomicReference<String?>(null)
        val writer =
            thread {
                for (frame in 1L..2_000_000L) {
                    map.changeRateAtFrame(frame.toDouble(), rate)
                }
            }
        val readers =
            List(3) {
                thread {
                    // Past every segment, so each read extrapolates the newest one
                    // while the writer laps the ring under it.
                    val frame = 1e7
                    while (writer.isAlive && failure.get() == null) {
                        val position = map.positionAt(frame)
                        if (position != frame * rate) failure.set("position $position at frame $frame")
                    }
                }
            }
        writer.join()
        readers.forEach { it.join() }
        assertEquals(null, failure.get())
    }
}
