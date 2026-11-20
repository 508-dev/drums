package dev.co508.drums.hits

import dev.co508.drums.playback.AudioClock
import dev.co508.drums.playback.TempoMap

/**
 * Puts a hit on the output's audio clock and the transport's musical timeline,
 * so hits and notes share one time base (DECISIONS.md, "One Audio Clock").
 *
 * A hit heard by the mic at time T lines up with the output frame that was
 * being heard at T, less [latencyNanos]: whatever the two streams' timestamps
 * don't account for (air, converters, Bluetooth, the player's ear). Calibration
 * (#29) measures it per output route; until then it is 0.
 */
class HitTimeline(
    private val clock: AudioClock,
    private val tempoMap: TempoMap,
) {
    @Volatile
    var latencyNanos = 0L

    /** The output frame (fractional) the hit lines up with, or NaN while the output clock isn't anchored. */
    fun outputFrame(hitNanoTime: Long): Double {
        if (hitNanoTime == Long.MIN_VALUE) return Double.NaN
        return clock.frameAt(hitNanoTime - latencyNanos)
    }

    /** The musical position, in quarter notes, the hit lines up with, or NaN. */
    fun position(hitNanoTime: Long): Double {
        val frame = outputFrame(hitNanoTime)
        return if (frame.isNaN()) Double.NaN else tempoMap.positionAt(frame)
    }
}
