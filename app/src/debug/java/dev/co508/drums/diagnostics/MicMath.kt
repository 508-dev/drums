package dev.co508.drums.diagnostics

import dev.co508.drums.hits.MicStats
import dev.co508.drums.playback.PlaySettings
import dev.co508.drums.playback.TempoMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The output frame of the scheduled sound nearest to output [frame]: a snare
 * stroke (grace strokes [graceGapFrames] early per lead level) or, with
 * [includeClicks], a click, the count-in included. NaN if nothing is scheduled
 * or the map hasn't begun. For the debug screen's hit offsets, before #30's
 * real matching exists.
 */
fun nearestScheduledFrame(
    settings: PlaySettings,
    tempoMap: TempoMap,
    frame: Double,
    graceGapFrames: Double,
    includeClicks: Boolean,
): Double {
    val position = tempoMap.positionAt(frame)
    if (position.isNaN()) return Double.NaN
    val pattern = settings.pattern
    val length = pattern.lengthQuarters
    var best = Double.NaN

    fun consider(
        at: Double,
        leadLevel: Int,
    ) {
        val candidate = tempoMap.frameAt(at) - leadLevel * graceGapFrames
        if (candidate.isFinite() && (best.isNaN() || abs(candidate - frame) < abs(best - frame))) best = candidate
    }

    val loop = floor(position / length).toLong()
    val loops = if (settings.loop) (loop - 1)..(loop + 1) else 0L..0L
    for (k in loops) {
        if (k < 0) continue
        val base = k * length
        for (event in pattern.events) consider(base + event.position, event.leadLevel)
        if (includeClicks) for (click in pattern.clicks) consider(base + click.position, 0)
    }
    if (includeClicks && settings.countIn) {
        for (m in 0 until pattern.countInBeats) consider((m - pattern.countInBeats) * pattern.beatQuarters, 0)
    }
    return best
}

/** Running mean and spread of hit offsets (Welford), in milliseconds. */
class OffsetStats {
    var count = 0
        private set
    var mean = 0.0
        private set
    private var m2 = 0.0

    val standardDeviation: Double get() = if (count > 1) sqrt(m2 / (count - 1)) else 0.0

    fun add(millis: Double) {
        count++
        val delta = millis - mean
        mean += delta / count
        m2 += delta * (millis - mean)
    }

    fun reset() {
        count = 0
        mean = 0.0
        m2 = 0.0
    }
}

internal fun micReport(stats: MicStats): String {
    if (stats.source == null) return "Mic       off"
    val c = stats.clock
    return buildString {
        appendLine(
            "Mic       ${if (stats.running) "listening" else "stopped"}: ${stats.source}, " +
                "${stats.sampleRate} Hz ${if (stats.floatSamples) "float" else "16-bit"}",
        )
        appendLine("Device    ${stats.device ?: "unknown"}")
        appendLine(
            "Buffer    ${stats.bufferFrames} frames, blocks of ${stats.blockFrames}; " +
                "effects off: ${stats.disabledEffects.ifEmpty { listOf("none attached") }.joinToString()}",
        )
        appendLine(
            "Levels    floor ${fmt1(stats.noiseFloorDb)} dB, now ${fmt1(stats.levelDb)} dB",
        )
        appendLine(
            "Hits      ${stats.onsets} detected, ${stats.quietOnsets} too quiet, ${stats.droppedHits} dropped",
        )
        appendLine(
            "In clock  ${when {
                !c.anchored -> "no timestamp yet"
                c.exactTimestamps -> "stream timestamps"
                else -> "ESTIMATED from reads"
            }}, ${c.updates} updates, ${c.resets} resets",
        )
        append(
            "In jitter last ${fmt2(c.lastErrorMillis)} ms, mean ${fmt2(c.meanAbsErrorMillis)} ms, " +
                "max ${fmt2(c.maxAbsErrorMillis)} ms",
        )
        if (stats.readError != 0) append("\nREAD ERROR ${stats.readError}")
    }
}

private fun fmt1(value: Float) = if (value.isFinite()) "%.1f".format(Locale.ROOT, value) else "—"

private fun fmt2(value: Double) = "%.2f".format(Locale.ROOT, value)
