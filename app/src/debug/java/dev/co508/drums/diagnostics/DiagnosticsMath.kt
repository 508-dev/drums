package dev.co508.drums.diagnostics

import dev.co508.drums.audio.Sound
import dev.co508.drums.playback.Pattern
import dev.co508.drums.playback.PatternEvent
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * One bar of 4/4 with [notesPerBeat] evenly spaced snare strokes per beat,
 * alternating right and left (panned slightly right and left).
 */
fun subdivisionPattern(
    notesPerBeat: Int,
    snare: Sound,
): Pattern {
    require(notesPerBeat > 0) { "notesPerBeat must be positive" }
    val strokes = 4 * notesPerBeat
    return Pattern(
        lengthQuarters = 4.0,
        beatQuarters = 1.0,
        countInBeats = 4,
        events =
            List(strokes) { i ->
                PatternEvent(
                    position = i.toDouble() / notesPerBeat,
                    sound = snare,
                    gain = 0.8f,
                    pan = if (i % 2 == 0) HAND_PAN else -HAND_PAN,
                )
            },
        clicks = Pattern.steadyClicks(4.0, beatsPerBar = 4, beatQuarters = 1.0),
    )
}

private const val HAND_PAN = 0.3f

/** Where a musical position falls among the beats, for drawing the metronome. */
data class BeatPhase(
    /** 0-based beat within the bar; 0 is the downbeat. */
    val beatInBar: Int,
    /** 0 at the beat, rising to 1 at the next. */
    val fraction: Double,
    /** Time since the beat, at [beatsPerMinute]. */
    val millisSinceBeat: Double,
)

fun beatPhase(
    position: Double,
    beatsPerBar: Int,
    beatQuarters: Double,
    beatsPerMinute: Double,
): BeatPhase {
    val beats = position / beatQuarters
    val beat = floor(beats)
    val fraction = beats - beat
    val beatInBar = Math.floorMod(beat.toLong(), beatsPerBar.toLong()).toInt()
    val millis = if (beatsPerMinute > 0) fraction * 60_000.0 / beatsPerMinute else 0.0
    return BeatPhase(beatInBar, fraction, millis)
}

/**
 * Counts UI frames the display skipped, from the times of the frames that did
 * run: a gap of about n frame intervals means n - 1 were dropped.
 */
class FrameDropCounter {
    var frames = 0L
        private set
    var dropped = 0L
        private set
    var worstGapMillis = 0.0
        private set
    private var lastNanos = 0L

    fun onFrame(
        frameTimeNanos: Long,
        intervalNanos: Long,
    ) {
        if (lastNanos != 0L && intervalNanos > 0) {
            val gap = frameTimeNanos - lastNanos
            val missed = (gap.toDouble() / intervalNanos).roundToLong() - 1
            if (missed > 0) dropped += missed
            worstGapMillis = maxOf(worstGapMillis, gap / 1e6)
        }
        frames++
        lastNanos = frameTimeNanos
    }

    fun reset() {
        frames = 0
        dropped = 0
        worstGapMillis = 0.0
        lastNanos = 0
    }
}
