package dev.co508.drums.hits

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Synthetic microphone signals for the onset tests. Everything is a continuous
 * function of time sampled at [rate], so an onset can sit between two samples
 * and the expected answer is exact.
 */
class SyntheticSignals(
    val rate: Int = 48_000,
    seconds: Double,
    seed: Int = 1,
) {
    val samples = DoubleArray((seconds * rate).toInt())
    private val random = Random(seed)

    /**
     * A stick on a gum-rubber pad: a broadband "tock" of inharmonic partials
     * (0.4 to 9 kHz, random phases per hit, so no two hits are identical) with
     * a short attack and a fast decay, plus a low thump the high-pass removes.
     * [amplitude] is the peak of the envelope; [decayMillis] the amplitude time
     * constant at 1 kHz. With [damping] above 0, higher partials die faster,
     * as in a real drum: the time constant scales with (1 kHz / f)^damping.
     * Returns the exact onset, in fractional frames.
     */
    fun padHit(
        atSeconds: Double,
        amplitude: Double,
        attackMillis: Double = 0.4,
        decayMillis: Double = 6.0,
        partials: Int = 14,
        damping: Double = 0.0,
    ): Double {
        val onset = atSeconds * rate
        val freqs = DoubleArray(partials) { 400.0 * (9_000.0 / 400.0).pow(random.nextDouble()) }
        val phases = DoubleArray(partials) { random.nextDouble() * 2 * PI }
        val norm = 1.0 / sqrt(partials.toDouble())
        val thump = 120.0 + random.nextDouble() * 60
        val attack = attackMillis / 1000.0
        val decays = DoubleArray(partials) { decayMillis / 1000.0 * (1_000.0 / freqs[it]).pow(damping) }
        val decay = decays.max()
        val first = kotlin.math.ceil(onset).toInt()
        val last = minOf(samples.size, (onset + rate * (attack + decay * 12)).toInt())
        for (n in first until last) {
            val t = (n - onset) / rate
            val rise = if (t < attack) 0.5 - 0.5 * kotlin.math.cos(PI * t / attack) else 1.0
            val after = maxOf(0.0, t - attack)
            var s = 0.0
            for (k in 0 until partials) s += exp(-after / decays[k]) * sin(2 * PI * freqs[k] * t + phases[k])
            val body = 0.8 * exp(-after / decay) * sin(2 * PI * thump * t)
            samples[n] += amplitude * rise * (s * norm + body)
        }
        return onset
    }

    /** White noise at an RMS level of [rmsDb] dBFS, between [fromSeconds] and [toSeconds]. */
    fun whiteNoise(
        rmsDb: Double,
        fromSeconds: Double = 0.0,
        toSeconds: Double = samples.size.toDouble() / rate,
        rampSeconds: Double = 0.0,
    ) {
        val rms = 10.0.pow(rmsDb / 20)
        val from = (fromSeconds * rate).toInt()
        val to = minOf(samples.size, (toSeconds * rate).toInt())
        val ramp = rampSeconds * rate
        for (n in from until to) {
            val gain = if (ramp > 0 && n - from < ramp) (n - from) / ramp else 1.0
            // Sum of uniforms: near-Gaussian, unit variance.
            var g = 0.0
            repeat(12) { g += random.nextDouble() }
            samples[n] += gain * rms * (g - 6.0)
        }
    }

    /** Low-frequency room rumble (traffic, air conditioning): a few drifting tones below 200 Hz. */
    fun rumble(amplitude: Double) {
        val freqs = doubleArrayOf(31.0, 57.0, 83.0, 140.0)
        for (n in samples.indices) {
            val t = n.toDouble() / rate
            var s = 0.0
            for (f in freqs) s += sin(2 * PI * f * t + f)
            samples[n] += amplitude * s / freqs.size
        }
    }

    /**
     * A short tonal burst, like the 7.4 kHz electrical whine a test phone's
     * microphone picked up in a quiet house: sharp onset, narrow band.
     */
    fun toneBurst(
        atSeconds: Double,
        amplitude: Double,
        hz: Double = 7_400.0,
        millis: Double = 8.0,
    ) {
        val from = (atSeconds * rate).toInt()
        val n = (millis * rate / 1000).toInt()
        for (i in 0 until minOf(n, samples.size - from)) {
            val t = i.toDouble() / rate
            val env = minOf(1.0, i / (0.0015 * rate)) * minOf(1.0, (n - i) / (0.002 * rate))
            samples[from + i] += amplitude * env * sin(2 * PI * hz * t)
        }
    }

    /** A copy of everything so far, [delayMillis] later at [gain]: an early reflection off a wall. */
    fun reflection(
        delayMillis: Double,
        gain: Double,
    ) {
        val d = (delayMillis * rate / 1000).toInt()
        for (n in samples.size - 1 downTo d) samples[n] += gain * samples[n - d]
    }

    fun toFloats(): FloatArray = FloatArray(samples.size) { samples[it].toFloat() }
}

/** Runs [detector] over [samples] in blocks of [block] and returns the onsets as (frame, level dB). */
fun detect(
    samples: FloatArray,
    detector: OnsetDetector = OnsetDetector(48_000),
    block: Int = 240,
): List<Pair<Double, Float>> {
    val onsets = mutableListOf<Pair<Double, Float>>()
    var offset = 0
    while (offset < samples.size) {
        val n = minOf(block, samples.size - offset)
        detector.process(samples, offset, n, offset.toLong()) { frame, level -> onsets += frame to level }
        offset += n
    }
    return onsets
}

fun decibelsToAmplitude(db: Double): Double = 10.0.pow(db / 20)
