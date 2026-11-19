package dev.co508.drums.ui.practice

import dev.co508.drums.playback.PlaySettings
import kotlin.math.floor
import kotlin.math.roundToInt

/** Heard progress, including count-in. A loop is the entire expanded lesson, repeats included. */
internal fun practiceProgress(
    settings: PlaySettings,
    position: Double,
    bpm: Double,
): String {
    if (!position.isFinite() || position < 0) return "Count-in"
    val loop = floor(position / settings.pattern.lengthQuarters).toLong()
    val trainer = settings.trainer ?: return if (settings.loop) "Loop ${loop + 1}" else "Playing once"
    if (bpm >= trainer.targetBpm) return "Loop ${loop + 1} · at target (${trainer.targetBpm.roundToInt()} BPM)"
    val cycle = loop % trainer.everyLoops + 1
    val next = minOf(bpm + trainer.stepBpm, trainer.targetBpm).roundToInt()
    return "Loop $cycle/${trainer.everyLoops}, next +${(next - bpm).roundToInt()} → $next BPM"
}
