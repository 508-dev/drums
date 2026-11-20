package dev.co508.drums.hits

/** One detected stick hit. Mutable, so a consumer can reuse one while polling. */
class DetectedHit {
    /** When the hit reached the microphone, on the [System.nanoTime] clock. */
    var nanoTime: Long = 0

    /** The capture frame of the onset (fractional), for recordings and diagnostics. */
    var inputFrame: Double = 0.0

    /** Peak short-term power, dBFS. See [OnsetSink.onset]. */
    var levelDb: Float = 0f

    fun copyFrom(other: DetectedHit) {
        nanoTime = other.nanoTime
        inputFrame = other.inputFrame
        levelDb = other.levelDb
    }
}

/**
 * A fixed-size single-producer, single-consumer queue of hits: the capture
 * thread offers, one consumer polls. Neither side locks or allocates. When
 * full, new hits are dropped and counted; a consumer that polls every UI frame
 * never lets [capacity] hits pile up.
 */
class HitQueue(
    val capacity: Int = DEFAULT_CAPACITY,
) {
    private val nanos = LongArray(capacity)
    private val frames = DoubleArray(capacity)
    private val levels = FloatArray(capacity)

    /** Hits ever offered and accepted. Volatile: publishes each slot to the consumer. */
    @Volatile private var written = 0L

    /** Hits ever polled. Volatile: frees each slot for the producer. */
    @Volatile private var read = 0L

    @Volatile
    var dropped = 0L
        private set

    /** Producer only. Returns false (and counts a drop) when full. */
    fun offer(
        nanoTime: Long,
        inputFrame: Double,
        levelDb: Float,
    ): Boolean {
        val w = written
        if (w - read >= capacity) {
            dropped++
            return false
        }
        val i = (w % capacity).toInt()
        nanos[i] = nanoTime
        frames[i] = inputFrame
        levels[i] = levelDb
        written = w + 1
        return true
    }

    /** Consumer only. Fills [out] with the oldest hit and returns true, or returns false when empty. */
    fun poll(out: DetectedHit): Boolean {
        val r = read
        if (r == written) return false
        val i = (r % capacity).toInt()
        out.nanoTime = nanos[i]
        out.inputFrame = frames[i]
        out.levelDb = levels[i]
        read = r + 1
        return true
    }

    /** Consumer only: discards everything queued. */
    fun clear() {
        read = written
    }

    companion object {
        const val DEFAULT_CAPACITY = 256
    }
}
