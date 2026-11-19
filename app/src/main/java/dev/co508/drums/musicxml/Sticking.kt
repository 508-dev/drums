package dev.co508.drums.musicxml

import dev.co508.drums.lesson.Hand

/** Hands for one note and the grace notes leading into it, in playing order. */
data class StickingAssignment(
    val graceHands: List<Hand>,
    val mainHand: Hand,
)

sealed interface StickingResult {
    data class Ok(
        val assignment: StickingAssignment,
        /** True when the words broke the style guide's form (single word, lowercase graces, uppercase main). */
        val offStyle: Boolean,
    ) : StickingResult

    data class Invalid(
        val reason: String,
    ) : StickingResult
}

/**
 * Turns the sticking words found at one moment into hands for the note there,
 * per docs/musicxml-style.md.
 *
 * The style-guide form is one word: a lowercase letter per grace note, in
 * playing order, then the uppercase main note (`R`, `lR`, `llR`). Files from
 * MuseScore may instead stack several words at one moment, in an order that
 * isn't reliable. They are merged by trying every order. The result is
 * accepted only if every order that reads validly gives the same hands.
 * Letters are case-insensitive, but where cases are mixed the lowercase ones
 * must be the grace notes.
 */
object Sticking {
    private val word = Regex("^[rlRL]+$")
    private const val MAX_WORDS = 5

    /** Whether [text] looks like sticking at all (as opposed to "rit.", "Fine"…). */
    fun isSticking(text: String): Boolean = word.matches(text.trim())

    fun parse(
        words: List<String>,
        graceNotes: Int,
    ): StickingResult {
        val trimmed = words.map { it.trim() }
        require(trimmed.isNotEmpty() && trimmed.all(::isSticking)) { "not sticking: $words" }
        val letters = trimmed.sumOf { it.length }
        if (letters != graceNotes + 1) {
            val expected =
                if (graceNotes ==
                    0
                ) {
                    "one letter"
                } else {
                    "${graceNotes + 1} letters ($graceNotes grace notes and the note)"
                }
            return StickingResult.Invalid("sticking ${quote(trimmed)} has $letters letters; expected $expected")
        }
        if (trimmed.size > MAX_WORDS) return StickingResult.Invalid("too many sticking words: ${quote(trimmed)}")

        val readings = permutations(trimmed).mapNotNull { read(it.joinToString("")) }.distinct()
        return when {
            readings.isEmpty() ->
                StickingResult.Invalid(
                    "sticking ${quote(trimmed)} puts an uppercase letter on a grace note; " +
                        "write grace notes lowercase before the note, like \"lR\"",
                )
            readings.size > 1 ->
                StickingResult.Invalid(
                    "sticking ${quote(trimmed)} is ambiguous: the words are stacked at one note, " +
                        "so their order is unknown; write one word like \"lR\"",
                )
            else -> {
                val styled =
                    trimmed.size == 1 &&
                        trimmed[0].dropLast(1).all { it.isLowerCase() } &&
                        trimmed[0].last().isUpperCase()
                StickingResult.Ok(readings.single(), offStyle = !styled)
            }
        }
    }

    /** Reads one concatenation, or null if mixed case puts an uppercase letter before a lowercase one. */
    private fun read(s: String): StickingAssignment? {
        val mixed = s.any { it.isLowerCase() } && s.any { it.isUpperCase() }
        if (mixed && (s.last().isLowerCase() || s.dropLast(1).any { it.isUpperCase() })) return null
        return StickingAssignment(s.dropLast(1).map(::hand), hand(s.last()))
    }

    private fun hand(c: Char) = if (c == 'r' || c == 'R') Hand.RIGHT else Hand.LEFT

    private fun quote(words: List<String>) = words.joinToString(" + ") { "\"$it\"" }

    private fun <T> permutations(items: List<T>): List<List<T>> =
        if (items.size <= 1) {
            listOf(items)
        } else {
            items.indices.flatMap { i ->
                permutations(items.take(i) + items.drop(i + 1)).map { listOf(items[i]) + it }
            }
        }
}
