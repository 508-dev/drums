package dev.co508.drums.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Stable session ID lets M5 attach a stats table without changing lesson identity. */
@Entity(
    tableName = "practice_sessions",
    indices = [Index(value = ["startedAtMillis"]), Index(value = ["packId", "lessonId"])],
)
data class PracticeRecord(
    @PrimaryKey val id: String,
    val startedAtMillis: Long,
    val packId: String,
    val lessonId: String,
    /** Monotonic elapsed time, including count-in; store precision, format minutes in the UI. */
    val durationMillis: Long,
    val startingTempo: Int,
    val highestTempo: Int,
    val trainerUsed: Boolean,
)

/**
 * A scored session's results (M5, schema v2): one row per scored practice
 * session, attached by [sessionId]. Main notes only; grace strokes are
 * optional and counted apart. Offsets are hit minus note, ms (positive is
 * late). Hands are the notes' written sticking: the mic can't tell hands
 * apart, but each hit is matched to an R or L note.
 */
@Entity(
    tableName = "session_scores",
    foreignKeys = [
        ForeignKey(
            entity = PracticeRecord::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SessionScore(
    @PrimaryKey val sessionId: String,
    /** A [dev.co508.drums.scoring.Strictness] name. */
    val strictness: String,
    /** The output route's key, if known. */
    val routeKey: String?,
    /** Whether the route had a calibration; uncalibrated offsets include the route's delay. */
    val calibrated: Boolean,
    val green: Int,
    val yellow: Int,
    val red: Int,
    val misses: Int,
    val extras: Int,
    /** Mean hit offset, or null with no hits. */
    val meanOffsetMillis: Double?,
    /** Standard deviation of the hit offsets, or null with fewer than two hits. */
    val spreadMillis: Double?,
    val leftHits: Int,
    val leftMisses: Int,
    val leftMeanOffsetMillis: Double?,
    val rightHits: Int,
    val rightMisses: Int,
    val rightMeanOffsetMillis: Double?,
    val gracesHeard: Int,
    val gracesUnheard: Int,
) {
    val hits: Int get() = green + yellow + red

    /** Main notes decided: hit or missed. */
    val notes: Int get() = hits + misses

    /** Hits within the yellow band (green or yellow), as a share of all notes. */
    val inWindowShare: Double get() = if (notes == 0) 0.0 else (green + yellow).toDouble() / notes
}

data class LessonBest(
    val packId: String,
    val lessonId: String,
    val highestTempo: Int,
)

data class PracticeIdentity(
    val packId: String,
    val lessonId: String,
)

data class HistoryData(
    val sessions: List<PracticeRecord> = emptyList(),
    val bests: List<LessonBest> = emptyList(),
    /** Scores of scored sessions, by session ID. */
    val scores: Map<String, SessionScore> = emptyMap(),
)
