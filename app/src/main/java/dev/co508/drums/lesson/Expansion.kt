package dev.co508.drums.lesson

/** How loud a stroke is meant to be. */
enum class StrokeDynamic { GHOST, GRACE, NORMAL, ACCENT }

/** Where a stroke comes from in the written notation, for lighting notes up and for scoring (M5). */
data class StrokeSource(
    /** Index into [Notation.measures]. */
    val measureIndex: Int,
    /** The measure's label in the file. */
    val measureNumber: String,
    /** Index into that measure's notes. */
    val noteIndex: Int,
)

/**
 * One stroke to play.
 *
 * A grace stroke has its main note's [position] and a [graceLead] of 1 or more:
 * it sounds that many grace gaps early. The gap is a time, not a note value,
 * so its spacing is the same at every tempo.
 */
data class Stroke(
    val position: Fraction,
    val hand: Hand?,
    val dynamic: StrokeDynamic,
    val graceLead: Int = 0,
    /** For a buzz roll: how long it lasts, ties included. */
    val buzzLength: Fraction? = null,
    val source: StrokeSource,
)

/** One measure as played, after unrolling repeats. */
data class Bar(
    val start: Fraction,
    val length: Fraction,
    val timeSignature: TimeSignature,
    val source: Int,
)

/**
 * A lesson as played: every stroke in order, with repeats unrolled, ties
 * merged, diddles split and hands assigned. Positions are exact quarter notes
 * from the start of the first bar.
 */
data class StrokeTimeline(
    val strokes: List<Stroke>,
    val bars: List<Bar>,
) {
    val length: Fraction get() = bars.lastOrNull()?.let { it.start + it.length } ?: Fraction.ZERO

    val timeSignature: TimeSignature get() = bars.firstOrNull()?.timeSignature ?: TimeSignature.FOUR_FOUR
}

/**
 * Turns written [Notation] into the [StrokeTimeline] the transport plays:
 * - repeats are unrolled
 * - a tied note isn't struck again, but a tied buzz roll goes on
 * - tremolo slashes become diddle strokes, all with the note's hand
 * - buzz rolls become one sustained stroke
 * - accents and ghost notes become dynamics, and grace notes are quieter
 * - "lead with left" swaps every R and L
 */
object Expansion {
    /** How many times a repeat may be played, so a broken file can't make a huge timeline. */
    const val MAX_REPEAT_TIMES = 16

    fun expand(
        notation: Notation,
        leadWithLeft: Boolean = false,
    ): StrokeTimeline {
        val strokes = mutableListOf<Stroke>()
        val bars = mutableListOf<Bar>()
        var barStart = Fraction.ZERO
        // The buzz stroke a tie may extend, as an index into strokes.
        var tiedBuzz = -1

        for (measureIndex in unrolledOrder(notation.measures)) {
            val measure = notation.measures[measureIndex]
            bars += Bar(barStart, measure.length, measure.timeSignature, measureIndex)
            measure.notes.forEachIndexed { noteIndex, note ->
                val source = StrokeSource(measureIndex, measure.number, noteIndex)
                val position = barStart + note.offset
                val hand = note.hand?.let { if (leadWithLeft) it.other() else it }
                val dynamic =
                    when {
                        note.ghost -> StrokeDynamic.GHOST
                        note.accent != Accent.NONE -> StrokeDynamic.ACCENT
                        else -> StrokeDynamic.NORMAL
                    }

                if (note.tieStop && note.ornament == Ornament.Buzz && tiedBuzz >= 0) {
                    val buzz = strokes[tiedBuzz]
                    strokes[tiedBuzz] = buzz.copy(buzzLength = buzz.buzzLength!! + note.duration)
                    if (!note.tieStart) tiedBuzz = -1
                    return@forEachIndexed
                }
                tiedBuzz = -1
                if (note.tieStop && note.ornament == null) return@forEachIndexed // Held, not struck again.

                note.graceNotes.forEachIndexed { i, grace ->
                    val graceHand = grace.hand?.let { if (leadWithLeft) it.other() else it }
                    strokes +=
                        Stroke(
                            position,
                            graceHand,
                            StrokeDynamic.GRACE,
                            graceLead = note.graceNotes.size - i,
                            source = source,
                        )
                }
                when (val ornament = note.ornament) {
                    null -> strokes += Stroke(position, hand, dynamic, source = source)
                    Ornament.Buzz -> {
                        strokes += Stroke(position, hand, dynamic, buzzLength = note.duration, source = source)
                        if (note.tieStart) tiedBuzz = strokes.lastIndex
                    }
                    is Ornament.Tremolo -> {
                        val count = diddleCount(note, ornament.slashes)
                        val step = note.duration / count.toLong()
                        for (k in 0 until count) {
                            val d = if (k == 0 || dynamic == StrokeDynamic.GHOST) dynamic else StrokeDynamic.NORMAL
                            strokes += Stroke(position + step * k.toLong(), hand, d, source = source)
                        }
                    }
                }
            }
            barStart += measure.length
        }
        return StrokeTimeline(strokes, bars)
    }

    /**
     * How many strokes a tremolo splits a note into. Each slash adds a beam, so
     * the strokes have the note's beams plus [slashes]: an eighth with one
     * slash is two 16ths, a dotted quarter with two is six 16ths.
     */
    fun diddleCount(
        note: Note,
        slashes: Int,
    ): Int {
        val value = note.value ?: NoteValue.QUARTER
        val written = writtenQuarters(value, note.dots)
        val stroke = Fraction.of(1, 1L shl (value.beams + slashes))
        return (written / stroke).floor().toInt().coerceAtLeast(1)
    }

    private fun writtenQuarters(
        value: NoteValue,
        dots: Int,
    ): Fraction {
        val base =
            when (value) {
                NoteValue.WHOLE -> Fraction.of(4)
                NoteValue.HALF -> Fraction.of(2)
                else -> Fraction.of(1, 1L shl value.beams)
            }
        // Each dot adds half of what came before: 1 + 1/2 + 1/4…
        var total = base
        var add = base
        repeat(dots) {
            add /= 2
            total += add
        }
        return total
    }

    /**
     * The order measures are played in. A backward repeat goes back to the
     * last forward repeat (or the start, or just after the previous repeat)
     * until it has been played its number of times. Repeats don't nest.
     */
    fun unrolledOrder(measures: List<Measure>): List<Int> {
        val order = mutableListOf<Int>()
        val played = IntArray(measures.size)
        var sectionStart = 0
        var i = 0
        while (i < measures.size) {
            val measure = measures[i]
            if (measure.repeatStart) sectionStart = i
            order += i
            val times = measure.repeatTimes?.coerceIn(1, MAX_REPEAT_TIMES)
            if (times != null) {
                played[i]++
                if (played[i] < times) {
                    i = sectionStart
                    continue
                }
                played[i] = 0
                sectionStart = i + 1
            }
            i++
        }
        return order
    }

    private fun Hand.other() = if (this == Hand.RIGHT) Hand.LEFT else Hand.RIGHT
}
