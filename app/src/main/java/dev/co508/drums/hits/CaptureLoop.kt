package dev.co508.drums.hits

/** A frame of the input stream and when it was captured. Mutable, so it can be reused. */
class InputTimestamp {
    var framePosition: Long = 0
    var nanoTime: Long = 0
}

/** Small AudioRecord boundary, so the capture loop is JVM-testable. */
internal interface PcmInput {
    val sampleRate: Int

    fun startRecording()

    /** Blocks until [frames] mono samples are read into [buffer]; returns the count, or a negative error. */
    fun read(
        buffer: FloatArray,
        frames: Int,
    ): Int

    /** When a captured frame reached the input, on CLOCK_MONOTONIC; false if the device can't say. */
    fun timestamp(out: InputTimestamp): Boolean

    /** Unblocks [read] from another thread. Never releases. */
    fun stop()

    fun release()
}

/**
 * Sees every captured block, on the capture thread, before onset detection.
 * Must not allocate, block or take locks shared with slow code. Debug builds
 * use it to record the raw microphone to a WAV file.
 */
fun interface CaptureTap {
    fun onBlock(
        samples: FloatArray,
        count: Int,
        firstFrame: Long,
    )
}

/**
 * The capture thread's loop: read a block, refresh the input clock, show the
 * block to the tap, find its onsets and queue them as hits on the
 * [System.nanoTime] clock. Allocation-free once running, like the mixer.
 * The thread that calls [run] owns the input and releases it.
 */
internal class CaptureLoop(
    private val input: PcmInput,
    private val detector: OnsetDetector,
    private val clock: InputClock,
    private val queue: HitQueue,
    private val blockFrames: Int,
    private val tap: () -> CaptureTap?,
    private val nanoTime: () -> Long = System::nanoTime,
    private val log: (String) -> Unit = {},
) {
    @Volatile private var stopped = false

    @Volatile
    var framesCaptured = 0L
        private set

    /** The input's negative read error, if capture failed; 0 otherwise. */
    @Volatile
    var readError = 0
        private set

    private val buffer = FloatArray(blockFrames)
    private val timestamp = InputTimestamp()
    private val timestampInterval = maxOf(1, input.sampleRate / TIMESTAMPS_PER_SECOND)
    private var nextTimestampFrame = 0L
    private var lastExactFrame = Long.MIN_VALUE / 2

    private val sink =
        OnsetSink { frame, levelDb ->
            queue.offer(clock.nanoTimeAt(frame), frame, levelDb)
        }

    fun requestStop() {
        stopped = true
        try {
            input.stop()
        } catch (_: RuntimeException) {
            // Already dead; the worker still releases it.
        }
    }

    fun run() {
        try {
            if (stopped) return
            clock.reset()
            input.startRecording()
            var frames = 0L
            while (!stopped) {
                val n = input.read(buffer, blockFrames)
                if (n < 0) {
                    if (!stopped) {
                        readError = n
                        log("capture read error=$n")
                    }
                    return
                }
                if (n == 0) continue
                val end = frames + n
                if (end >= nextTimestampFrame) {
                    refreshClock(end)
                    nextTimestampFrame = end + timestampInterval
                }
                tap()?.onBlock(buffer, n, frames)
                detector.process(buffer, 0, n, frames, sink)
                frames = end
                framesCaptured = frames
            }
        } finally {
            try {
                input.release()
            } catch (error: RuntimeException) {
                log("capture release failed: $error")
            }
        }
    }

    private fun refreshClock(endFrame: Long) {
        if (input.timestamp(timestamp)) {
            clock.update(timestamp.framePosition, timestamp.nanoTime, exact = true)
            lastExactFrame = endFrame
        } else if (endFrame - lastExactFrame > input.sampleRate) {
            // No stream timestamps for a second (or ever): estimate. The read just
            // returned, so its last frame was captured no later than now. A brief
            // gap in real timestamps just extrapolates, rather than jumping to an
            // estimate biased by the input latency.
            clock.update(endFrame, nanoTime(), exact = false)
        }
    }

    private companion object {
        const val TIMESTAMPS_PER_SECOND = 10
    }
}
