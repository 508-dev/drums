package dev.co508.drums.ui.sheet

import dev.co508.drums.lesson.Fraction
import dev.co508.drums.lesson.Hand
import dev.co508.drums.lesson.Measure
import dev.co508.drums.lesson.Notation
import dev.co508.drums.lesson.Note
import dev.co508.drums.lesson.StrokeTimeline
import dev.co508.drums.lesson.TimeSignature
import kotlin.math.floor

/** A note placed on the sheet. Coordinates are in staff spaces; the line is at y = 0, down is positive. */
data class PlacedNote(
    val measureIndex: Int,
    val noteIndex: Int,
    val note: Note,
    /** The notehead's left edge. */
    val x: Float,
    /** Beams (or flags) this note has: 0 for a quarter or longer. */
    val beams: Int,
    /** Whether it is beamed with neighbours (else it gets flags). */
    val beamed: Boolean,
    /** The sticking text under it: lowercase grace hands, then the main hand ("lR"), or "" if unknown. */
    val sticking: String,
) {
    val stemX: Float get() = x + STEM_X
    val offset: Fraction get() = note.offset

    companion object {
        /** Bravura's stemUpSE anchor, less half a stem. */
        const val STEM_X = 1.18f - STEM_THICKNESS / 2
    }
}

data class PlacedRest(
    val x: Float,
    val duration: Fraction,
)

/** Notes joined by flat beams, stems up. */
data class BeamGroup(
    val notes: List<PlacedNote>,
)

/** A tuplet's number, over (or bracketing) its notes. */
data class TupletMark(
    val notes: List<PlacedNote>,
    val number: Int,
    val bracket: Boolean,
)

data class PlacedMeasure(
    val index: Int,
    val measure: Measure,
    val x: Float,
    val width: Float,
    /** Where the measure's time 0 is; time runs right at [spacesPerQuarter]. */
    val contentX: Float,
    val spacesPerQuarter: Float,
    val showTimeSignature: Boolean,
    val notes: List<PlacedNote>,
    val rests: List<PlacedRest>,
    val beams: List<BeamGroup>,
    val tuplets: List<TupletMark>,
) {
    fun xAt(offset: Double): Float = contentX + (offset * spacesPerQuarter).toFloat()
}

/** One line of music. */
data class SheetSystem(
    val measures: List<PlacedMeasure>,
    /** Whether the line starts with a clef. */
    val clef: Boolean = true,
)

/** Two notes joined by a tie (both may be in different measures of one system). */
data class Tie(
    val from: PlacedNote,
    val to: PlacedNote,
)

/**
 * The engraved sheet for one lesson: measures broken into lines, with
 * proportional spacing (DECISIONS.md, "Linear Time In Both Views"): within a
 * line, distance is proportional to time, so the playhead moves at a constant
 * speed. Each line is stretched to the full [width].
 */
