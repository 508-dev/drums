package dev.co508.drums.musicxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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

    @Test
    fun malformedXmlIsAReadableError() {
        val errors = errors("<score-partwise><part></score-partwise>")
        assertTrue(errors.single(), errors.single().startsWith("The file isn't valid XML"))
    }
}
