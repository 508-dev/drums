package dev.co508.drums.hits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class InputClockTest {
    private val rate = 48_000
    private val ms = 1_000_000L

    /** Nanoseconds at which [frame] truly was captured, in these tests. */
    private fun truth(frame: Long) = 5_000 * ms + frame * 1_000_000_000L / rate

    @Test
    fun `is unanchored until the first pair`() {
        val clock = InputClock(rate)
        assertEquals(Long.MIN_VALUE, clock.nanoTimeAt(0.0))
        assertFalse(clock.stats().anchored)
    }

    @Test
    fun `extrapolates both ways from the anchor, to the fraction of a frame`() {
        val clock = InputClock(rate)
        clock.update(48_000, truth(48_000), exact = true)
        assertEquals(truth(48_000) + 10 * ms, clock.nanoTimeAt(48_480.0))
        assertEquals(truth(48_000) - 1 * ms, clock.nanoTimeAt(47_952.0))
        assertEquals(truth(48_000) + 10_416, clock.nanoTimeAt(48_000.5))
        assertTrue(clock.stats().exactTimestamps)
    }

    @Test
    fun `averages away timestamp jitter`() {
        val clock = InputClock(rate)
        val random = Random(1)
        var worst = 0L
        for (i in 0 until 600) {
            val frame = i * 4_800L
            // ±1 ms of jitter on every timestamp.
            clock.update(frame, truth(frame) + ((random.nextDouble() * 2 - 1) * ms).toLong(), exact = true)
            if (i > 50) worst = maxOf(worst, abs(clock.nanoTimeAt(frame + 2_400.0) - truth(frame + 2_400)))
        }
        // Raw timestamps are up to 1 ms off; the smoothed clock stays well inside that.
        assertTrue("worst ${worst / 1e6} ms", worst < 600_000)
        assertEquals(0L, clock.stats().resets)
    }

    @Test
    fun `follows slow drift between the clocks`() {
        val clock = InputClock(rate)

        // The capture clock runs 100 ppm fast against nanoTime.
        fun drifting(frame: Long) = 5_000 * ms + (frame * 1_000_000_000L / (rate * 1.0001)).toLong()
        for (i in 0 until 3_000) clock.update(i * 4_800L, drifting(i * 4_800L), exact = true)
        val frame = 3_000 * 4_800L
        assertEquals(drifting(frame).toDouble(), clock.nanoTimeAt(frame.toDouble()).toDouble(), 100_000.0)
    }

    @Test
    fun `a jump re-anchors at once`() {
        val clock = InputClock(rate)
        clock.update(0, truth(0), exact = true)
        clock.update(4_800, truth(4_800) + 50 * ms, exact = true)
        assertEquals(truth(4_800) + 50 * ms, clock.nanoTimeAt(4_800.0))
        assertEquals(1L, clock.stats().resets)
    }

    @Test
    fun `estimates follow the earliest read time`() {
        val clock = InputClock(rate)
        val random = Random(2)
        // Reads return 5 ms after capture, plus up to 4 ms of scheduling lateness.
        for (i in 0 until 400) {
            val frame = i * 4_800L
            clock.update(frame, truth(frame) + 5 * ms + (random.nextDouble() * 4 * ms).toLong(), exact = false)
        }
        val error = clock.nanoTimeAt(400 * 4_800.0) - truth(400 * 4_800L)
        assertTrue("estimate off by ${error / 1e6} ms", error in 4 * ms..6 * ms)
        assertFalse(clock.stats().exactTimestamps)
    }

    @Test
    fun `switching from estimates to real timestamps re-anchors`() {
        val clock = InputClock(rate)
        clock.update(0, truth(0) + 8 * ms, exact = false)
        clock.update(4_800, truth(4_800), exact = true)
        assertEquals(truth(4_800), clock.nanoTimeAt(4_800.0))
    }
}
