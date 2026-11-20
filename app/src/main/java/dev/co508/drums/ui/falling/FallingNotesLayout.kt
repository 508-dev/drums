package dev.co508.drums.ui.falling

import dev.co508.drums.lesson.Fraction
import dev.co508.drums.lesson.Hand
import dev.co508.drums.lesson.StrokeDynamic
import dev.co508.drums.lesson.StrokeTimeline
import kotlin.math.floor

/** Which lane a note falls in. [MIDDLE] is for notes with no sticking (proper support: #36). */
enum class Lane { LEFT, MIDDLE, RIGHT }

/** One note to draw: everything the view needs, precomputed. */
data class FallingNote(
    /** Quarter notes from the pattern's start. */
    val position: Double,
    val lane: Lane,
    val dynamic: StrokeDynamic,
    /** 0 for a main note; 1 and up for grace notes, drawn that many steps above their main note. */
    val graceLead: Int = 0,
    /** For a buzz roll: its length in quarter notes, drawn as a tail. */
    val buzzQuarters: Double = 0.0,
    /** For a grace note: how many grace notes lead into its main note (1 for a flam, 2 for a drag). */
    val graceCount: Int = 0,
    /**
     * For a grace note: the time back to the previous note in its own lane, in
     * quarter notes (wrapping around the loop). Its offset stays well inside
     * this, so it reads as part of its main note, not of that neighbour.
     */
    val laneGapQuarters: Double = Double.POSITIVE_INFINITY,
    /** The stroke's index in [StrokeTimeline.strokes], for its scoring marks. */
    val strokeIndex: Int = -1,
)

/** A grid line: a beat, or a bar line ([isBar]). */
data class GridLine(
    val position: Double,
    val isBar: Boolean,
)

/** A counting label in the margin ("1", "e", "&", "a"). */
data class CountLabel(
    val position: Double,
    val text: String,
    val isBeat: Boolean,
)

/**
 * The falling-notes view's content for one loop of a lesson: notes, grid and
 * counting labels, in musical position. The view repeats it every
 * [lengthQuarters], as the transport does. DECISIONS.md, "Falling Notes:
 * Hands Are Lanes".
 */
class FallingNotesLayout(
    val lengthQuarters: Double,
    val beatQuarters: Double,
    val notes: List<FallingNote>,
    val grid: List<GridLine>,
    val counts: List<CountLabel>,
) {
    /** Whether any note has no hand, so the middle lane is needed. */
    val hasMiddleLane: Boolean = notes.any { it.lane == Lane.MIDDLE }

    /** Indexes of loops (0 = first) that overlap the positions [from, to], for drawing repeats. */
    fun loopsBetween(
        from: Double,
        to: Double,
    ): LongRange = floor(from / lengthQuarters).toLong()..floor(to / lengthQuarters).toLong()

    companion object {
        fun from(timeline: StrokeTimeline): FallingNotesLayout {
            val notes =
                timeline.strokes.mapIndexed { index, s ->
                    FallingNote(
                        position = s.position.toDouble(),
                        lane =
                            when (s.hand) {
                                Hand.LEFT -> Lane.LEFT
                                Hand.RIGHT -> Lane.RIGHT
                                null -> Lane.MIDDLE
                            },
                        dynamic = s.dynamic,
                        graceLead = s.graceLead,
                        buzzQuarters = s.buzzLength?.toDouble() ?: 0.0,
                        strokeIndex = index,
                    )
                }
            val grid = mutableListOf<GridLine>()
            val counts = mutableListOf<CountLabel>()
            for (bar in timeline.bars) {
                val signature = bar.timeSignature
                val beat = signature.beatQuarters
                var k = 0L
                while (beat * k < bar.length) {
                    val beatStart = bar.start + beat * k
                    grid += GridLine(beatStart.toDouble(), isBar = k == 0L)
                    counts += CountLabel(beatStart.toDouble(), "${k + 1}", isBeat = true)
                    // "e & a" between beats in simple time (16ths of a quarter-note beat).
                    if (!signature.isCompound && beat == Fraction.ONE) {
                        for ((i, text) in listOf("e", "&", "a").withIndex()) {
                            val at = beatStart + Fraction.of(i + 1L, 4)
                            if (at < bar.start + bar.length) counts += CountLabel(at.toDouble(), text, isBeat = false)
                        }
                    }
                    k++
                }
            }
            val length = timeline.length.toDouble()
            return FallingNotesLayout(
                lengthQuarters = length,
                beatQuarters = timeline.timeSignature.beatQuarters.toDouble(),
                notes = withGraceSpacing(notes.sortedBy { it.position }, length),
                grid = grid,
                counts = counts,
            )
        }
    }
}

