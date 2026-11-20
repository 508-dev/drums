package dev.co508.drums.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickGuardTest {
    private val ms = 48.0

    /** 120 BPM, position 0 at frame 96 000 after a 4-beat count-in from frame 0. */
    private val map =
        object : TimeMap {
            override fun frameAt(position: Double) = 96_000 + position * 24_000

            override fun positionAt(frame: Double) = (frame - 96_000) / 24_000
        }

    private fun guard() =
        ClickGuard(
            clicks = doubleArrayOf(0.0, 1.0, 2.0, 3.0),
            lengthQuarters = 4.0,
            countIn = doubleArrayOf(-4.0, -3.0, -2.0, -1.0),
            sampleRate = 48_000,
        )

    @Test
    fun `learns the click's leak in the count-in and drops its echoes`() {
        val g = guard()
        // The count-in's clicks come back faintly, about 1 ms late.
        for (k in 0 until 4) assertTrue(g.isClickEcho(k * 24_000 + 1 * ms, -62.0 - k, map))
        assertEquals(-62.0, g.leakDb, 0.0)
        // In the run: an echo at the leak's level is dropped…
        assertTrue(g.isClickEcho(96_000 + 24_000 + 0.5 * ms, -63.0, map))
        // …a real hit on the beat, much louder, is kept…
        assertFalse(g.isClickEcho(96_000 + 48_000 + 1 * ms, -38.0, map))
        // …and nothing away from a click is touched, however quiet.
        assertFalse(g.isClickEcho(96_000 + 12_000.0, -70.0, map))
        // A quiet sound just outside the 4 ms tolerance isn't a click either.
        assertFalse(g.isClickEcho(96_000 + 72_000 + 6 * ms, -64.0, map))
        assertEquals(5, g.echoesDropped)
    }

    @Test
    fun `with nothing heard in the count-in, nothing is dropped later`() {
        val g = guard()
        assertFalse(g.isClickEcho(96_000 + 24_000.0, -65.0, map))
        assertEquals(0, g.echoesDropped)
    }

    @Test
    fun `works in later loops`() {
        val g = guard()
        g.isClickEcho(0.0, -60.0, map)
        // Loop 3, beat 2.
        assertTrue(g.isClickEcho(96_000 + (12 + 2) * 24_000.0, -61.0, map))
    }
}
