package dev.co508.drums.settings

import dev.co508.drums.lesson.Lesson
import dev.co508.drums.playback.Pattern
import dev.co508.drums.playback.PlaySettings
import dev.co508.drums.playback.SpeedTrainer
import dev.co508.drums.ui.practice.PracticeView
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Typed, version-tolerant settings. Pack and lesson IDs together identify a preference. */
@Serializable
data class PracticePreferences(
    val view: String = PracticeView.FALLING_NOTES.name,
    val lessons: Map<String, LessonPreferences> = emptyMap(),
    val pixelsPerBeat: Float = DEFAULT_ZOOM,
    val favorites: Set<String> = emptySet(),
    val defaults: AppDefaults = AppDefaults(),
) {
    val zoomPixelsPerBeat: Float
        get() = if (pixelsPerBeat.isFinite()) pixelsPerBeat.coerceIn(48f, 480f) else DEFAULT_ZOOM

    companion object {
        const val DEFAULT_ZOOM = 220f
    }

    fun lesson(lesson: Lesson): LessonPreferences =
        (
            lessons[lesson.preferenceKey] ?: LessonPreferences(
                tempo = lesson.manifest.tempo.start,
                trainerStart = lesson.manifest.tempo.start,
                trainerTarget = lesson.manifest.tempo.max,
                click = defaults.click,
                clickVolume = defaults.clickVolume,
                snare = defaults.snare,
                snareVolume = defaults.snareVolume,
                countIn = defaults.countIn,
            )
        ).normalized()

    val practiceView: PracticeView
        get() = PracticeView.entries.firstOrNull { it.name == view } ?: PracticeView.FALLING_NOTES

    fun isFavorite(lesson: Lesson): Boolean = lesson.preferenceKey in favorites
}

// Length-prefixed pack ID avoids collisions even if future imported IDs contain separators.
val Lesson.preferenceKey: String get() = "${packId.length}:$packId$id"

@Serializable
data class LessonPreferences(
    val tempo: Int = 80,
    val countIn: Boolean = true,
    val loop: Boolean = true,
    val click: Boolean = true,
    val clickVolume: Float = 1f,
    val snare: Boolean = true,
    val snareVolume: Float = 1f,
    val leadWithLeft: Boolean = false,
    val trainer: Boolean = false,
    val trainerStart: Int = 80,
    val trainerStep: Int = 5,
    val trainerLoops: Int = 4,
    val trainerTarget: Int = 120,
    val showCounts: Boolean = true,
) {
    /** Remember the latest heard trainer step, never replace the trainer's configured start. */
    fun rememberHeardTempo(bpm: Double): LessonPreferences =
        if (bpm.isFinite() && bpm > 0.0) copy(tempo = bpm.roundToInt().coerceIn(30, 300)) else this

    fun normalized(): LessonPreferences {
        val start = trainerStart.coerceIn(30, 300)
        return copy(
            tempo = tempo.coerceIn(30, 300),
            trainerStart = start,
            trainerStep = trainerStep.coerceIn(1, 50),
            trainerLoops = trainerLoops.coerceIn(1, 100),
            trainerTarget = trainerTarget.coerceIn(start, 300),
            clickVolume = clickVolume.finiteVolume(),
            snareVolume = snareVolume.finiteVolume(),
        )
    }

    fun playSettings(pattern: Pattern): PlaySettings =
        PlaySettings(
            pattern,
            beatsPerMinute = (if (trainer) trainerStart else tempo).toDouble(),
            countIn = countIn,
            loop = loop,
            trainer =
                if (trainer &&
                    loop
                ) {
                    SpeedTrainer(trainerStep.toDouble(), trainerLoops, trainerTarget.toDouble())
                } else {
                    null
                },
        )
}

private fun Float.finiteVolume(): Float = if (isFinite()) coerceIn(0f, 1f) else 1f
