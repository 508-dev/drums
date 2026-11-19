package dev.co508.drums.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.co508.drums.history.HistoryData
import dev.co508.drums.history.LessonBest
import dev.co508.drums.history.PracticeRecord
import dev.co508.drums.ui.history.HistoryContent
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HistoryScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val originalLocale = Locale.getDefault()
    private val originalZone = TimeZone.getDefault()

    @After fun restoreLocale() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalZone)
    }

    private val data =
        HistoryData(
            listOf(
                PracticeRecord("new", 1_795_084_200_000, "builtin", "Single stroke roll", 90_000, 80, 120, true),
                PracticeRecord("old", 1_794_997_800_000, "builtin", "Quarter notes", 240_000, 60, 90, false),
            ),
            listOf(LessonBest("builtin", "Single stroke roll", 120), LessonBest("builtin", "Quarter notes", 90)),
        )

    private fun content(
        value: HistoryData?,
        error: String? = null,
        retry: () -> Unit = {},
    ) {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        compose.setContent { MaterialTheme { HistoryContent(value, emptyList(), error, retry) } }
    }

    @Test fun populatedHistory() {
        content(data)
        compose.onNodeWithText("Session log · newest first").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/history-sessions.png")
        compose.onNodeWithText("Speed trainer").assertIsDisplayed()
        compose.onNodeWithText("Manual tempo").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyHistory() {
        content(HistoryData())
        compose.onNodeWithText("No practice sessions yet. Play a lesson to start your log.").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/history-empty.png")
    }

    @Test fun failureCanBeRetriedWithoutShowingAnEmptyLog() {
        var retried = false
        content(null, "Couldn't load practice history.") { retried = true }
        compose.onNodeWithText("Retry history").performClick()
        assertTrue(retried)
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/history-error.png")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeHistoryScrolls() {
        content(data)
        compose.onNodeWithText("Manual tempo").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/history-landscape.png")
    }
}
