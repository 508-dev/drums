package dev.co508.drums.hits

import kotlin.math.abs

/**
 * Maps capture frames to [System.nanoTime] (CLOCK_MONOTONIC), the time base
 * of the output's [dev.co508.drums.playback.AudioClock]. See DECISIONS.md,
 * "One Audio Clock" and "Mic Timestamps Are Smoothed, Not Trusted Raw".
 *
 * It is anchored on (frame, time) pairs, normally the input stream's own
 * timestamps (`AudioRecord.getTimestamp`), refreshed a few times a second, and
 * extrapolated between them at the nominal rate. Each new pair moves the anchor
 * only a fraction ([smoothing]) of the way towards itself, so timestamp jitter
 * is averaged away while slow drift between the two clocks is still followed.
 * A pair further than [resetNanos] from the prediction re-anchors at once.
 *
 * Some devices give no input timestamps. Then the pairs are *estimates*: the
 * last frame of each read and the moment the read returned. A read can only
 * return late, never early, so estimates follow the earliest pair (a lower
 * envelope) and creep up only slowly. They carry the input latency as a
 * constant bias, which calibration (#29) removes along with everything else.
 *
 * One thread updates and reads it (the capture thread), without allocating;
 * [stats] may be called from any thread.
 */
class InputClock(
    private val sampleRate: Int,
    private val smoothing: Double = DEFAULT_SMOOTHING,
    private val estimateCreep: Double = DEFAULT_ESTIMATE_CREEP,
    private val resetNanos: Long = DEFAULT_RESET_NANOS,
) {
    private var anchored = false
    private var exact = false
    private var anchorFrame = 0L
    private var anchorNanos = 0.0

    // Written by the capture thread, read by stats(): volatile, never allocated.
    @Volatile private var publishedAnchored = false

    @Volatile private var publishedExact = false

    @Volatile private var updates = 0L

    @Volatile private var resets = 0L

    @Volatile private var maxAbsErrorMillis = 0.0

    @Volatile private var sumAbsErrorMillis = 0.0

    @Volatile private var lastErrorMillis = 0.0

    /** Whether [nanoTimeAt] can answer yet. */
    val isAnchored: Boolean get() = anchored

    /**
     * A new (frame, time) pair: [frame] was captured at [nanoTime]. [exact] is
     * true for the stream's own timestamps and false for read-time estimates.
     */
    fun update(
        frame: Long,
        nanoTime: Long,
        exact: Boolean,
    ) {
        if (!anchored || exact != this.exact) {
            anchor(frame, nanoTime.toDouble(), exact)
            return
        }
        val predicted = predict(frame)
        val error = nanoTime - predicted
        updates++
        lastErrorMillis = error / NANOS_PER_MILLI
        maxAbsErrorMillis = maxOf(maxAbsErrorMillis, abs(lastErrorMillis))
        sumAbsErrorMillis += abs(lastErrorMillis)
        val corrected =
            when {
                abs(error) > resetNanos -> {
                    resets++
                    nanoTime.toDouble()
                }
                exact -> predicted + smoothing * error
                error < 0 -> nanoTime.toDouble()
                else -> predicted + estimateCreep * error
            }
        anchor(frame, corrected, exact)
    }

    /** When input [frame] (fractional) was captured, in nanoseconds; [Long.MIN_VALUE] before the first [update]. */
    fun nanoTimeAt(frame: Double): Long {
        if (!anchored) return Long.MIN_VALUE
        return (anchorNanos + (frame - anchorFrame) * NANOS_PER_SECOND / sampleRate).toLong()
    }

    fun reset() {
        anchored = false
        updates = 0
        resets = 0
        maxAbsErrorMillis = 0.0
        sumAbsErrorMillis = 0.0
        lastErrorMillis = 0.0
        publishedAnchored = false
    }

    private fun predict(frame: Long): Double = anchorNanos + (frame - anchorFrame) * NANOS_PER_SECOND / sampleRate

    private fun anchor(
        frame: Long,
        nanos: Double,
        exact: Boolean,
    ) {
        anchored = true
        this.exact = exact
        anchorFrame = frame
        anchorNanos = nanos
        publishedAnchored = true
        publishedExact = exact
    }

    /** A snapshot for diagnostics. Any thread. */
    fun stats(): InputClockStats =
        InputClockStats(
            anchored = publishedAnchored,
            exactTimestamps = publishedAnchored && publishedExact,
            updates = updates,
            resets = resets,
            lastErrorMillis = lastErrorMillis,
            maxAbsErrorMillis = maxAbsErrorMillis,
            meanAbsErrorMillis = if (updates > 0) sumAbsErrorMillis / updates else 0.0,
        )

    companion object {
        const val DEFAULT_SMOOTHING = 0.1
        const val DEFAULT_ESTIMATE_CREEP = 0.01
        const val DEFAULT_RESET_NANOS = 20_000_000L
        private const val NANOS_PER_SECOND = 1e9
        private const val NANOS_PER_MILLI = 1e6
    }
}

/** How the input timestamps behave, for the diagnostics screen. */
data class InputClockStats(
    val anchored: Boolean = false,
    /** True when anchored on the stream's own timestamps; false for read-time estimates. */
    val exactTimestamps: Boolean = false,
    /** Pairs compared against the previous anchor's extrapolation. */
    val updates: Long = 0,
    /** Pairs so far off that the clock re-anchored on them outright. */
    val resets: Long = 0,
    /** Positive when the pair was later than predicted. */
    val lastErrorMillis: Double = 0.0,
    val maxAbsErrorMillis: Double = 0.0,
    val meanAbsErrorMillis: Double = 0.0,
)
