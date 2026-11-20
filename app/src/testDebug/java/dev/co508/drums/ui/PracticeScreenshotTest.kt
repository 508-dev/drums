package dev.co508.drums.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.co508.drums.lesson.DirectoryPackSource
import dev.co508.drums.lesson.PackLoader
import dev.co508.drums.lesson.PackResult
import dev.co508.drums.musicxml.MusicXmlImporter
import dev.co508.drums.settings.LessonPreferences
import dev.co508.drums.ui.practice.PracticeContent
import dev.co508.drums.ui.practice.PracticeView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kxml2.io.KXmlParser
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PracticeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val lesson =
        (
            PackLoader(MusicXmlImporter { KXmlParser() }).load(
                DirectoryPackSource(File("src/main/assets/${PackLoader.BUILT_IN_FOLDER}")),
                strict = true,
            ) as PackResult.Success
        ).pack.lessons.single { it.id == "flam" }

    private var latest = LessonPreferences()

    private fun screen(
        view: PracticeView = PracticeView.FALLING_NOTES,
        trainer: Boolean = false,
        progress: String? = null,
    ) {
        compose.setContent {
            var settings by remember { mutableStateOf(LessonPreferences(trainer = trainer)) }
            var selected by remember { mutableStateOf(view) }
            MaterialTheme {
                PracticeContent(
                    lesson,
                    settings,
                    selected,
                    progress != null,
                    settings.tempo,
                    { 0.0 },
                    onChange = {
                        settings = it
                        latest = it
                    },
                    onTempo = {
                        settings = settings.copy(tempo = it)
                        latest = settings
                    },
                    onView = { selected = it },
                    onPlayStop = {},
                    onBack = {},
                    progress = progress,
                    tempoStepped = progress != null,
                )
            }
        }
    }

    @Test fun scoringToggleAndItsNote() {
        var scoring = false
        compose.setContent {
            MaterialTheme {
                PracticeContent(
                    lesson,
                    LessonPreferences(),
                    PracticeView.FALLING_NOTES,
                    false,
                    80,
                    { 0.0 },
                    onChange = {},
                    onTempo = {},
                    onView = {},
                    onPlayStop = {},
                    onBack = {},
                    scoring = true,
                    onScoring = { scoring = it },
                    scoringNote = "Phone speaker isn't calibrated: Settings → Calibrate hit timing.",
                )
            }
        }
        compose.onNodeWithText("Phone speaker isn't calibrated: Settings → Calibrate hit timing.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Score my playing").performClick()
        assertEquals(false, scoring)
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/practice-scoring.png")
    }

    @Test fun portrait() {
        screen()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/practice-portrait.png")
    }

    @Test fun trainerProgressIsVisible() {
        screen(trainer = true, progress = "Loop 2/4, next +5 → 85 BPM")
        compose.onNodeWithText("80 BPM · Loop 2/4, next +5 → 85 BPM").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/practice-trainer-progress.png")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscape() {
        screen(PracticeView.SHEET)
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/practice-landscape.png")
    }

    @Test fun everyControlAndTipsRemainReachable() {
        screen(trainer = true)
        compose.onNodeWithText("+5").performClick()
        assertEquals(85, latest.tempo)
        compose.onNodeWithText("Practice options").performClick()
        compose.onNodeWithText("Target BPM: 120").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Counting").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Description and tips").performScrollTo().performClick()
        compose.onNodeWithText(lesson.manifest.description).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hide description and tips").performScrollTo().performClick()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeOptions() {
        screen(trainer = true)
        compose.onNodeWithText("Practice options").performScrollTo().performClick()
        compose.onNodeWithText("Target BPM: 120").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/practice-trainer-landscape.png")
    }
}
