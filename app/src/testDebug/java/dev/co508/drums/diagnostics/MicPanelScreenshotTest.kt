package dev.co508.drums.diagnostics

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.co508.drums.hits.InputClockStats
import dev.co508.drums.hits.MicStats
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h700dp-xxhdpi")
class MicPanelScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun listeningWithHits() {
        val report =
            micReport(
                MicStats(
                    running = true,
                    source = "UNPROCESSED",
                    device = "built-in mic (ASUS_AI2202)",
                    sampleRate = 48_000,
                    floatSamples = true,
                    bufferFrames = 4_800,
                    blockFrames = 192,
                    disabledEffects = emptyList(),
                    framesCaptured = 480_000,
                    onsets = 3,
                    noiseFloorDb = -68.4f,
                    levelDb = -55.2f,
                    clock =
                        InputClockStats(
                            anchored = true,
                            exactTimestamps = true,
                            updates = 99,
                            lastErrorMillis = 0.04,
                            maxAbsErrorMillis = 0.31,
                            meanAbsErrorMillis = 0.08,
                        ),
                ),
            )
        compose.setContent {
            MaterialTheme {
                MicPanel(
                    state =
                        MicPanelState(
                            listening = true,
                            report = report,
                            summary = "Offsets   n=3, mean +21.40 ms, SD 0.35 ms (vs nearest stroke or click)",
                            recentHits =
                                listOf(
                                    " -22.1 dB   +21.02 ms  pos 2.005",
                                    " -24.8 dB   +21.85 ms  pos 1.505",
                                    " -21.9 dB   +21.33 ms  pos 1.005",
                                ),
                            recording = true,
                            recordingSeconds = 12.0,
                            label = "singles-120",
                        ),
                    onToggle = {},
                    onIncludeClicks = {},
                    onResetOffsets = {},
                    onLabel = {},
                    onRecord = {},
                )
            }
        }
        compose.onNodeWithText("Save 12 s").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/diagnostics-mic.png")
    }
}
