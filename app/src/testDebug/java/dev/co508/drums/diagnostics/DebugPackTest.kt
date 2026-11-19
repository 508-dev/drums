package dev.co508.drums.diagnostics

import dev.co508.drums.lesson.DirectoryPackSource
import dev.co508.drums.lesson.Expansion
import dev.co508.drums.lesson.PackLoader
import dev.co508.drums.lesson.PackResult
import dev.co508.drums.lesson.StrokeDynamic
import dev.co508.drums.musicxml.MusicXmlImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.File

class DebugPackTest {
    @Test
    fun theOrnamentCheckLessonLoadsStrictlyAndExpands() {
        val result =
            PackLoader(
                MusicXmlImporter {
                    KXmlParser()
                },
            ).load(DirectoryPackSource(File("src/debug/assets/packs/debug")), strict = true)
        assertTrue(result.toString(), result is PackResult.Success)
        val lesson = (result as PackResult.Success).pack.lessons.single()
        val strokes = Expansion.expand(lesson.notation).strokes
        // Two passes of: 2 flams (4 strokes), 2 drags (6), accent, ghost, 2 diddles (4), buzz.
        assertEquals(2 * (4 + 6 + 2 + 4 + 1), strokes.size)
        assertEquals(2 * 6, strokes.count { it.dynamic == StrokeDynamic.GRACE })
        assertEquals(2, strokes.count { it.buzzLength != null })
    }
}
