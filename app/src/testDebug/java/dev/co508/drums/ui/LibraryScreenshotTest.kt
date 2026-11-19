package dev.co508.drums.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import dev.co508.drums.settings.PracticePreferences
import dev.co508.drums.settings.preferenceKey
import dev.co508.drums.ui.library.LibraryNavigation
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
class LibraryScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val pack =
        (
            PackLoader(MusicXmlImporter { KXmlParser() }).load(
                DirectoryPackSource(File("src/main/assets/${PackLoader.BUILT_IN_FOLDER}")),
                strict = true,
            ) as PackResult.Success
        ).pack

    @Test fun navigationFavoritesAndRestoration() {
        val restoration = StateRestorationTester(compose)
        val quarter = pack.lessons.first()
        restoration.setContent {
            var preferences by remember {
                mutableStateOf(
                    PracticePreferences(
                        lessons = mapOf(quarter.preferenceKey to LessonPreferences(tempo = 97)),
                    ),
                )
            }
            MaterialTheme {
                LibraryNavigation(listOf(pack), preferences, onFavorite = {
                    val key = it.preferenceKey
                    preferences =
                        preferences.copy(
                            favorites =
                                if (key in preferences.favorites) {
                                    preferences.favorites - key
                                } else {
                                    preferences.favorites + key
                                },
                        )
                }) { lesson, back ->
                    Text("Practice: ${lesson.manifest.title}")
                    TextButton(onClick = back) { Text("Return to library") }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/library-packs.png")
        compose.onNodeWithText("Rudiments").performClick()
        compose.onNodeWithText("Timing basics").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/library-families.png")
        compose.onNodeWithText("Timing basics").performClick()
        compose.onNodeWithText("Level 1 · Last tempo: 97 BPM").assertIsDisplayed()
        compose.onNodeWithContentDescription("Favorite Quarter Notes").performClick().assertIsOn()
        compose.onNodeWithText("Quarter Notes").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/library-lessons.png")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Quarter Notes").assertIsDisplayed()
        compose.onNodeWithContentDescription("History").performClick()
        compose.onNodeWithText("Your practice log will appear here when session logging is added.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose
            .onNodeWithText(
                "Default practice sounds",
            ).assertIsDisplayed()
        compose.onNodeWithText("About").performScrollTo().performClick()
        compose.onNodeWithText("Version unknown").assertIsDisplayed()
        compose.onNodeWithText("Licenses and credits").performClick()
        compose.onNodeWithText("Bravura 1.482").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Version unknown").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Quarter Notes").performClick()
        compose.onNodeWithText("Practice: Quarter Notes").assertIsDisplayed()
        compose.onNodeWithText("Return to library").performClick()
        compose.onNodeWithText("Quarter Notes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Rolls").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Rudiments").assertIsDisplayed()
    }
}