class SheetLayout(
    val width: Float,
    val systems: List<SheetSystem>,
    val ties: List<Tie>,
) {
    /** The placed measure for a written measure index, and its line. */
    fun find(measureIndex: Int): Pair<Int, PlacedMeasure>? {
        systems.forEachIndexed { s, system ->
            system.measures.firstOrNull { it.index == measureIndex }?.let { return s to it }
        }
        return null
    }

    companion object {
        /** The smallest gap between two notes' onsets, so noteheads and stickings never collide. */
        const val MIN_NOTE_GAP = 2.2f

        /** Extra room before a note for each grace note. */
        const val GRACE_ROOM = 1.3f

        /** Never sparser than this. */
        const val MIN_SPACES_PER_QUARTER = 5f

        const val CLEF_WIDTH = 3.4f
        const val TIME_SIGNATURE_WIDTH = 2.8f
        const val MEASURE_PADDING_START = 1.4f
        const val MEASURE_PADDING_END = 1.4f
        const val REPEAT_WIDTH = 1.6f

        /** How many staff spaces per quarter note [notation] needs so nothing collides. */
        fun spacesPerQuarterNeeded(notation: Notation): Float {
            var needed = MIN_SPACES_PER_QUARTER
            for (measure in notation.measures) {
                val onsets = measure.notes
                for (i in 0 until onsets.size - 1) {
                    val gap = (onsets[i + 1].offset - onsets[i].offset).toDouble()
                    if (gap <= 0) continue
                    val room = MIN_NOTE_GAP + onsets[i + 1].graceNotes.size * GRACE_ROOM + onsets[i].dots * 0.6f
                    needed = maxOf(needed, (room / gap).toFloat())
                }
            }
            return needed
        }

        /** The width a measure needs, at [spacesPerQuarter]. */
        fun naturalWidth(
            measure: Measure,
            spacesPerQuarter: Float,
        ): Float = fixedWidth(measure) + (measure.length.toDouble() * spacesPerQuarter).toFloat()

        private fun fixedWidth(measure: Measure): Float {
            val graces =
                measure.notes.firstOrNull()?.let {
                    if (it.offset ==
                        Fraction.ZERO
                    ) {
                        it.graceNotes.size * GRACE_ROOM
                    } else {
                        0f
                    }
                }
                    ?: 0f
            return MEASURE_PADDING_START + MEASURE_PADDING_END + graces +
                (if (measure.repeatStart) REPEAT_WIDTH else 0f) +
                (if (measure.repeatTimes != null) REPEAT_WIDTH else 0f)
        }

        /** Lays out [notation] in lines [width] staff spaces wide, as many measures per line as fit. */
        fun layout(
            notation: Notation,
            width: Float,
        ): SheetLayout {
            val needed = spacesPerQuarterNeeded(notation)
            val measures = notation.measures
            val lines = mutableListOf<List<Int>>()
            var current = mutableListOf<Int>()
            var used = CLEF_WIDTH
            var lastSignature: TimeSignature? = null
            measures.forEachIndexed { i, m ->
                val signature = if (m.timeSignature != lastSignature) TIME_SIGNATURE_WIDTH else 0f
                val w = naturalWidth(m, needed) + signature
                if (current.isNotEmpty() && used + w > width) {
                    lines += current
                    current = mutableListOf()
                    used = CLEF_WIDTH
                }
                current += i
                used += w
                lastSignature = m.timeSignature
            }
            if (current.isNotEmpty()) lines += current

            lastSignature = null
            val systems =
                lines.map { indexes ->
                    // Stretch the line: fixed widths stay, time gets the rest, proportionally.
                    var fixed = CLEF_WIDTH
                    var quarters = 0.0
                    val showSignature =
                        indexes.map { i ->
                            val show = measures[i].timeSignature != lastSignature
                            lastSignature = measures[i].timeSignature
                            show
                        }
                    indexes.forEachIndexed { k, i ->
                        fixed += fixedWidth(measures[i]) + if (showSignature[k]) TIME_SIGNATURE_WIDTH else 0f
                        quarters += measures[i].length.toDouble()
                    }
                    val spacesPerQuarter = ((width - fixed) / quarters).toFloat()
                    var x = CLEF_WIDTH
                    val placed =
                        indexes.mapIndexed { k, i ->
                            val m = measures[i]
                            val signatureWidth = if (showSignature[k]) TIME_SIGNATURE_WIDTH else 0f
                            val measureWidth =
                                fixedWidth(m) + signatureWidth + (m.length.toDouble() * spacesPerQuarter).toFloat()
                            val graceRoom =
                                m.notes.firstOrNull()?.let {
                                    if (it.offset ==
                                        Fraction.ZERO
                                    ) {
                                        it.graceNotes.size * GRACE_ROOM
                                    } else {
                                        0f
                                    }
                                }
                                    ?: 0f
                            val contentX =
                                x + signatureWidth + (if (m.repeatStart) REPEAT_WIDTH else 0f) + MEASURE_PADDING_START +
                                    graceRoom
                            val pm = place(i, m, x, measureWidth, contentX, spacesPerQuarter, showSignature[k])
                            x += measureWidth
                            pm
                        }
                    SheetSystem(placed)
                }
            return SheetLayout(width, systems, ties(systems))
        }

        private fun place(
            index: Int,
            measure: Measure,
            x: Float,
            width: Float,
            contentX: Float,
            spacesPerQuarter: Float,
            showTimeSignature: Boolean,
        ): PlacedMeasure {
            fun xOf(offset: Fraction) = contentX + (offset.toDouble() * spacesPerQuarter).toFloat()
            val beatQuarters = measure.timeSignature.beatQuarters

            // Beam groups: runs of eighths or shorter within one beat, not broken by a rest.
            val groupOf = IntArray(measure.notes.size) { -1 }
            var group = 0
            for (i in measure.notes.indices) {
                val n = measure.notes[i]
                val beams = n.value?.beams ?: 0
                if (beams == 0) continue
                val prev = measure.notes.getOrNull(i - 1)
                val sameBeat =
                    prev != null &&
                        (prev.value?.beams ?: 0) > 0 &&
                        (prev.offset / beatQuarters).floor() == (n.offset / beatQuarters).floor() &&
                        measure.rests.none { it.offset > prev.offset && it.offset < n.offset }
                if (sameBeat && groupOf[i - 1] >= 0) groupOf[i] = groupOf[i - 1] else groupOf[i] = group++
            }
            val sizes = groupOf.filter { it >= 0 }.groupingBy { it }.eachCount()

            val notes =
                measure.notes.mapIndexed { i, n ->
                    PlacedNote(
                        measureIndex = index,
                        noteIndex = i,
                        note = n,
                        x = xOf(n.offset),
                        beams = n.value?.beams ?: 0,
                        beamed = groupOf[i] >= 0 && (sizes[groupOf[i]] ?: 0) > 1,
                        sticking = sticking(n),
                    )
                }
            val beamGroups =
                notes.indices
                    .filter { groupOf[it] >= 0 && (sizes[groupOf[it]] ?: 0) > 1 }
                    .groupBy { groupOf[it] }
                    .values
                    .map { idx -> BeamGroup(idx.map { notes[it] }) }

            // Tuplets: from a note marked as starting one to the next marked as stopping it.
            val tuplets = mutableListOf<TupletMark>()
            var start = -1
            for (i in notes.indices) {
                val t = notes[i].note.tuplet ?: continue
                if (t.starts || start < 0) start = i
                if (t.stops) {
                    val members = notes.subList(start, i + 1)
                    val oneBeam = beamGroups.any { it.notes.containsAll(members) }
                    tuplets += TupletMark(members.toList(), t.actualNotes, bracket = !oneBeam)
                    start = -1
                }
            }

            return PlacedMeasure(
                index = index,
                measure = measure,
                x = x,
                width = width,
                contentX = contentX,
                spacesPerQuarter = spacesPerQuarter,
                showTimeSignature = showTimeSignature,
                notes = notes,
                rests = measure.rests.map { PlacedRest(xOf(it.offset), it.duration) },
                beams = beamGroups,
                tuplets = tuplets,
            )
        }

        private fun ties(systems: List<SheetSystem>): List<Tie> =
            systems.flatMap { system ->
                val notes = system.measures.flatMap { it.notes }
                (0 until notes.size - 1).filter { notes[it].note.tieStart && notes[it + 1].note.tieStop }.map {
                    Tie(
                        notes[it],
                        notes[
                            it +
                                1,
                        ],
                    )
                }
            }

        fun sticking(note: Note): String {
            val main = note.hand ?: return ""
            val graces =
                note.graceNotes.joinToString("") {
                    if (it.hand ==
                        Hand.LEFT
                    ) {
                        "l"
                    } else if (it.hand == Hand.RIGHT) {
                        "r"
                    } else {
                        ""
                    }
                }
            return graces + if (main == Hand.LEFT) "L" else "R"
        }
    }
}

/** Stem thickness in staff spaces (Bravura's engraving default). */
const val STEM_THICKNESS = 0.12f

/**
 * Where the playhead is in the written music: a written measure and an offset
 * into it. The transport plays the timeline (repeats unrolled, looped); this
 * maps its position back to what is written. Null during the count-in.
 */
data class SheetPosition(
    val measureIndex: Int,
    val offset: Double,
)

fun sheetPosition(
    timeline: StrokeTimeline,
    position: Double,
): SheetPosition? {
    val length = timeline.length.toDouble()
    if (position < 0 || length <= 0) return null
    val inLoop = position - floor(position / length) * length
    val bar = timeline.bars.lastOrNull { it.start.toDouble() <= inLoop } ?: return null
    return SheetPosition(bar.source, inLoop - bar.start.toDouble())
}
