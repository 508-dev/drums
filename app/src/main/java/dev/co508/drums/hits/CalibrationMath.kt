package dev.co508.drums.hits

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** How a route's delay was measured. See DECISIONS.md, "Calibration Per Output Route". */
enum class CalibrationMethod {
    /** The phone plays clicks through its speaker and listens to itself. */
    LOOPBACK,

    /** The player taps along to clicks on the pad. Works on any output. */
    TAP_ALONG,
}

sealed interface CalibrationOutcome {
    /**
     * [latencyMillis] is how much later the mic hears a sound than the output
     * clock says it was heard: what scoring subtracts from every hit.
     */
    data class Measured(
        val latencyMillis: Double,
        /** Standard deviation of the offsets used. */
        val spreadMillis: Double,
        val used: Int,
        val expected: Int,
    ) : CalibrationOutcome

    data class Failed(
        val reason: Reason,
        val heard: Int,
        val expected: Int,
        val spreadMillis: Double = Double.NaN,
    ) : CalibrationOutcome

    enum class Reason {
        /** Not one sound in the windows: muted, too quiet, or no microphone signal. */
        NOT_HEARD,

        /** Too few of the clicks (or taps) were heard. */
        TOO_FEW,

        /** Heard, but the offsets disagree too much to trust. */
        INCONSISTENT,
    }
}

/**
 * Turns the output frames at which clicks were due and the output frames at
 * which the mic heard something into one delay, robustly. Pure; frames are
 * fractional output frames from the same clock.
 */
object CalibrationMath {
    /** A loopback offset counts if it lies this close to the median. */
    const val LOOPBACK_TOLERANCE_MILLIS = 2.0

    /** Loopback needs this share of the clicks heard and consistent. */
    const val LOOPBACK_MIN_SHARE = 0.75

    /** Taps further than this (robust SD) from each other are not a usable run. */
    const val TAP_MAX_SPREAD_MILLIS = 25.0
    const val TAP_MIN_COUNT = 10

    /**
     * The speaker loopback. Every sound heard from 5 ms before a click until
     * shortly before the next is a candidate delay for that click. The delay is
     * the **earliest** one that at least [LOOPBACK_MIN_SHARE] of the clicks
     * agree on to within [LOOPBACK_TOLERANCE_MILLIS]: random room sounds can't
     * agree like that, and a later echo of the click loses to the direct sound.
     * The result is the mean of the agreeing offsets, one per click.
     */
    fun loopback(
        clicks: DoubleArray,
        hits: DoubleArray,
        sampleRate: Int,
    ): CalibrationOutcome {
        val perMilli = sampleRate / 1000.0
        val interval = smallestGap(clicks)
        val latest = min(interval - 50 * perMilli, 500 * perMilli)
        val candidates =
            clicks.map { click ->
                hits.filter { it - click >= -5 * perMilli && it - click < latest }.map { (it - click) / perMilli }
            }
        val heard = candidates.count { it.isNotEmpty() }
        if (heard == 0) return CalibrationOutcome.Failed(CalibrationOutcome.Reason.NOT_HEARD, 0, clicks.size)
        val needed = (clicks.size * LOOPBACK_MIN_SHARE).toInt()
        if (heard < needed) return CalibrationOutcome.Failed(CalibrationOutcome.Reason.TOO_FEW, heard, clicks.size)

        fun agreeing(delay: Double): List<Double> =
            candidates.mapNotNull { offsets ->
                offsets.filter { abs(it - delay) <= LOOPBACK_TOLERANCE_MILLIS }.minByOrNull { abs(it - delay) }
            }
        for (delay in candidates.flatten().sorted()) {
            var agree = agreeing(delay)
            if (agree.size < needed) continue
            // Centre on the cluster, then take the clicks that agree with its centre.
            agree = agreeing(median(agree))
            if (agree.size < needed) continue
            return CalibrationOutcome.Measured(agree.average(), standardDeviation(agree), agree.size, clicks.size)
        }
        return CalibrationOutcome.Failed(
            CalibrationOutcome.Reason.INCONSISTENT,
            heard,
            clicks.size,
            robustSpread(candidates.mapNotNull { it.minOrNull() }),
        )
    }

    /**
     * Tap-along: each tap belongs to the nearest click within half a beat, the
     * first tap per click counts, and the delay is the median. People wander
     * by a few ms, so the run needs [TAP_MIN_COUNT] taps whose robust spread
     * (1.4826 × median absolute deviation) is at most [TAP_MAX_SPREAD_MILLIS];
     * taps more than three spreads (at least 10 ms) out are left out.
     */
    fun tapAlong(
        clicks: DoubleArray,
        hits: DoubleArray,
        sampleRate: Int,
    ): CalibrationOutcome {
        val perMilli = sampleRate / 1000.0
        val half = smallestGap(clicks) / 2
        val perClick = arrayOfNulls<Double>(clicks.size)
        for (hit in hits.sorted()) {
            var nearest = -1
            for (i in clicks.indices) {
                if (abs(hit - clicks[i]) <= half &&
                    (nearest < 0 || abs(hit - clicks[i]) < abs(hit - clicks[nearest]))
                ) {
                    nearest = i
                }
            }
            if (nearest >= 0 && perClick[nearest] == null) perClick[nearest] = (hit - clicks[nearest]) / perMilli
        }
        val offsets = perClick.filterNotNull()
        if (offsets.isEmpty()) return CalibrationOutcome.Failed(CalibrationOutcome.Reason.NOT_HEARD, 0, clicks.size)
        if (offsets.size < TAP_MIN_COUNT) {
            return CalibrationOutcome.Failed(CalibrationOutcome.Reason.TOO_FEW, offsets.size, clicks.size)
        }
        val median = median(offsets)
        val spread = robustSpread(offsets)
        if (spread > TAP_MAX_SPREAD_MILLIS) {
            return CalibrationOutcome.Failed(CalibrationOutcome.Reason.INCONSISTENT, offsets.size, clicks.size, spread)
        }
        val inliers = offsets.filter { abs(it - median) <= max(3 * spread, 10.0) }
        if (inliers.size < TAP_MIN_COUNT) {
            return CalibrationOutcome.Failed(CalibrationOutcome.Reason.TOO_FEW, inliers.size, clicks.size)
        }
        return CalibrationOutcome.Measured(median(inliers), standardDeviation(inliers), inliers.size, clicks.size)
    }

    internal fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
    }

    private fun robustSpread(values: List<Double>): Double {
        val m = median(values)
        return 1.4826 * median(values.map { abs(it - m) })
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }

    private fun smallestGap(clicks: DoubleArray): Double {
        require(clicks.size >= 2) { "calibration needs at least two clicks" }
        val sorted = clicks.sorted()
        return (1 until sorted.size).minOf { sorted[it] - sorted[it - 1] }
    }
}
