package dev.co508.drums.scoring

import dev.co508.drums.lesson.Hand
import dev.co508.drums.lesson.StrokeTimeline
import kotlin.math.abs
import kotlin.math.min

/**
 * How tight the timing windows are (DECISIONS.md, "Showing How Far Off You
 * Were" and "Matching Hits To Notes"). Each window is fixed in milliseconds,
 * and narrowed at fast tempos to at most [MAX_GAP_SHARE] of the gap to the
 * neighbouring note on that side, so neighbours' windows never overlap.
 */
enum class Strictness(
    /** Within this many ms either way: green. */
    val greenMillis: Double,
    /** Within this: yellow; beyond, red. */
    val yellowMillis: Double,
    /** A hit further than this from every note is an extra, and the note is missed. */
    val matchMillis: Double,
) {
    RELAXED(25.0, 50.0, 110.0),
    STANDARD(15.0, 35.0, 80.0),
    STRICT(10.0, 25.0, 60.0),
    ;

    companion object {
        const val MAX_GAP_SHARE = 0.4

        fun of(name: String?): Strictness = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

enum class Grade { GREEN, YELLOW, RED }

/**
 * One note's windows, in ms, after narrowing: [earlyMillis] and [lateMillis]
 * bound the match on each side, and the colour bands are capped by the same
 * side's limit.
 */
data class NoteWindows(
    val strictness: Strictness,
    val earlyMillis: Double,
    val lateMillis: Double,
) {
    fun grade(offsetMillis: Double): Grade {
        val side = if (offsetMillis < 0) earlyMillis else lateMillis
        val off = abs(offsetMillis)
        return when {
            off <= min(strictness.greenMillis, side) -> Grade.GREEN
            off <= min(strictness.yellowMillis, side) -> Grade.YELLOW
            else -> Grade.RED
        }
    }

    companion object {
        /** Windows for a note [gapBeforeMillis] after its previous note and [gapAfterMillis] before its next. */
        fun of(
            strictness: Strictness,
            gapBeforeMillis: Double,
            gapAfterMillis: Double,
        ) = NoteWindows(
            strictness,
            min(strictness.matchMillis, Strictness.MAX_GAP_SHARE * gapBeforeMillis),
            min(strictness.matchMillis, Strictness.MAX_GAP_SHARE * gapAfterMillis),
        )
    }
}

/** A stroke as scoring sees it. [index] is its index in [StrokeTimeline.strokes]. */
data class ScoredStroke(
    val index: Int,
    val position: Double,
    val graceLead: Int,
    val hand: Hand?,
    val buzzQuarters: Double,
) {
    val isGrace: Boolean get() = graceLead > 0
}

/**
 * A lesson's strokes arranged for matching: the main strokes in order, and
 * each one's grace strokes (earliest first). One loop; loop k repeats it
 * k × [lengthQuarters] later.
 */
class ScoredLesson(
    val lengthQuarters: Double,
    val strokes: List<ScoredStroke>,
) {
    /** Indexes of the main (non-grace) strokes, by position. */
    val mains: IntArray =
        strokes
            .filter { !it.isGrace }
            .sortedBy { it.position }
            .map { it.index }
            .toIntArray()

    /** For each main stroke index, its grace strokes' indexes, earliest-sounding (highest lead) first. */
    val graces: Map<Int, IntArray> =
        mains.associateWith { main ->
            strokes
                .filter { it.isGrace && it.position == strokes[main].position }
                .sortedByDescending { it.graceLead }
                .map { it.index }
                .toIntArray()
        }

    companion object {
        fun from(timeline: StrokeTimeline): ScoredLesson =
            ScoredLesson(
                timeline.length.toDouble(),
                timeline.strokes.mapIndexed { i, s ->
                    ScoredStroke(i, s.position.toDouble(), s.graceLead, s.hand, s.buzzLength?.toDouble() ?: 0.0)
                },
            )
    }
}

/** What happened to one stroke in one loop. */
data class NoteMark(
    val strokeIndex: Int,
    val loop: Long,
    val kind: Kind,
    /** Hit minus expected, ms; positive is late. 0 unless [Kind.HIT]. */
    val offsetMillis: Double = 0.0,
    /** The same offset in quarter notes at the tempo then, for drawing. */
    val offsetQuarters: Double = 0.0,
    val grade: Grade? = null,
) {
    enum class Kind {
        HIT,
        MISS,

        /** A grace stroke not heard as its own onset: optional, so neither hit nor miss. */
        GRACE_UNHEARD,
    }
}

/** A hit that matched no note, at [position] (absolute quarter notes, loop [loop]). */
data class ExtraMark(
    val position: Double,
    val loop: Long,
)

/** Running totals of one scored run, for the summary (#31). Main strokes only; graces counted apart. */
data class ScoringStats(
    val green: Int = 0,
    val yellow: Int = 0,
    val red: Int = 0,
    val misses: Int = 0,
    val extras: Int = 0,
    val sumOffsetMillis: Double = 0.0,
    val sumSquaredOffsetMillis: Double = 0.0,
    val perHand: Map<Hand, HandStats> = emptyMap(),
    val gracesHeard: Int = 0,
    val gracesUnheard: Int = 0,
) {
    val hits: Int get() = green + yellow + red
}

data class HandStats(
    val hits: Int = 0,
    val misses: Int = 0,
    val sumOffsetMillis: Double = 0.0,
)

/**
 * An immutable picture of a scored run, published by the matcher for the
 * views to read while drawing. [marks] holds, per stroke index, the most
 * recent [MARK_LOOPS] loops' marks, oldest first.
 */
class ScoringSnapshot(
    val marks: List<List<NoteMark>>,
    val extras: List<ExtraMark>,
    val stats: ScoringStats,
) {
    /** The newest loop anything was marked in, or 0. */
    val latestLoop: Long =
        maxOf(
            marks.maxOfOrNull { list -> list.maxOfOrNull { it.loop } ?: 0L } ?: 0L,
            extras.maxOfOrNull { it.loop } ?: 0L,
        )

    /**
     * The mark to show on stroke [strokeIndex] drawn in loop [loop]: that
     * loop's, else the latest earlier one; with its age in loops (0 = this
     * loop). Null when there is none within [MARK_LOOPS].
     */
    fun markFor(
        strokeIndex: Int,
        loop: Long,
    ): NoteMark? {
        val list = marks.getOrNull(strokeIndex) ?: return null
        for (i in list.indices.reversed()) {
            val mark = list[i]
            if (mark.loop <= loop) return mark.takeIf { loop - it.loop < MARK_LOOPS }
        }
        return null
    }

    companion object {
        /** Marks fade out over this many loops. */
        const val MARK_LOOPS = 3
        const val MAX_EXTRAS = 64
        val EMPTY = ScoringSnapshot(emptyList(), emptyList(), ScoringStats())

        /** How opaque a mark [age] loops old is drawn. */
        fun fade(age: Long): Float =
            when (age) {
                0L -> 1f
                1L -> 0.35f
                2L -> 0.15f
                else -> 0f
            }
    }
}
