package dev.co508.drums.hits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HitQueueTest {
    @Test
    fun `hands hits over in order`() {
        val queue = HitQueue(capacity = 4)
        val out = DetectedHit()
        assertFalse(queue.poll(out))
        for (round in 0 until 3) {
            for (i in 0 until 3) assertTrue(queue.offer(100L * round + i, i + 0.5, -10f - i))
            for (i in 0 until 3) {
                assertTrue(queue.poll(out))
                assertEquals(100L * round + i, out.nanoTime)
                assertEquals(i + 0.5, out.inputFrame, 0.0)
                assertEquals(-10f - i, out.levelDb)
            }
            assertFalse(queue.poll(out))
        }
    }

    @Test
    fun `drops and counts hits when full`() {
        val queue = HitQueue(capacity = 2)
        assertTrue(queue.offer(1, 0.0, 0f))
        assertTrue(queue.offer(2, 0.0, 0f))
        assertFalse(queue.offer(3, 0.0, 0f))
        assertEquals(1L, queue.dropped)
        val out = DetectedHit()
        assertTrue(queue.poll(out))
        assertEquals(1L, out.nanoTime)
        queue.clear()
        assertFalse(queue.poll(out))
    }

    @Test
    fun `survives a producer and a consumer on two threads`() {
        val queue = HitQueue(capacity = 64)
        val total = 200_000
        val producer =
            Thread {
                var i = 0L
                while (i < total) if (queue.offer(i, i.toDouble(), 0f)) i++
            }
        producer.start()
        val out = DetectedHit()
        var expected = 0L
        while (expected < total) {
            if (queue.poll(out)) {
                assertEquals(expected, out.nanoTime)
                assertEquals(expected.toDouble(), out.inputFrame, 0.0)
                expected++
            }
        }
        producer.join()
    }
}
