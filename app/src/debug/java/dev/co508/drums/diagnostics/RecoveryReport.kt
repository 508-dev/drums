package dev.co508.drums.diagnostics

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import dev.co508.drums.audio.AudioEngineStats

@Composable
internal fun RecoveryStatus(stats: AudioEngineStats) {
    Text(recoveryReport(stats), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 15.sp)
}

internal fun recoveryReport(stats: AudioEngineStats): String =
    "Recovery  ${if (stats.recovering) {
        "recreating"
    } else if (stats.writeError < 0) {
        "FAILED"
    } else {
        "ready"
    }}, " +
        "${stats.recoveries} recreated / ${stats.recoveryAttempts} attempts\n" +
        "Last error ${stats.lastWriteError}; ${stats.lastRecoveryReason ?: "no recovery yet"}"
