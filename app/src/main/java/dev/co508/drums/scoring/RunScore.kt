package dev.co508.drums.scoring

import dev.co508.drums.history.SessionScore
import dev.co508.drums.lesson.Hand
import kotlin.math.sqrt

/** A finished run's totals as a practice-log row for session [sessionId]. */
fun ScoringStats.toSessionScore(
    sessionId: String,
    strictness: Strictness,
    routeKey: String?,
    calibrated: Boolean,
): SessionScore {
    val n = hits
    val mean = if (n > 0) sumOffsetMillis / n else null
    val spread =
        if (n > 1 && mean != null) sqrt((sumSquaredOffsetMillis / n - mean * mean).coerceAtLeast(0.0)) else null
    val left = perHand[Hand.LEFT] ?: HandStats()
    val right = perHand[Hand.RIGHT] ?: HandStats()
    return SessionScore(
        sessionId = sessionId,
        strictness = strictness.name,
        routeKey = routeKey,
        calibrated = calibrated,
        green = green,
        yellow = yellow,
        red = red,
        misses = misses,
        extras = extras,
        meanOffsetMillis = mean,
        spreadMillis = spread,
        leftHits = left.hits,
        leftMisses = left.misses,
        leftMeanOffsetMillis = if (left.hits > 0) left.sumOffsetMillis / left.hits else null,
        rightHits = right.hits,
        rightMisses = right.misses,
        rightMeanOffsetMillis = if (right.hits > 0) right.sumOffsetMillis / right.hits else null,
        gracesHeard = gracesHeard,
        gracesUnheard = gracesUnheard,
    )
}
