package dev.co508.drums.audio

/**
 * A mono sound, as float samples at the engine's sample rate. Its onset is at
 * sample 0, so scheduling it at a frame puts the attack on that frame.
 *
 * The samples are shared with the mixer thread and must not be modified after
 * construction.
 */
class Sound(
    val name: String,
    internal val samples: FloatArray,
    internal val alternatives: List<Sound> = emptyList(),
) {
    val frames: Int get() = samples.size

    override fun toString(): String = "Sound($name, $frames frames)"
}
