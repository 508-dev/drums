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
import dev.co508.drums.scoring.HitMatcher
import dev.co508.drums.scoring.ScoredLesson
import dev.co508.drums.scoring.ScoringSnapshot
import dev.co508.drums.scoring.Strictness
import dev.co508.drums.scoring.TimeMap
import dev.co508.drums.ui.falling.FallingNotesLayout
import dev.co508.drums.ui.falling.FallingNotesView
import dev.co508.drums.ui.sheet.SheetView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kxml2.io.KXmlParser
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Scoring marks in both views (#30), from a real [HitMatcher] run over two
 * loops of the Single Paradiddle: green, yellow and red hits early and late,
 * a missed note (hollow), an extra hit (×), and the previous loop's marks
 * fading on the notes still to come.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScoringScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val lesson =
        (
            PackLoader(MusicXmlImporter { KXmlParser() })
                .load(DirectoryPackSource(File("src/main/assets/${PackLoader.BUILT_IN_FOLDER}")), strict = true)
                as PackResult.Success
        ).pack.lessons
            .single { it.id == "single-paradiddle" }
    private val timeline = Expansion.expand(lesson.notation)

    /** 100 BPM from frame 0: 28 800 frames a quarter note. */
    private val map =
        object : TimeMap {
            override fun frameAt(position: Double) = position * 28_800

            override fun positionAt(frame: Double) = frame / 28_800
        }

    /** The matcher's run up to [until] (quarter notes): only what has been played is decided. */
    private fun snapshot(until: Double): ScoringSnapshot {
        val scored = ScoredLesson.from(timeline)
        val matcher = HitMatcher(scored, 48_000, Strictness.STANDARD)
        // Offsets cycle through green, yellow and red, early and late; every 7th note is missed.
        val offsets = listOf(3.0, -22.0, 48.0, -9.0, 30.0, -55.0, 12.0)
        val ms = 48.0
        var n = 0
        for (loop in 0 until 2) {
            for (main in scored.mains) {
                val position = loop * scored.lengthQuarters + scored.strokes[main].position
                if (position < until &&
                    n % 7 != 6
                ) {
                    matcher.addHit(map.frameAt(position) + offsets[n % offsets.size] * ms)
                }
                n++
            }
        }
        // One extra, between the first two 16ths of loop 1's second beat.
        matcher.addHit(map.frameAt(scored.lengthQuarters + 1.125))
        matcher.advance(map.frameAt(until), map, 12 * ms)
        return matcher.snapshot()
    }

    @Test
    fun fallingNotesShowMarksMissesAndExtras() {
        // Loop 1's first beats have just passed the hit line; the rest still show loop 0's marks, fading.
        val now = timeline.length.toDouble() + 1.6
        val snapshot = snapshot(now - 0.1)
        assertEquals(1, snapshot.stats.extras)
        val layout = FallingNotesLayout.from(timeline)
        compose.setContent {
            FallingNotesView(
                layout,
                { now },
                Modifier.fillMaxSize().background(Color.White),
                marks = { snapshot },
            )
        }
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/scoring-falling.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h420dp-xxhdpi")
    fun sheetShowsTheLatestPassOfEachNote() {
        val now = 2 * timeline.length.toDouble() - 0.3
        val snapshot = snapshot(now - 0.1)
        compose.setContent {
            SheetView(
                lesson.notation,
                timeline,
                { now },
                Modifier.fillMaxSize().background(Color.White),
                marks = { snapshot },
            )
        }
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/scoring-sheet.png")
    }
}
