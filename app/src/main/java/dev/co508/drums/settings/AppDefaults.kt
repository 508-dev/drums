package dev.co508.drums.settings

import dev.co508.drums.playback.AudioClock
import kotlinx.serialization.Serializable

/** Defaults seed lessons without saved settings; the visual offset is always global. */
@Serializable
data class AppDefaults(
    val click: Boolean = true,
    val clickVolume: Float = 1f,
    val snare: Boolean = true,
    val snareVolume: Float = 1f,
    val countIn: Boolean = true,
    val visualOffsetMillis: Int = 0,
) {
    fun normalized(): AppDefaults =
        copy(
            clickVolume = if (clickVolume.isFinite()) clickVolume.coerceIn(0f, 1f) else 1f,
            snareVolume = if (snareVolume.isFinite()) snareVolume.coerceIn(0f, 1f) else 1f,
            visualOffsetMillis = visualOffsetMillis.coerceIn(-500, 500),
        )

    fun applyTo(clock: AudioClock) {
        clock.visualOffsetMillis = normalized().visualOffsetMillis.toDouble()
    }
}
