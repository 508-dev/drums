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
import dev.co508.drums.lesson.PackLoader
import dev.co508.drums.lesson.PackResult
import dev.co508.drums.musicxml.MusicXmlImporter
import dev.co508.drums.ui.falling.FallingNotesLayout
import dev.co508.drums.ui.falling.FallingNotesView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kxml2.io.KXmlParser
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshots of the falling-notes view for a few built-in lessons at fixed
 * playback positions, compared against the committed references in
 * src/testDebug/screenshots. Record new references with
 * `./gradlew recordRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class FallingNotesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val pack =
        (
            PackLoader(
                MusicXmlImporter {
                    KXmlParser()
                },
            ).load(
                DirectoryPackSource(File("src/main/assets/${PackLoader.BUILT_IN_FOLDER}")),
                strict = true,
            ) as PackResult.Success
        ).pack

    private fun capture(
        id: String,
        now: Double,
        name: String,
    ) {
        val layout = FallingNotesLayout.from(Expansion.expand(pack.lessons.single { it.id == id }.notation))
        compose.setContent {
            FallingNotesView(layout, { now }, Modifier.fillMaxSize().background(Color.White))
        }
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/$name.png")
    }

    /** A paradiddle with its first accented note right on the hit line. */
    @Test
    fun singleParadiddleOnTheBeat() = capture("single-paradiddle", 4.0, "single-paradiddle-portrait")

    /** Flam accents halfway into a beat: a flam lit just past the line, triplets and graces above. */
    @Test
    fun flamAccentMidBeat() = capture("flam-accent", 1.1, "flam-accent-portrait")

    /** Buzz rolls with their tails, and drags' two grace notes. */
    @Test
    fun multipleBounceRoll() = capture("multiple-bounce-roll", 0.5, "multiple-bounce-roll-portrait")

    @Test
    fun drag() = capture("drag", 2.0, "drag-portrait")

    /** Flams: each grace note sits just under its main note in the other lane. */
    @Test
    fun flam() = capture("flam", 1.0, "flam-portrait")

    /** The count-in: the grid falls with no notes yet. */
    @Test
    fun countIn() = capture("single-stroke-roll", -2.0, "count-in-portrait")

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun landscapeKeepsTheLanesVertical() = capture("single-paradiddle", 4.5, "single-paradiddle-landscape")
}
