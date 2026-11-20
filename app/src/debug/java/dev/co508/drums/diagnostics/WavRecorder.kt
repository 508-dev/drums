package dev.co508.drums.diagnostics

import dev.co508.drums.hits.CaptureTap
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Debug-only: records the raw microphone (before any filtering) into memory
 * on the capture thread, for saving as a WAV file. Real pad recordings become
 * onset test fixtures (docs/hit-detection.md).
 *
 * The buffer is allocated once, on the first [begin]; the capture thread only
 * copies into it. Don't [begin] again until the previous take is saved.
 */
class WavRecorder(
    val sampleRate: Int,
    val maxSeconds: Int = DEFAULT_MAX_SECONDS,
) : CaptureTap {
    private var buffer: FloatArray? = null

    @Volatile private var armed = false

    @Volatile private var target: FloatArray? = null

    /** Frames recorded so far. Written by the capture thread after each copy. */
    @Volatile
    var frames = 0
        private set

    /** The capture frame of the first recorded sample, or −1 before any. */
    @Volatile
    var firstFrame = -1L
        private set

    val isRecording: Boolean get() = armed

    val seconds: Double get() = frames.toDouble() / sampleRate

    fun begin() {
        val b = buffer ?: FloatArray(sampleRate * maxSeconds).also { buffer = it }
        frames = 0
        firstFrame = -1
        target = b
        armed = true
    }

    /** Stops recording; returns the take (a view of the buffer, valid until the next [begin]). */
    fun end(): Take {
        armed = false
        return Take(buffer ?: FloatArray(0), frames, firstFrame, sampleRate)
    }

    override fun onBlock(
        samples: FloatArray,
        count: Int,
        firstFrame: Long,
    ) {
        if (!armed) return
        val b = target ?: return
        if (this.firstFrame < 0) this.firstFrame = firstFrame
        val n = minOf(count, b.size - frames)
        samples.copyInto(b, frames, 0, n)
        frames += n
        if (frames == b.size) armed = false
    }

    class Take(
        val samples: FloatArray,
        val frames: Int,
        val firstFrame: Long,
        val sampleRate: Int,
    )

    companion object {
        const val DEFAULT_MAX_SECONDS = 60
    }
}

/** Writes [count] samples as a mono 16-bit PCM WAV file (clipped to ±1, rounded). */
fun writeWav16(
    file: File,
    samples: FloatArray,
    count: Int,
    sampleRate: Int,
) {
    BufferedOutputStream(file.outputStream()).use { out ->
        val dataBytes = count * 2
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.int32(36 + dataBytes)
        out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        out.int32(16)
        out.int16(1) // PCM
        out.int16(1) // mono
        out.int32(sampleRate)
        out.int32(sampleRate * 2)
        out.int16(2)
        out.int16(16)
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.int32(dataBytes)
        for (i in 0 until count) out.int16((samples[i].coerceIn(-1f, 1f) * 32_767f).roundToInt())
    }
}

private fun OutputStream.int16(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
}

private fun OutputStream.int32(value: Int) {
    int16(value and 0xFFFF)
    int16((value ushr 16) and 0xFFFF)
}
