package dev.co508.drums.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.co508.drums.lesson.DirectoryPackSource
import dev.co508.drums.lesson.Expansion
import dev.co508.drums.lesson.Notation
import dev.co508.drums.lesson.PackLoader
import dev.co508.drums.lesson.PackResult
import dev.co508.drums.musicxml.ImportResult
import dev.co508.drums.musicxml.MusicXmlImporter
import dev.co508.drums.musicxml.fixture
import dev.co508.drums.ui.sheet.SheetView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kxml2.io.KXmlParser
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshots of the sheet view: every built-in lesson, plus the MuseScore
 * fixtures for what the lessons don't use (ties, rests, dots, ghost notes,
 * 6/8). Compare with the MuseScore renders in lessons/builtin/renders.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h420dp-xxhdpi")
class SheetScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val importer = MusicXmlImporter { KXmlParser() }
    private val pack =
        (
            PackLoader(
                importer,
            ).load(
                DirectoryPackSource(File("src/main/assets/${PackLoader.BUILT_IN_FOLDER}")),
                strict = true,
            ) as PackResult.Success
        ).pack

    private fun capture(
        notation: Notation,
        now: Double,
        name: String,
    ) {
        val timeline = Expansion.expand(notation)
        compose.setContent { SheetView(notation, timeline, { now }, Modifier.fillMaxSize().background(Color.White)) }
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/sheet-$name.png")
    }

    private fun lesson(
        id: String,
        now: Double = 1.0,
    ) = capture(pack.lessons.single { it.id == id }.notation, now, id)

    // Each lesson with the playhead on beat 2, so its first note there is lit.
    @Test fun singleStrokeRoll() = lesson("single-stroke-roll")

    @Test fun doubleStrokeOpenRoll() = lesson("double-stroke-open-roll")

    @Test fun multipleBounceRoll() = lesson("multiple-bounce-roll")

    @Test fun fiveStrokeRoll() = lesson("five-stroke-roll")

    @Test fun singleParadiddle() = lesson("single-paradiddle")

    @Test fun doubleParadiddle() = lesson("double-paradiddle")

    @Test fun flam() = lesson("flam")

    @Test fun flamAccent() = lesson("flam-accent")

    @Test fun swissArmyTriplet() = lesson("swiss-army-triplet")

    @Test fun drag() = lesson("drag")

    @Test fun changingSubdivisions() = lesson("changing-subdivisions")

    @Test fun lesson25() = lesson("lesson-25")

    @Test fun doubleRatamacue() = lesson("double-ratamacue")

    @Test
    fun museScoreFixtureWithTiesTupletsAndGhostNotes() {
        val notation =
            (
                importer.read(
                    fixture("sticking-and-ornaments.musicxml"),
                    strict = true,
                ) as ImportResult.Success
            ).notation
        capture(notation, 5.5, "fixture-ornaments")
    }

    @Test
    fun compoundTimeRestsAndDots() {
        val notation =
            (
                importer.read(
                    fixture("compound-and-repeats.musicxml"),
                    strict = true,
                ) as ImportResult.Success
            ).notation
        capture(notation, 0.5, "fixture-compound")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun landscape() = capture(pack.lessons.single { it.id == "flam-accent" }.notation, 2.0, "flam-accent-landscape")
}
