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
import dev.co508.drums.audio.OutputRoute
import dev.co508.drums.hits.CalibrationMethod
import dev.co508.drums.hits.CalibrationOutcome
import dev.co508.drums.hits.Calibrator
import dev.co508.drums.settings.StoredCalibration
import dev.co508.drums.ui.settings.CalibrationContent
import dev.co508.drums.ui.settings.resultText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CalibrationScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val speaker = OutputRoute(OutputRoute.Kind.SPEAKER, "AI2202")
    private val buds = OutputRoute(OutputRoute.Kind.BLUETOOTH, "Buds")
    private val savedSpeaker = StoredCalibration(35.04, 0.15, "LOOPBACK", "Phone speaker", 1_763_600_000_000L)
    private val savedBuds = StoredCalibration(182.4, 6.1, "TAP_ALONG", "Bluetooth: Buds", 1_763_600_000_000L)

    @Test
    fun speakerNotCalibratedOffersBothMethods() {
        var started: CalibrationMethod? = null
        compose.setContent {
            MaterialTheme {
                CalibrationContent(speaker, null, emptyMap(), Calibrator.State.Idle, null, { started = it }, {}, {})
            }
        }
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/calibration-speaker.png")
        compose.onNodeWithText("Start speaker check").performClick()
        assertEquals(CalibrationMethod.LOOPBACK, started)
    }

    @Test
    fun headphonesListeningThenMeasured() {
        compose.setContent {
            MaterialTheme {
                CalibrationContent(
                    buds,
                    null,
                    mapOf("p|speaker" to savedSpeaker),
                    Calibrator.State.Listening(CalibrationMethod.TAP_ALONG, 7, 16),
                    null,
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("Heard 7 of 16").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/calibration-listening.png")
    }

    @Test
    fun resultsAndSavedRoutes() {
        val measured =
            Calibrator.State.Finished(
                CalibrationMethod.TAP_ALONG,
                buds,
                CalibrationOutcome.Measured(182.4, 6.1, 15, 16),
            )
        compose.setContent {
            MaterialTheme {
                CalibrationContent(
                    buds,
                    savedBuds,
                    mapOf("p|speaker" to savedSpeaker, "p|bluetooth:Buds" to savedBuds),
                    measured,
                    null,
                    {},
                    {},
                    {},
                )
            }
        }
        compose
            .onNodeWithText(
                "Measured +182.4 ms, consistent to ±6.1 ms. Saved for Bluetooth: Buds.",
            ).performScrollTo()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/calibration-measured.png")
    }

    @Test
    fun failuresExplainWhatToDo() {
        fun text(
            method: CalibrationMethod,
            reason: CalibrationOutcome.Reason,
        ) = resultText(
            Calibrator.State.Finished(method, speaker, CalibrationOutcome.Failed(reason, 6, 16, 41.0)),
        )
        assertEquals(
            "Didn't hear the clicks. Turn the media volume up and try again." to true,
            text(CalibrationMethod.LOOPBACK, CalibrationOutcome.Reason.NOT_HEARD),
        )
        assertEquals(
            "Heard only 6 of 16 taps. Tap on every click." to true,
            text(CalibrationMethod.TAP_ALONG, CalibrationOutcome.Reason.TOO_FEW),
        )
        assertEquals(
            "Your taps varied by about ±41 ms. Tap steadily with the click and try again." to true,
            text(CalibrationMethod.TAP_ALONG, CalibrationOutcome.Reason.INCONSISTENT),
        )
    }
}
