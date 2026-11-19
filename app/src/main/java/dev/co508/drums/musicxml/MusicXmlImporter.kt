package dev.co508.drums.musicxml

import android.util.Xml
import dev.co508.drums.lesson.Accent
import dev.co508.drums.lesson.DynamicMark
import dev.co508.drums.lesson.Fraction
import dev.co508.drums.lesson.GraceNote
import dev.co508.drums.lesson.Hand
import dev.co508.drums.lesson.Measure
import dev.co508.drums.lesson.Notation
import dev.co508.drums.lesson.Note
import dev.co508.drums.lesson.NoteValue
import dev.co508.drums.lesson.Ornament
import dev.co508.drums.lesson.Rest
import dev.co508.drums.lesson.Tempo
import dev.co508.drums.lesson.TimeSignature
import dev.co508.drums.lesson.Tuplet
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

/** A problem found while importing, located by measure and beat where possible. */
data class ImportProblem(
    val message: String,
    val measure: String? = null,
    val beat: String? = null,
) {
    override fun toString(): String =
        when {
            measure != null && beat != null -> "Measure $measure, $beat: $message"
            measure != null -> "Measure $measure: $message"
            else -> message
        }
}

sealed interface ImportResult {
    data class Success(
        val notation: Notation,
        val warnings: List<ImportProblem>,
    ) : ImportResult

    data class Failure(
        val errors: List<ImportProblem>,
    ) : ImportResult
}

/**
 * Reads a MusicXML lesson into a [Notation], following docs/musicxml-style.md.
 *
 * With [strict] on (built-in lessons), departures from the style guide are
 * errors. Every note needs sticking, in the one-word form, and the part must
 * be an unpitched snare. With it off (imported files), they are warnings. A
 * note without sticking then gets a null hand.
 *
 * Sticking is matched to notes by position in the measure, never by document
 * order. Words at one moment, from `<words>` directions and lyrics, are
 * merged and must parse unambiguously ([Sticking]). A single-letter
 * `<fingering>` sets its own note's hand.
 *
 * [newParser] makes the XML pull parser: Android's built-in one in the app,
 * KXml in JVM tests.
 */
class MusicXmlImporter(
    private val newParser: () -> XmlPullParser = { Xml.newPullParser() },
) {
    fun read(
        input: InputStream,
        strict: Boolean,
        partId: String? = null,
    ): ImportResult {
        val root =
            try {
                XmlTree.parse(newParser(), input)
            } catch (e: XmlSecurityException) {
                return failure(e.message.orEmpty())
            } catch (e: XmlPullParserException) {
                return failure("The file isn't valid XML: ${e.message}")
            } catch (e: IOException) {
                return failure("The file couldn't be read: ${e.message}")
            }
        return PartReader(root, strict, partId).read()
    }

    private fun failure(message: String) = ImportResult.Failure(listOf(ImportProblem(message)))
}

/** Thrown inside the reader to stop at a problem that makes the rest meaningless. */
private class Fatal(
    val problem: ImportProblem,
) : Exception(problem.toString())

