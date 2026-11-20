package dev.co508.drums.hits

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Receives onsets from an [OnsetDetector], on the thread that called [OnsetDetector.process]. */
fun interface OnsetSink {
    /**
     * A stick hit started at input [frame] (fractional: sub-sample precision).
     * [levelDb] is the hit's peak short-term power in dBFS (a full-scale sine is
     * −3 dB), for diagnostics now and accent detection later.
     */
    fun onset(
        frame: Double,
        levelDb: Float,
    )
}

/**
 * Finds the onsets of stick hits in a mono microphone stream. See DECISIONS.md,
 * "Onsets: A Rise Gate, An Adaptive Floor And A Constant Fraction".
 *
 * Streaming and allocation-free: [process] takes blocks of any size, in order,
 * on one thread. Each sample is high-passed (room rumble, voices and handling
 * noise live low; a stick on a pad is a broadband click), squared and smoothed
 * into a fast power envelope. A hit is **triggered** when that envelope
 *
 * - clears the noise floor by [Config.noiseMarginDb] (and an absolute minimum),
 * - and has risen by [Config.riseDb] above the loudest moment of a short window
 *   just before ([Config.riseDelayMillis], [Config.riseWindowMillis]).
 *
 * The rise test is what lets a hit 25 ms after the last one (32nds at 300 BPM)
 * or a flam's main note 12 ms after its grace note trigger on top of the
 * previous hit's tail, while a ringing tail or a room reflection never does:
 * tails only fall. After a trigger the detector stays deaf until
 * [Config.minGapMillis] has passed and the envelope is falling, so one hit
 * gives one onset. Hits closer together than that merge into one.
 *
 * The onset time is then **refined** from a short history of the filtered
 * signal, with a constant-fraction rule: the moment a centred power envelope
 * first reaches [Config.fraction] of the way from the local pre-hit level to
 * the hit's peak, interpolated between samples. A constant fraction makes the
 * timing independent of how hard the pad was hit; a fixed threshold would put
 * soft hits late.
 *
 * Finally, an onset far quieter than the recent hits ([Config.relativeGateDb])
 * is dropped: rooms tick, and some phones pick up bursts of electrical whine,
 * well below anyone playing.
 *
 * The noise floor is minimum statistics: the quietest moment of a medium
 * envelope in each [Config.floorBlockMillis] block. It drops at once, but after
 * a warm-up it rises at most [Config.floorRiseDbPerSecond], and not at all
 * within [Config.floorHoldMillis] of a hit, so continuous playing or a ringing
 * drum can't drag it up into the hits.
 */
