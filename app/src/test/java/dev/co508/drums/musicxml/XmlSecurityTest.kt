package dev.co508.drums.musicxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Untrusted MusicXML must never make the importer read anything but the file
 * itself (DECISIONS.md, "XML: Pull Parser, No DTDs, No Entities").
 */
class XmlSecurityTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun import(xml: String) = testImporter().read(xml.byteInputStream(), strict = false)

    private fun errors(xml: String): List<String> = (import(xml) as ImportResult.Failure).errors.map { it.toString() }

    private val secret = "TOP-SECRET-${System.nanoTime()}"

    private fun secretFile() = temp.newFile("secret.txt").apply { writeText(secret) }

    @Test
    fun anExternalEntityIsRejectedNotRead() {
        val file = secretFile()
        val xml =
            """<?xml version="1.0"?>
            <!DOCTYPE score-partwise [ <!ENTITY xxe SYSTEM "${file.toURI()}"> ]>
            <score-partwise version="4.0"><work><work-title>&xxe;</work-title></work></score-partwise>"""
        val errors = errors(xml)
        assertEquals(listOf("The file declares its own DTD, which isn't allowed."), errors)
        assertFalse(errors.any { secret in it })
    }

    @Test
    fun anEntityExpansionBombIsRejected() {
        val lol = (1..9).joinToString("\n") { i -> "<!ENTITY lol$i \"${"&lol${i - 1};".repeat(10)}\">" }
        val xml =
            """<?xml version="1.0"?>
            <!DOCTYPE lolz [ <!ENTITY lol0 "lol"> $lol ]>
            <score-partwise>&lol9;</score-partwise>"""
        assertEquals(listOf("The file declares its own DTD, which isn't allowed."), errors(xml))
    }

    @Test
    fun anUndeclaredEntityIsRejected() {
        val xml = """<?xml version="1.0"?><score-partwise><work-title>&xxe;</work-title></score-partwise>"""
        assertEquals(listOf("The file uses the entity &xxe;, which isn't allowed."), errors(xml))
    }

    @Test
    fun anExternalDtdIsNeverFetched() {
        // A DTD on disk that would declare an entity. Not reading it leaves the entity undeclared.
        val dtd = temp.newFile("evil.dtd").apply { writeText("<!ENTITY xxe SYSTEM \"${secretFile().toURI()}\">") }
        val xml =
            """<?xml version="1.0"?>
            <!DOCTYPE score-partwise SYSTEM "${dtd.toURI()}">
            <score-partwise><work><work-title>&xxe;</work-title></work></score-partwise>"""
        val errors = errors(xml)
        assertEquals(listOf("The file uses the entity &xxe;, which isn't allowed."), errors)
        assertFalse(errors.any { secret in it })
    }

    @Test
    fun theStandardMusicXmlDoctypeImportsOfflineAndPredefinedEntitiesWork() {
        // The real header names a remote DTD; it's skipped, not fetched.
        val title = "<work-title>Rolls &amp; Flams &#x263A;</work-title>"
        val xml = LessonXml.score(LessonXml.rlrl).replace("<work-title>Test</work-title>", title)
        val result = import(xml)
        assertTrue(result.toString(), result is ImportResult.Success)
        assertEquals("Rolls & Flams \u263A", (result as ImportResult.Success).notation.title)
    }

    /** Like Android's Xml.newPullParser(), which turns DOCTYPE processing on. */
    private class DoctypeProcessingParser : KXmlParser() {
        var processDocdecl = true

        override fun getFeature(name: String?): Boolean =
            if (name == XmlPullParser.FEATURE_PROCESS_DOCDECL) processDocdecl else super.getFeature(name)

        override fun setFeature(
            name: String?,
            value: Boolean,
        ) {
            if (name == XmlPullParser.FEATURE_PROCESS_DOCDECL) processDocdecl = value else super.setFeature(name, value)
        }
    }

    @Test
    fun aParserThatProcessesDoctypesIsTurnedOffFirst() {
        val parser = DoctypeProcessingParser()
        val result = MusicXmlImporter { parser }.read(LessonXml.score(LessonXml.rlrl).byteInputStream(), strict = true)
        assertTrue(result.toString(), result is ImportResult.Success)
        assertFalse(parser.processDocdecl)
    }

    /** A parser that fails the test if it is ever handed a file. */
    private class UntouchableParser : KXmlParser() {
        override fun setInput(
            stream: InputStream?,
            encoding: String?,
        ) = throw AssertionError("the parser was given the file")
    }

    @Test
    fun entityDeclarationsAreRejectedBeforeAnyParserSeesThem() {
        val xml = """<?xml version="1.0"?><!DOCTYPE a [ <!ENTITY x "y"> ]><a>&x;</a>"""
        val result = MusicXmlImporter { UntouchableParser() }.read(xml.byteInputStream(), strict = false)
        assertEquals(
            listOf(
                "The file declares its own DTD, which isn't allowed.",
            ),
            (result as ImportResult.Failure).errors.map {
                it.toString()
            },
        )
        // The same in UTF-16, with a byte-order mark.
        val utf16 =
            MusicXmlImporter {
                UntouchableParser()
            }.read(xml.toByteArray(Charsets.UTF_16).inputStream(), strict = false)
        assertTrue(utf16 is ImportResult.Failure)
    }

    @Test
    fun hugeFilesAreRejected() {
        val big = ByteArray(XmlTree.MAX_BYTES + 1) { ' '.code.toByte() }
        val result = testImporter().read(big.inputStream(), strict = false)
        assertEquals(
            listOf("The file is too large to be a lesson."),
            (result as ImportResult.Failure).errors.map {
                it.toString()
            },
        )
    }

    @Test
    fun malformedXmlIsAReadableError() {
        val errors = errors("<score-partwise><part></score-partwise>")
        assertTrue(errors.single(), errors.single().startsWith("The file isn't valid XML"))
    }
}
