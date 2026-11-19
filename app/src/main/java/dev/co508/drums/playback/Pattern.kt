package dev.co508.drums.playback

import dev.co508.drums.audio.Sound

/**
 * One sound in a [Pattern], [position] quarter notes after the pattern starts.
 *
 * A grace stroke sits at its main note's position with a [leadLevel] above 0.
 * It sounds that many grace gaps ([Transport.graceGapMillis]) before the
 * position, so its spacing is a time, the same at every tempo.
 */
data class PatternEvent(
    val position: Double,
    val sound: Sound,
    val gain: Float = 1f,
    val pan: Float = 0f,
    val leadLevel: Int = 0,
    /** How long the sound lasts, in quarter notes, cut with a short fade; 0 plays the whole sound. */
    val durationQuarters: Double = 0.0,
)

/** A metronome click; [accent] marks a bar's first beat. */
data class Click(
    val position: Double,
    val accent: Boolean,
)

/**
 * What the transport plays and loops: instrument events and metronome clicks
 * over [lengthQuarters]. It is built from a lesson ([LessonPattern]), or by
 * hand for diagnostics and tests. Immutable, so it can be handed to the mixer
 * thread; its arrays are read there without allocating.
 */
class Pattern(
    val lengthQuarters: Double,
    /** The counted beat in quarter notes (a dotted quarter is 1.5): tempo is in these per minute. */
    val beatQuarters: Double,
    /** Beats in the count-in bar. */
    val countInBeats: Int,
    events: List<PatternEvent>,
    clicks: List<Click>,
) {
    init {
        require(lengthQuarters > 0.0) { "a pattern needs a positive length" }
        require(beatQuarters > 0.0 && countInBeats > 0) { "invalid beat" }
        require(
            events.all { it.position >= 0.0 && it.position < lengthQuarters },
        ) { "events must lie in [0, $lengthQuarters)" }
        require(
            clicks.all { it.position >= 0.0 && it.position < lengthQuarters },
        ) { "clicks must lie in [0, $lengthQuarters)" }
        require(events.all { it.leadLevel in 0..MAX_LEAD_LEVEL }) { "lead levels run from 0 to $MAX_LEAD_LEVEL" }
    }

    private val sortedEvents = events.sortedBy { it.position }
    internal val eventPositions = DoubleArray(sortedEvents.size) { sortedEvents[it].position }
    internal val eventSounds = Array(sortedEvents.size) { sortedEvents[it].sound }
    internal val eventGains = FloatArray(sortedEvents.size) { sortedEvents[it].gain }
    internal val eventPans = FloatArray(sortedEvents.size) { sortedEvents[it].pan }
    internal val eventLeadLevels = IntArray(sortedEvents.size) { sortedEvents[it].leadLevel }
    internal val eventDurations = DoubleArray(sortedEvents.size) { sortedEvents[it].durationQuarters }

    /** The highest lead level used, so the transport only tracks the windows it needs. */
    internal val maxLeadLevel = sortedEvents.maxOfOrNull { it.leadLevel } ?: 0

    private val sortedClicks = clicks.sortedBy { it.position }
    internal val clickPositions = DoubleArray(sortedClicks.size) { sortedClicks[it].position }
    internal val clickAccents = BooleanArray(sortedClicks.size) { sortedClicks[it].accent }

    val events: List<PatternEvent> get() = sortedEvents
    val clicks: List<Click> get() = sortedClicks

    companion object {
        /** Grace notes per stroke the transport can place: a drag needs 2, a ruff 3. */
        const val MAX_LEAD_LEVEL = 4

        /** Clicks on every beat of an unchanging meter, accenting each bar's first beat. */
        fun steadyClicks(
            lengthQuarters: Double,
            beatsPerBar: Int,
            beatQuarters: Double,
        ): List<Click> {
            val count = kotlin.math.ceil(lengthQuarters / beatQuarters - 1e-9).toInt()
            return List(count) { Click(it * beatQuarters, accent = it % beatsPerBar == 0) }
        }
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

/** The first index in sorted [values] whose value is greater than [x], or [values].size. */
internal fun firstAbove(
    values: DoubleArray,
    x: Double,
): Int {
    var lo = 0
    var hi = values.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (values[mid] > x) hi = mid else lo = mid + 1
    }
    return lo
}
