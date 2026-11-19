package dev.co508.drums.lesson

/**
 * What a lesson's notation says, as written: measures of notes with hands,
 * accents, grace notes, tremolos, buzz rolls, tuplets, ties and repeats. It
 * isn't yet a timeline of strokes; expanding it into one (grace timing,
 * diddles, buzz, repeats) is a separate step.
 *
 * Positions and durations are exact [Fraction]s of a quarter note. A note's
 * [Note.offset] is from the start of its measure; [Measure.start] is from the
 * start of the piece as written, ignoring repeats.
 *
 * Every lesson has one instrument (DECISIONS.md, "Snare Rudiments, One
 * Instrument Forever"). A multi-part score yields one [Notation] per part,
 * and the importer picks which one.
 */
data class Notation(
    val title: String?,
    val partName: String?,
    val measures: List<Measure>,
) {
    /** The first tempo mark, if any. */
    val tempo: Tempo? get() = measures.firstNotNullOfOrNull { it.tempos.firstOrNull() }

    /** Every note, in order. */
    val notes: List<Note> get() = measures.flatMap { it.notes }
}

data class Measure(
    /** The measure's label in the file (MusicXML `number`), for messages. */
    val number: String,
    val start: Fraction,
    /** Actual length: shorter than the time signature for a pickup. */
    val length: Fraction,
    val timeSignature: TimeSignature,
    /** Notes in time order. */
    val notes: List<Note>,
    val rests: List<Rest> = emptyList(),
    val dynamics: List<DynamicMark> = emptyList(),
    val tempos: List<Tempo> = emptyList(),
    /** A forward repeat barline at the start. */
    val repeatStart: Boolean = false,
    /** A backward repeat barline at the end: play the section this many times in all. */
    val repeatTimes: Int? = null,
)

data class TimeSignature(
    val beats: Int,
    val beatType: Int,
) {
    init {
        require(beats > 0 && beatType > 0) { "invalid time signature $beats/$beatType" }
    }

    /** 6/8, 9/8, 12/8…: counted in dotted quarters. */
    val isCompound: Boolean get() = beatType >= 8 && beats > 3 && beats % 3 == 0

    /** The counted beat, in quarter notes: a dotted quarter in compound time. */
    val beatQuarters: Fraction
        get() = if (isCompound) Fraction.of(4 * 3, beatType.toLong()) else Fraction.of(4, beatType.toLong())

    val beatsPerBar: Int get() = if (isCompound) beats / 3 else beats

    val barQuarters: Fraction get() = Fraction.of(4L * beats, beatType.toLong())

    override fun toString() = "$beats/$beatType"

    companion object {
        val FOUR_FOUR = TimeSignature(4, 4)
    }
}

enum class Hand { RIGHT, LEFT }

enum class Accent { NONE, ACCENT, MARCATO }

/** Written note values, with the number of flags or beams each has. */
enum class NoteValue(
    val musicXml: String,
    val beams: Int,
) {
    WHOLE("whole", 0),
    HALF("half", 0),
    QUARTER("quarter", 0),
    EIGHTH("eighth", 1),
    SIXTEENTH("16th", 2),
    THIRTY_SECOND("32nd", 3),
    SIXTY_FOURTH("64th", 4),
    ;

    companion object {
        fun fromMusicXml(type: String): NoteValue? = entries.firstOrNull { it.musicXml == type }
    }
}

sealed interface Ornament {
    /** Single-note tremolo: each slash adds a beam, splitting the note into diddle strokes. */
    data class Tremolo(
        val slashes: Int,
    ) : Ornament

    /** A buzz (multiple-bounce) roll lasting the note. */
    data object Buzz : Ornament
}

data class Tuplet(
    val actualNotes: Int,
    val normalNotes: Int,
    val starts: Boolean = false,
    val stops: Boolean = false,
)

data class GraceNote(
    val hand: Hand?,
    val slashed: Boolean,
    val value: NoteValue?,
)

/**
 * One written note (stroke) with the grace notes that lead into it.
 * [hand] is null when the file has no sticking for it.
 */
data class Note(
    val offset: Fraction,
    val duration: Fraction,
    val hand: Hand?,
    val value: NoteValue? = null,
    val dots: Int = 0,
    val graceNotes: List<GraceNote> = emptyList(),
    val accent: Accent = Accent.NONE,
    val ghost: Boolean = false,
    val ornament: Ornament? = null,
    val tuplet: Tuplet? = null,
    val tieStart: Boolean = false,
    val tieStop: Boolean = false,
)

data class Rest(
    val offset: Fraction,
    val duration: Fraction,
)

/** A dynamic mark such as "p" or "mf", from the given position on. */
data class DynamicMark(
    val offset: Fraction,
    val level: String,
)

/** A tempo mark. MusicXML's `<sound tempo>` is always in quarter notes per minute. */
data class Tempo(
    val offset: Fraction,
    val quarterNotesPerMinute: Double,
)
