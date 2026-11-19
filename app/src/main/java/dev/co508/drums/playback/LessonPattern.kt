package dev.co508.drums.playback

import dev.co508.drums.audio.Sound
import dev.co508.drums.lesson.Hand
import dev.co508.drums.lesson.StrokeDynamic
import dev.co508.drums.lesson.StrokeTimeline

/** The sounds a lesson plays with. */
class DrumKit(
    val snare: Sound,
    val buzz: Sound,
)

/** Builds the transport's [Pattern] from a lesson's [StrokeTimeline]. */
object LessonPattern {
    /** Gain for each dynamic. An accent is full level; the rest sit below it. */
    fun gainOf(dynamic: StrokeDynamic): Float =
        when (dynamic) {
            StrokeDynamic.ACCENT -> 1f
            StrokeDynamic.NORMAL -> 0.55f
            StrokeDynamic.GRACE -> 0.3f
            StrokeDynamic.GHOST -> 0.18f
        }

    /** Hands are panned slightly apart (DECISIONS.md, "Practice Controls"). */
    fun panOf(hand: Hand?): Float =
        when (hand) {
            Hand.RIGHT -> HAND_PAN
            Hand.LEFT -> -HAND_PAN
            null -> 0f
        }

    fun build(
        timeline: StrokeTimeline,
        kit: DrumKit,
    ): Pattern {
        val signature = timeline.timeSignature
        val beat = signature.beatQuarters.toDouble()
        val events =
            timeline.strokes.map { stroke ->
                val buzz = stroke.buzzLength
                PatternEvent(
                    position = stroke.position.toDouble(),
                    sound = if (buzz != null) kit.buzz else kit.snare,
                    gain = gainOf(stroke.dynamic),
                    pan = panOf(stroke.hand),
                    leadLevel = stroke.graceLead.coerceAtMost(Pattern.MAX_LEAD_LEVEL),
                    durationQuarters = buzz?.toDouble() ?: 0.0,
                )
            }
        // Clicks follow each bar's own time signature.
        val clicks =
            timeline.bars.flatMap { bar ->
                val beatQuarters = bar.timeSignature.beatQuarters
                (0 until bar.timeSignature.beatsPerBar)
                    .map { k -> beatQuarters * k.toLong() }
                    .filter { it < bar.length }
                    .map { offset ->
                        Click(
                            (bar.start + offset).toDouble(),
                            accent =
                                offset == dev.co508.drums.lesson.Fraction.ZERO,
                        )
                    }
            }
        return Pattern(
            lengthQuarters = timeline.length.toDouble(),
            beatQuarters = beat,
            countInBeats = signature.beatsPerBar,
            events = events,
            clicks = clicks,
        )
    }

    private const val HAND_PAN = 0.3f
}
