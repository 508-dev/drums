package dev.co508.drums.playback

import kotlin.math.max

/**
 * The map between output frames and musical position, in quarter notes.
 *
 * It is piecewise linear: a list of segments, each starting at a frame and a
 * position and moving at a constant rate (quarter notes per frame). A tempo
 * change starts a new segment exactly where the previous one has reached, so
 * position stays continuous across tempo changes. A rate of 0 holds the
 * position still (stopped). Frames are doubles: a tempo change at a loop
 * boundary falls between two frames.
 *
 * Segments are grouped into epochs. [begin] starts a new epoch, for a new
 * playback or a new output stream (whose frames restart at 0). Readers only
 * consult the latest epoch, and frames before its start (or before the oldest
 * segment still kept) map to that segment's start position.
 *
 * One thread writes (the mixer thread); any thread may read. Segments are
 * written into a ring and published by a volatile count, so a reader sees only
 * complete segments. Readers use the most recent segments, and retry if the
 * writer laps them while they read, which would take [capacity] changes in a
 * few microseconds.
 */
class TempoMap(
    val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity >= 4) { "capacity must be at least 4" }
    }

    private val startFrame = DoubleArray(capacity)
    private val startPosition = DoubleArray(capacity)
    private val rate = DoubleArray(capacity)
    private val epoch = IntArray(capacity)

    /** Segments ever written. Volatile: publishes each segment to readers. */
    @Volatile
    private var count = 0L
    private var currentEpoch = 0

    /** Whether any segment has been written. */
    val isEmpty: Boolean get() = count == 0L

    // ---- Writer (one thread) ----

    /** Starts a new epoch at [frame], [position], moving at [quarterNotesPerFrame]. */
    fun begin(
        frame: Double,
        position: Double,
        quarterNotesPerFrame: Double,
    ) {
        currentEpoch++
        append(frame, position, quarterNotesPerFrame)
    }

    /** Changes the rate from [frame] on, at the position reached there. Returns that position. */
    fun changeRateAtFrame(
        frame: Double,
        quarterNotesPerFrame: Double,
    ): Double {
        val last = lastIndex()
        require(frame >= startFrame[last]) { "frame $frame is before the latest segment" }
        val position = startPosition[last] + (frame - startFrame[last]) * rate[last]
        append(frame, position, quarterNotesPerFrame)
        return position
    }

    /**
     * Changes the rate from [position] on, at the frame where it is reached, and
     * returns that frame. If the latest segment starts after [position] (by less
     * than a frame, when a block-start change just passed it), the change starts
     * with that segment instead.
     */
    fun changeRateAtPosition(
        position: Double,
        quarterNotesPerFrame: Double,
    ): Double {
        val last = lastIndex()
        val frame: Double
        val at: Double
        if (position <= startPosition[last]) {
            frame = startFrame[last]
            at = startPosition[last]
        } else {
            check(rate[last] > 0.0) { "position $position is never reached" }
            frame = startFrame[last] + (position - startPosition[last]) / rate[last]
            at = position
        }
        append(frame, at, quarterNotesPerFrame)
        return frame
    }

    private fun lastIndex(): Int {
        check(count > 0) { "begin() first" }
        return ((count - 1) % capacity).toInt()
    }

    private fun append(
        frame: Double,
        position: Double,
        quarterNotesPerFrame: Double,
    ) {
        require(quarterNotesPerFrame >= 0.0) { "rate must not be negative" }
        val i = (count % capacity).toInt()
        startFrame[i] = frame
        startPosition[i] = position
        rate[i] = quarterNotesPerFrame
        epoch[i] = currentEpoch
        count++ // Volatile write: publishes the segment.
    }

    // ---- Readers (any thread) ----

    /**
     * Position in quarter notes at [frame], or NaN before [begin]. Frames before
     * the current epoch's start map to its start position.
     */
    fun positionAt(frame: Double): Double {
        while (true) {
            val n = count
            if (n == 0L) return Double.NaN
            val i = findByFrame(n, frame)
            val s = (i % capacity).toInt()
            val position =
                if (frame < startFrame[s]) startPosition[s] else startPosition[s] + (frame - startFrame[s]) * rate[s]
            if (!lapped(i)) return position
        }
    }

    /** Quarter notes per frame at [frame], or NaN before [begin]. */
    fun rateAt(frame: Double): Double {
        while (true) {
            val n = count
            if (n == 0L) return Double.NaN
            val i = findByFrame(n, frame)
            val r = rate[(i % capacity).toInt()]
            if (!lapped(i)) return r
        }
    }

    /**
     * The exact (fractional) frame at which [position] is reached in the current
     * epoch, or NaN before [begin]. Positive infinity if it is never reached
     * because the map is stopped before it.
     */
    fun frameAt(position: Double): Double {
        while (true) {
            val n = count
            if (n == 0L) return Double.NaN
            val i = findByPosition(n, position)
            val s = (i % capacity).toInt()
            val frame =
                when {
                    position <= startPosition[s] -> startFrame[s]
                    rate[s] == 0.0 -> Double.POSITIVE_INFINITY
                    else -> startFrame[s] + (position - startPosition[s]) / rate[s]
                }
            if (!lapped(i)) return frame
        }
    }

    /** The index of the newest segment in the latest epoch starting at or before [frame], else its oldest. */
    private fun findByFrame(
        n: Long,
        frame: Double,
    ): Long {
        val newest = n - 1
        val latestEpoch = epoch[(newest % capacity).toInt()]
        var i = newest
        val oldest = oldestReadable(n)
        while (i > oldest) {
            val s = (i % capacity).toInt()
            if (startFrame[s] <= frame) return i
            if (epoch[((i - 1) % capacity).toInt()] != latestEpoch) return i
            i--
        }
        return i
    }

    /** As [findByFrame], by position. Positions never decrease within an epoch. */
    private fun findByPosition(
        n: Long,
        position: Double,
    ): Long {
        val newest = n - 1
        val latestEpoch = epoch[(newest % capacity).toInt()]
        var i = newest
        val oldest = oldestReadable(n)
        while (i > oldest) {
            val s = (i % capacity).toInt()
            if (startPosition[s] <= position) return i
            if (epoch[((i - 1) % capacity).toInt()] != latestEpoch) return i
            i--
        }
        return i
    }

    // The writer may be filling the slot after the newest, which is the oldest
    // slot in the ring, so readers never touch it.
    private fun oldestReadable(n: Long): Long = max(0L, n - capacity + 1)

    /** Whether the writer has started overwriting segment [i] since it was read. */
    private fun lapped(i: Long): Boolean = count - i >= capacity

    companion object {
        const val DEFAULT_CAPACITY = 256
    }
}
