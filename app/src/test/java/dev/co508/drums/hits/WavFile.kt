package dev.co508.drums.hits

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A mono WAV file read for tests: samples as floats in ±1. */
class WavFile(
    val sampleRate: Int,
    val samples: FloatArray,
) {
    companion object {
        /** Reads 16-bit PCM or 32-bit float WAV; takes the first channel of multichannel files. */
        fun read(file: File): WavFile {
            val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            require(ascii(bytes, 0) == "RIFF" && ascii(bytes, 8) == "WAVE") { "$file is not a WAV file" }
            var offset = 12
            var format = 0
            var channels = 0
            var rate = 0
            var bits = 0
            while (offset + 8 <= bytes.limit()) {
                val id = ascii(bytes, offset)
                val size = bytes.getInt(offset + 4)
                val body = offset + 8
                when (id) {
                    "fmt " -> {
                        format = bytes.getShort(body).toInt()
                        channels = bytes.getShort(body + 2).toInt()
                        rate = bytes.getInt(body + 4)
                        bits = bytes.getShort(body + 14).toInt()
                    }
                    "data" -> {
                        val frameBytes = channels * bits / 8
                        val frames = size / frameBytes
                        val samples =
                            FloatArray(frames) { i ->
                                val at = body + i * frameBytes
                                when {
                                    format == 1 && bits == 16 -> bytes.getShort(at) / 32_768f
                                    format == 3 && bits == 32 -> bytes.getFloat(at)
                                    else -> error("unsupported WAV format $format/$bits bits")
                                }
                            }
                        return WavFile(rate, samples)
                    }
                }
                offset = body + size + (size and 1)
            }
            error("$file has no data chunk")
        }

        private fun ascii(
            bytes: ByteBuffer,
            at: Int,
        ) = String(ByteArray(4) { bytes.get(at + it) }, Charsets.US_ASCII)
    }
}
