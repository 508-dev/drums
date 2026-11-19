package dev.co508.drums.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.co508.drums.audio.AudioEngineStats
import dev.co508.drums.audio.MixerStats
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h400dp-xxhdpi")
class RecoveryStatusTest {
    @get:Rule val compose = createComposeRule()
    private val stats = AudioEngineStats(48000, 144, 2016, 9648, true, "speaker", 0, 48000, MixerStats(0, 0, 0, 2), 0)

    @Test fun diagnosticsShowsPendingSuccessAndTerminalFailure() {
        compose.setContent {
            MaterialTheme {
                Column {
                    RecoveryStatus(
                        stats.copy(
                            recovering = true,
                            recoveryAttempts = 1,
                            lastWriteError = -6,
                            lastRecoveryReason = "write -6",
                        ),
                    )
                    RecoveryStatus(
                        stats.copy(
                            recoveries = 1,
                            recoveryAttempts = 1,
                            lastWriteError = -6,
                            lastRecoveryReason = "write -6",
                        ),
                    )
                    RecoveryStatus(
                        stats.copy(
                            writeError = -6,
                            recoveryAttempts = 3,
                            lastWriteError = -6,
                            lastRecoveryReason = "write -6",
                        ),
                    )
                }
            }
        }
        compose.onNodeWithText("Recovery  ready, 1 recreated / 1 attempts\nLast error -6; write -6").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/testDebug/screenshots/diagnostics-recovery.png")
    }
}
