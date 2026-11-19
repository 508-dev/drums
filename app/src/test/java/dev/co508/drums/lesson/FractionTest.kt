package dev.co508.drums.lesson

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FractionTest {
    private fun f(
        n: Long,
        d: Long = 1,
    ) = Fraction.of(n, d)

    @Test
    fun normalizes() {
        assertEquals(f(1, 2), f(3, 6))
        assertEquals(f(-1, 2), f(1, -2))
        assertEquals("1/3", f(2, 6).toString())
        assertEquals("4", f(8, 2).toString())
        assertEquals(Fraction.ZERO, f(0, 7))
    }

    @Test
    fun arithmeticIsExact() {
        val third = f(1, 3)
        assertEquals(Fraction.ONE, third + third + third)
        assertEquals(f(1, 6), f(1, 2) - third)
        assertEquals(f(1, 9), third * third)
        assertEquals(f(3, 2), f(1, 2) / third)
        assertEquals(f(2, 3), third * 2)
        assertEquals(f(1, 6), third / 2)
    }

    @Test
    fun comparesAndFloors() {
        assertTrue(f(1, 3) < f(1, 2))
        assertEquals(2L, f(7, 3).floor())
        assertEquals(-1L, f(-1, 3).floor())
        assertTrue(f(4, 2).isWhole)
        assertEquals(0.25, f(1, 4).toDouble(), 0.0)
    }
}
