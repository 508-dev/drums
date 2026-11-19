package dev.co508.drums.musicxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pins what MuseScore actually exports, as recorded in docs/musicxml-style.md,
 * to the reference fixtures. If a re-export with a newer MuseScore
 * (scripts/export-musescore-fixtures.sh) changes any of this, the style guide
 * and the importer need another look.
 */
class MuseScoreFixturesTest {
    private fun load(name: String): Element {
        val stream =
            javaClass.getResourceAsStream("/musicxml/musescore-4.7.5/$name")
                ?: error("missing fixture $name")
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        return stream.use { factory.newDocumentBuilder().parse(it).documentElement }
    }

    private fun Element.children(tag: String? = null): List<Element> {
        val out = mutableListOf<Element>()
        var n = firstChild
        while (n != null) {
            if (n is Element && (tag == null || n.tagName == tag)) out += n
            n = n.nextSibling
        }
        return out
    }

    private fun Element.child(tag: String): Element? = children(tag).firstOrNull()

    private fun Element.descendants(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return List(nodes.length) { nodes.item(it) as Element }
    }

    private fun Element.measure(number: Int): Element =
        child("part")!!.children("measure").first { it.getAttribute("number") == number.toString() }

    /**
     * A measure's children in document order as short tokens: `w:R` for sticking
     * words, `tempo`, `dyn`, `g` for a grace note, `n` for a note (with
     * `>` accent, `^` marcato, `()` ghost), `rest`, `|:` and `:|` for repeats.
     */
    private fun tokens(measure: Element): List<String> =
        measure.children().mapNotNull { e ->
            when (e.tagName) {
                "direction" -> {
                    val type = e.child("direction-type")!!
                    when {
                        type.child("words") != null -> "w:" + type.child("words")!!.textContent
                        type.child("metronome") != null -> "tempo"
                        type.child("dynamics") != null -> "dyn"
                        else -> "direction"
                    }
                }
                "note" ->
                    when {
                        e.child("grace") != null -> "g"
                        e.child("rest") != null -> "rest"
                        else ->
                            "n" +
                                (if (e.descendants("accent").isNotEmpty()) ">" else "") +
                                (if (e.descendants("strong-accent").isNotEmpty()) "^" else "") +
                                (if (e.child("notehead")?.getAttribute("parentheses") == "yes") "()" else "")
                    }
                "barline" -> {
                    val repeat = e.child("repeat")?.getAttribute("direction")
                    if (repeat == "forward") {
                        "|:"
                    } else if (repeat == "backward") {
                        ":|"
                    } else {
                        null
                    }
                }
                else -> null
            }
        }

    @Test
    fun theSnareIsAOneLinePercussionPart() {
        val score = load("sticking-and-ornaments.musicxml")
        assertEquals("4.0", score.getAttribute("version"))
        val instrument = score.descendants("score-instrument").single()
        assertEquals("P1-I39", instrument.getAttribute("id"))
        assertEquals("Acoustic Snare", instrument.child("instrument-name")!!.textContent)
        assertEquals("39", score.descendants("midi-unpitched").single().textContent)
        assertEquals("percussion", score.descendants("sign").single().textContent)
        assertEquals("1", score.descendants("staff-lines").single().textContent)
        for (note in score.descendants("note").filter { it.child("rest") == null }) {
            val unpitched = note.child("unpitched")!!
            assertEquals("E", unpitched.child("display-step")!!.textContent)
            assertEquals("4", unpitched.child("display-octave")!!.textContent)
            assertEquals("P1-I39", note.child("instrument")!!.getAttribute("id"))
            assertEquals("1", note.child("voice")!!.textContent)
        }
        assertTrue(score.descendants("backup").isEmpty() && score.descendants("forward").isEmpty())
    }

    @Test
    fun stickingWordsComeBeforeTheirNotesAndBeforeGraceNotes() {
        val score = load("sticking-and-ornaments.musicxml")
        assertEquals(
            listOf("w:R", "tempo", "n>", "w:L", "n()", "w:lR", "g", "n", "w:llR", "g", "g", "n"),
            tokens(score.measure(1)),
        )
        for (words in score.descendants("words")) {
            assertEquals("below", (words.parentNode.parentNode as Element).getAttribute("placement"))
        }
    }

