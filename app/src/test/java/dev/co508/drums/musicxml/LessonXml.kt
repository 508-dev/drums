package dev.co508.drums.musicxml

import org.kxml2.io.KXmlParser
import java.io.InputStream

/** The importer as tests use it: KXml, Android's parser, instead of the android.jar stub. */
fun testImporter() = MusicXmlImporter { KXmlParser() }

fun fixture(name: String): InputStream =
    object {}.javaClass.getResourceAsStream("/musicxml/musescore-4.7.5/$name") ?: error("missing fixture $name")

/** Builds small MusicXML lessons in the style guide's form. */
object LessonXml {
    const val HEADER =
        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">"""

    fun score(
        vararg measures: String,
        parts: Int = 1,
        divisions: Int = 4,
        time: String = "<beats>4</beats><beat-type>4</beat-type>",
    ): String {
        val partList =
            (1..parts).joinToString("") {
                """<score-part id="P$it"><part-name>Snare Drum</part-name>
                <score-instrument id="P$it-I39"><instrument-name>Acoustic Snare</instrument-name></score-instrument>
                <midi-instrument id="P$it-I39"><midi-unpitched>39</midi-unpitched></midi-instrument></score-part>"""
            }
        val attributes =
            "<attributes><divisions>$divisions</divisions><time>$time</time><clef><sign>percussion</sign></clef>" +
                "<staff-details><staff-lines>1</staff-lines></staff-details></attributes>"
        val body =
            measures
                .mapIndexed { i, m -> """<measure number="${i + 1}">${if (i == 0) attributes else ""}$m</measure>""" }
                .joinToString("")
        val partsXml = (1..parts).joinToString("") { """<part id="P$it">$body</part>""" }
        return "$HEADER<score-partwise version=\"4.0\"><work><work-title>Test</work-title></work>" +
            "<part-list>$partList</part-list>$partsXml</score-partwise>"
    }

    fun words(text: String) =
        """<direction placement="below"><direction-type><words>$text</words></direction-type></direction>"""

    fun note(
        duration: Int = 4,
        type: String = "quarter",
        extra: String = "",
        notations: String = "",
    ) = """<note><unpitched><display-step>E</display-step><display-octave>4</display-octave></unpitched>""" +
        """<duration>$duration</duration>$extra<voice>1</voice><type>$type</type>""" +
        (if (notations.isEmpty()) "" else "<notations>$notations</notations>") + "</note>"

    fun grace(slash: Boolean = true) =
        """<note><grace${if (slash) " slash=\"yes\"" else ""}/><unpitched><display-step>E</display-step>""" +
            """<display-octave>4</display-octave></unpitched><voice>1</voice><type>eighth</type></note>"""

    fun rest(duration: Int = 4) =
        """<note><rest/><duration>$duration</duration><voice>1</voice><type>quarter</type></note>"""

    /** Four quarter notes, R L R L, the style guide's way. */
    val rlrl = listOf("R", "L", "R", "L").joinToString("") { words(it) + note() }
}
