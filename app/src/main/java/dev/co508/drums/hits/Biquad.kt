package dev.co508.drums.hits

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A second-order IIR filter (RBJ cookbook coefficients), transposed direct
 * form II with double-precision state. [process] doesn't allocate.
 */
class Biquad private constructor(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    companion object {
        /** Butterworth (Q = 1/√2) unless [q] says otherwise. */
        fun highPass(
            sampleRate: Int,
            cornerHz: Double,
            q: Double = BUTTERWORTH_Q,
        ): Biquad {
            val w = 2 * PI * cornerHz / sampleRate
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            val a0 = 1 + alpha
            return Biquad(
                b0 = (1 + c) / 2 / a0,
                b1 = -(1 + c) / a0,
                b2 = (1 + c) / 2 / a0,
                a1 = -2 * c / a0,
                a2 = (1 - alpha) / a0,
            )
        }

        /** A notch at [centreHz], [q] wide: removes one tone and little else. */
        fun notch(
            sampleRate: Int,
            centreHz: Double,
            q: Double,
        ): Biquad {
            val w = 2 * PI * centreHz / sampleRate
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            val a0 = 1 + alpha
            return Biquad(
                b0 = 1 / a0,
                b1 = -2 * c / a0,
                b2 = 1 / a0,
                a1 = -2 * c / a0,
                a2 = (1 - alpha) / a0,
            )
        }

        const val BUTTERWORTH_Q = 0.7071067811865476
    }
}