private class PartReader(
    private val root: XmlElement,
    private val strict: Boolean,
    private val partId: String?,
) {
    private val errors = mutableListOf<ImportProblem>()
    private val warnings = mutableListOf<ImportProblem>()

    /** A style problem: an error for built-in lessons, a warning for imports. */
    private fun styleProblem(problem: ImportProblem) {
        if (strict) errors += problem else warnings += problem
    }

    fun read(): ImportResult =
        try {
            val notation = readNotation()
            if (errors.isEmpty()) ImportResult.Success(notation, warnings) else ImportResult.Failure(errors)
        } catch (f: Fatal) {
            ImportResult.Failure(errors + f.problem)
        }

    private fun fatal(
        message: String,
        measure: String? = null,
        beat: String? = null,
    ): Nothing = throw Fatal(ImportProblem(message, measure, beat))

    private fun readNotation(): Notation {
        if (root.name == "score-timewise") fatal("Timewise MusicXML isn't supported; export it as partwise.")
        if (root.name != "score-partwise") fatal("This isn't a MusicXML score (the root element is <${root.name}>).")

        val scoreParts = root.child("part-list")?.children("score-part").orEmpty()
        val parts = root.children("part")
        val part =
            when {
                partId != null ->
                    parts.firstOrNull { it.attribute("id") == partId }
                        ?: fatal("The score has no part \"$partId\".")
                parts.size == 1 -> parts.single()
                parts.isEmpty() -> fatal("The score has no parts.")
                else -> fatal("The score has ${parts.size} parts; scores with more than one part aren't supported yet.")
            }
        val scorePart = scoreParts.firstOrNull { it.attribute("id") == part.attribute("id") }
        checkSnare(scorePart)

        val title = root.child("work")?.child("work-title")?.text ?: root.child("movement-title")?.text
        val measures = readMeasures(part)
        if (measures.none { it.notes.isNotEmpty() }) fatal("The part has no notes.")
        return Notation(title?.ifBlank { null }, scorePart?.child("part-name")?.text, measures)
    }

    private fun checkSnare(scorePart: XmlElement?) {
        val unpitched = scorePart?.descendants("midi-unpitched")?.map { it.text }.orEmpty()
        val names = scorePart?.descendants("instrument-name")?.map { it.text.lowercase() }.orEmpty()
        val isSnare = "39" in unpitched || names.any { "snare" in it }
        if (!isSnare) {
            styleProblem(
                ImportProblem(
                    "The part isn't marked as a snare (MIDI unpitched 39, or an instrument named \"snare\").",
                ),
            )
        }
    }

    // Carried from measure to measure.
    private var previousHand: Hand? = null
    private var divisions: Long = 0
    private var timeSignature = TimeSignature.FOUR_FOUR

    private fun readMeasures(part: XmlElement): List<Measure> {
        var start = Fraction.ZERO
        return part.children("measure").map { element ->
            val measure = readMeasure(element, start)
            start += measure.length
            measure
        }
    }

    private class PendingNote(
        val element: XmlElement,
        val offset: Fraction,
        val duration: Fraction,
        val graces: List<XmlElement>,
    )

    private fun readMeasure(
        element: XmlElement,
        start: Fraction,
    ): Measure {
        val number = element.attribute("number") ?: "?"
        var cursor = Fraction.ZERO
        var end = Fraction.ZERO
        val notes = mutableListOf<PendingNote>()
        val rests = mutableListOf<Rest>()
        val dynamics = mutableListOf<DynamicMark>()
        val tempos = mutableListOf<Tempo>()
        val words = mutableMapOf<Fraction, MutableList<String>>()
        var graces = mutableListOf<XmlElement>()
        var repeatStart = false
        var repeatTimes: Int? = null

        fun duration(of: XmlElement): Fraction {
            val value =
                of.child("duration")?.text?.toLongOrNull() ?: fatal("A note has no duration.", number, beat(cursor))
            if (divisions <= 0) fatal("The file doesn't set <divisions> before its first note.", number)
            return Fraction.of(value, divisions)
        }

        for (child in element.children) {
            when (child.name) {
                "attributes" -> readAttributes(child, number)
                "direction" -> {
                    val offset =
                        cursor +
                            (
                                child
                                    .child(
                                        "offset",
                                    )?.text
                                    ?.toLongOrNull()
                                    ?.let { Fraction.of(it, divisions.coerceAtLeast(1)) }
                                    ?: Fraction.ZERO
                            )
                    for (type in child.children("direction-type")) {
                        for (w in type.children("words")) {
                            if (Sticking.isSticking(w.text)) words.getOrPut(offset) { mutableListOf() } += w.text
                        }
                        type
                            .child(
                                "dynamics",
                            )?.children
                            ?.firstOrNull()
                            ?.let { dynamics += DynamicMark(offset, it.name) }
                    }
                    child
                        .child("sound")
                        ?.attribute("tempo")
                        ?.toDoubleOrNull()
                        ?.let { tempos += Tempo(offset, it) }
                }
                "sound" -> child.attribute("tempo")?.toDoubleOrNull()?.let { tempos += Tempo(cursor, it) }
                "note" -> {
                    val voice = child.child("voice")?.text
                    if (voice != null &&
                        voice != "1"
                    ) {
                        fatal("Only one voice is supported; this note is in voice $voice.", number, beat(cursor))
                    }
                    when {
                        child.has("grace") -> graces += child
                        child.has(
                            "chord",
                        ) -> fatal("Chords (two notes at once) aren't supported.", number, beat(cursor))
                        child.has("rest") -> {
                            if (graces.isNotEmpty()) fatal("Grace notes lead into a rest.", number, beat(cursor))
                            val d = duration(child)
                            rests += Rest(cursor, d)
                            cursor += d
                        }
                        else -> {
                            val d = duration(child)
                            if (child.has("pitch")) {
                                styleProblem(
                                    ImportProblem(
                                        "The note is pitched; snare notes should be <unpitched>.",
                                        number,
                                        beat(cursor),
                                    ),
                                )
                            }
                            child
                                .children(
                                    "lyric",
                                ).mapNotNull { it.child("text")?.text }
                                .filter(Sticking::isSticking)
                                .forEach {
                                    words.getOrPut(cursor) { mutableListOf() } += it
                                }
                            notes += PendingNote(child, cursor, d, graces)
                            graces = mutableListOf()
                            cursor += d
                        }
                    }
                }
                "backup" -> fatal("Only one voice is supported; the measure goes back in time (<backup>).", number)
                "forward" -> cursor += duration(child)
                "barline" -> {
                    if (child.has("ending")) fatal("Voltas (first and second endings) aren't supported yet.", number)
                    when (child.child("repeat")?.attribute("direction")) {
                        "forward" -> repeatStart = true
                        "backward" -> repeatTimes = child.child("repeat")?.attribute("times")?.toIntOrNull() ?: 2
                    }
                }
            }
            if (cursor > end) end = cursor
        }
        if (graces.isNotEmpty()) fatal("Grace notes at the end of the measure lead into no note.", number)

        val finished = notes.map { finishNote(it, words, number) }
        for ((offset, w) in words) {
            if (notes.none { it.offset == offset }) {
                val onRest = rests.any { it.offset == offset }
                val problem =
                    ImportProblem(
                        "sticking ${w.joinToString(
                            " + ",
                        ) { "\"$it\"" }} is ${if (onRest) "on a rest" else "on no note"}.",
                        number,
                        beat(offset),
                    )
                if (onRest) styleProblem(problem) else warnings += problem
            }
        }
        return Measure(
            number = number,
            start = start,
            length = end,
            timeSignature = timeSignature,
            notes = finished,
            rests = rests,
            dynamics = dynamics,
            tempos = tempos,
            repeatStart = repeatStart,
            repeatTimes = repeatTimes,
        )
    }

    private fun readAttributes(
        attributes: XmlElement,
        number: String,
    ) {
        attributes.child("divisions")?.text?.toLongOrNull()?.let {
            if (it <= 0) fatal("<divisions> must be positive.", number)
            divisions = it
        }
        attributes.child("time")?.let { time ->
            val beats = time.child("beats")?.text?.toIntOrNull()
            val beatType = time.child("beat-type")?.text?.toIntOrNull()
            if (beats == null || beatType == null || beats <= 0 || beatType <= 0) {
                fatal("Unsupported time signature (only simple n/d signatures are).", number)
            }
            timeSignature = TimeSignature(beats, beatType)
        }
        attributes.child("staff-details")?.child("staff-lines")?.text?.toIntOrNull()?.let {
            if (it != 1) styleProblem(ImportProblem("The staff has $it lines; lessons use a one-line staff.", number))
        }
    }

    private fun finishNote(
        pending: PendingNote,
        words: Map<Fraction, List<String>>,
        number: String,
    ): Note {
        val element = pending.element
        val beat = beat(pending.offset)
        val notations = element.children("notations")
        val graceCount = pending.graces.size

        // Hands from sticking words at this moment, else from each note's fingering.
        var mainHand = fingering(element)
        val graceHands = MutableList(graceCount) { fingering(pending.graces[it]) }
        val here = words[pending.offset]
        var invalidSticking = false
        if (here != null) {
            when (val result = Sticking.parse(here, graceCount)) {
                is StickingResult.Ok -> {
                    mainHand = result.assignment.mainHand
                    result.assignment.graceHands.forEachIndexed { i, h -> graceHands[i] = h }
                    if (result.offStyle) {
                        styleProblem(
                            ImportProblem(
                                "sticking ${here.joinToString(
                                    " + ",
                                ) { "\"$it\"" }} should be one word with lowercase grace notes, like \"lR\".",
                                number,
                                beat,
                            ),
                        )
                    }
                }
                is StickingResult.Invalid -> {
                    errors += ImportProblem(result.reason + ".", number, beat)
                    invalidSticking = true
                }
            }
        }
        val tieStops = element.children("tie").any { it.attribute("type") == "stop" }
        // A tied continuation isn't a new stroke: it keeps the hand of the note it's tied from.
        if (mainHand == null && tieStops && graceCount == 0) mainHand = previousHand
        if (!invalidSticking && (mainHand == null || graceHands.any { it == null })) {
            styleProblem(ImportProblem("no sticking for this note.", number, beat))
        }
        previousHand = mainHand

        val ornaments = notations.flatMap { it.children("ornaments") }
        val tremolo = ornaments.flatMap { it.children("tremolo") }.firstOrNull()
        val ornament =
            tremolo?.let {
                when (it.attribute("type") ?: "single") {
                    "single" -> {
                        val slashes = it.text.toIntOrNull()
                        if (slashes == null ||
                            slashes !in 1..8
                        ) {
                            fatal("A tremolo needs 1 to 8 slashes, not \"${it.text}\".", number, beat)
                        }
                        Ornament.Tremolo(slashes)
                    }
                    "unmeasured" -> Ornament.Buzz
                    else -> fatal("Two-note tremolos aren't supported.", number, beat)
                }
            }
        val articulations = notations.flatMap { it.children("articulations") }
        val accent =
            when {
                articulations.any { it.has("strong-accent") } -> Accent.MARCATO
                articulations.any { it.has("accent") } -> Accent.ACCENT
                else -> Accent.NONE
            }
        val tuplet =
            element.child("time-modification")?.let { tm ->
                val actual = tm.child("actual-notes")?.text?.toIntOrNull()
                val normal = tm.child("normal-notes")?.text?.toIntOrNull()
                if (actual == null ||
                    normal == null ||
                    actual <= 0 ||
                    normal <= 0
                ) {
                    fatal("A tuplet has no valid ratio.", number, beat)
                }
                val marks = notations.flatMap { it.children("tuplet") }.map { it.attribute("type") }
                Tuplet(actual, normal, starts = "start" in marks, stops = "stop" in marks)
            }
        val ties = element.children("tie").map { it.attribute("type") }

        return Note(
            offset = pending.offset,
            duration = pending.duration,
            hand = mainHand,
            value = element.child("type")?.text?.let(NoteValue::fromMusicXml),
            dots = element.children("dot").size,
            graceNotes =
                pending.graces.mapIndexed { i, g ->
                    GraceNote(
                        hand = graceHands[i],
                        slashed = g.child("grace")?.attribute("slash") == "yes",
                        value = g.child("type")?.text?.let(NoteValue::fromMusicXml),
                    )
                },
            accent = accent,
            ghost = element.child("notehead")?.attribute("parentheses") == "yes",
            ornament = ornament,
            tuplet = tuplet,
            tieStart = "start" in ties,
            tieStop = "stop" in ties,
        )
    }

    /** A single-letter `<fingering>` on a note, read as its hand. */
    private fun fingering(note: XmlElement): Hand? {
        val text =
            note
                .children("notations")
                .flatMap { it.children("technical") }
                .flatMap { it.children("fingering") }
                .firstOrNull()
                ?.text
                ?.trim()
        return when (text) {
            "R", "r" -> Hand.RIGHT
            "L", "l" -> Hand.LEFT
            else -> null
        }
    }

    /** "beat 2", or "beat 2 + 1/4" between beats, in the current time signature's beats. */
    private fun beat(offset: Fraction): String {
        val beats = offset / timeSignature.beatQuarters
        val whole = beats.floor()
        val rest = beats - Fraction.of(whole)
        return if (rest == Fraction.ZERO) "beat ${whole + 1}" else "beat ${whole + 1} + $rest"
    }
}
