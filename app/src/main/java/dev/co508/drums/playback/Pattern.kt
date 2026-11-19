package dev.co508.drums.playback

import dev.co508.drums.audio.Sound
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * A time signature, as the transport needs it: how many beats make a bar and
 * how long a beat is in quarter notes. 4/4 is `Meter(4, 1.0)`; 6/8, counted in
 * dotted quarters, is `Meter(2, 1.5)`. Tempo is in these beats per minute
 * (DECISIONS.md, "Practice Controls").
 */
data class Meter(
    val beatsPerBar: Int,
    val beatQuarters: Double,
) {
    init {
        require(beatsPerBar > 0 && beatQuarters > 0.0) { "invalid meter $beatsPerBar x $beatQuarters" }
    }

    val barQuarters: Double get() = beatsPerBar * beatQuarters

    companion object {
        val FOUR_FOUR = Meter(4, 1.0)
    }
}

/** One stroke of a [Pattern], [position] quarter notes after the pattern starts. */
data class PatternEvent(
    val position: Double,
    val sound: Sound,
    val gain: Float = 1f,
    val pan: Float = 0f,
)

/**
 * What the transport plays and loops: instrument events over [lengthQuarters],
 * plus a metronome click on every beat of [meter], counted from the pattern's
 * start, with an accent on each bar's first beat. Immutable, so it can be
 * handed to the mixer thread; its arrays are read there without allocating.
 */
class Pattern(
    val lengthQuarters: Double,
    val meter: Meter,
    events: List<PatternEvent>,
) {
    init {
        require(lengthQuarters > 0.0) { "a pattern needs a positive length" }
        require(events.all { it.position >= 0.0 && it.position < lengthQuarters }) {
            "events must lie in [0, $lengthQuarters)"
        }
    }

    private val sorted = events.sortedBy { it.position }
    internal val eventPositions = DoubleArray(sorted.size) { sorted[it].position }
    internal val eventSounds = Array(sorted.size) { sorted[it].sound }
    internal val eventGains = FloatArray(sorted.size) { sorted[it].gain }
    internal val eventPans = FloatArray(sorted.size) { sorted[it].pan }

    internal val clickPositions: DoubleArray
    internal val clickAccents: BooleanArray

    init {
        val clicks = ceil(lengthQuarters / meter.beatQuarters - EPSILON).toInt()
        clickPositions = DoubleArray(clicks) { it * meter.beatQuarters }
        clickAccents = BooleanArray(clicks) { isWholeBars(it * meter.beatQuarters) }
    }

    val events: List<PatternEvent> get() = sorted

    private fun isWholeBars(position: Double): Boolean {
        val bars = position / meter.barQuarters
        return abs(bars - bars.roundToLong()) < EPSILON
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}

/**
 * The speed trainer: every [everyLoops] loops, the tempo rises by [stepBpm],
 * until it reaches [targetBpm]. Changes land exactly on loop boundaries.
 */
data class SpeedTrainer(
    val stepBpm: Double,
    val everyLoops: Int,
    val targetBpm: Double,
) {
    init {
        require(stepBpm > 0.0 && everyLoops > 0) { "invalid speed trainer $this" }
    }

    /** The tempo for the loop with index [loop] (0 is the first), given the tempo before it. */
    fun tempoEntering(
        loop: Long,
        currentBpm: Double,
    ): Double =
        if (loop > 0 && loop % everyLoops == 0L && currentBpm < targetBpm) {
            minOf(currentBpm + stepBpm, targetBpm)
        } else {
            currentBpm
        }
}
