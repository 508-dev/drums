package dev.co508.drums.lesson

import kotlin.math.abs

/**
 * An exact rational number, used for musical positions and durations in
 * quarter notes, so triplets and other tuplets never accumulate rounding.
 * Always normalized: lowest terms, positive denominator.
 */
class Fraction private constructor(
    val numerator: Long,
    val denominator: Long,
) : Comparable<Fraction> {
    operator fun plus(other: Fraction) =
        of(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)

    operator fun minus(other: Fraction) =
        of(numerator * other.denominator - other.numerator * denominator, denominator * other.denominator)

    operator fun times(other: Fraction) = of(numerator * other.numerator, denominator * other.denominator)

    operator fun times(n: Long) = of(numerator * n, denominator)

    operator fun div(other: Fraction): Fraction {
        require(other.numerator != 0L) { "division by zero" }
        return of(numerator * other.denominator, denominator * other.numerator)
    }

    operator fun div(n: Long) = of(numerator, denominator * n)

    override fun compareTo(other: Fraction): Int =
        (numerator * other.denominator).compareTo(
            other.numerator * denominator,
        )

    val isWhole: Boolean get() = denominator == 1L

    /** The largest whole number not above this one. */
    fun floor(): Long = Math.floorDiv(numerator, denominator)

    fun toDouble(): Double = numerator.toDouble() / denominator

    override fun equals(other: Any?) =
        other is Fraction && numerator == other.numerator && denominator == other.denominator

    override fun hashCode() = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString() = if (denominator == 1L) "$numerator" else "$numerator/$denominator"

    companion object {
        val ZERO = Fraction(0, 1)
        val ONE = Fraction(1, 1)

        fun of(
            numerator: Long,
            denominator: Long = 1,
        ): Fraction {
            require(denominator != 0L) { "zero denominator" }
            val g = gcd(abs(numerator), abs(denominator)).coerceAtLeast(1)
            val sign = if (denominator < 0) -1 else 1
            return Fraction(sign * numerator / g, sign * denominator / g)
        }

        private tailrec fun gcd(
            a: Long,
            b: Long,
        ): Long = if (b == 0L) a else gcd(b, a % b)
    }
}
