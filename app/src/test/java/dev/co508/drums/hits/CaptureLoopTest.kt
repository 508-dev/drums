package dev.co508.drums.hits

import dev.co508.drums.ALLOCATION_NOISE_BYTES
import dev.co508.drums.threadAllocatedBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLoopTest {
    private val rate = 48_000
    private val startNanos = 7_000_000_000L

    /** A recording played back as a capture stream whose frame N was captured at startNanos + N / rate. */
    private class FakeInput(
        private val samples: FloatArray,
        override val sampleRate: Int,
        private val startNanos: Long,
        var timestamps: Boolean = true,
        val failAt: Int = -1,
    ) : PcmInput {
        var position = 0
        var released = false
        var stopRequested = false
        var onEnd: () -> Unit = {}

        override fun startRecording() = Unit

        override fun read(
            buffer: FloatArray,
            frames: Int,
        ): Int {
            if (failAt in 0..position) return -6
            if (position >= samples.size) {
                onEnd()
                return 0
            }
            val n = minOf(frames, samples.size - position)
            samples.copyInto(buffer, 0, position, position + n)
            position += n
            return n
        }

        override fun timestamp(out: InputTimestamp): Boolean {
            if (!timestamps) return false
            out.framePosition = position.toLong()
            out.nanoTime = startNanos + position * 1_000_000_000L / sampleRate
            return true
        }

        override fun stop() {
            stopRequested = true
        }

        override fun release() {
            released = true
        }
    }

    private fun signal(): Pair<FloatArray, List<Double>> {
        val s = SyntheticSignals(rate, seconds = 2.0, seed = 41)
        s.whiteNoise(-65.0)
        val onsets = (0 until 8).map { s.padHit(0.25 + it * 0.2 + it * 0.00001, 0.3) }
        return s.toFloats() to onsets
    }

    private fun drain(queue: HitQueue): List<DetectedHit> {
        val hits = mutableListOf<DetectedHit>()
        while (true) {
            val hit = DetectedHit()
            if (!queue.poll(hit)) return hits
            hits += hit
        }
    }

    private fun loop(
        input: FakeInput,
        queue: HitQueue,
        tap: CaptureTap? = null,
        now: () -> Long = { 0L },
    ): CaptureLoop {
        val loop = CaptureLoop(input, OnsetDetector(rate), InputClock(rate), queue, 192, { tap }, now)
        input.onEnd = { loop.requestStop() }
        return loop
    }

    @Test
    fun `queues hits timed on the capture clock`() {
        val (samples, onsets) = signal()
        val input = FakeInput(samples, rate, startNanos)
        val queue = HitQueue()
        loop(input, queue).run()
        val hits = drain(queue)
        assertEquals(onsets.size, hits.size)
        for ((onset, hit) in onsets.zip(hits)) {
            val trueNanos = startNanos + onset * 1e9 / rate
            // The detector's own bias is about 0.3 ms.
            assertEquals(trueNanos + 300_000, hit.nanoTime.toDouble(), 400_000.0)
            assertEquals(onset, hit.inputFrame, 0.03 * rate / 1000 * 20)
        }
        assertTrue(input.released)
    }

    @Test
    fun `without stream timestamps, hits are timed from when reads return`() {
        val (samples, onsets) = signal()
        val input = FakeInput(samples, rate, startNanos, timestamps = false)
        val queue = HitQueue()
        // Each read "returns" 3 ms after its last frame was captured.
        loop(input, queue, now = { startNanos + input.position * 1_000_000_000L / rate + 3_000_000 }).run()
        val hits = drain(queue)
        assertEquals(onsets.size, hits.size)
        for ((onset, hit) in onsets.zip(hits)) {
            val trueNanos = startNanos + onset * 1e9 / rate
            assertEquals(trueNanos + 3_300_000, hit.nanoTime.toDouble(), 400_000.0)
        }
    }

    @Test
    fun `the tap sees every frame, in order, before detection`() {
        val (samples, _) = signal()
        val input = FakeInput(samples, rate, startNanos)
        val copy = FloatArray(samples.size)
        var expectedFrame = 0L
        val tap =
            CaptureTap { block, count, first ->
                assertEquals(expectedFrame, first)
                block.copyInto(copy, first.toInt(), 0, count)
                expectedFrame += count
            }
        loop(input, HitQueue(), tap).run()
        assertEquals(samples.size.toLong(), expectedFrame)
        assertTrue(samples.contentEquals(copy))
    }

    @Test
    fun `a read error ends capture and is reported`() {
        val (samples, _) = signal()
        val input = FakeInput(samples, rate, startNanos, failAt = 9_600)
        val loop = loop(input, HitQueue())
        loop.run()
        assertEquals(-6, loop.readError)
        assertTrue(input.released)
    }

    @Test
    fun `capturing doesn't allocate`() {
        val allocated = threadAllocatedBytes() ?: return
        val (samples, _) = signal()
        val long = FloatArray(samples.size * 20) { samples[it % samples.size] }
        val input = FakeInput(long, rate, startNanos)
        // Measure from a quarter of the way in, once the JIT has warmed up.
        val warmUp = long.size / 4L
        var before = 0L
        val tap = CaptureTap { _, count, first -> if (warmUp in first until first + count) before = allocated() }
        // A full queue drops hits rather than allocating, so its size doesn't matter.
        loop(input, HitQueue(), tap).run()
        val bytes = allocated() - before
        assertTrue("$bytes bytes allocated while capturing", bytes < ALLOCATION_NOISE_BYTES)
    }
}