    @Test
    fun aGraceNotesOwnStickingIsJustAnotherWordAtTheSameTime() {
        // Two Stickings on the flam (L for the grace note, R for the main note)
        // export as two loose words before the grace note, in entry order.
        val score = load("grace-sticking-stacked.musicxml")
        assertEquals(
            listOf("w:R", "tempo", "n>", "w:L", "n()", "w:L", "w:R", "g", "n", "w:llR", "g", "g", "n"),
            tokens(score.measure(1)),
        )
    }

    @Test
    fun flamsAndDragsAreSlashedOrPlainGraceNotesWithoutDuration() {
        val graces = load("sticking-and-ornaments.musicxml").descendants("note").filter { it.child("grace") != null }
        assertEquals(listOf("yes", "", ""), graces.map { it.child("grace")!!.getAttribute("slash") })
        assertEquals(listOf("eighth", "16th", "16th"), graces.map { it.child("type")!!.textContent })
        assertTrue(graces.all { it.child("duration") == null })
    }

    @Test
    fun diddlesBuzzRollsTupletsTiesAndRepeats() {
        val measure = load("sticking-and-ornaments.musicxml").measure(2)
        assertEquals(
            listOf("|:", "w:R", "n", "w:L", "n", "w:R", "n", "w:L", "n", "w:R", "n", "w:R", "n", "n", ":|"),
            tokens(measure),
        )
        val notes = measure.children("note")
        val tremolos = notes.map { it.descendants("tremolo").singleOrNull() }
        assertEquals(listOf("single", "single"), tremolos.take(2).map { it!!.getAttribute("type") })
        assertEquals(listOf("1", "1"), tremolos.take(2).map { it!!.textContent })
        // Buzz roll: the value is "0", and both tied notes carry it.
        assertEquals(listOf("unmeasured", "unmeasured"), tremolos.takeLast(2).map { it!!.getAttribute("type") })
        assertEquals(listOf("0", "0"), tremolos.takeLast(2).map { it!!.textContent })
        assertEquals(listOf("start", "stop"), notes.takeLast(2).map { it.child("tie")!!.getAttribute("type") })
        assertEquals(
            listOf("start", "stop"),
            notes.takeLast(2).map { it.descendants("tied").single().getAttribute("type") },
        )
        val triplet = notes.subList(2, 5)
        assertTrue(triplet.all { it.descendants("actual-notes").single().textContent == "3" })
        assertTrue(triplet.all { it.descendants("normal-notes").single().textContent == "2" })
        assertEquals(
            "start",
            triplet
                .first()
                .descendants("tuplet")
                .single()
                .getAttribute("type"),
        )
        assertEquals(
            "stop",
            triplet
                .last()
                .descendants("tuplet")
                .single()
                .getAttribute("type"),
        )
        // The default two passes: no times attribute.
        assertEquals("", measure.descendants("repeat").last().getAttribute("times"))
    }

    @Test
    fun tempoIsInQuarterNotesWhateverTheBeat() {
        val score = load("compound-and-repeats.musicxml")
        assertEquals("100", load("sticking-and-ornaments.musicxml").descendants("sound").single().getAttribute("tempo"))
        val metronome = score.descendants("metronome").single()
        assertEquals("quarter", metronome.child("beat-unit")!!.textContent)
        assertTrue(metronome.child("beat-unit-dot") != null)
        assertEquals("60", metronome.child("per-minute")!!.textContent)
        // Dotted quarter = 60 is 90 quarter notes a minute.
        assertEquals("90", score.descendants("sound").first { it.hasAttribute("tempo") }.getAttribute("tempo"))
    }

    @Test
    fun compoundMeterDynamicsMarcatoAndRepeatCounts() {
        val score = load("compound-and-repeats.musicxml")
        assertEquals("6", score.descendants("beats").single().textContent)
        assertEquals("8", score.descendants("beat-type").single().textContent)
        assertEquals(
            // Directions at one time come in MuseScore's order, not the order they were added.
            listOf("w:R", "dyn", "tempo", "n^", "w:L", "n", "w:R", "n", "w:L", "dyn", "n"),
            tokens(score.measure(1)),
        )
        val dottedDiddle = score.measure(1).children("note").last()
        assertTrue(dottedDiddle.child("dot") != null)
        assertEquals("2", dottedDiddle.descendants("tremolo").single().textContent)
        assertEquals(listOf("|:", "w:R", "n", "rest", ":|"), tokens(score.measure(2)))
        assertEquals(
            "3",
            score
                .measure(2)
                .descendants("repeat")
                .last()
                .getAttribute("times"),
        )
    }
}