/** Fills in each grace note's [FallingNote.graceCount] and [FallingNote.laneGapQuarters]. */
private fun withGraceSpacing(
    notes: List<FallingNote>,
    length: Double,
): List<FallingNote> {
    val positionsByLane = notes.groupBy { it.lane }.mapValues { (_, n) -> n.map { it.position }.distinct().sorted() }
    return notes.map { note ->
        if (note.graceLead == 0) return@map note
        val count = notes.filter { it.position == note.position && it.graceLead > 0 }.maxOf { it.graceLead }
        val lane = positionsByLane.getValue(note.lane)
        val previous = lane.lastOrNull { it < note.position } ?: (lane.last() - length)
        val gap = if (previous < note.position) note.position - previous else length
        note.copy(graceCount = count, laneGapQuarters = gap)
    }
}

/**
 * How far below its main note a grace note is drawn, in pixels: [lead] steps
 * of at most [maxStep] each, shrunk so that the farthest grace stays within
 [GRACE_GAP_SHARE] of the gap to the previous note in its lane. A grace note
 * is then always closer to its main note than to that neighbour, and in dense
 * music it closes up toward its main note rather than collide with the
 * neighbour.
 */
fun graceOffset(
    lead: Int,
    count: Int,
    laneGapQuarters: Double,
    pixelsPerQuarter: Float,
    maxStep: Float,
): Float {
    if (lead <= 0) return 0f
    val room = (laneGapQuarters * pixelsPerQuarter * GRACE_GAP_SHARE).toFloat()
    val step = minOf(maxStep, room / count.coerceAtLeast(1))
    return lead * step
}

/** The share of the gap to a grace note's lane neighbour its offset may use: well under half, so it stays with its main note and clear of the neighbour. */
const val GRACE_GAP_SHARE = 0.3

/**
 * Where things go on screen. Time runs up the screen: the note being played
 * now is on the hit line near the bottom, and later notes are above it, at a
 * fixed distance per beat ([pixelsPerBeat], the zoom). DECISIONS.md, "Linear
 * Time In Both Views".
 */
class FallingNotesGeometry(
    val width: Float,
    val height: Float,
    val pixelsPerBeat: Float,
    val beatQuarters: Double,
    /** Lanes in use, left to right. */
    val lanes: List<Lane>,
    val laneWidth: Float,
) {
    /** The hit line, a little above the bottom so passing notes stay visible. */
    val hitLineY: Float = height * HIT_LINE_FRACTION

    val pixelsPerQuarter: Float get() = (pixelsPerBeat / beatQuarters).toFloat()

    /** Lanes are centred as a group. */
    fun laneCenterX(lane: Lane): Float {
        val index = lanes.indexOf(lane).coerceAtLeast(0)
        val left = (width - laneWidth * lanes.size) / 2
        return left + laneWidth * (index + 0.5f)
    }

    /** The y of a [position] when [now] is on the hit line. */
    fun y(
        position: Double,
        now: Double,
    ): Float = hitLineY - ((position - now) * pixelsPerQuarter).toFloat()

    /** The positions visible on screen, with [margin] pixels to spare at each end. */
    fun visiblePositions(
        now: Double,
        margin: Float,
    ): ClosedFloatingPointRange<Double> {
        val below = (height - hitLineY + margin) / pixelsPerQuarter
        val above = (hitLineY + margin) / pixelsPerQuarter
        return (now - below)..(now + above)
    }

    companion object {
        const val HIT_LINE_FRACTION = 0.82f
    }
}

/** How lit a note is as it crosses the hit line: 1 right on it, fading to 0 [LIT_QUARTERS] later. */
fun litAmount(
    position: Double,
    now: Double,
): Float {
    val since = now - position
    return if (since < 0 || since > LIT_QUARTERS) 0f else (1 - since / LIT_QUARTERS).toFloat()
}

const val LIT_QUARTERS = 0.35
