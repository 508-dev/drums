package dev.co508.drums.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sounds generated in code, so M1 needs no audio files. The click stays in the
 * app (DECISIONS.md, "Sounds"); the snare is a placeholder until the sampled
 * snare arrives in M3.
 *
 * Every sound starts at full attack within its first millisecond, so its
 * scheduled frame is where it is heard.
 */
object PlaceholderSounds {
    /** A metronome click; the accented downbeat is higher and louder. */
    fun click(
        sampleRate: Int,
        accented: Boolean,
    ): Sound {
        val frequency = if (accented) ACCENT_CLICK_HZ else CLICK_HZ
        val peak = if (accented) 0.9f else 0.6f
        val samples = FloatArray(frames(sampleRate, CLICK_SECONDS))
        for (i in samples.indices) {
            val t = i.toDouble() / sampleRate
            samples[i] = (sin(2 * PI * frequency * t) * exp(-t / CLICK_DECAY_SECONDS) * attack(t)).toFloat()
        }
        finish(samples, sampleRate, peak)
        return Sound(if (accented) "click-accent" else "click", samples)
    }

    /**
     * A synthesized snare: high-passed noise for the wires plus two decaying
     * tones for the shell. [seed] makes the noise reproducible.
     */
    fun snare(
        sampleRate: Int,
        seed: Int = 1,
    ): Sound {
        val random = Random(seed)
        val samples = FloatArray(frames(sampleRate, SNARE_SECONDS))
        // One-pole high-pass, about 1 kHz, to take the boom out of the noise.
        val rc = 1.0 / (2 * PI * SNARE_NOISE_HIGH_PASS_HZ)
        val alpha = rc / (rc + 1.0 / sampleRate)
        var previousIn = 0.0
        var previousOut = 0.0
        for (i in samples.indices) {
            val t = i.toDouble() / sampleRate
            val white = random.nextDouble(-1.0, 1.0)
            val highPassed = alpha * (previousOut + white - previousIn)
            previousIn = white
            previousOut = highPassed
            val wires = highPassed * exp(-t / SNARE_NOISE_DECAY_SECONDS)
            val shell =
                (sin(2 * PI * SNARE_TONE_HZ * t) + 0.5 * sin(2 * PI * SNARE_OVERTONE_HZ * t)) *
                    exp(-t / SNARE_TONE_DECAY_SECONDS)
            samples[i] = ((0.8 * wires + 0.5 * shell) * attack(t)).toFloat()
        }
        finish(samples, sampleRate, 0.9f)
        return Sound("snare", samples)
    }

    /**
     * A sustained buzz (multiple-bounce) roll: high-passed noise pulsing at the
     * rate of stick bounces. It is long, and the mixer cuts it to each roll's
     * length ([dev.co508.drums.audio.EventSink.playFor]).
     */
    fun buzz(
        sampleRate: Int,
        seed: Int = 2,
    ): Sound {
        val random = Random(seed)
        val samples = FloatArray(frames(sampleRate, BUZZ_SECONDS))
        val rc = 1.0 / (2 * PI * SNARE_NOISE_HIGH_PASS_HZ)
        val alpha = rc / (rc + 1.0 / sampleRate)
        var previousIn = 0.0
        var previousOut = 0.0
        for (i in samples.indices) {
            val t = i.toDouble() / sampleRate
            val white = random.nextDouble(-1.0, 1.0)
            val highPassed = alpha * (previousOut + white - previousIn)
            previousIn = white
            previousOut = highPassed
            // Each bounce is a quick swell and decay of the wires.
            val bounce = (t * BUZZ_BOUNCE_HZ) % 1.0
            val envelope = 0.45 + 0.55 * exp(-bounce * 6)
            samples[i] = (highPassed * envelope * attack(t)).toFloat()
        }
        finish(samples, sampleRate, 0.5f)
        return Sound("buzz", samples)
    }

    private fun frames(
        sampleRate: Int,
        seconds: Double,
    ): Int {
        require(sampleRate > 0) { "sampleRate must be positive" }
        return (sampleRate * seconds).toInt()
    }

    /** A short linear fade-in, so the onset doesn't pop. */
    private fun attack(t: Double): Double = if (t < ATTACK_SECONDS) t / ATTACK_SECONDS else 1.0

    /** Fades the tail to silence, so the end doesn't click, and normalizes to [peak]. */
    private fun finish(
        samples: FloatArray,
        sampleRate: Int,
        peak: Float,
    ) {
        val fade = minOf(samples.size, (sampleRate * RELEASE_SECONDS).toInt())
        for (i in 0 until fade) {
            samples[samples.size - 1 - i] *= i.toFloat() / fade
        }
        val max = samples.maxOf { abs(it) }
        if (max == 0f) return
        val scale = peak / max
        for (i in samples.indices) samples[i] *= scale
    }

    private const val ATTACK_SECONDS = 0.0003
    private const val RELEASE_SECONDS = 0.005
    private const val CLICK_HZ = 1_000.0
    private const val ACCENT_CLICK_HZ = 1_600.0
    private const val CLICK_SECONDS = 0.03
    private const val CLICK_DECAY_SECONDS = 0.006
    private const val SNARE_SECONDS = 0.25
    private const val BUZZ_SECONDS = 8.0
    private const val BUZZ_BOUNCE_HZ = 28.0
    private const val SNARE_NOISE_HIGH_PASS_HZ = 1_000.0
    private const val SNARE_NOISE_DECAY_SECONDS = 0.055
    private const val SNARE_TONE_HZ = 185.0
    private const val SNARE_OVERTONE_HZ = 330.0
    private const val SNARE_TONE_DECAY_SECONDS = 0.025
}
