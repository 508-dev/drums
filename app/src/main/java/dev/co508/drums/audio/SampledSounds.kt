package dev.co508.drums.audio

import java.io.InputStream
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** Decodes the bundled mono PCM files once, away from the mixer thread. */
object SampledSounds {
    /**
     * Where every sampled sound's perceived attack sits: each variation is
     * padded with silence in front until its measured attack
     * ([perceivedAttack]) is this far in. Above the latest measured attack
     * (about 2.7 ms), so all of them share one offset the transport can lead by.
     */
    const val ATTACK_MILLIS = 4.0

    fun snare(
        layer: String,
        sampleRate: Int,
        open: (String) -> InputStream,
    ): Sound {
        val attack = attackFrames(sampleRate)
        val first = aligned(wav(open("samples/$layer-1.wav"), sampleRate, "$layer-1"), sampleRate)
        val second = aligned(wav(open("samples/$layer-2.wav"), sampleRate, "$layer-2"), sampleRate)
        return Sound(layer, first.samples, listOf(second), attack)
    }

    fun attackFrames(sampleRate: Int): Int = (ATTACK_MILLIS * sampleRate / 1000).roundToInt()

    /**
     * Where a recorded hit is heard to start, in (fractional) frames: the first
     * moment its loudness reaches [ATTACK_THRESHOLD_DB] of its loudest. Loudness
     * here is power integrated over [LOUDNESS_MILLIS], roughly as the ear
     * integrates it. A fixed threshold well below the maximum follows the
     * perceptual-onset studies (Vos and Rasch, 1981): a sharp stick transient is
     * heard as the attack even when the drum's body peaks 10 ms later. See
     * DECISIONS.md, "A Snare's Attack Is On The Beat".
     */
    fun perceivedAttack(
        samples: FloatArray,
        sampleRate: Int,
    ): Double {
        val a = 1 - exp(-1000.0 / (LOUDNESS_MILLIS * sampleRate))
        val loudness = DoubleArray(samples.size)
        var e = 0.0
        var loudest = 0.0
        for (i in samples.indices) {
            e += a * (samples[i].toDouble() * samples[i] - e)
            loudness[i] = e
            loudest = max(loudest, e)
        }
        if (loudest == 0.0) return 0.0
        val threshold = loudest * 10.0.pow(ATTACK_THRESHOLD_DB / 10)
        val i = loudness.indexOfFirst { it >= threshold }
        if (i <= 0) return 0.0
        return i - 1 + (threshold - loudness[i - 1]) / (loudness[i] - loudness[i - 1])
    }

    /** [sound], padded in front so its perceived attack is [attackFrames] in. */
    private fun aligned(
        sound: Sound,
        sampleRate: Int,
    ): Sound {
        val target = attackFrames(sampleRate)
        val measured = perceivedAttack(sound.samples, sampleRate)
        check(measured <= target) { "${sound.name}'s attack is ${measured / sampleRate * 1000} ms in" }
        val pad = (target - measured).roundToInt()
        val samples = FloatArray(pad + sound.frames)
        sound.samples.copyInto(samples, pad)
        return Sound(sound.name, samples, attackFrames = target)
    }

    private const val LOUDNESS_MILLIS = 10.0
    private const val ATTACK_THRESHOLD_DB = -15.0

    /** Build a deterministic roll from short, differently played sampled bounces. */
    fun buzz(
        ghost: Sound,
        sampleRate: Int,
    ): Sound {
        val random = Random(2)
        val samples = FloatArray(sampleRate * 8)
        val bounces = listOf(ghost, *ghost.alternatives.toTypedArray())
        var strokeStart = 0.0
        var right = true
        while (strokeStart < 8.0) {
            var level = (if (right) 1.0 else 0.88) * random.nextDouble(0.85, 1.0)
            var t = strokeStart
            var interval = random.nextDouble(0.016, 0.022)
            repeat(5 + random.nextInt(4)) {
                val bounce = bounces[random.nextInt(bounces.size)].samples
                val start = (t * sampleRate).toInt()
                // 30 ms of each bounce from its attack (the padding before it is silent).
                val length = minOf(bounce.size, ghost.attackFrames + sampleRate * 30 / 1000, samples.size - start)
                val attack = ghost.attackFrames
                for (i in 0 until length.coerceAtLeast(0)) {
                    val fade = 1.0 - maxOf(0, i - attack).toDouble() / (length - attack)
                    samples[start + i] += (bounce[i] * level * fade).toFloat()
                }
                t += interval * random.nextDouble(0.8, 1.2)
                interval *= random.nextDouble(0.8, 0.92)
                level *= random.nextDouble(0.7, 0.85)
            }
            strokeStart = t + random.nextDouble(-0.006, 0.002)
            right = !right
        }
        val peak = samples.maxOf { abs(it) }
        if (peak > 0f) for (i in samples.indices) samples[i] *= 0.5f / peak
        val fade = sampleRate / 200
        for (i in 0 until fade) samples[samples.lastIndex - i] *= i.toFloat() / fade
        // Every bounce shares the ghost hits' attack offset, so the roll starts on the beat too.
        return Sound("sampled-buzz", samples, attackFrames = ghost.attackFrames)
    }

    internal fun wav(
        input: InputStream,
        sampleRate: Int,
        name: String,
    ): Sound {
        require(sampleRate > 0)
        val bytes = input.use { it.readBytes() }

        fun text(
            offset: Int,
            n: Int,
        ) = String(bytes, offset, n, Charsets.US_ASCII)

        fun u16(offset: Int) = (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)

        fun u32(offset: Int) = u16(offset) or (u16(offset + 2) shl 16)
        require(bytes.size >= 44 && text(0, 4) == "RIFF" && text(8, 4) == "WAVE")
        var offset = 12
        var rate = 0
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val size = u32(offset + 4)
            require(size >= 0 && size <= bytes.size - offset - 8)
            when (text(offset, 4)) {
                "fmt " -> {
                    require(size >= 16 && u16(offset + 8) == 1 && u16(offset + 10) == 1 && u16(offset + 22) == 16)
                    rate = u32(offset + 12)
                }
                "data" -> {
                    dataOffset = offset + 8
                    dataSize = size
                }
            }
            offset += 8 + size + (size and 1)
        }
        require(rate > 0 && dataOffset >= 0 && dataSize >= 2 && dataSize % 2 == 0)
        val original = FloatArray(dataSize / 2) { u16(dataOffset + it * 2).toShort() / 32768f }
        if (rate == sampleRate) return Sound(name, original)
        val result =
            FloatArray((original.size.toLong() * sampleRate / rate).toInt()) { i ->
                val position = i.toDouble() * rate / sampleRate
                val left = position.toInt().coerceAtMost(original.lastIndex)
                val right = (left + 1).coerceAtMost(original.lastIndex)
                val fraction = (position - left).toFloat()
                original[left] + (original[right] - original[left]) * fraction
            }
        return Sound(name, result)
    }
}
