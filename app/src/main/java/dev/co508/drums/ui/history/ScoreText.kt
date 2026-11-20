package dev.co508.drums.ui.history

import dev.co508.drums.history.SessionScore
import java.util.Locale
import kotlin.math.roundToInt

/** Within this many ms of the beat on average counts as on time, not rushing or dragging. */
const val ON_TIME_MILLIS = 3.0

/**
 * A scored run in a few lines, for the practice screen's summary and the
 * History log (DECISIONS.md, "Run Summaries In The Practice Log").
 */
fun scoreLines(
    score: SessionScore,
    locale: Locale = Locale.getDefault(),
): List<String> {
    fun ms(value: Double) = String.format(locale, "%+.1f ms", value)
    val lines = mutableListOf<String>()
    val inWindow = (score.inWindowShare * 100).roundToInt()
    val green = if (score.notes == 0) 0 else (score.green * 100.0 / score.notes).roundToInt()
    lines +=
        "$inWindow% in window ($green% green) · ${score.misses} missed · ${score.extras} extra" +
        if (score.extras == 1) "" else "s"
    val mean = score.meanOffsetMillis
    if (mean != null) {
        val tendency =
            when {
                mean < -ON_TIME_MILLIS -> "rushing"
                mean > ON_TIME_MILLIS -> "dragging"
                else -> "on time"
            }
        lines +=
            "Average ${ms(mean)} ($tendency)" +
            (score.spreadMillis?.let { String.format(locale, " · spread ±%.1f ms", it) } ?: "")
    }
    val hands =
        listOfNotNull(
            hand("Left", score.leftHits, score.leftMisses, score.leftMeanOffsetMillis, ::ms),
            hand("Right", score.rightHits, score.rightMisses, score.rightMeanOffsetMillis, ::ms),
        )
    if (hands.isNotEmpty()) lines += hands.joinToString(" · ")
    val graces = score.gracesHeard + score.gracesUnheard
    if (graces > 0) lines += "${score.gracesHeard} of $graces grace notes heard as their own stroke"
    if (!score.calibrated) lines += "Not calibrated for this output: offsets include its delay."
    return lines
}

private fun hand(
    name: String,
    hits: Int,
    misses: Int,
    mean: Double?,
    ms: (Double) -> String,
): String? {
    if (hits + misses == 0) return null
    val average = mean?.let { ms(it) } ?: "no hits"
    return if (misses > 0) "$name $average, $misses missed" else "$name $average"
}
