package dev.co508.drums.history

import androidx.room.Entity
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
)
