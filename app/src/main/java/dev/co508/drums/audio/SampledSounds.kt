package dev.co508.drums.audio

import java.io.InputStream
import kotlin.math.abs
import kotlin.random.Random

/** Decodes the bundled mono PCM files once, away from the mixer thread. */
object SampledSounds {
    fun snare(
        layer: String,
        sampleRate: Int,
        open: (String) -> InputStream,
    ): Sound {
        val first = wav(open("samples/$layer-1.wav"), sampleRate, "$layer-1")
        val second = wav(open("samples/$layer-2.wav"), sampleRate, "$layer-2")
        return Sound(layer, first.samples, listOf(second))
    }

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
                val length = minOf(bounce.size, sampleRate * 30 / 1000, samples.size - start)
                for (i in 0 until length.coerceAtLeast(0)) {
                    val fade = 1.0 - i.toDouble() / length
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
        return Sound("sampled-buzz", samples)
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
