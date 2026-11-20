package dev.co508.drums.audio

/**
 * A mono sound, as float samples at the engine's sample rate. Its perceived
 * attack is [attackFrames] into [samples]: 0 for synthesized sounds, a few ms
 * for recorded ones. The mixer plays a sound from the frame it is given; the
 * transport gives instrument sounds a frame [attackFrames] early, so the attack
 * lands on the beat (DECISIONS.md, "A Snare's Attack Is On The Beat").
 *
 * The samples are shared with the mixer thread and must not be modified after
 * construction.
 */
class Sound(
    val name: String,
    internal val samples: FloatArray,
    internal val alternatives: List<Sound> = emptyList(),
    val attackFrames: Int = 0,
) {
    init {
        require(attackFrames in 0..samples.size) { "attack must lie within the sound" }
        require(alternatives.all { it.attackFrames == attackFrames }) { "variations must share one attack" }
    }

    val frames: Int get() = samples.size

    override fun toString(): String = "Sound($name, $frames frames)"
}
