package dev.co508.drums.scoring

import dev.co508.drums.playback.Pattern
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Speaker mode's last line of defence (DECISIONS.md, "Speaker Mode"): the
 * mic still hears a faint copy of every click through a real phone speaker,
 * and after calibration that copy lands exactly on the beat, where it would
 * count as a hit nobody played.
 *
 * The click's arrival is known to the sample. During the count-in, when the
 * player isn't playing yet, every onset at a click's arrival is the click
 * itself: the loudest of them is the leak's level. During the run, an onset
 * within [toleranceMillis] of a click's arrival and no louder than the leak
 * plus [marginDb] is dropped as the click's echo. A stick hit on the beat is
 * far louder than the leak, so it stays. Without a count-in there is nothing
 * to learn from, so speaker-mode scored runs always count in.
 *
 * Positions are in quarter notes, frames on the output clock (calibrated, as
 * hits are). One thread, the scoring session's.
 */
class ClickGuard(
    /** Click positions in one loop of the pattern. */
    private val clicks: DoubleArray,
    private val lengthQuarters: Double,
    /** The count-in's click positions (negative). */
    private val countIn: DoubleArray,
    private val sampleRate: Int,
    private val toleranceMillis: Double = DEFAULT_TOLERANCE_MILLIS,
    private val marginDb: Double = DEFAULT_MARGIN_DB,
) {
    /** The loudest click echo heard in the count-in, dBFS. */
    var leakDb = Double.NEGATIVE_INFINITY
        private set

    var echoesDropped = 0
        private set

    /** Whether the hit at [frame] with [levelDb] is the click's own echo, to be dropped. */
    fun isClickEcho(
        frame: Double,
        levelDb: Double,
        map: TimeMap,
    ): Boolean {
        val position = map.positionAt(frame)
        if (position.isNaN()) return false
        val click = nearestClick(position, frame, map) ?: return false
        if (abs(map.frameAt(click) - frame) > toleranceMillis * sampleRate / 1000.0) return false
        if (click < 0) {
            leakDb = max(leakDb, levelDb)
            echoesDropped++
            return true
        }
        if (levelDb > leakDb + marginDb) return false
        echoesDropped++
        return true
    }

    private fun nearestClick(
        position: Double,
        frame: Double,
        map: TimeMap,
    ): Double? {
        var best: Double? = null
        var bestDistance = Double.POSITIVE_INFINITY

        fun consider(c: Double) {
            val distance = abs(map.frameAt(c) - frame)
            if (distance.isFinite() && distance < bestDistance) {
                best = c
                bestDistance = distance
            }
        }
        for (c in countIn) consider(c)
        if (clicks.isNotEmpty() && position > -lengthQuarters) {
            val loop = floor(position / lengthQuarters).toLong()
            for (k in maxOf(0L, loop - 1)..loop + 1) for (c in clicks) consider(k * lengthQuarters + c)
        }
        return best
    }

    companion object {
        const val DEFAULT_TOLERANCE_MILLIS = 4.0
        const val DEFAULT_MARGIN_DB = 6.0

        fun of(
            pattern: Pattern,
            sampleRate: Int,
        ): ClickGuard =
            ClickGuard(
                clicks = pattern.clicks.map { it.position }.toDoubleArray(),
                lengthQuarters = pattern.lengthQuarters,
                countIn = DoubleArray(pattern.countInBeats) { (it - pattern.countInBeats) * pattern.beatQuarters },
                sampleRate = sampleRate,
            )
    }
}
