package dev.co508.drums.lesson

import dev.co508.drums.musicxml.LessonXml
import dev.co508.drums.musicxml.testImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PackLoaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val loader = PackLoader(testImporter())

    /** The real built-in pack, from the app's assets (unit tests run in the module folder). */
    private val builtIn = File("src/main/assets/${PackLoader.BUILT_IN_FOLDER}")

    @Test
    fun theBuiltInPackLoadsStrictly() {
        val result = loader.load(DirectoryPackSource(builtIn), strict = true)
        assertTrue(result.toString(), result is PackResult.Success)
        val pack = (result as PackResult.Success).pack
        assertEquals("builtin", pack.manifest.id)
        assertEquals("CC-BY-SA-4.0", pack.manifest.license)
        assertEquals(
            listOf(
                "quarter-notes",
                "eighth-notes",
                "triplet-notes",
                "sixteenth-notes",
                "changing-subdivisions",
                "single-stroke-roll",
                "single-stroke-four",
                "single-stroke-seven",
                "multiple-bounce-roll",
                "triple-stroke-roll",
                "double-stroke-open-roll",
                "five-stroke-roll",
                "six-stroke-roll",
                "seven-stroke-roll",
                "nine-stroke-roll",
                "ten-stroke-roll",
                "eleven-stroke-roll",
                "thirteen-stroke-roll",
                "fifteen-stroke-roll",
                "seventeen-stroke-roll",
                "single-paradiddle",
                "double-paradiddle",
                "triple-paradiddle",
                "single-paradiddle-diddle",
                "flam",
                "flam-accent",
                "flam-tap",
                "flamacue",
                "flam-paradiddle",
                "single-flammed-mill",
                "flam-paradiddle-diddle",
                "pataflafla",
                "swiss-army-triplet",
                "inverted-flam-tap",
                "flam-drag",
                "drag",
                "single-drag-tap",
                "double-drag-tap",
                "lesson-25",
                "single-dragadiddle",
                "drag-paradiddle-1",
                "drag-paradiddle-2",
                "single-ratamacue",
                "double-ratamacue",
                "triple-ratamacue",
            ),
            pack.lessons.map { it.id },
        )
        // Every lesson's notes carry sticking, grace notes included (strict mode guarantees it).
        for (lesson in pack.lessons) {
            assertTrue(
                lesson.id,
                lesson.notation.notes.all { n ->
                    n.hand != null &&
                        n.graceNotes.all { it.hand != null }
                },
            )
        }
    }

    private fun pack(
        json: String,
        vararg files: Pair<String, String>,
    ): PackResult {
        val dir = temp.newFolder()
        File(dir, "pack.json").writeText(json)
        for ((name, content) in files) File(dir, name).writeText(content)
        return loader.load(DirectoryPackSource(dir), strict = true)
    }

    private fun errors(result: PackResult) = (result as PackResult.Failure).errors

    private fun manifest(
        lessons: String,
        format: Int = 1,
        id: String = "test",
    ) =
        """{"format": $format, "id": "$id", "title": "Test", "version": 1, "author": "Us", "license": "CC0-1.0", "lessons": [$lessons]}"""

    private fun lesson(
        id: String = "a",
        file: String = "a.musicxml",
        family: String = "rolls",
        tempo: String = """{"min": 60, "max": 120, "start": 80}""",
    ) =
        """{"id": "$id", "file": "$file", "title": "A", "family": "$family", "level": 1, "tempo": $tempo, "description": "D"}"""

    private val goodLesson = "a.musicxml" to LessonXml.score(LessonXml.rlrl)

    @Test
    fun aGoodPackLoads() {
        val result = pack(manifest(lesson()), goodLesson)
        assertEquals(
            emptyList<String>(),
            (result as PackResult.Success)
                .pack.lessons
                .single()
                .manifest.tips,
        )
    }

    @Test
    fun manifestProblemsAreAllReported() {
        val result =
            pack(
                manifest(
                    lesson(tempo = """{"min": 120, "max": 60, "start": 400}""") + "," + lesson(file = "a.xml"),
                    format = 2,
                    id = "Bad Id",
                ),
                goodLesson,
            )
        assertEquals(
            listOf(
                "pack.json: format 2 isn't supported (this app reads format 1).",
                "pack.json: pack id \"Bad Id\" should be lowercase letters, digits and dashes.",
                "pack.json: lesson ids appear more than once: a.",
                "pack.json: lesson \"a\": the tempo range 120-60 should lie within 30-300.",
                "pack.json: lesson \"a\": the start tempo 400 is outside 120-60.",
                "pack.json: lesson \"a\": the file should be an uncompressed .musicxml file.",
                "a.xml (lesson \"a\") is missing or unreadable.",
            ),
            errors(result),
        )
    }

    @Test
    fun lessonErrorsNameTheFileMeasureAndBeat() {
        val noSticking = "a.musicxml" to LessonXml.score(LessonXml.note() + LessonXml.rest(12))
        assertEquals(
            listOf("a.musicxml: Measure 1, beat 1: no sticking for this note."),
            errors(pack(manifest(lesson()), noSticking)),
        )
    }

    @Test
    fun malformedJsonAndUnknownFamiliesAreReadable() {
        assertTrue(errors(pack("{ not json")).single().startsWith("pack.json: "))
        val unknown = errors(pack(manifest(lesson(family = "cowbell")), goodLesson)).single()
        assertTrue(unknown, unknown.startsWith("pack.json: ") && "cowbell" in unknown)
        assertEquals(
            listOf("pack.json is missing."),
            errors(loader.load(DirectoryPackSource(temp.newFolder()), strict = true)),
        )
    }

    @Test
    fun lessonFilesCantEscapeThePackFolder() {
        val outside = File(temp.root, "outside.musicxml").apply { writeText(LessonXml.score(LessonXml.rlrl)) }
        assertTrue(outside.exists())
        val result = pack(manifest(lesson(file = "../outside.musicxml")))
        assertEquals(listOf("../outside.musicxml (lesson \"a\") is missing or unreadable."), errors(result))
    }
}
