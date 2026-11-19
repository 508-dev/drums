package dev.co508.drums.playback

import dev.co508.drums.audio.OutputTimestamp
import dev.co508.drums.audio.OutputTimestampSource
import kotlin.math.abs

/**
 * "Now" on the audio clock: which output frame is being heard at a given
 * moment. See DECISIONS.md, "One Audio Clock".
 *
 * It is anchored on the output stream's own timestamps (a frame and the time
 * it was presented), refreshed every [refreshIntervalNanos], and extrapolated
 * between them at the nominal sample rate. There is no separate timer, so the
 * visuals can't drift from the sound.
 *
 * Times are [System.nanoTime] values (CLOCK_MONOTONIC), the same base as
 * Choreographer/Compose frame times and as `AudioRecord` timestamps. The UI
 * passes its frame time to [visualFrameAt]; hit detection (M5) will map a mic
 * timestamp to an output frame with [frameAt], then to a musical position with
 * the transport's tempo map.
 *
 * Thread-safe; meant to be called once per UI frame.
 */
class AudioClock(
    private val source: OutputTimestampSource,
    private val sampleRate: Int,
    private val refreshIntervalNanos: Long = DEFAULT_REFRESH_INTERVAL_NANOS,
    private val systemNanoTime: () -> Long = System::nanoTime,
) {
    /**
     * The sound/visual offset setting, in milliseconds. Positive values delay
     * the visuals, for outputs that play later than they report (for example
     * some Bluetooth headsets). Applies to [visualFrameAt] only.
     */
    @Volatile
    var visualOffsetMillis = 0.0

    private val timestamp = OutputTimestamp()
    private var hasAnchor = false
    private var anchorFrame = 0L
    private var anchorNanos = 0L
    private var lastRefreshNanos = 0L
    private var refreshed = false

    private var updates = 0L
    private var lastErrorMillis = 0.0
    private var maxAbsErrorMillis = 0.0
    private var sumAbsErrorMillis = 0.0

    /**
     * The output frame heard at [nanoTime], as a fractional frame, or NaN while
     * the stream has not reported a timestamp yet.
     */
    @Synchronized
    fun frameAt(nanoTime: Long): Double {
        refreshIfDue()
        if (!hasAnchor) return Double.NaN
        return anchorFrame + (nanoTime - anchorNanos) * sampleRate / NANOS_PER_SECOND
    }

    /** The frame to draw for a UI frame shown at [frameTimeNanos], after the sound/visual offset. */
    fun visualFrameAt(frameTimeNanos: Long): Double =
        frameAt(frameTimeNanos - (visualOffsetMillis * NANOS_PER_MILLI).toLong())

    /** Forgets the anchor and the statistics. Call when a new output stream starts. */
    @Synchronized
    fun reset() {
        hasAnchor = false
        refreshed = false
        resetStats()
    }

    /** Starts the jitter statistics afresh, keeping the anchor. */
    @Synchronized
    fun resetStats() {
        updates = 0
        lastErrorMillis = 0.0
        maxAbsErrorMillis = 0.0
        sumAbsErrorMillis = 0.0
    }

    /**
     * How far each new timestamp was from where extrapolating the previous one
     * put it: the audio clock's jitter, for the diagnostics screen.
     */
    @Synchronized
    fun stats(): AudioClockStats =
        AudioClockStats(
            anchored = hasAnchor,
            updates = updates,
            lastErrorMillis = lastErrorMillis,
            maxAbsErrorMillis = maxAbsErrorMillis,
            meanAbsErrorMillis = if (updates > 0) sumAbsErrorMillis / updates else 0.0,
        )

    private fun refreshIfDue() {
        val now = systemNanoTime()
        if (refreshed && now - lastRefreshNanos < refreshIntervalNanos) return
        refreshed = true
        lastRefreshNanos = now
        // On failure keep the old anchor: timestamps can be briefly unavailable
        // mid-stream, and a new stream calls reset().
        if (!source.outputTimestamp(timestamp)) return
        if (hasAnchor) {
            val predicted = anchorFrame + (timestamp.nanoTime - anchorNanos) * sampleRate / NANOS_PER_SECOND
            val errorMillis = (timestamp.framePosition - predicted) * MILLIS_PER_SECOND / sampleRate
            updates++
            lastErrorMillis = errorMillis
            maxAbsErrorMillis = maxOf(maxAbsErrorMillis, abs(errorMillis))
            sumAbsErrorMillis += abs(errorMillis)
        }
        hasAnchor = true
        anchorFrame = timestamp.framePosition
        anchorNanos = timestamp.nanoTime
    }

    companion object {
        const val DEFAULT_REFRESH_INTERVAL_NANOS = 100_000_000L
        private const val NANOS_PER_SECOND = 1e9
        private const val NANOS_PER_MILLI = 1e6
        private const val MILLIS_PER_SECOND = 1e3
    }
}

data class AudioClockStats(
    val anchored: Boolean,
    /** Timestamps compared against the previous one's extrapolation. */
    val updates: Long,
    /** Positive when the stream was ahead of the extrapolation. */
    val lastErrorMillis: Double,
    val maxAbsErrorMillis: Double,
    val meanAbsErrorMillis: Double,
)