class OnsetDetector(
    val sampleRate: Int,
    val config: Config = Config(),
) {
    data class Config(
        val highPassHz: Double = 2000.0,
        /** Cascaded second-order sections: 2 is 24 dB per octave. */
        val highPassSections: Int = 2,
        val fastMillis: Double = 0.5,
        val floorEnvelopeMillis: Double = 5.0,
        /** The rise is measured against the loudest moment of a window ending this long ago… */
        val riseDelayMillis: Double = 2.0,
        /** …and lasting this long. A window, not a point, so a tail's beating partials don't count as a rise. */
        val riseWindowMillis: Double = 3.0,
        val riseDb: Double = 6.0,
        val noiseMarginDb: Double = 12.0,
        val minLevelDb: Double = -70.0,
        val minGapMillis: Double = 5.0,
        val maxAttackMillis: Double = 4.0,
        val preOnsetMillis: Double = 3.0,
        val floorBlockMillis: Double = 20.0,
        val floorRiseDbPerSecond: Double = 2.0,
        /** The floor doesn't rise at all within this long of a hit: a ringing drum isn't the room. */
        val floorHoldMillis: Double = 250.0,
        val warmUpMillis: Double = 300.0,
        val windowMillis: Double = 0.5,
        val fraction: Double = 0.05,
        /**
         * Onsets this far below the loudest recent hit are ignored: room ticks and
         * electrical whine bursts, not playing. A ghost note is about 15 dB under an accent.
         */
        val relativeGateDb: Double = 30.0,
        /** How fast the loudest recent hit is forgotten, so playing can get softer. */
        val loudestDecayDbPerSecond: Double = 3.0,
    )

    private fun frames(millis: Double): Int = max(1, (millis * sampleRate / 1000.0).roundToInt())

    private val filters = Array(config.highPassSections) { Biquad.highPass(sampleRate, config.highPassHz) }
    private val window = max(2, frames(config.windowMillis) / 2 * 2)
    private val half = window / 2
    private val subBlock = frames(SUB_BLOCK_MILLIS)
    private val riseFirst = frames(config.riseDelayMillis + config.riseWindowMillis)
    private val riseLast = frames(config.riseDelayMillis)
    private val minGap = frames(config.minGapMillis)
    private val maxAttack = frames(config.maxAttackMillis)
    private val preOnset = frames(config.preOnsetMillis)
    private val blockLength = frames(config.floorBlockMillis)
    private val warmUp = frames(config.warmUpMillis)
    private val floorHold = frames(config.floorHoldMillis)

    private val history = FloatArray(powerOfTwoAtLeast(preOnset + maxAttack + 4 * window + frames(2.0)))
    private val historyMask = history.size - 1L
    private val envelope = DoubleArray(history.size)

    // The fast envelope's maximum in each sub-block, for the rise reference.
    private val subMax = DoubleArray(powerOfTwoAtLeast(riseFirst / subBlock + 4))
    private val subMask = subMax.size - 1L
    private var currentSubMax = 0.0

    private val fastCoefficient = 1 - exp(-1000.0 / (config.fastMillis * sampleRate))
    private val floorCoefficient = 1 - exp(-1000.0 / (config.floorEnvelopeMillis * sampleRate))
    private val riseRatio = power(config.riseDb)
    private val noiseRatio = power(config.noiseMarginDb)
    private val minPower = power(config.minLevelDb)
    private val floorRisePerBlock = power(config.floorRiseDbPerSecond * config.floorBlockMillis / 1000.0)

    private var expectedFrame = Long.MIN_VALUE
    private var validFrom = 0L
    private var fast = 0.0
    private var slow = 0.0
    private var blockMin = Double.POSITIVE_INFINITY
    private var blockCount = 0
    private var floor = Double.NaN
    private var state = ARMED
    private var triggerFrame = 0L
    private var peak = 0.0
    private var peakFrame = 0L
    private var lastTrigger = Long.MIN_VALUE / 2
    private var loudestDb = Double.NEGATIVE_INFINITY
    private var loudestFrame = 0.0

    /** The noise floor in dBFS, or NaN while warming up. Updated per block, readable from any thread. */
    @Volatile
    var noiseFloorDb = Float.NaN
        private set

    /** The loudest fast-envelope power in the last processed block, in dBFS. */
    @Volatile
    var blockPeakDb = Float.NEGATIVE_INFINITY
        private set

    @Volatile
    var onsetCount = 0L
        private set

    /** Onsets ignored for being far quieter than the recent hits ([Config.relativeGateDb]). */
    @Volatile
    var quietOnsetCount = 0L
        private set

    /**
     * Analyses [count] samples of [samples] from [offset]; the first is input
     * frame [firstFrame]. Blocks should follow on; a gap (or the first call)
     * restarts the envelopes but keeps the noise floor once warmed up.
     */
    fun process(
        samples: FloatArray,
        offset: Int,
        count: Int,
        firstFrame: Long,
        sink: OnsetSink,
    ) {
        if (firstFrame != expectedFrame) restart(firstFrame)
        var blockPeak = 0.0
        for (i in 0 until count) {
            val frame = firstFrame + i
            var y = samples[offset + i].toDouble()
            for (f in filters) y = f.process(y)
            history[(frame and historyMask).toInt()] = y.toFloat()
            val p = y * y
            fast += fastCoefficient * (p - fast)
            slow += floorCoefficient * (p - slow)
            if (fast > blockPeak) blockPeak = fast
            trackSubBlocks(frame)
            trackFloor(frame)
            val reference = riseReference(frame)
            when (state) {
                ARMED ->
                    if (!floor.isNaN() &&
                        fast > max(minPower, floor * noiseRatio) &&
                        fast > riseRatio * reference &&
                        frame - lastTrigger >= minGap
                    ) {
                        state = TRIGGERED
                        triggerFrame = frame
                        peak = fast
                        peakFrame = frame
                    }
                TRIGGERED -> {
                    if (fast > peak) {
                        peak = fast
                        peakFrame = frame
                    }
                    val fell = fast < peak * 0.5 && frame - peakFrame >= half
                    if (fell || frame - triggerFrame >= maxAttack) {
                        // A quiet (gated) onset mustn't hold off a real hit just after it:
                        // it only waits for its own rise to end.
                        lastTrigger = if (refine(frame, sink)) triggerFrame else Long.MIN_VALUE / 2
                        state = REFRACTORY
                    }
                }
                else ->
                    if (frame - lastTrigger >= minGap && fast <= reference) state = ARMED
            }
        }
        expectedFrame = firstFrame + count
        blockPeakDb = decibels(blockPeak)
    }

    /** Forgets everything, including the noise floor. */
    fun reset() {
        expectedFrame = Long.MIN_VALUE
        loudestDb = Double.NEGATIVE_INFINITY
        floor = Double.NaN
        noiseFloorDb = Float.NaN
        for (f in filters) f.reset()
    }

    private fun restart(firstFrame: Long) {
        validFrom = firstFrame
        fast = 0.0
        slow = 0.0
        blockMin = Double.POSITIVE_INFINITY
        blockCount = 0
        currentSubMax = 0.0
        state = ARMED
        lastTrigger = Long.MIN_VALUE / 2
        for (f in filters) f.reset()
    }

    private fun trackSubBlocks(frame: Long) {
        if (fast > currentSubMax) currentSubMax = fast
        if ((frame + 1) % subBlock == 0L) {
            subMax[((frame / subBlock) and subMask).toInt()] = currentSubMax
            currentSubMax = 0.0
        }
    }

    /**
     * The loudest fast envelope in the completed sub-blocks within
     * [frame − riseFirst, frame − riseLast], or [fast] itself (no rise) until
     * there is such history.
     */
    private fun riseReference(frame: Long): Double {
        val first = Math.floorDiv(frame - riseFirst, subBlock.toLong())
        val last = Math.floorDiv(frame - riseLast + 1, subBlock.toLong()) - 1
        if (first * subBlock < validFrom || last < first) return fast
        var reference = 0.0
        for (j in first..last) {
            val m = subMax[(j and subMask).toInt()]
            if (m > reference) reference = m
        }
        return reference
    }

    private fun trackFloor(frame: Long) {
        if (slow < blockMin) blockMin = slow
        if (++blockCount < blockLength) return
        val sinceStart = frame - validFrom
        // The first blocks hold the envelopes and filters settling from zero.
        if (sinceStart >= 2 * blockLength) {
            floor =
                when {
                    floor.isNaN() || sinceStart < warmUp || blockMin < floor -> blockMin
                    state != ARMED || frame - lastTrigger < floorHold -> floor
                    else -> min(blockMin, floor * floorRisePerBlock)
                }
            floor = max(floor, MIN_FLOOR)
            noiseFloorDb = decibels(floor)
        }
        blockMin = Double.POSITIVE_INFINITY
        blockCount = 0
    }

    /**
     * Constant-fraction onset of the hit triggered at [triggerFrame], from the
     * filtered history up to [now]. Returns whether it was reported.
     */
    private fun refine(
        now: Long,
        sink: OnsetSink,
    ): Boolean {
        val start = max(triggerFrame - preOnset, validFrom + half)
        val end = now - half + 1 // The last centre whose window lies within the history.
        val n = (end - start + 1).toInt()
        if (n < 2) return emit(triggerFrame.toDouble(), peak, sink)
        var sum = 0.0
        for (j in start - half until start + half) sum += square(j)
        var top = 0
        for (k in 0 until n) {
            // envelope[k]: mean power over [start + k − half, start + k + half).
            envelope[k] = sum / window
            if (envelope[k] > envelope[top]) top = k
            val i = start + k
            if (k < n - 1) sum += square(i + half) - square(i - half)
        }
        var base = envelope[top]
        for (k in 0..top) if (envelope[k] < base) base = envelope[k]
        val level = base + config.fraction * (envelope[top] - base)
        // Walk back from the peak to where the hit began: the start of the
        // above-level stretch, bridging dips shorter than a window. Merged hits
        // (a flat flam) then report their first sound, not a dip between them.
        var k = top
        var quiet = 0
        var i = top - 1
        while (i >= 0 && quiet < window) {
            if (envelope[i] >= level) {
                k = i
                quiet = 0
            } else {
                quiet++
            }
            i--
        }
        val crossing =
            if (k == 0) {
                start.toDouble()
            } else {
                val below = envelope[k - 1]
                (start + k - 1) + (level - below) / (envelope[k] - below)
            }
        // A step in power reaches the fraction (0.5 − fraction) windows early in a centred mean.
        return emit(crossing + window * (0.5 - config.fraction), envelope[top], sink)
    }

    private fun emit(
        frame: Double,
        peakPower: Double,
        sink: OnsetSink,
    ): Boolean {
        val level = decibels(peakPower).toDouble()
        val loudest = loudestDb - config.loudestDecayDbPerSecond * (frame - loudestFrame) / sampleRate
        if (level < loudest - config.relativeGateDb) {
            quietOnsetCount++
            return false
        }
        if (level >= loudest) {
            loudestDb = level
            loudestFrame = frame
        }
        onsetCount++
        sink.onset(frame, level.toFloat())
        return true
    }

    private fun square(frame: Long): Double {
        val y = history[(frame and historyMask).toInt()].toDouble()
        return y * y
    }

    private companion object {
        const val ARMED = 0
        const val TRIGGERED = 1
        const val REFRACTORY = 2
        const val MIN_FLOOR = 1e-14
        const val SUB_BLOCK_MILLIS = 0.5

        fun power(db: Double): Double = 10.0.pow(db / 10.0)

        fun decibels(power: Double): Float = (10.0 * log10(max(power, 1e-20))).toFloat()

        fun powerOfTwoAtLeast(n: Int): Int {
            var size = 1
            while (size < n) size *= 2
            return size
        }
    }
}
